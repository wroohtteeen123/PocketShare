#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SOURCE=${PROJECT_ROOT}/app/src/main/assets/samba-arm64
REPLACEMENT=${PROJECT_ROOT}/app/src/main/assets/samba-arm64-real
BACKUP=/opt/pocketshare/samba-assets-symlink-backup

test -d "$SOURCE"
rm -rf "$REPLACEMENT"
test ! -e "$BACKUP"
mkdir "$REPLACEMENT"
(cd "$SOURCE" && find . -type f -print0 | tar --null -T - -cf -) | tar -xf - -C "$REPLACEMENT"
(cd "$SOURCE" && find . -type l -printf '%p\t%l\n') > "$REPLACEMENT/samba-links.txt"
mv "$SOURCE" "$BACKUP"
mv "$REPLACEMENT" "$SOURCE"
find "$SOURCE" -type l -print | wc -l
du -sh "$SOURCE"
