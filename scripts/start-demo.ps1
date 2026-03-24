param(
  [switch]$NoBuild,
  [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $NoBuild) {
  Write-Host 'Compiling Java demo server...'
  javac -encoding UTF-8 -d out src\SimpleServer.java
}

Write-Host 'Starting SimpleServer on port 8080...'
$server = Start-Process -FilePath java -ArgumentList '-cp','out','SimpleServer' -WorkingDirectory $root -PassThru

Write-Host 'Waiting for server port to open...'
$ready = $false
for ($i = 0; $i -lt 20; $i++) {
  $ready = (Test-NetConnection -ComputerName localhost -Port 8080).TcpTestSucceeded
  if ($ready) { break }
  Start-Sleep -Milliseconds 300
}

if (-not $ready) {
  Write-Host 'Server did not open port 8080 in time.'
  Stop-Process -Id $server.Id -ErrorAction SilentlyContinue
  exit 1
}

if (-not $NoBrowser) {
  $url = 'http://localhost:8080/'
  $javafxHome = $env:JAVAFX_SDK
  if (-not $javafxHome) {
    $javafxHome = $env:JAVAFX_HOME
  }
  if ($javafxHome) {
    Write-Host 'Launching JavaFX BrowserClient...'
    & "$PSScriptRoot\start-client.ps1" -Url $url -NoBuild
  } else {
    Write-Host '未检测到 JavaFX SDK，尝试使用系统默认浏览器打开。'
    try {
      Start-Process -FilePath $url
    } catch {
      try {
        Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'start', '', $url
      } catch {
        try {
          Start-Process -FilePath 'rundll32.exe' -ArgumentList 'url.dll,FileProtocolHandler', $url
        } catch {
          Write-Host "Failed to open browser automatically. Please open: $url"
        }
      }
    }
  }
}

Write-Host 'Press Enter to stop the server...'
[void](Read-Host)
Stop-Process -Id $server.Id -ErrorAction SilentlyContinue
