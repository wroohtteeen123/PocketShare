#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

BUILD_ROOT=/opt/pocketshare
NDK=/opt/android/android-ndk-r27c
SRC="$BUILD_ROOT/samba-4.24.7"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
TERMUX_ROOT="$BUILD_ROOT/termux-root"

mkdir -p "$BUILD_ROOT"
if [ ! -d "$SRC" ]; then
  tar -xzf "${PROJECT_ROOT}/samba-latest.tar.gz" -C "$BUILD_ROOT"
fi
cd "$SRC"
cp "${PROJECT_ROOT}/tools/android-cross-answers.txt" android-cross-answers.txt
# Restore Samba's matching in-tree generator; Waf must compile it with HOSTCC.
sed -i "s/if False:/if not bld.CONFIG_SET('USING_SYSTEM_COMPILE_ET'):/g" third_party/heimdal_build/wscript_build
sed -i "/bld.env\['COMPILE_ET'\] = '\/usr\/bin\/compile_et'/d" third_party/heimdal_build/wscript_build
export CC="$TOOLCHAIN/aarch64-linux-android28-clang"
export CXX="$TOOLCHAIN/aarch64-linux-android28-clang++"
export HOSTCC=/usr/bin/gcc
export AR="$TOOLCHAIN/llvm-ar"
export RANLIB="$TOOLCHAIN/llvm-ranlib"
export PKG_CONFIG_SYSROOT_DIR="$TERMUX_ROOT"
export PKG_CONFIG_LIBDIR="$TERMUX_ROOT/data/data/com.termux/files/usr/lib/pkgconfig"
# Samba links to GnuTLS dynamically. Its private static-link dependency list is
# not needed here and would otherwise make pkg-config demand unrelated .pc files.
sed -i '/^Requires.private:/d; /^Libs.private:/d' "$PKG_CONFIG_LIBDIR/gnutls.pc"
./configure \
  --host=aarch64-linux-android \
  --cross-compile \
  --cross-answers=android-cross-answers.txt \
  --hostcc=/usr/bin/gcc \
  --prefix=/data/local/tmp/pocketshare \
  --without-ad-dc --without-ads --without-ldap --without-pam \
  --disable-cups --disable-iprint --disable-python --without-json --without-gpgme \
  --disable-avahi --without-acl-support --without-smb1-server --without-libunwind --without-libarchive \
  --without-dmapi --without-automount \
  --with-shared-modules='!vfs_snapper' \
  --nonshared-binary=smbd/smbd --disable-rpath-install --disable-rpath-private-install
