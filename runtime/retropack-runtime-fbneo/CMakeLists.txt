cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-fbneo C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(FBNEO_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/fbneo")

set(FBNEO_SRCS
    src/main/cpp/fbneo-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${FBNEO_SOURCE_DIR}/src/burner/burner.h" OR EXISTS "${FBNEO_SOURCE_DIR}/CMakeLists.txt" OR EXISTS "${FBNEO_SOURCE_DIR}/makefile-libretro")
    message(STATUS "Found upstream FBNeo submodule at ${FBNEO_SOURCE_DIR}")
    add_definitions(-DHAVE_FBNEO_CORE=1)
endif()

add_library(retropack-runtime-fbneo SHARED
    ${FBNEO_SRCS}
)

target_include_directories(retropack-runtime-fbneo PRIVATE
    src/main/cpp
    "${FBNEO_SOURCE_DIR}"
    "${FBNEO_SOURCE_DIR}/src"
    "${FBNEO_SOURCE_DIR}/src/burner"
    "${FBNEO_SOURCE_DIR}/src/burner/libretro"
    "${FBNEO_SOURCE_DIR}/src/cpu"
)

target_link_libraries(retropack-runtime-fbneo PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-fbneo PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
