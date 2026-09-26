cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-melonds C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(MELONDS_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/melonds")

set(MELONDS_SRCS
    src/main/cpp/melonds-jni.cpp
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${MELONDS_SOURCE_DIR}/src/NDS.cpp" OR EXISTS "${MELONDS_SOURCE_DIR}/CMakeLists.txt")
    message(STATUS "Found upstream melonDS submodule at ${MELONDS_SOURCE_DIR}")
    add_definitions(-DHAVE_MELONDS_CORE=1)
endif()

add_library(retropack-runtime-melonds SHARED
    ${MELONDS_SRCS}
)

target_include_directories(retropack-runtime-melonds PRIVATE
    src/main/cpp
    "${MELONDS_SOURCE_DIR}/src"
)

target_link_libraries(retropack-runtime-melonds PRIVATE
    GLESv3
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-melonds PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
