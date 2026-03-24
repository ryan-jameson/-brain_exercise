param(
  [string]$Version = "22",
  [string]$InstallDir = "$PSScriptRoot\..\tools",
  [switch]$NoEnv
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$candidateVersions = @()
if ($Version -match '\.') {
  $candidateVersions += $Version
} else {
  $candidateVersions += "$Version.0.2", "$Version.0.1", "$Version.0.0", $Version
}
$downloadDir = Join-Path $root "downloads"
$zipPath = $null
$targetDir = $null

New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null

foreach ($candidate in $candidateVersions) {
  $zipName = "openjfx-${candidate}_windows-x64_bin-sdk.zip"
  $downloadUrl = "https://download2.gluonhq.com/openjfx/$candidate/$zipName"
  $zipPath = Join-Path $downloadDir $zipName
  $targetDir = Join-Path $InstallDir "javafx-sdk-$candidate"

  if (Test-Path $zipPath) {
    Write-Host "Using existing archive: $zipPath"
    break
  }

  Write-Host "Downloading JavaFX $candidate from $downloadUrl"
  try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath
    if (Test-Path $zipPath) {
      break
    }
  } catch {
    Write-Host "Download failed for $candidate, trying next..."
    $zipPath = $null
    $targetDir = $null
  }
}

if (-not $zipPath) {
  Write-Host 'Failed to download JavaFX SDK. Please check your network or version.'
  exit 1
}

if (Test-Path $targetDir) {
  Write-Host "Removing existing: $targetDir"
  Remove-Item -Recurse -Force $targetDir
}

Write-Host "Extracting to $InstallDir"
Expand-Archive -Path $zipPath -DestinationPath $InstallDir

if (-not $NoEnv) {
  $env:JAVAFX_SDK = $targetDir
  [Environment]::SetEnvironmentVariable('JAVAFX_SDK', $targetDir, 'User')
  Write-Host "JAVAFX_SDK set to $targetDir"
}

Write-Host 'JavaFX setup complete.'
