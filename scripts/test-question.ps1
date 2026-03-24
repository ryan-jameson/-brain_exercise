param(
  [string]$Topic = "memory",
  [string]$Difficulty = "random",
  [switch]$NoBuild,
  [switch]$KeepServer
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $NoBuild) {
  Write-Host 'Compiling SimpleServer/SimpleClient...'
  javac -encoding UTF-8 -d out src\SimpleServer.java src\SimpleClient.java
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

$payload = "topic=$Topic&difficulty=$Difficulty"
Write-Host "Requesting question topic: $Topic"
java -cp out SimpleClient POST /cgi-bin/QuestionSelector $payload

if (-not $KeepServer) {
  Stop-Process -Id $server.Id -ErrorAction SilentlyContinue
} else {
  Write-Host 'Server still running (KeepServer enabled).'
}
