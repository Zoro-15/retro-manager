cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-pce C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

add_library(retropack-runtime-pce SHARED
    src/main/cpp/pce-jni.c
    src/main/cpp/ringbuffer.c
)

target_include_directories(retropack-runtime-pce PRIVATE
    src/main/cpp
)

target_link_libraries(retropack-runtime-pce PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-pce PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
