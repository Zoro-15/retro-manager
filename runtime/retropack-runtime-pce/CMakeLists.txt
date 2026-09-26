cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-pce C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(PCE_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/beetle-pce-fast")

set(PCE_SRCS
    src/main/cpp/pce-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${PCE_SOURCE_DIR}/mednafen/pce_fast/pce.h" OR EXISTS "${PCE_SOURCE_DIR}/pce.h" OR EXISTS "${PCE_SOURCE_DIR}/CMakeLists.txt")
    message(STATUS "Found upstream Beetle PCE Fast submodule at ${PCE_SOURCE_DIR}")
    add_definitions(-DHAVE_BEETLE_PCE_CORE=1)
endif()

add_library(retropack-runtime-pce SHARED
    ${PCE_SRCS}
)

target_include_directories(retropack-runtime-pce PRIVATE
    src/main/cpp
    "${PCE_SOURCE_DIR}"
    "${PCE_SOURCE_DIR}/mednafen"
    "${PCE_SOURCE_DIR}/mednafen/pce_fast"
    "${PCE_SOURCE_DIR}/mednafen/cdrom"
    "${PCE_SOURCE_DIR}/mednafen/hw_misc"
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

