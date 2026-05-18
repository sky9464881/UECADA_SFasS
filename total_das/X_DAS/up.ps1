param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$ComposeArgs
)

$ErrorActionPreference = "Stop"

if (-not $ComposeArgs -or $ComposeArgs.Count -eq 0) {
  $ComposeArgs = @("up", "-d", "--build")
}

cmd /c "docker network inspect total-das-net >NUL 2>NUL"
if ($LASTEXITCODE -ne 0) {
  cmd /c "docker network create total-das-net >NUL 2>NUL"
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create Docker network 'total-das-net'. Is Docker Desktop running?"
  }
}

cmd /c "docker network inspect factory-net >NUL 2>NUL"
if ($LASTEXITCODE -ne 0) {
  cmd /c "docker network create factory-net >NUL 2>NUL"
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create Docker network 'factory-net'. Is Docker Desktop running?"
  }
}

Write-Host "==> docker compose $($ComposeArgs -join ' ')"
& docker compose @ComposeArgs
if ($LASTEXITCODE -ne 0) {
  throw "docker compose failed with exit code $LASTEXITCODE"
}
