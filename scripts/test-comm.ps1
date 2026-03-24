param(
  [string]$ClientPath = "/index.html",
  [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $NoBuild) {
  Write-Host 'Compiling SimpleServer/SimpleClient...'
  javac -encoding UTF-8 -d out src\SimpleServer.java src\SimpleClient.java
}

$logFile = Join-Path $root 'server_log.txt'
if (Test-Path $logFile) {
  Remove-Item $logFile -Force
}

Write-Host 'Starting SimpleServer (headless)...'
$server = Start-Process -FilePath java -ArgumentList '-cp','out','SimpleServer','headless' -WorkingDirectory $root -PassThru

Write-Host 'Waiting for port 8080...'
$ready = $false
for ($i = 0; $i -lt 20; $i++) {
  $ready = (Test-NetConnection -ComputerName localhost -Port 8080).TcpTestSucceeded
  if ($ready) { break }
  Start-Sleep -Milliseconds 300
}

if (-not $ready) {
  Write-Host 'Server did not open port 8080.'
  Stop-Process -Id $server.Id -ErrorAction SilentlyContinue
  exit 1
}

Write-Host "Running client request: $ClientPath"
java -cp out SimpleClient $ClientPath
Start-Sleep -Milliseconds 300

if (Test-Path $logFile) {
  $content = Get-Content $logFile
  if ($content -match 'GET') {
    Write-Host 'Log check: OK'
  } else {
    Write-Host 'Log check: FAILED (no GET found)'
  }
} else {
  Write-Host 'Log check: FAILED (log file missing)'
}

Stop-Process -Id $server.Id -ErrorAction SilentlyContinue
