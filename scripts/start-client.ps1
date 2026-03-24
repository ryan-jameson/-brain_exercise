param(
  [string]$Url = "http://localhost:8080/",
  [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$javafxHome = $env:JAVAFX_SDK
if (-not $javafxHome) {
  $javafxHome = $env:JAVAFX_HOME
}

if (-not $javafxHome) {
  Write-Host '未找到 JavaFX SDK。请设置环境变量 JAVAFX_SDK 或 JAVAFX_HOME 指向 JavaFX SDK 路径。'
  Write-Host '例如: $env:JAVAFX_SDK = "C:\\javafx-sdk-21"'
  exit 1
}

$modulePath = Join-Path $javafxHome 'lib'

if (-not $NoBuild) {
  Write-Host 'Compiling BrowserClient...'
  javac -encoding UTF-8 -d out --module-path $modulePath --add-modules javafx.controls,javafx.web src\BrowserClient.java
}

Write-Host "Launching BrowserClient with $Url"
java --module-path $modulePath --add-modules javafx.controls,javafx.web -cp out BrowserClient $Url
