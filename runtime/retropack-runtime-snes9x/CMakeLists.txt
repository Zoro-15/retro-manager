cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-snes9x C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(SNES9X_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/snes9x")

set(SNES9X_SRCS
    src/main/cpp/snes9x-jni.cpp
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${SNES9X_SOURCE_DIR}/snes9x.h" OR EXISTS "${SNES9X_SOURCE_DIR}/CMakeLists.txt")
    message(STATUS "Found upstream Snes9x submodule at ${SNES9X_SOURCE_DIR}")
    add_definitions(-DHAVE_SNES9X_CORE=1)
endif()

add_library(retropack-runtime-snes9x SHARED
    ${SNES9X_SRCS}
)

target_include_directories(retropack-runtime-snes9x PRIVATE
    src/main/cpp
    "${SNES9X_SOURCE_DIR}"
)

target_link_libraries(retropack-runtime-snes9x PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-snes9x PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
