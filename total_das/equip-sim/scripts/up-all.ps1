# scripts/up-all.ps1
# ----------------------------------------------------------------------
# Bring up 3 lines (LINE-01/02/03) in one shot.
#
# Usage:
#   .\scripts\up-all.ps1                       # bring everything up
#   .\scripts\up-all.ps1 down                  # tear everything down
#   .\scripts\up-all.ps1 logs LINE-02
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

function Get-EnvFile($lineId) {
    switch ($lineId) {
        "LINE-01" { return ".env.line01" }
        "LINE-02" { return ".env.line02" }
        "LINE-03" { return ".env.line03" }
        default   { throw "unknown line: $lineId" }
    }
}

function Build-LineFlow($lineId) {
    $flowFile = "flows_das_$lineId.json"
    Write-Host "==> flow file for $lineId : $flowFile"
    Write-Host "    if missing, Node-RED will create a blank flow file in /data"
}

function Up-Line($lineId) {
    $envFile = Get-EnvFile $lineId
    Build-LineFlow $lineId

    Remove-Item Env:LINE_ID -ErrorAction SilentlyContinue
    Remove-Item Env:LINE_DIR -ErrorAction SilentlyContinue
    Remove-Item Env:LINE_NUM_SUFFIX -ErrorAction SilentlyContinue
    Remove-Item Env:COMPOSE_PROJECT_NAME -ErrorAction SilentlyContinue

    Write-Host "==> docker compose --env-file $envFile up -d --build"
    docker compose --env-file $envFile up -d --build
    if ($LASTEXITCODE -ne 0) { throw "compose up failed for $lineId" }
}

function Down-Line($lineId) {
    $envFile = Get-EnvFile $lineId
    Write-Host "==> docker compose --env-file $envFile down"
    docker compose --env-file $envFile down
}


switch ($Command) {
    "up" {
        Ensure-Network
        foreach ($L in @("LINE-01", "LINE-02", "LINE-03")) { Up-Line $L }
        Write-Host ""
        Write-Host "==================================================================="
        Write-Host " UP COMPLETE"
        Write-Host "   LINE-01 Node-RED UI : http://localhost:2880"
        Write-Host "   LINE-02 Node-RED UI : http://localhost:3880"
        Write-Host "   LINE-03 Node-RED UI : http://localhost:4880"
        Write-Host ""
        Write-Host "   LINE-0X DAS OPC UA  : opc.tcp://localhost:4860 / 4960 / 5060"
        Write-Host "                          endpoint = line-das/LINE-0X"
        Write-Host "==================================================================="
    }
    "down" {
        foreach ($L in @("LINE-01", "LINE-02", "LINE-03")) {
            try { Down-Line $L } catch { Write-Warning $_ }
        }
        Write-Host "==> NOTE: factory-net is kept. To remove it: docker network rm factory-net"
    }

    "logs" {
    if (-not $Line) { 
        throw "usage: .\scripts\up-all.ps1 logs <LINE-01|LINE-02|LINE-03>" }
        $envFile = Get-EnvFile $Line.ToUpper()
        docker compose --env-file $envFile logs -f
    }

    { @("ps", "status") -contains $_ } {
        docker compose --env-file .env.line01 ps
        docker compose --env-file .env.line02 ps
        docker compose --env-file .env.line03 ps
    }
}
