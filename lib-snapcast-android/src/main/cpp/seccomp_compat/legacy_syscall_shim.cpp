// Legacy-syscall shim for x86 / x86_64 on Android 8.0 - 9 (API 26 - 28).
//
// On those releases the seccomp filter that zygote installs for every app process (and that
// therefore applies to binaries the app exec()s, like snapclient) only allows the syscalls
// bionic itself issues. On x86/x86_64 that excludes the legacy path syscalls (readlink, open,
// stat, ...), since bionic always uses the *at variants.
//
// The x86/x86_64 emulator system images for those releases build the media stack
// (libmediametrics, libstagefright_foundation, libmedia, ...) against the UBSan standalone runtime,
// whose constructor calls raw readlink("/proc/self/exe"). snapclient links OpenSL ES and
// Oboe/AAudio, which pull that runtime in, so it dies with SIGSYS before main().
//
// The filter's action for a disallowed call is SECCOMP_RET_TRAP, which delivers a catchable
// SIGSYS. This file installs a SIGSYS handler from .preinit_array, which the dynamic linker runs
// before any shared library constructor. The handler re-issues the trapped call through its
// *at equivalent and writes the result into the interrupted context. A trapped syscall it does
// not know is reported on stderr and then fatal, as before.
//
// Other ABIs (arm, arm64) and API 29+ are unaffected; there the handler is never invoked.

#if defined(__ANDROID__) && (defined(__x86_64__) || defined(__i386__))

#include <cerrno>
#include <csignal>
#include <cstring>
#include <fcntl.h>
#include <sys/syscall.h>
#include <ucontext.h>
#include <unistd.h>

#ifndef SYS_SECCOMP
#define SYS_SECCOMP 1
#endif

namespace
{

#if defined(__x86_64__)
constexpr int kResultReg = REG_RAX;
constexpr int kArgRegs[] = {REG_RDI, REG_RSI, REG_RDX, REG_R10};
#else
constexpr int kResultReg = REG_EAX;
constexpr int kArgRegs[] = {REG_EBX, REG_ECX, REG_EDX, REG_ESI};
#endif

long raw_result(long ret)
{
    return ret == -1 ? -errno : ret;
}

// Returns true and sets *result (a raw kernel return value: >= 0, or -errno) when the trapped
// syscall has an allowed *at equivalent.
bool translate(int nr, const long* a, long* result)
{
    switch (nr)
    {
        case __NR_readlink:
            *result = raw_result(syscall(__NR_readlinkat, AT_FDCWD, a[0], a[1], a[2]));
            return true;
        case __NR_open:
            *result = raw_result(syscall(__NR_openat, AT_FDCWD, a[0], a[1], a[2]));
            return true;
        case __NR_access:
            *result = raw_result(syscall(__NR_faccessat, AT_FDCWD, a[0], a[1], 0));
            return true;
#if defined(__x86_64__)
        case __NR_stat:
            *result = raw_result(syscall(__NR_newfstatat, AT_FDCWD, a[0], a[1], 0));
            return true;
        case __NR_lstat:
            *result = raw_result(syscall(__NR_newfstatat, AT_FDCWD, a[0], a[1], AT_SYMLINK_NOFOLLOW));
            return true;
#else
        case __NR_stat64:
            *result = raw_result(syscall(__NR_fstatat64, AT_FDCWD, a[0], a[1], 0));
            return true;
        case __NR_lstat64:
            *result = raw_result(syscall(__NR_fstatat64, AT_FDCWD, a[0], a[1], AT_SYMLINK_NOFOLLOW));
            return true;
#endif
        case __NR_unlink:
            *result = raw_result(syscall(__NR_unlinkat, AT_FDCWD, a[0], 0));
            return true;
        case __NR_rmdir:
            *result = raw_result(syscall(__NR_unlinkat, AT_FDCWD, a[0], AT_REMOVEDIR));
            return true;
        case __NR_mkdir:
            *result = raw_result(syscall(__NR_mkdirat, AT_FDCWD, a[0], a[1]));
            return true;
        default:
            return false;
    }
}

void report_unhandled(int nr)
{
    char msg[64] = "seccomp shim: unhandled trapped syscall ";
    size_t len = strlen(msg);
    char digits[12];
    int n = 0;
    unsigned value = nr < 0 ? 0u : static_cast<unsigned>(nr);
    do
    {
        digits[n++] = static_cast<char>('0' + value % 10);
        value /= 10;
    } while (value != 0 && n < static_cast<int>(sizeof(digits)));
    while (n > 0 && len < sizeof(msg) - 2)
        msg[len++] = digits[--n];
    msg[len++] = '\n';
    (void)!write(STDERR_FILENO, msg, len);
}

void on_sigsys(int sig, siginfo_t* info, void* ucontext_raw)
{
    auto* uc = static_cast<ucontext_t*>(ucontext_raw);
    if (info->si_code == SYS_SECCOMP && uc != nullptr)
    {
        const int saved_errno = errno;
        long args[4];
        for (int i = 0; i < 4; ++i)
            args[i] = static_cast<long>(uc->uc_mcontext.gregs[kArgRegs[i]]);
        long result = 0;
        const bool handled = translate(info->si_syscall, args, &result);
        errno = saved_errno;
        if (handled)
        {
            uc->uc_mcontext.gregs[kResultReg] = result;
            return;
        }
        report_unhandled(info->si_syscall);
    }
    // Not ours to fix: die from SIGSYS as the process would have without the shim.
    signal(sig, SIG_DFL);
    raise(sig);
}

void install_legacy_syscall_shim(int, char**, char**)
{
    struct sigaction sa = {};
    sa.sa_sigaction = on_sigsys;
    sa.sa_flags = SA_SIGINFO | SA_RESTART;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGSYS, &sa, nullptr);
}

} // namespace

// Runs before the constructors of every shared library the executable depends on.
__attribute__((section(".preinit_array"), used)) static void (*const kPreinitLegacySyscallShim)(int, char**, char**) =
    install_legacy_syscall_shim;

#endif
