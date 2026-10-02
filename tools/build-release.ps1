param([string]$StudioHome, [string]$JdkHome, [string]$SdkHome)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'AndroidEnvironment.ps1')
$android = Get-PocketShareAndroidEnvironment @PSBoundParameters
$previousJava = $env:JAVA_HOME
$previousAndroid = $env:ANDROID_HOME
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $env:JAVA_HOME = $android.JdkHome
    $env:ANDROID_HOME = $android.SdkHome
    & .\gradlew.bat assembleRelease --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousAndroid
    Pop-Location
}
