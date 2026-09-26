cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-mupen64 C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(MUPEN_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/mupen64plus-next")

set(MUPEN_SRCS
    src/main/cpp/mupen64-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/api/m64p_types.h" OR EXISTS "${MUPEN_SOURCE_DIR}/CMakeLists.txt")
    message(STATUS "Found upstream Mupen64Plus submodule at ${MUPEN_SOURCE_DIR}")
    add_definitions(-DHAVE_MUPEN64_CORE=1)
    
    if(EXISTS "${MUPEN_SOURCE_DIR}/CMakeLists.txt")
        set(BUILD_SHARED OFF CACHE BOOL "Build static" FORCE)
        add_subdirectory("${MUPEN_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/mupen64plus-next" EXCLUDE_FROM_ALL)
    endif()
endif()

add_library(retropack-runtime-mupen64 SHARED
    ${MUPEN_SRCS}
)

target_include_directories(retropack-runtime-mupen64 PRIVATE
    src/main/cpp
    "${MUPEN_SOURCE_DIR}"
    "${MUPEN_SOURCE_DIR}/mupen64plus-core/src"
    "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/api"
    "${MUPEN_SOURCE_DIR}/GLideN64/src"
)

target_link_libraries(retropack-runtime-mupen64 PRIVATE
    GLESv3
    GLESv2
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-mupen64 PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
