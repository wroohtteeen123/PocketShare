#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${PROJECT_ROOT}"
test "$(id -u)" = 0
test_root=$(mktemp -d /tmp/pocketshare-passdb-XXXXXX)
chmod 700 "$test_root"
mkdir -p "$test_root/private" "$test_root/lock" "$test_root/state" "$test_root/cache"
printf 'alice:200000000\nbob:200000001\n' > "$test_root/private/accounts.passwd"
cat > "$test_root/smb.conf" <<EOF
[global]
workgroup = WORKGROUP
security = user
passdb backend = tdbsam:$test_root/private/accounts.tdb
private dir = $test_root/private
lock directory = $test_root/lock
state directory = $test_root/state
cache directory = $test_root/cache
map to guest = Never
[Share]
path = $test_root
read only = yes
valid users = alice bob
write list = bob
force user = root
EOF
export POCKETSHARE_STATE="$test_root"
export LD_PRELOAD="$PWD/build/account-tests/nss.so"
umask 077
: > "$test_root/private/accounts.tdb"
printf 'test-alice-123\ntest-alice-123\n' | smbpasswd -c "$test_root/smb.conf" -s -a alice >/dev/null 2>&1
printf 'test-bob-456\ntest-bob-456\n' | smbpasswd -c "$test_root/smb.conf" -s -a bob >/dev/null 2>&1
/usr/bin/python3 tools/test-account-tdb.py "$test_root"
testparm -s "$test_root/smb.conf" >/dev/null 2>&1
echo 'PASS: tdbsam creates independent virtual users, updates passwords and rebuilds without deleted accounts'
# Only remove the freshly generated test directory after validating its absolute prefix.
case "$test_root" in /tmp/pocketshare-passdb-*) rm -rf -- "$test_root";; *) exit 1;; esac
