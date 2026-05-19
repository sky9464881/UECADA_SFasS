$ErrorActionPreference = "Stop"

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Failures = New-Object System.Collections.Generic.List[string]

function Invoke-StopStep {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Name,

    [Parameter(Mandatory = $true)]
    [scriptblock]$Action
  )

  Write-Host ""
  Write-Host "==> Stopping $Name"
  try {
    & $Action
  }
  catch {
    Write-Warning "Failed to stop $Name`: $($_.Exception.Message)"
    $Failures.Add($Name)
  }
}

Invoke-StopStep -Name "X_DAS" -Action {
  Push-Location (Join-Path $ScriptRoot "X_DAS")
  try {
    docker compose down
    if ($LASTEXITCODE -ne 0) {
      throw "X_DAS docker compose down failed with exit code $LASTEXITCODE"
    }
  }
  finally {
    Pop-Location
  }
}

Invoke-StopStep -Name "equip-sim" -Action {
  $scriptPath = Join-Path $ScriptRoot "equip-sim\scripts\up-all.ps1"
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath down
  if ($LASTEXITCODE -ne 0) {
    throw "equip-sim down failed with exit code $LASTEXITCODE"
  }
}

Invoke-StopStep -Name "DAS" -Action {
  Push-Location (Join-Path $ScriptRoot "DAS")
  try {
    docker compose down
    if ($LASTEXITCODE -ne 0) {
      throw "DAS docker compose down failed with exit code $LASTEXITCODE"
    }
  }
  finally {
    Pop-Location
  }
}

if ($Failures.Count -gt 0) {
  throw "Stop completed with failures: $($Failures -join ', ')"
}

Write-Host ""
Write-Host "All DAS, equip-sim, and X_DAS containers are stopped."
