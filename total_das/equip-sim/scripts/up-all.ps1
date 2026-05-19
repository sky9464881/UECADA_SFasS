# scripts/up-all.ps1
# ----------------------------------------------------------------------
# Bring up 3 lines (LINE-01/02/03) + integration DAS in one shot.
#
# Usage:
#   .\scripts\up-all.ps1                       # bring everything up
#   .\scripts\up-all.ps1 down                  # tear everything down
#   .\scripts\up-all.ps1 logs LINE-02
#   .\scripts\up-all.ps1 logs INTEGRATION
#   .\scripts\up-all.ps1 ps
# ----------------------------------------------------------------------

param(
    [Parameter(Position = 0)]
    [ValidateSet("up", "down", "logs", "ps", "status")]
    [string]$Command = "up",

    [Parameter(Position = 1)]
    [string]$Line = ""
)

$ErrorActionPreference = "Stop"

# Force UTF-8 on console to avoid cp949 garbling
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
    $env:PYTHONIOENCODING = "utf-8"
} catch { }

# cd to repo root
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Join-Path $ScriptDir "..")

function Ensure-Network {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    docker network inspect factory-net *> $null
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prev

    if ($code -ne 0) {
        Write-Host "==> creating docker network: factory-net"
        docker network create factory-net | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "failed to create factory-net" }
    } else {
        Write-Host "==> factory-net already exists"
    }
}

if (Get-Command py -ErrorAction SilentlyContinue) {
    $script:PythonExe = "py"
    $script:PythonPrefixArgs = @("-3")
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    $script:PythonExe = "python"
    $script:PythonPrefixArgs = @()
} else {
    throw "Python was not found. Install Python 3 or make the Windows 'py' launcher available."
}

function Invoke-Python {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Args
    )
    $allArgs = @($script:PythonPrefixArgs) + @($Args)
    & $script:PythonExe @allArgs
}

function Get-EnvFile($lineId) {
    switch ($lineId) {
        "LINE-01" { return ".env.line01" }
        "LINE-02" { return ".env.line02" }
        "LINE-03" { return ".env.line03" }
        default   { throw "unknown line: $lineId" }
    }
}

function Build-LineFlow($lineId) {
    Write-Host "==> building Node-RED flow for $lineId (docker host-mode)"
    $env:LINE_ID = $lineId
    $outFile = "nodered/flows_das_$lineId.json"
    Invoke-Python nodered/build_flow_das.py --line-id $lineId --host-mode docker --out $outFile
    if ($LASTEXITCODE -ne 0) { throw "flow build failed for $lineId" }
    if (-not (Test-Path $outFile)) { throw "flow file not created: $outFile" }
    $sz = (Get-Item $outFile).Length
    Write-Host "    -> $outFile ($sz bytes)"
}

function Up-Line($lineId) {
    $envFile = Get-EnvFile $lineId
    Build-LineFlow $lineId
    Write-Host "==> docker compose --env-file $envFile up -d --build"
    docker compose --env-file $envFile up -d --build
    if ($LASTEXITCODE -ne 0) { throw "compose up failed for $lineId" }
}

function Down-Line($lineId) {
    $envFile = Get-EnvFile $lineId
    Write-Host "==> docker compose --env-file $envFile down"
    docker compose --env-file $envFile down
}

function Up-Integration {
    Write-Host "==> building integration DAS flow"
    Invoke-Python integration/build_flow_integration.py
    if ($LASTEXITCODE -ne 0) { throw "integration flow build failed" }
    Write-Host "==> docker compose -f integration/docker-compose.yml up -d --build"
    docker compose -f integration/docker-compose.yml up -d --build
    if ($LASTEXITCODE -ne 0) { throw "integration compose up failed" }
}

function Down-Integration {
    Write-Host "==> docker compose -f integration/docker-compose.yml down"
    docker compose -f integration/docker-compose.yml down
}

switch ($Command) {
    "up" {
        Ensure-Network
        foreach ($L in @("LINE-01", "LINE-02", "LINE-03")) { Up-Line $L }
        Up-Integration
        Write-Host ""
        Write-Host "==================================================================="
        Write-Host " UP COMPLETE"
        Write-Host "   LINE-01 Node-RED UI : http://localhost:2880"
        Write-Host "   LINE-02 Node-RED UI : http://localhost:3880"
        Write-Host "   LINE-03 Node-RED UI : http://localhost:4880"
        Write-Host "   INTEGRATION DAS UI  : http://localhost:5880"
        Write-Host ""
        Write-Host "   LINE-0X DAS OPC UA  : opc.tcp://localhost:4860 / 4960 / 5060"
        Write-Host "                          endpoint = line-das/LINE-0X"
        Write-Host "   INTEGRATION OPC UA  : opc.tcp://localhost:5860/integration-das"
        Write-Host "==================================================================="
    }
    "down" {
        Down-Integration
        foreach ($L in @("LINE-01", "LINE-02", "LINE-03")) {
            try { Down-Line $L } catch { Write-Warning $_ }
        }
        Write-Host "==> NOTE: factory-net is kept. To remove it: docker network rm factory-net"
    }
    "logs" {
        if (-not $Line) { throw "usage: .\scripts\up-all.ps1 logs <LINE-0X|INTEGRATION>" }
        switch ($Line.ToUpper()) {
            "INTEGRATION" { docker compose -f integration/docker-compose.yml logs -f }
            "DAS"         { docker compose -f integration/docker-compose.yml logs -f }
            default {
                $envFile = Get-EnvFile $Line
                docker compose --env-file $envFile logs -f
            }
        }
    }
    { @("ps", "status") -contains $_ } {
        docker compose --env-file .env.line01 ps
        docker compose --env-file .env.line02 ps
        docker compose --env-file .env.line03 ps
        docker compose -f integration/docker-compose.yml ps
    }
}
