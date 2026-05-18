param(
  [Parameter(Mandatory = $true, Position = 0)]
  [ValidateSet("LINE-01", "LINE-02", "LINE-03")]
  [string]$Line,

  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$ComposeArgs
)

$ErrorActionPreference = "Stop"

switch ($Line) {
  "LINE-01" { $envFile = ".env.line01" }
  "LINE-02" { $envFile = ".env.line02" }
  "LINE-03" { $envFile = ".env.line03" }
}

$flowFile = ".\nodered\flows_das_$Line.json"
if (-not (Test-Path -LiteralPath $flowFile)) {
  throw "Missing fixed line DAS flow file: $flowFile"
}

if (-not $ComposeArgs -or $ComposeArgs.Count -eq 0) {
  Write-Host "==> using fixed Node-RED flow for ${Line}: $flowFile"
  $ComposeArgs = @("up", "-d", "--build")
}

cmd /c "docker network inspect total-das-net >NUL 2>NUL"
if ($LASTEXITCODE -ne 0) {
  cmd /c "docker network create total-das-net >NUL 2>NUL"
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create Docker network 'total-das-net'. Is Docker Desktop running?"
  }
}

Write-Host "==> docker compose --env-file $envFile $($ComposeArgs -join ' ')"
& docker compose --env-file $envFile @ComposeArgs
if ($LASTEXITCODE -ne 0) {
  throw "docker compose failed with exit code $LASTEXITCODE"
}
