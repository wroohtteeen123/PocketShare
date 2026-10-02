# Shared local environment discovery; does not change machine/user settings.
function Get-PocketShareAndroidEnvironment {
    param([string]$StudioHome, [string]$JdkHome, [string]$SdkHome)
    if (!$StudioHome) {
        $StudioHome = $env:ANDROID_STUDIO_HOME
        if (!$StudioHome) { $StudioHome = Join-Path $env:ProgramFiles 'Android/Android Studio' }
    }
    if (!$JdkHome) {
        $bundledJdk = Join-Path $StudioHome 'jbr'
        $JdkHome = if (Test-Path (Join-Path $bundledJdk 'bin/java.exe')) { $bundledJdk } else { $env:JAVA_HOME }
    }
    if (!$JdkHome -or !(Test-Path (Join-Path $JdkHome 'bin/java.exe'))) {
        throw 'JDK not found. Supply -JdkHome, JAVA_HOME, or ANDROID_STUDIO_HOME.'
    }
    if (!$SdkHome) {
        $SdkHome = $env:ANDROID_HOME
        if (!$SdkHome) { $SdkHome = $env:ANDROID_SDK_ROOT }
        if (!$SdkHome) { $SdkHome = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    }
    if (!(Test-Path (Join-Path $SdkHome 'platforms/android-35/android.jar'))) {
        throw 'Android SDK Platform 35 not found. Install it in SDK Manager or supply -SdkHome.'
    }
    [pscustomobject]@{
        StudioHome = [IO.Path]::GetFullPath($StudioHome)
        JdkHome = [IO.Path]::GetFullPath($JdkHome)
        SdkHome = [IO.Path]::GetFullPath($SdkHome)
    }
}
