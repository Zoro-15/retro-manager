cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-genesis C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(GENESIS_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/genesis-plus-gx")

set(GENESIS_SRCS
    src/main/cpp/genesis-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${GENESIS_SOURCE_DIR}/shared.h" OR EXISTS "${GENESIS_SOURCE_DIR}/CMakeLists.txt")
    message(STATUS "Found upstream Genesis Plus GX submodule at ${GENESIS_SOURCE_DIR}")
    add_definitions(-DHAVE_GENESIS_CORE=1)
endif()

add_library(retropack-runtime-genesis SHARED
    ${GENESIS_SRCS}
)

target_include_directories(retropack-runtime-genesis PRIVATE
    src/main/cpp
    "${GENESIS_SOURCE_DIR}"
)

target_link_libraries(retropack-runtime-genesis PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-genesis PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
