cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-ppsspp C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

add_library(retropack-runtime-ppsspp SHARED
    src/main/cpp/ppsspp-jni.c
    src/main/cpp/ringbuffer.c
)

target_include_directories(retropack-runtime-ppsspp PRIVATE
    src/main/cpp
)

target_link_libraries(retropack-runtime-ppsspp PRIVATE
    GLESv3
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-ppsspp PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
