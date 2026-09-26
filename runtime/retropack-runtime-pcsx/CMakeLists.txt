cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-pcsx C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(PCSX_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/pcsx_rearmed")

set(PCSX_SRCS
    src/main/cpp/pcsx-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${PCSX_SOURCE_DIR}/libpcsxcore/psxcommon.h" OR EXISTS "${PCSX_SOURCE_DIR}/frontend/main.c" OR EXISTS "${PCSX_SOURCE_DIR}/CMakeLists.txt")
    message(STATUS "Found upstream PCSX ReARMed submodule at ${PCSX_SOURCE_DIR}")
    add_definitions(-DHAVE_PCSX_CORE=1)
    add_definitions(-DUSE_LIBRETRO_VFS=1)
    
    if(EXISTS "${PCSX_SOURCE_DIR}/CMakeLists.txt")
        set(BUILD_SHARED OFF CACHE BOOL "Build static" FORCE)
        add_subdirectory("${PCSX_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/pcsx_rearmed" EXCLUDE_FROM_ALL)
    endif()
endif()

add_library(retropack-runtime-pcsx SHARED
    ${PCSX_SRCS}
)

target_include_directories(retropack-runtime-pcsx PRIVATE
    src/main/cpp
    "${PCSX_SOURCE_DIR}"
    "${PCSX_SOURCE_DIR}/include"
    "${PCSX_SOURCE_DIR}/libpcsxcore"
    "${PCSX_SOURCE_DIR}/frontend"
    "${PCSX_SOURCE_DIR}/plugins"
)

target_link_libraries(retropack-runtime-pcsx PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-pcsx PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
