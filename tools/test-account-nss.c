#define _GNU_SOURCE
#include <assert.h>
#include <pwd.h>
#include <grp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <errno.h>
#include <unistd.h>

int main(void) {
    char dir[] = "/tmp/pocketshare-nss-XXXXXX";
    assert(mkdtemp(dir));
    char path[256]; snprintf(path, sizeof(path), "%s/private", dir); assert(mkdir(path, 0700) == 0);
    snprintf(path, sizeof(path), "%s/private/accounts.passwd", dir);
    FILE *f = fopen(path, "w"); assert(f);
    fputs("alice:200000000\nbob:200000001\nabcdefghijklmnopqrstuvwxyz123456:200000002\n", f); fclose(f);
    setenv("POCKETSHARE_STATE", dir, 1);
    struct passwd *pw = getpwnam("alice"); assert(pw && pw->pw_uid == 200000000 && pw->pw_gid == 65534);
    pw = getpwnam("bob"); assert(pw && pw->pw_uid == 200000001);
    pw = getpwuid(200000000); assert(pw && strcmp(pw->pw_name, "alice") == 0);
    pw = getpwnam("root"); assert(pw && pw->pw_uid == 0);
    pw = getpwnam("abcdefghijklmnopqrstuvwxyz123456"); assert(pw && strcmp(pw->pw_shell, "/system/bin/false") == 0);
    struct passwd out, *result = NULL; char shortbuf[8];
    assert(getpwnam_r("alice", &out, shortbuf, sizeof(shortbuf), &result) == ERANGE && result == NULL);
    gid_t groups[4]; int count = 4; assert(getgrouplist("alice", 65534, groups, &count) == 1 && count == 1 && groups[0] == 65534);
    count = 0; assert(getgrouplist("alice", 65534, groups, &count) == -1 && count == 1);
    unlink(path); snprintf(path, sizeof(path), "%s/private", dir); rmdir(path); rmdir(dir);
    puts("PASS: virtual names and UIDs, distinct identities, root fallback, buffers and supplementary groups");
}
