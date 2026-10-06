#pragma once

#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstdarg>

namespace retropack {

class Logger {
public:
    static void init(const std::string& internalPath, const std::string& externalPath, const std::string& packageName);
    static void log(int androidPriority, const char* tag, const char* fmt, ...);
    static void logV(int androidPriority, const char* tag, const char* fmt, va_list args);
    static void logCrash(int sig, const char* sigName);
    static void flush();

    static void info(const char* tag, const char* fmt, ...);
    static void warn(const char* tag, const char* fmt, ...);
    static void error(const char* tag, const char* fmt, ...);
    static void debug(const char* tag, const char* fmt, ...);

    static void ensureDirectoryRecursive(const std::string& path);

private:
    static std::vector<std::string> s_logPaths;
    static std::vector<std::string> s_crashPaths;
    static std::mutex s_mutex;
    static bool s_initialized;
    static std::string s_packageName;
};

} // namespace retropack

#define LOGI(...) retropack::Logger::info(LOG_TAG, __VA_ARGS__)
#define LOGW(...) retropack::Logger::warn(LOG_TAG, __VA_ARGS__)
#define LOGE(...) retropack::Logger::error(LOG_TAG, __VA_ARGS__)
#define LOGD(...) retropack::Logger::debug(LOG_TAG, __VA_ARGS__)
