#pragma once

#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstdarg>
#include <csignal>

namespace retropack {

class Logger {
public:
    static void init(
        const std::string& internalPath,
        const std::string& externalPath,
        const std::string& packageName,
        const std::string& appSlug,
        JavaVM* vm = nullptr,
        jobject activityObj = nullptr
    );

    static void log(int androidPriority, const char* tag, const char* fmt, ...);
    static void logV(int androidPriority, const char* tag, const char* fmt, va_list args);
    static void logCrash(int sig, siginfo_t* info, void* ucontext);
    static void installCrashHandlers();
    static void flush();

    static void info(const char* tag, const char* fmt, ...);
    static void warn(const char* tag, const char* fmt, ...);
    static void error(const char* tag, const char* fmt, ...);
    static void debug(const char* tag, const char* fmt, ...);

    static void sendBroadcast(const char* tag, const char* level, const char* message, bool isCrash);
    static void ensureDirectoryRecursive(const std::string& path);

private:
    static std::vector<std::string> s_logPaths;
    static std::vector<std::string> s_crashPaths;
    static std::mutex s_mutex;
    static bool s_initialized;
    static std::string s_packageName;
    static std::string s_appSlug;
    static JavaVM* s_vm;
    static jobject s_activityGlobalRef;
};

} // namespace retropack

#ifndef LOG_TAG
#define LOG_TAG "RetroEngine"
#endif

#define LOGI(...) retropack::Logger::info(LOG_TAG, __VA_ARGS__)
#define LOGW(...) retropack::Logger::warn(LOG_TAG, __VA_ARGS__)
#define LOGE(...) retropack::Logger::error(LOG_TAG, __VA_ARGS__)
#define LOGD(...) retropack::Logger::debug(LOG_TAG, __VA_ARGS__)
