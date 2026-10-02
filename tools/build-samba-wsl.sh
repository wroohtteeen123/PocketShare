#!/usr/bin/env bash
set -euo pipefail

BUILD_ROOT=/opt/pocketshare
NDK=/opt/android/android-ndk-r27c
SRC="$BUILD_ROOT/samba-4.24.7"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
TERMUX_ROOT="$BUILD_ROOT/termux-root"

cd "$SRC"
export CC="$TOOLCHAIN/aarch64-linux-android28-clang"
export CXX="$TOOLCHAIN/aarch64-linux-android28-clang++"
export HOSTCC=/usr/bin/gcc
export AR="$TOOLCHAIN/llvm-ar"
export RANLIB="$TOOLCHAIN/llvm-ranlib"
export PKG_CONFIG_SYSROOT_DIR="$TERMUX_ROOT"
export PKG_CONFIG_LIBDIR="$TERMUX_ROOT/data/data/com.termux/files/usr/lib/pkgconfig"
make -j2 smbd/smbd
