$ErrorActionPreference = "Stop"

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Get-DockerNetworkInfo {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Name
  )

  $inspect = docker network inspect $Name --format '{{json .}}' 2>$null
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($inspect)) {
    return $null
  }

  return $inspect | ConvertFrom-Json
}

function Ensure-ExternalNetwork {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Name
  )

  $network = Get-DockerNetworkInfo -Name $Name
  if ($null -ne $network) {
    return
  }

  Write-Host "Creating Docker network '$Name'"
  docker network create $Name | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create Docker network '$Name'. Is Docker Desktop running?"
  }
}

function Repair-ComposeNetworkIfSafe {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Name,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedComposeNetworkLabel
  )

  $network = Get-DockerNetworkInfo -Name $Name
  if ($null -eq $network) {
    return
  }

  $actualLabel = $null
  if ($network.Labels) {
    $actualLabel = $network.Labels.'com.docker.compose.network'
  }

  if ($actualLabel -eq $ExpectedComposeNetworkLabel) {
    return
  }

  $attachedContainers = @()
  if ($network.Containers) {
    $attachedContainers = @(
      $network.Containers.PSObject.Properties |
        ForEach-Object { $_.Value.Name } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
  }

  if ($attachedContainers.Count -eq 0) {
    Write-Warning "Removing stale Docker network '$Name' because its Compose label is '$actualLabel' but expected '$ExpectedComposeNetworkLabel'."
    docker network rm $Name | Out-Null
    if ($LASTEXITCODE -ne 0) {
      throw "Failed to remove stale Docker network '$Name'. Remove it manually with: docker network rm $Name"
    }
    return
  }

  $containerList = $attachedContainers -join ", "
  $containerArgs = $attachedContainers -join " "
  throw @"
Docker network '$Name' already exists, but it was not created by this Compose project.
Current label com.docker.compose.network='$actualLabel', expected '$ExpectedComposeNetworkLabel'.
Attached containers: $containerList

Fix it once on this PC:
  docker rm -f $containerArgs
  docker network rm $Name
  .\total_das\start-all.ps1

If one of those containers is not from UECADA, stop it from its own project before removing the network.
"@
}

Ensure-ExternalNetwork -Name "total-das-net"
Ensure-ExternalNetwork -Name "factory-net"
Repair-ComposeNetworkIfSafe -Name "das_das-internal" -ExpectedComposeNetworkLabel "das-internal"

Push-Location (Join-Path $ScriptRoot "DAS")
try {
  docker compose up -d --build
  if ($LASTEXITCODE -ne 0) {
    throw "DAS docker compose failed with exit code $LASTEXITCODE"
  }
}
finally {
  Pop-Location
}

Push-Location (Join-Path $ScriptRoot "equip-sim")
try {
  .\scripts\up-all.ps1 up
}
finally {
  Pop-Location
}

Push-Location (Join-Path $ScriptRoot "X_DAS")
try {
  docker compose up -d --build
  if ($LASTEXITCODE -ne 0) {
    throw "X_DAS docker compose failed with exit code $LASTEXITCODE"
  }
}
finally {
  Pop-Location
}

Write-Host ""
Write-Host "DAS UI:       http://localhost:1888"
Write-Host "LINE-01 UI:   http://localhost:2880"
Write-Host "LINE-02 UI:   http://localhost:3880"
Write-Host "LINE-03 UI:   http://localhost:4880"
Write-Host "X_DAS UI:     http://localhost:1890"
