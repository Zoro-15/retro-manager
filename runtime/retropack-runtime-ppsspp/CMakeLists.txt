cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-ppsspp C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(PPSSPP_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/ppsspp")

set(PPSSPP_SRCS
    src/main/cpp/ppsspp-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${PPSSPP_SOURCE_DIR}/Core/System.h" OR EXISTS "${PPSSPP_SOURCE_DIR}/CMakeLists.txt")
    message(STATUS "Found upstream PPSSPP submodule at ${PPSSPP_SOURCE_DIR}")
    add_definitions(-DHAVE_PPSSPP_CORE=1)
    
    if(EXISTS "${PPSSPP_SOURCE_DIR}/CMakeLists.txt")
        set(BUILD_SHARED OFF CACHE BOOL "Build static" FORCE)
        add_subdirectory("${PPSSPP_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/ppsspp" EXCLUDE_FROM_ALL)
    endif()
endif()

add_library(retropack-runtime-ppsspp SHARED
    ${PPSSPP_SRCS}
)

target_include_directories(retropack-runtime-ppsspp PRIVATE
    src/main/cpp
    "${PPSSPP_SOURCE_DIR}"
    "${PPSSPP_SOURCE_DIR}/Core"
    "${PPSSPP_SOURCE_DIR}/Common"
    "${PPSSPP_SOURCE_DIR}/GPU"
)

target_link_libraries(retropack-runtime-ppsspp PRIVATE
    GLESv3
    GLESv2
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-ppsspp PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
