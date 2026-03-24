param(
  [switch]$NoBrowser,
  [switch]$NoBuild,
  [switch]$SmokeTest
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $NoBuild) {
  Write-Host 'Checking JavaFX SDK...'
  $fxPath = "$root\tools\javafx-sdk-22.0.2\lib"
  if (-not (Test-Path $fxPath)) {
      Write-Host "JavaFX SDK not found. Running setup-javafx.ps1..."
      & "$root\scripts\setup-javafx.ps1"
  }

  Write-Host 'Compiling Java sources...'
  javac -encoding UTF-8 -d out src\SimpleServer.java src\WebServer.java src\TaskGenerator.java src\DataManager.java
  if (Test-Path "src\BrowserClient.java") {
      javac -encoding UTF-8 --module-path $fxPath --add-modules javafx.controls,javafx.web -d out src\BrowserClient.java
  }
}

Write-Host 'Starting SimpleServer with GUI on http://localhost:8080/'
$server = Start-Process -FilePath java -ArgumentList '-cp','out','SimpleServer' -WorkingDirectory $root -PassThru

if ($SmokeTest) {
  Start-Sleep -Seconds 1
  $resp = Invoke-WebRequest 'http://localhost:8080/' -UseBasicParsing
  Write-Host "Smoke test status: $($resp.StatusCode)"
  Stop-Process -Id $server.Id -ErrorAction SilentlyContinue
  exit 0
}

Start-Sleep -Seconds 1
$fxPath = "$root\tools\javafx-sdk-22.0.2\lib"
$client = $null
if (-not $NoBrowser) {
  if (Test-Path "out\BrowserClient.class") {
      Write-Host 'Starting JavaFX Browser Client...'
      $clientArgs = "--module-path ""$fxPath"" --add-modules javafx.controls,javafx.web -cp out BrowserClient"
      $startInfo = New-Object System.Diagnostics.ProcessStartInfo
      $startInfo.FileName = "java"
      $startInfo.Arguments = $clientArgs
      $startInfo.WorkingDirectory = $root
      $startInfo.UseShellExecute = $true
      $client = [System.Diagnostics.Process]::Start($startInfo)
  } else {
      Write-Host 'Starting default browser...'
      Start-Process 'http://localhost:8080/'
  }
}

Write-Host 'Press Enter to stop the server and client...'
[void](Read-Host)
Stop-Process -Id $server.Id -ErrorAction SilentlyContinue
if ($null -ne $client -and -not $client.HasExited) {
    Stop-Process -Id $client.Id -ErrorAction SilentlyContinue
}
