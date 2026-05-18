$ErrorActionPreference = "Stop"

cmd /c "docker network inspect total-das-net >NUL 2>NUL"
if ($LASTEXITCODE -ne 0) {
  cmd /c "docker network create total-das-net >NUL 2>NUL"
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create Docker network 'total-das-net'. Is Docker Desktop running?"
  }
}

Push-Location .\DAS
try {
  docker compose up -d --build
  if ($LASTEXITCODE -ne 0) {
    throw "DAS docker compose failed with exit code $LASTEXITCODE"
  }
}
finally {
  Pop-Location
}

Push-Location .\equip-sim
try {
  .\scripts\up.ps1 LINE-01
  .\scripts\up.ps1 LINE-02
  .\scripts\up.ps1 LINE-03
}
finally {
  Pop-Location
}

Push-Location .\X_DAS
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
Write-Host "LINE-01 UI:   http://localhost:1880"
Write-Host "LINE-02 UI:   http://localhost:1881"
Write-Host "LINE-03 UI:   http://localhost:1882"
Write-Host "X_DAS UI:     http://localhost:1890"
