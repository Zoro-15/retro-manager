cmake_minimum_required(VERSION 3.22.1)
project(retro_engine C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Compulsory Android 15+ / 16 KB ELF Page Alignment
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# Compiler optimization and hardening flags
add_compile_options(
    -O3
    -flto
    -fvisibility=hidden
    -Wall
    -Wextra
    -Wno-unused-parameter
)

# Locate android_native_app_glue from NDK
if(DEFINED ANDROID_NDK)
    set(NDK_ROOT "${ANDROID_NDK}")
elseif(DEFINED CMAKE_ANDROID_NDK)
    set(NDK_ROOT "${CMAKE_ANDROID_NDK}")
elseif(DEFINED ENV{ANDROID_NDK_ROOT})
    set(NDK_ROOT "$ENV{ANDROID_NDK_ROOT}")
elseif(DEFINED ENV{ANDROID_NDK_HOME})
    set(NDK_ROOT "$ENV{ANDROID_NDK_HOME}")
endif()

# Core native engine sources
set(ENGINE_SOURCES
    src/main.cpp
    src/common/logger.cpp
    src/core/libretro_bridge.cpp
    src/video/gles_renderer.cpp
    src/audio/aaudio_player.cpp
    src/input/virtual_pad.cpp
    src/ui/font_renderer.cpp
    src/ui/osd_menu.cpp
    src/storage/state_manager.cpp
)

if(NDK_ROOT)
    include_directories(${NDK_ROOT}/sources/android/native_app_glue)
    list(APPEND ENGINE_SOURCES
        ${NDK_ROOT}/sources/android/native_app_glue/android_native_app_glue.c
    )
endif()

# Main shared library: libretro_engine.so
add_library(retro_engine SHARED
    ${ENGINE_SOURCES}
)

target_include_directories(retro_engine PRIVATE
    src
    src/common
    src/core
    src/video
    src/audio
    src/input
    src/ui
    src/storage
)

set(LINK_LIBRARIES
    android
    log
    EGL
    GLESv2
    aaudio
    dl
    m
)

target_link_libraries(retro_engine PRIVATE
    ${LINK_LIBRARIES}
)

target_link_options(retro_engine PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
    "-Wl,-u,ANativeActivity_onCreate"
    "-Wl,--export-dynamic"
    "-flto"
)
