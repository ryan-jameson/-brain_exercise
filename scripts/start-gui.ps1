param(
  [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $NoBuild) {
  Write-Host 'Compiling SimpleServer...'
  javac -encoding UTF-8 -d out src\SimpleServer.java
}

Write-Host 'Starting SimpleServer GUI...'
java -cp out SimpleServer
