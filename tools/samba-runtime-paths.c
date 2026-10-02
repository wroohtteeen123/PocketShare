#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <limits.h>
#include <stdint.h>
#include <malloc.h>

/* Samba's ptr_overflow compares heap addresses against INTPTR_MAX.
 * Android TBI tags can make valid allocations exceed that bound. Limit
 * the compatibility change to the separately executed Samba process. */
static void configure_heap_addresses(void)
{
    void *probe = malloc(32);
    if (probe == NULL) _exit(126);
    int tagged = (uintptr_t)probe > (uintptr_t)INTPTR_MAX;
    free(probe);
    if (!tagged) return;
    if (!mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE)) {
        fprintf(stderr, "PocketShare: cannot disable incompatible heap pointer tags\n");
        _exit(126);
    }
    probe = malloc(32);
    if (probe == NULL || (uintptr_t)probe > (uintptr_t)INTPTR_MAX) {
        fprintf(stderr, "PocketShare: heap addresses remain incompatible with Samba\n");
        _exit(126);
    }
    free(probe);
}

static void set_path(const char *symbol, const char *root, const char *suffix)
{
    char path[PATH_MAX];
    int length = snprintf(path, sizeof(path), "%s/%s", root, suffix);
    const char *(*setter)(const char *) =
        (const char *(*)(const char *))dlsym(RTLD_DEFAULT, symbol);
    if (length < 0 || (size_t)length >= sizeof(path) || setter == NULL || setter(path) == NULL) {
        fprintf(stderr, "PocketShare: cannot configure %s\n", symbol);
        _exit(126);
    }
}

/* Applied only to PocketShare's Samba children via LD_PRELOAD. Samba's
 * module directory is a dynconfig setting, not an smb.conf parameter. */
__attribute__((constructor)) static void configure_samba_modules(void)
{
    configure_heap_addresses();
    const char *path = getenv("POCKETSHARE_MODULES");
    if (path == NULL || path[0] != '/') {
        fprintf(stderr, "PocketShare: missing absolute module directory\n");
        _exit(126);
    }
    const char *(*set_modules)(const char *) =
        (const char *(*)(const char *))dlsym(RTLD_DEFAULT, "set_dyn_MODULESDIR");
    if (set_modules == NULL || set_modules(path) == NULL) {
        fprintf(stderr, "PocketShare: cannot set Samba module directory\n");
        _exit(126);
    }
    const char *runtime = getenv("POCKETSHARE_RUNTIME");
    const char *state = getenv("POCKETSHARE_STATE");
    if (runtime == NULL || state == NULL || runtime[0] != '/' || state[0] != '/') {
        fprintf(stderr, "PocketShare: missing runtime/state directory\n");
        _exit(126);
    }
    set_path("set_dyn_BINDIR", runtime, "bin");
    set_path("set_dyn_SBINDIR", runtime, "bin");
    set_path("set_dyn_SAMBA_LIBEXECDIR", runtime, "libexec/samba");
    set_path("set_dyn_CONFIGFILE", state, "smb.conf");
    set_path("set_dyn_SMB_PASSWD_FILE", state, "private/smbpasswd");
    set_path("set_dyn_PRIVATE_DIR", state, "private");
    set_path("set_dyn_NCALRPCDIR", state, "run/ncalrpc");
    set_path("set_dyn_LOCKDIR", state, "lock");
    set_path("set_dyn_STATEDIR", state, "state");
    set_path("set_dyn_CACHEDIR", state, "cache");
    set_path("set_dyn_PIDDIR", state, "run");
    set_path("set_dyn_LOGFILEBASE", state, "log");
}
