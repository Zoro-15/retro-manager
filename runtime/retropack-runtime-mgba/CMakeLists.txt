cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-mgba C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# Pinned Canonical mGBA options
set(LIBMGBA_ONLY ON CACHE BOOL "Build only libmGBA" FORCE)
set(BUILD_SHARED OFF CACHE BOOL "Build shared lib" FORCE)
set(BUILD_STATIC ON CACHE BOOL "Build static lib" FORCE)
set(BUILD_QT OFF CACHE BOOL "Build Qt frontend" FORCE)
set(BUILD_SDL OFF CACHE BOOL "Build SDL frontend" FORCE)
set(ENABLE_SCRIPTING OFF CACHE BOOL "Build scripting" FORCE)
set(M_CORE_GBA ON CACHE BOOL "Build Game Boy Advance core" FORCE)
set(M_CORE_GB ON CACHE BOOL "Build Game Boy core" FORCE)
set(BUILD_LTO OFF CACHE BOOL "Keep the integration build deterministic" FORCE)

set(MGBA_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/mgba")
if(NOT EXISTS "${MGBA_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "mGBA submodule is missing at ${MGBA_SOURCE_DIR}. Run: git submodule update --init --recursive")
endif()

add_subdirectory("${MGBA_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/mgba" EXCLUDE_FROM_ALL)

add_library(retropack-runtime SHARED
    src/main/cpp/mgba-jni.c
    src/main/cpp/ringbuffer.c
)

target_include_directories(retropack-runtime PRIVATE
    src/main/cpp
    "${MGBA_SOURCE_DIR}/include"
)

target_link_libraries(retropack-runtime PRIVATE
    mgba
    GLESv2
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
