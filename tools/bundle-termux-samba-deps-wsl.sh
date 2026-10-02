#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ROOT=/opt/pocketshare/termux-samba
DEBS="$ROOT/debs"
DEPS="$ROOT/deps"
ASSETS=${PROJECT_ROOT}/app/src/main/assets/samba-arm64
BASE=https://packages.termux.dev/apt/termux-main

mkdir -p "$DEBS" "$DEPS"
cd "$DEBS"
for package in \
  pool/main/liba/libandroid-execinfo/libandroid-execinfo_0.1-3_aarch64.deb \
  pool/main/liba/libandroid-spawn/libandroid-spawn_0.3_aarch64.deb \
  pool/main/libb/libbsd/libbsd_0.12.2_aarch64.deb \
  pool/main/libc/libcap/libcap_2.78_aarch64.deb \
  pool/main/libc/libcrypt/libcrypt_0.2-6_aarch64.deb \
  pool/main/libi/libiconv/libiconv_1.19_aarch64.deb \
  pool/main/libi/libicu/libicu_78.3_aarch64.deb \
  pool/main/libp/libpopt/libpopt_1.19-3_aarch64.deb \
  pool/main/libt/libtirpc/libtirpc_1.3.8_aarch64.deb \
  pool/main/n/ncurses/ncurses_6.6.20260307+really6.5.20250830_aarch64.deb \
  pool/main/o/openssl/openssl_1%3a3.6.3_aarch64.deb \
  pool/main/r/readline/readline_8.3.3_aarch64.deb; do
  wget -q "$BASE/$package"
done
for package in *.deb; do
  dpkg-deb -x "$package" "$DEPS"
done
cp -a /opt/pocketshare/termux-root/data/data/com.termux/files/usr/lib/. "$ASSETS/lib/"
cp -a "$DEPS/data/data/com.termux/files/usr/lib/." "$ASSETS/lib/"
