# PocketShare 1.3.3 — multi-user sharing

## Behavior

- Settings → Manage users supports adding, renaming, changing passwords, enabling/disabling and deleting up to 32 users.
- Accounts are independent of Android. Legacy system-account credentials are not imported; create replacement accounts explicitly.
- New users default to read-only. Every user accesses the same currently configured Share folder.
- The existing write switch now applies only to guests. Guests and named users can coexist. Anyone can still use anonymous access while guests are enabled.
- Required SMB3 encryption remains incompatible with guests.
- Account changes take effect after stopping and restarting sharing; active sessions are not revoked immediately on save.
- Passwords are encrypted with an Android Keystore AES-GCM key and account-name AAD. Plaintext is neither stored in preferences nor passed in process arguments. Samba still needs its private password verifier database.

## Implementation

- `ShareAccounts.kt`: validated account metadata, encrypted credentials and explicit authorization lists.
- `AccountPanel.kt`: native Material account editor; passwords excluded from view-state saving.
- `samba-account-nss.c`: process-local passwd lookup with distinct stable UIDs. Loaded only into bundled Samba processes. Android system accounts are untouched.
- `SmbService.kt`: rebuilds a dedicated `private/accounts.tdb` database on startup using the bundled `tdbsam` backend, creates enabled users using smbpasswd stdin, defaults Share to read-only and grants write access via an explicit write list. Encrypted app account settings remain unchanged; the old text database is no longer used.
- Guest access enabled uses `map to guest = Bad User`: unknown client identities (including Windows automatically supplied accounts) receive only guest permissions. Existing enabled users with a wrong password are not mapped to guests. Guest access disabled uses `Never`.
- Disabled/deleted accounts are absent from the rebuilt password database and may consequently fall back to guest permissions when guest access is enabled. Disable guest access if only named users should connect. Windows client policy may independently block guests.

## Checks performed

- 1.3.3 WSL TDB tests pass: two independent virtual accounts, password update changes only the target record, rebuilding removes deleted accounts, database permissions are 0600, and `testparm` accepts the configuration. These use host Samba, not Android; the device-specific text-backend failure is bypassed but Android behavior remains to be verified.

- 1.3.2 captures account-tool exit codes and diagnostic output, redacts the submitted password and long hexadecimal credentials before truncation, and creates a current-attempt log before provisioning. Early stdin closure no longer discards subprocess output. This improves diagnosis; the reported Android `pc123` provisioning failure is not yet reproduced or confirmed fixed.
- `change notify` is now in the global section, matching the bundled server's parameter scope.

- Resource XML, bilingual placeholders and UI regression checks.
- JVM tests: username injection rejection, duplicate names/UIDs, read-only default, individual write permissions, guest separation, disabled/deleted users excluded from generated lists.
- WSL C tests: virtual name/UID lookup, distinct identities, ordinary root fallback, short buffers and supplementary groups.
- WSL Samba `smbpasswd` tests: two independent virtual accounts created without OS accounts; distinct password verifiers; rebuilding the passdb removes a deleted account. `testparm` accepted the test configuration.
- ARM64 NDK compilation with warnings treated as errors.

## Not yet verified on Android

No ADB device was connected. Keystore persistence, UI layout, actual client authentication, enforcement of write restrictions, guest negotiation and encrypted sessions require a rooted ARM64 device.

Device acceptance sequence: create read-only Alice and writable Bob; restart sharing; test listing/downloading/uploading/deleting separately; reject wrong passwords; test guest with read-only then write enabled; disable/delete a named user and restart; confirm that named credentials fail while separately configured anonymous access still works. Test rename/password replacement and app relaunch. Test required SMB3 encryption with guests off. Disconnect previous Windows SMB sessions before switching credentials to the same server.

Build native runtime with `tools/build-account-runtime.ps1`, run `tools/Test-Accounts.ps1` after Kotlin compilation, and package with `tools/build-release.ps1`.
