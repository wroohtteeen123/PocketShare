#!/usr/bin/env bash
set -Eeuo pipefail

# Collect the complete runtime dependency closure of the Termux Samba bundle.
# Run this script from WSL.  The output directory must be the Android asset
# directory that contains the runtime lib/ folder.

asset_root=${1:?"usage: bundle-termux-samba-closure-wsl.sh <asset-root>"}
index=/opt/pocketshare/termux-index/Packages
repo=https://packages.termux.dev/apt/termux-main
work_dir=/tmp/pocketshare-termux-runtime-closure

test -f "$index"
mkdir -p "$asset_root/lib" "$work_dir"

declare -A seen
queue=(
  samba
  krb5
  libbsd
  libmd
  libandroid-glob
  libresolv-wrapper
)

package_record() {
  awk -v wanted="$1" 'BEGIN { RS=""; FS="\n" } $1 == "Package: " wanted { print; exit }' "$index"
}

while ((${#queue[@]})); do
  package=${queue[0]}
  queue=("${queue[@]:1}")
  [[ -n ${seen[$package]:-} ]] && continue
  seen[$package]=1

  record=$(package_record "$package")
  if [[ -z $record ]]; then
    printf 'Skipping unavailable Termux package: %s\n' "$package" >&2
    continue
  fi

  filename=$(awk -F ': ' '$1 == "Filename" { print $2; exit }' <<<"$record")
  dependencies=$(awk -F ': ' '$1 == "Depends" || $1 == "Pre-Depends" { print $2 }' <<<"$record" \
    | tr ',' '\n' \
    | sed -E 's/[[:space:]]*\([^)]*\)//g; s/^[[:space:]]*//; s/[[:space:]].*$//; s/\|.*$//' \
    | sed '/^$/d')

  while IFS= read -r dependency; do
    [[ -n $dependency ]] && queue+=("$dependency")
  done <<<"$dependencies"

  [[ -z $filename ]] && continue
  deb="$work_dir/$package.deb"
  extract="$work_dir/$package"
  printf 'Bundling %s\n' "$package"
  curl -fsSL "$repo/$filename" -o "$deb"
  mkdir -p "$extract"
  dpkg-deb -x "$deb" "$extract"

  lib_dir="$extract/data/data/com.termux/files/usr/lib"
  if [[ -d $lib_dir ]]; then
    find "$lib_dir" -maxdepth 1 -type f -name '*.so*' -exec cp -Lf {} "$asset_root/lib/" \;
  fi
done

printf 'Bundled %s packages into %s/lib\n' "${#seen[@]}" "$asset_root"
