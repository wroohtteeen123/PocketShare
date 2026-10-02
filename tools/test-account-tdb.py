"""Verify test-only TDB records without emitting credentials or modifying OS users."""
import os
import subprocess
import sys
import tdb

root = sys.argv[1]
assert root.startswith('/tmp/pocketshare-passdb-')
path = root + '/private/accounts.tdb'

def record(name):
    db = tdb.Tdb(path)
    try:
        return db.get(('USER_' + name).encode() + b'\0')
    finally:
        db.close()

def password(name, value, add=False):
    args = ['smbpasswd', '-c', root + '/smb.conf', '-s']
    if add:
        args.append('-a')
    args.append(name)
    subprocess.run(args, input=(value + '\n') * 2, text=True, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

alice = record('alice')
bob = record('bob')
assert alice and bob and alice != bob
password('alice', 'replacement-alice-789')
assert record('alice') != alice
assert record('bob') == bob
# Match the app's dedicated database rebuild; all database handles are closed.
with open(path, 'wb'):
    pass
password('bob', 'test-bob-456', add=True)
assert record('alice') is None
assert record('bob')
assert os.stat(path).st_mode & 0o777 == 0o600
print('PASS: tdbsam user creation, independent records, password update, deletion on rebuild and private file mode')
