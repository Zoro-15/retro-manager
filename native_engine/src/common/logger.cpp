#include "logger.hpp"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/time.h>
#include <fcntl.h>
#include <ucontext.h>
#include <dlfcn.h>
#include <fstream>
#include <sstream>

namespace retropack {

std::vector<std::string> Logger::s_logPaths;
std::vector<std::string> Logger::s_crashPaths;
std::mutex Logger::s_mutex;
bool Logger::s_initialized = false;
std::string Logger::s_packageName = "com.retropack.game";
std::string Logger::s_appSlug = "game";

void Logger::ensureDirectoryRecursive(const std::string& path) {
    if (path.empty()) return;
    std::string current = "";
    for (size_t i = 0; i < path.length(); ++i) {
        current += path[i];
        if (path[i] == '/' || i == path.length() - 1) {
            struct stat st;
            if (stat(current.c_str(), &st) != 0) {
                mkdir(current.c_str(), 0777);
            }
        }
    }
}

void Logger::init(
    const std::string& internalPath,
    const std::string& externalPath,
    const std::string& packageName,
    const std::string& appSlug
) {
    std::lock_guard<std::mutex> lock(s_mutex);
    s_packageName = packageName;
    s_appSlug = appSlug;
    s_logPaths.clear();
    s_crashPaths.clear();

    // 1. App-private internal storage
    if (!internalPath.empty()) {
        ensureDirectoryRecursive(internalPath);
        s_logPaths.push_back(internalPath + "/engine.log");
        s_logPaths.push_back(internalPath + "/launch.log");
        s_logPaths.push_back(internalPath + "/game_launch.log");
        s_logPaths.push_back(internalPath + "/" + appSlug + ".log");

        s_crashPaths.push_back(internalPath + "/crash.log");
        s_crashPaths.push_back(internalPath + "/engine.log");
    }

    // 2. App-specific external storage
    if (!externalPath.empty()) {
        ensureDirectoryRecursive(externalPath);
        s_logPaths.push_back(externalPath + "/engine.log");
        s_logPaths.push_back(externalPath + "/" + appSlug + ".log");
        s_logPaths.push_back(externalPath + "/retropack_runtime.log");

        s_crashPaths.push_back(externalPath + "/crash.log");
        s_crashPaths.push_back(externalPath + "/engine.log");
    }

    // 3. Public Download directories (best-effort across storage providers)
    std::vector<std::string> downloadBases = {
        "/sdcard/Download",
        "/storage/emulated/0/Download"
    };

    for (const auto& base : downloadBases) {
        std::string retroPackLogs = base + "/RetroPack/Logs";
        ensureDirectoryRecursive(retroPackLogs);
        s_logPaths.push_back(retroPackLogs + "/" + appSlug + ".log");
        s_crashPaths.push_back(retroPackLogs + "/" + appSlug + "_crash.log");

        std::string slugDir = base + "/logs/" + appSlug;
        ensureDirectoryRecursive(slugDir);
        s_logPaths.push_back(slugDir + "/" + appSlug + ".log");
        s_crashPaths.push_back(slugDir + "/crash.log");

        std::string generalDir = base + "/logs";
        ensureDirectoryRecursive(generalDir);
        s_logPaths.push_back(generalDir + "/" + appSlug + ".log");
        s_logPaths.push_back(generalDir + "/game_launch.log");
    }

    s_initialized = true;

    // Write initial session banner
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    struct tm* tmInfo = localtime(&tv.tv_sec);
    char timeBuf[64];
    strftime(timeBuf, sizeof(timeBuf), "%Y-%m-%d %H:%M:%S", tmInfo);

    char banner[1024];
    snprintf(banner, sizeof(banner),
        "\n================================================================================\n"
        "RetroPack Standalone Pure C++ Native Runtime Diagnostic Session\n"
        "================================================================================\n"
        "Timestamp   : %s.%03d\n"
        "Package ID  : %s\n"
        "App Slug    : %s\n"
        "Process PID : %d (TID: %d)\n"
        "Architecture: arm64-v8a (16 KB Page Aligned)\n"
        "================================================================================\n",
        timeBuf, static_cast<int>(tv.tv_usec / 1000),
        s_packageName.c_str(), s_appSlug.c_str(),
        getpid(), gettid()
    );

    for (const auto& path : s_logPaths) {
        FILE* f = fopen(path.c_str(), "a");
        if (f) {
            fputs(banner, f);
            fflush(f);
            fclose(f);
        }
    }
}

void Logger::logV(int androidPriority, const char* tag, const char* fmt, va_list args) {
    char buffer[2048];
    va_list argsCopy;
    va_copy(argsCopy, args);
    vsnprintf(buffer, sizeof(buffer), fmt, argsCopy);
    va_end(argsCopy);

    // 1. Android System Logcat
    __android_log_print(androidPriority, tag, "%s", buffer);

    // 2. Multi-Target Persistent Files
    const char* levelStr = "INFO";
    switch (androidPriority) {
        case ANDROID_LOG_DEBUG: levelStr = "DEBUG"; break;
        case ANDROID_LOG_INFO:  levelStr = "INFO";  break;
        case ANDROID_LOG_WARN:  levelStr = "WARN";  break;
        case ANDROID_LOG_ERROR: levelStr = "ERROR"; break;
        case ANDROID_LOG_FATAL: levelStr = "FATAL"; break;
    }

    struct timeval tv;
    gettimeofday(&tv, nullptr);
    struct tm* tmInfo = localtime(&tv.tv_sec);
    char timeBuf[64];
    strftime(timeBuf, sizeof(timeBuf), "%Y-%m-%d %H:%M:%S", tmInfo);

    std::lock_guard<std::mutex> lock(s_mutex);
    for (const auto& path : s_logPaths) {
        if (!path.empty()) {
            FILE* f = fopen(path.c_str(), "a");
            if (f) {
                fprintf(f, "[%s.%03d] [%s] [%s] %s\n",
                        timeBuf, static_cast<int>(tv.tv_usec / 1000),
                        levelStr, tag, buffer);
                fflush(f);
                fclose(f);
            }
        }
    }
}

void Logger::log(int androidPriority, const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logV(androidPriority, tag, fmt, args);
    va_end(args);
}

void Logger::info(const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logV(ANDROID_LOG_INFO, tag, fmt, args);
    va_end(args);
}

void Logger::warn(const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logV(ANDROID_LOG_WARN, tag, fmt, args);
    va_end(args);
}

void Logger::error(const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logV(ANDROID_LOG_ERROR, tag, fmt, args);
    va_end(args);
}

void Logger::debug(const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logV(ANDROID_LOG_DEBUG, tag, fmt, args);
    va_end(args);
}

void Logger::flush() {
    // In multi-target append mode, files are flushed after each line.
}

static std::string findModuleForAddress(uintptr_t addr) {
    Dl_info info;
    if (dladdr(reinterpret_cast<void*>(addr), &info) && info.dli_fname) {
        uintptr_t base = reinterpret_cast<uintptr_t>(info.dli_fbase);
        uintptr_t offset = addr - base;
        char buf[256];
        snprintf(buf, sizeof(buf), "%s (base 0x%lx, offset +0x%lx)",
                 info.dli_fname, (unsigned long)base, (unsigned long)offset);
        return std::string(buf);
    }
    return "Unknown Module (dladdr lookup failed)";
}

void Logger::logCrash(int sig, siginfo_t* info, void* ucontext) {
    const char* sigName = "UNKNOWN_SIGNAL";
    const char* sigDesc = "Fatal Unhandled Signal";

    switch (sig) {
        case SIGSEGV:
            sigName = "SIGSEGV";
            sigDesc = "Segmentation Fault (Invalid memory access / null pointer)";
            break;
        case SIGABRT:
            sigName = "SIGABRT";
            sigDesc = "Abort signal (assert() or abort() invoked)";
            break;
        case SIGBUS:
            sigName = "SIGBUS";
            sigDesc = "Bus Error (Unaligned memory access or hardware page fault)";
            break;
        case SIGFPE:
            sigName = "SIGFPE";
            sigDesc = "Floating Point Exception (Division by zero / arithmetic overflow)";
            break;
        case SIGILL:
            sigName = "SIGILL";
            sigDesc = "Illegal Instruction (Unsupported CPU instruction opcode)";
            break;
    }

    uintptr_t faultAddr = info ? reinterpret_cast<uintptr_t>(info->si_addr) : 0;
    int siCode = info ? info->si_code : 0;

    const char* codeDesc = "Unknown code";
    if (sig == SIGSEGV) {
        if (siCode == SEGV_MAPERR) codeDesc = "SEGV_MAPERR (Address not mapped to object)";
        else if (siCode == SEGV_ACCERR) codeDesc = "SEGV_ACCERR (Invalid permissions for mapped object)";
    } else if (sig == SIGBUS) {
        if (siCode == BUS_ADRALN) codeDesc = "BUS_ADRALN (Invalid address alignment)";
        else if (siCode == BUS_ADRERR) codeDesc = "BUS_ADRERR (Non-existent physical address)";
    }

    uintptr_t pc = 0;
    uintptr_t lr = 0;
    uintptr_t sp = 0;
    uintptr_t fp = 0;

#if defined(__aarch64__)
    if (ucontext) {
        auto* uc = reinterpret_cast<ucontext_t*>(ucontext);
        pc = static_cast<uintptr_t>(uc->uc_mcontext.pc);
        sp = static_cast<uintptr_t>(uc->uc_mcontext.sp);
        lr = static_cast<uintptr_t>(uc->uc_mcontext.regs[30]);
        fp = static_cast<uintptr_t>(uc->uc_mcontext.regs[29]);
    }
#endif

    std::string pcModule = findModuleForAddress(pc);
    std::string lrModule = findModuleForAddress(lr);

    struct timeval tv;
    gettimeofday(&tv, nullptr);
    struct tm* tmInfo = localtime(&tv.tv_sec);
    char timeBuf[64];
    strftime(timeBuf, sizeof(timeBuf), "%Y-%m-%d %H:%M:%S", tmInfo);

    char crashReport[4096];
    int written = snprintf(crashReport, sizeof(crashReport),
        "\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n"
        "FATAL CRASH DETECTED: %s (%d)\n"
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n"
        "Description   : %s\n"
        "Timestamp     : %s.%03d\n"
        "Package ID    : %s\n"
        "Process PID   : %d (TID: %d)\n"
        "Fault Address : 0x%016lx (Code: %d - %s)\n"
        "Program Count : PC = 0x%016lx -> %s\n"
        "Link Register : LR = 0x%016lx -> %s\n"
        "Stack Pointer : SP = 0x%016lx, FP = 0x%016lx\n",
        sigName, sig, sigDesc,
        timeBuf, static_cast<int>(tv.tv_usec / 1000),
        s_packageName.c_str(),
        getpid(), gettid(),
        static_cast<unsigned long>(faultAddr), siCode, codeDesc,
        static_cast<unsigned long>(pc), pcModule.c_str(),
        static_cast<unsigned long>(lr), lrModule.c_str(),
        static_cast<unsigned long>(sp), static_cast<unsigned long>(fp)
    );

#if defined(__aarch64__)
    if (ucontext && written > 0 && static_cast<size_t>(written) < sizeof(crashReport) - 512) {
        auto* uc = reinterpret_cast<ucontext_t*>(ucontext);
        char regBuf[512];
        snprintf(regBuf, sizeof(regBuf),
            "Registers     : X0=0x%016lx X1=0x%016lx X2=0x%016lx\n"
            "                X3=0x%016lx X4=0x%016lx X5=0x%016lx\n"
            "                X6=0x%016lx X7=0x%016lx X8=0x%016lx\n",
            static_cast<unsigned long>(uc->uc_mcontext.regs[0]),
            static_cast<unsigned long>(uc->uc_mcontext.regs[1]),
            static_cast<unsigned long>(uc->uc_mcontext.regs[2]),
            static_cast<unsigned long>(uc->uc_mcontext.regs[3]),
            static_cast<unsigned long>(uc->uc_mcontext.regs[4]),
            static_cast<unsigned long>(uc->uc_mcontext.regs[5]),
            static_cast<unsigned long>(uc->uc_mcontext.regs[6]),
            static_cast<unsigned long>(uc->uc_mcontext.regs[7]),
            static_cast<unsigned long>(uc->uc_mcontext.regs[8])
        );
        strncat(crashReport, regBuf, sizeof(crashReport) - strlen(crashReport) - 1);
    }
#endif

    strncat(crashReport,
        "================================================================================\n"
        "End of Fatal Crash Diagnostics\n"
        "================================================================================\n",
        sizeof(crashReport) - strlen(crashReport) - 1
    );

    // 1. Android Logcat Fatal Report
    __android_log_print(ANDROID_LOG_FATAL, "RetroEngine-Crash", "%s", crashReport);

    // 2. Write to all registered persistent crash logs and fsync immediately
    for (const auto& path : s_crashPaths) {
        if (!path.empty()) {
            int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_APPEND, 0666);
            if (fd >= 0) {
                write(fd, crashReport, strlen(crashReport));
                fsync(fd);
                close(fd);
            }
        }
    }
}

void Logger::installCrashHandlers() {
    // Allocate alternate signal stack to survive stack overflow / corrupted stack
    stack_t ss;
    ss.ss_sp = malloc(SIGSTKSZ);
    ss.ss_size = SIGSTKSZ;
    ss.ss_flags = 0;
    if (ss.ss_sp) {
        sigaltstack(&ss, nullptr);
    }

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = [](int sig, siginfo_t* info, void* ucontext) {
        Logger::logCrash(sig, info, ucontext);
        _exit(128 + sig);
    };
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);

    sigaction(SIGSEGV, &sa, nullptr);
    sigaction(SIGABRT, &sa, nullptr);
    sigaction(SIGBUS, &sa, nullptr);
    sigaction(SIGFPE, &sa, nullptr);
    sigaction(SIGILL, &sa, nullptr);
}

} // namespace retropack
