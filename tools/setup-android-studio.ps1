param([string]$StudioHome, [string]$JdkHome, [string]$SdkHome)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'AndroidEnvironment.ps1')
$android = Get-PocketShareAndroidEnvironment @PSBoundParameters
$projectRoot = Split-Path $PSScriptRoot -Parent
function Set-LocalJavaProperty([string]$File, [string]$Name, [string]$Value) {
    $lines = if (Test-Path -LiteralPath $File) { @(Get-Content -LiteralPath $File) } else { @() }
    $lines = @($lines | Where-Object { $_ -notmatch ('^\s*' + [regex]::Escape($Name) + '\s*[=:]') })
    $escaped = $Value.Replace('\', '/').Replace(':', '\:')
    [IO.File]::WriteAllLines($File, [string[]]($lines + "$Name=$escaped"), [Text.UTF8Encoding]::new($false))
}
Set-LocalJavaProperty (Join-Path $projectRoot 'local.properties') 'sdk.dir' $android.SdkHome
New-Item -ItemType Directory -Force (Join-Path $projectRoot '.gradle') | Out-Null
Set-LocalJavaProperty (Join-Path $projectRoot '.gradle/config.properties') 'java.home' $android.JdkHome
Write-Output "SDK: $($android.SdkHome)"
Write-Output "Gradle JDK: $($android.JdkHome)"
Write-Output 'Ready. Open the project root in Android Studio; select GRADLE_LOCAL_JAVA_HOME for Gradle JDK.'
