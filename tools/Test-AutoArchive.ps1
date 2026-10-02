$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'AndroidEnvironment.ps1')
$android = Get-PocketShareAndroidEnvironment
$gradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
Set-Location (Split-Path $PSScriptRoot -Parent)
$deps = @('org.tukaani/xz/1.10', 'org.apache.commons/commons-compress/1.28.0', 'commons-io/commons-io/2.20.0', 'org.apache.commons/commons-lang3/3.18.0', 'commons-codec/commons-codec/1.19.0', 'org.jetbrains.kotlin/kotlin-stdlib/2.2.10')
$jars = foreach ($dep in $deps) { Get-ChildItem "$gradleCache/caches/modules-2/files-2.1/$dep" -Recurse -Filter '*.jar' | Select-Object -ExpandProperty FullName }
$cp = 'app/build/tmp/kotlin-classes/release;' + ($jars -join ';')
New-Item -ItemType Directory -Force build/archive-tests | Out-Null
& (Join-Path $android.JdkHome "bin/javac.exe") -cp $cp -d build/archive-tests tools/TestAutoArchive.java
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& (Join-Path $android.JdkHome "bin/java.exe") -cp "build/archive-tests;$cp" TestAutoArchive
if ($LASTEXITCODE -ne 0) { throw 'Archive tests failed' }
