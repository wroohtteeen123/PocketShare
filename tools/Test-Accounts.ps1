$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'AndroidEnvironment.ps1')
$android = Get-PocketShareAndroidEnvironment
$gradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
Set-Location (Split-Path $PSScriptRoot -Parent)
$jars = Get-ChildItem "$gradleCache/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.2.10" -Recurse -Filter '*.jar' | Select-Object -ExpandProperty FullName
$cp = 'app/build/tmp/kotlin-classes/release;' + ($jars -join ';')
New-Item -ItemType Directory -Force build/account-tests | Out-Null
& (Join-Path $android.JdkHome "bin/javac.exe") -cp $cp -d build/account-tests tools/TestAccountPolicy.java
if ($LASTEXITCODE -ne 0) { throw 'Account tests did not compile' }
& (Join-Path $android.JdkHome "bin/java.exe") -cp "build/account-tests;$cp" TestAccountPolicy
if ($LASTEXITCODE -ne 0) { throw 'Account policy tests failed' }
