#include "Platform.h"
#include <android/log.h>
#include <unistd.h>
#include <sys/time.h>
#include <pthread.h>
#include <semaphore.h>
#include <dlfcn.h>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <functional>

namespace melonDS {
namespace Platform {

struct FileHandle {
    FILE* fp;
};

std::string GetLocalFilePath(const std::string& filename) {
    return filename;
}

FileHandle* OpenFile(const std::string& path, FileMode mode) {
    const char* m = "rb";
    if (mode & Write) m = (mode & Preserve) ? "r+b" : "wb";
    if (mode & Append) m = "ab";
    FILE* fp = fopen(path.c_str(), m);
    if (!fp) return nullptr;
    return new FileHandle{fp};
}

FileHandle* OpenLocalFile(const std::string& path, FileMode mode) {
    return OpenFile(path, mode);
}

bool FileExists(const std::string& name) {
    FILE* fp = fopen(name.c_str(), "rb");
    if (fp) { fclose(fp); return true; }
    return false;
}

bool LocalFileExists(const std::string& name) {
    return FileExists(name);
}

bool CheckFileWritable(const std::string& filepath) {
    FILE* fp = fopen(filepath.c_str(), "a+b");
    if (fp) { fclose(fp); return true; }
    return false;
}

bool CheckLocalFileWritable(const std::string& filepath) {
    return CheckFileWritable(filepath);
}

bool CloseFile(FileHandle* file) {
    if (!file) return false;
    bool ok = (fclose(file->fp) == 0);
    delete file;
    return ok;
}

bool IsEndOfFile(FileHandle* file) {
    if (!file) return true;
    return feof(file->fp) != 0;
}

bool FileReadLine(char* str, int count, FileHandle* file) {
    if (!file) return false;
    return fgets(str, count, file->fp) != nullptr;
}

u64 FilePosition(FileHandle* file) {
    if (!file) return 0;
    return (u64)ftell(file->fp);
}

bool FileSeek(FileHandle* file, s64 offset, FileSeekOrigin origin) {
    if (!file) return false;
    int whence = SEEK_SET;
    if (origin == FileSeekOrigin::Current) whence = SEEK_CUR;
    else if (origin == FileSeekOrigin::End) whence = SEEK_END;
    return fseek(file->fp, (long)offset, whence) == 0;
}

void FileRewind(FileHandle* file) {
    if (file) rewind(file->fp);
}

u64 FileRead(void* data, u64 size, u64 count, FileHandle* file) {
    if (!file) return 0;
    return (u64)fread(data, (size_t)size, (size_t)count, file->fp);
}

bool FileFlush(FileHandle* file) {
    if (!file) return false;
    return fflush(file->fp) == 0;
}

u64 FileWrite(const void* data, u64 size, u64 count, FileHandle* file) {
    if (!file) return 0;
    return (u64)fwrite(data, (size_t)size, (size_t)count, file->fp);
}

u64 FileWriteFormatted(FileHandle* file, const char* fmt, ...) {
    if (!file) return 0;
    va_list args;
    va_start(args, fmt);
    int res = vfprintf(file->fp, fmt, args);
    va_end(args);
    return res > 0 ? (u64)res : 0;
}

u64 FileLength(FileHandle* file) {
    if (!file) return 0;
    long cur = ftell(file->fp);
    fseek(file->fp, 0, SEEK_END);
    long len = ftell(file->fp);
    fseek(file->fp, cur, SEEK_SET);
    return len > 0 ? (u64)len : 0;
}

void SignalStop(StopReason reason, void* userdata) {}

void Log(LogLevel level, const char* fmt, ...) {
    int prio = ANDROID_LOG_INFO;
    if (level == Debug) prio = ANDROID_LOG_DEBUG;
    else if (level == Warn) prio = ANDROID_LOG_WARN;
    else if (level == Error) prio = ANDROID_LOG_ERROR;
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(prio, "melonDS", fmt, args);
    va_end(args);
}

struct Thread {
    pthread_t thread;
    std::function<void()> func;
};

static void* ThreadEntry(void* arg) {
    auto* t = static_cast<Thread*>(arg);
    if (t && t->func) t->func();
    return nullptr;
}

Thread* Thread_Create(std::function<void()> func) {
    auto* t = new Thread();
    t->func = func;
    if (pthread_create(&t->thread, nullptr, ThreadEntry, t) != 0) {
        delete t;
        return nullptr;
    }
    return t;
}

void Thread_Free(Thread* thread) {
    if (thread) {
        pthread_join(thread->thread, nullptr);
        delete thread;
    }
}

void Thread_Wait(Thread* thread) {
    if (thread) {
        pthread_join(thread->thread, nullptr);
    }
}

struct Semaphore {
    sem_t sem;
};

Semaphore* Semaphore_Create() {
    auto* s = new Semaphore();
    sem_init(&s->sem, 0, 0);
    return s;
}

void Semaphore_Free(Semaphore* sema) {
    if (sema) {
        sem_destroy(&sema->sem);
        delete sema;
    }
}

void Semaphore_Reset(Semaphore* sema) {
    if (sema) {
        sem_destroy(&sema->sem);
        sem_init(&sema->sem, 0, 0);
    }
}

void Semaphore_Wait(Semaphore* sema) {
    if (sema) sem_wait(&sema->sem);
}

bool Semaphore_TryWait(Semaphore* sema, int timeout_ms) {
    if (!sema) return false;
    if (timeout_ms <= 0) return sem_trywait(&sema->sem) == 0;
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    ts.tv_sec += timeout_ms / 1000;
    ts.tv_nsec += (timeout_ms % 1000) * 1000000;
    if (ts.tv_nsec >= 1000000000) {
        ts.tv_sec += 1;
        ts.tv_nsec -= 1000000000;
    }
    return sem_timedwait(&sema->sem, &ts) == 0;
}

void Semaphore_Post(Semaphore* sema, int count) {
    if (sema) {
        for (int i = 0; i < count; i++) sem_post(&sema->sem);
    }
}

struct Mutex {
    pthread_mutex_t mtx;
};

Mutex* Mutex_Create() {
    auto* m = new Mutex();
    pthread_mutex_init(&m->mtx, nullptr);
    return m;
}

void Mutex_Free(Mutex* mutex) {
    if (mutex) {
        pthread_mutex_destroy(&mutex->mtx);
        delete mutex;
    }
}

void Mutex_Lock(Mutex* mutex) {
    if (mutex) pthread_mutex_lock(&mutex->mtx);
}

void Mutex_Unlock(Mutex* mutex) {
    if (mutex) pthread_mutex_unlock(&mutex->mtx);
}

bool Mutex_TryLock(Mutex* mutex) {
    if (!mutex) return false;
    return pthread_mutex_trylock(&mutex->mtx) == 0;
}

void Sleep(u64 usecs) {
    usleep((useconds_t)usecs);
}

u64 GetMSCount() {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return ((u64)tv.tv_sec * 1000) + (tv.tv_usec / 1000);
}

u64 GetUSCount() {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return ((u64)tv.tv_sec * 1000000) + tv.tv_usec;
}

void WriteNDSSave(const u8* savedata, u32 savelen, u32 writeoffset, u32 writelen, void* userdata) {}
void WriteGBASave(const u8* savedata, u32 savelen, u32 writeoffset, u32 writelen, void* userdata) {}
void WriteFirmware(const Firmware& firmware, u32 writeoffset, u32 writelen, void* userdata) {}
void WriteDateTime(int year, int month, int day, int hour, int minute, int second, void* userdata) {}

void MP_Begin(void* userdata) {}
void MP_End(void* userdata) {}
int MP_SendPacket(u8* data, int len, u64 timestamp, void* userdata) { return 0; }
int MP_RecvPacket(u8* data, u64* timestamp, void* userdata) { return 0; }
int MP_SendCmd(u8* data, int len, u64 timestamp, void* userdata) { return 0; }
int MP_SendReply(u8* data, int len, u64 timestamp, u16 aid, void* userdata) { return 0; }
int MP_SendAck(u8* data, int len, u64 timestamp, void* userdata) { return 0; }
int MP_RecvHostPacket(u8* data, u64* timestamp, void* userdata) { return 0; }
u16 MP_RecvReplies(u8* data, u64 timestamp, u16 aidmask, void* userdata) { return 0; }

int Net_SendPacket(u8* data, int len, void* userdata) { return 0; }
int Net_RecvPacket(u8* data, void* userdata) { return 0; }

void Camera_Start(int num, void* userdata) {}
void Camera_Stop(int num, void* userdata) {}
void Camera_CaptureFrame(int num, u32* frame, int width, int height, bool yuv, void* userdata) {}

void Mic_Start(void* userdata) {}
void Mic_Stop(void* userdata) {}
int Mic_ReadInput(s16* data, int maxlength, void* userdata) { return 0; }

struct AACDecoder {};
AACDecoder* AAC_Init() { return nullptr; }
void AAC_DeInit(AACDecoder* dec) {}
bool AAC_Configure(AACDecoder* dec, int frequency, int channels) { return false; }
bool AAC_DecodeFrame(AACDecoder* dec, const void* input, int inputlen, void* output, int outputlen) { return false; }

bool Addon_KeyDown(KeyType type, void* userdata) { return false; }
void Addon_RumbleStart(u32 len, void* userdata) {}
void Addon_RumbleStop(void* userdata) {}
float Addon_MotionQuery(MotionQueryType type, void* userdata) { return 0.0f; }

struct DynamicLibrary { void* handle; };
DynamicLibrary* DynamicLibrary_Load(const char* lib) {
    void* h = dlopen(lib, RTLD_NOW);
    if (!h) return nullptr;
    return new DynamicLibrary{h};
}
void DynamicLibrary_Unload(DynamicLibrary* lib) {
    if (lib) { dlclose(lib->handle); delete lib; }
}
void* DynamicLibrary_LoadFunction(DynamicLibrary* lib, const char* name) {
    if (!lib) return nullptr;
    return dlsym(lib->handle, name);
}

} // namespace Platform
} // namespace melonDS
