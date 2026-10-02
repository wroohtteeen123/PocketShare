$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
& tools/android-ndk-r27c/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android28-clang.cmd -shared -fPIC -O2 -Wall -Wextra -Werror tools/samba-runtime-paths.c tools/samba-account-nss.c -ldl -o app/src/main/assets/samba-arm64/lib/libpocketshare-paths.so
if ($LASTEXITCODE -ne 0) { throw 'Samba account runtime build failed' }
