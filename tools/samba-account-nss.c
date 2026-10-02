/* Process-local passwd entries. No Android system files or accounts are changed. */
#define _GNU_SOURCE
#include <pwd.h>
#include <grp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <dlfcn.h>
#include <limits.h>

static int lookup(const char *name, uid_t uid, struct passwd *pw, char *buf, size_t size) {
    const char *state = getenv("POCKETSHARE_STATE");
    char path[PATH_MAX], line[128], user[33]; unsigned int id;
    if (!state || state[0] != '/') return 0;
    int n = snprintf(path, sizeof(path), "%s/private/accounts.passwd", state);
    if (n < 0 || (size_t)n >= sizeof(path)) return 0;
    FILE *f = fopen(path, "re");
    if (!f) return 0;
    while (fgets(line, sizeof(line), f)) {
        if (sscanf(line, "%32[a-z0-9_-]:%u", user, &id) != 2 || id < 200000000 || id > 200999999) continue;
        if (name ? strcmp(name, user) != 0 : uid != id) continue;
        fclose(f);
        size_t len = strlen(user) + 1;
        if (size < len + 40) return -ERANGE;
        strcpy(buf, user);
        pw->pw_name = buf; pw->pw_uid = id; pw->pw_gid = 65534;
        pw->pw_passwd = buf + len; strcpy(pw->pw_passwd, "x");
        pw->pw_gecos = buf + len + 2; strcpy(pw->pw_gecos, "PocketShare");
        pw->pw_dir = buf + len + 14; strcpy(pw->pw_dir, "/");
        pw->pw_shell = buf + len + 16; strcpy(pw->pw_shell, "/system/bin/false");
        return 1;
    }
    fclose(f); return 0;
}
int getpwnam_r(const char *name, struct passwd *pw, char *buf, size_t size, struct passwd **result) {
    int found = lookup(name, 0, pw, buf, size);
    if (found) { *result = found > 0 ? pw : NULL; return found > 0 ? 0 : -found; }
    int (*real)(const char *, struct passwd *, char *, size_t, struct passwd **) = dlsym(RTLD_NEXT, "getpwnam_r");
    if (!real) { *result = NULL; return ENOSYS; }
    return real(name, pw, buf, size, result);
}
int getpwuid_r(uid_t uid, struct passwd *pw, char *buf, size_t size, struct passwd **result) {
    int found = lookup(NULL, uid, pw, buf, size);
    if (found) { *result = found > 0 ? pw : NULL; return found > 0 ? 0 : -found; }
    int (*real)(uid_t, struct passwd *, char *, size_t, struct passwd **) = dlsym(RTLD_NEXT, "getpwuid_r");
    if (!real) { *result = NULL; return ENOSYS; }
    return real(uid, pw, buf, size, result);
}
static __thread struct passwd entry;
static __thread char buffer[1024];
struct passwd *getpwnam(const char *name) {
    struct passwd *result = NULL; int err = getpwnam_r(name, &entry, buffer, sizeof(buffer), &result);
    if (err) errno = err;
    return result;
}
struct passwd *getpwuid(uid_t uid) {
    struct passwd *result = NULL; int err = getpwuid_r(uid, &entry, buffer, sizeof(buffer), &result);
    if (err) errno = err;
    return result;
}
int getgrouplist(const char *name, gid_t group, gid_t *groups, int *count) {
    struct passwd pw; char buf[128];
    if (lookup(name, 0, &pw, buf, sizeof(buf)) > 0) {
        if (*count < 1) { *count = 1; return -1; }
        groups[0] = group; *count = 1; return 1;
    }
    int (*real)(const char *, gid_t, gid_t *, int *) = dlsym(RTLD_NEXT, "getgrouplist");
    return real ? real(name, group, groups, count) : -1;
}
