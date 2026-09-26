cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-fceumm C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(FCEUMM_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/fceumm")

set(FCEUMM_SRCS
    src/main/cpp/fceumm-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${FCEUMM_SOURCE_DIR}/src/fceu.h" OR EXISTS "${FCEUMM_SOURCE_DIR}/fceu.h" OR EXISTS "${FCEUMM_SOURCE_DIR}/CMakeLists.txt")
    message(STATUS "Found upstream FCEUmm submodule at ${FCEUMM_SOURCE_DIR}")
    add_definitions(-DHAVE_FCEUMM_CORE=1)
endif()

add_library(retropack-runtime-fceumm SHARED
    ${FCEUMM_SRCS}
)

target_include_directories(retropack-runtime-fceumm PRIVATE
    src/main/cpp
    "${FCEUMM_SOURCE_DIR}"
    "${FCEUMM_SOURCE_DIR}/src"
    "${FCEUMM_SOURCE_DIR}/src/drivers/common"
)

target_link_libraries(retropack-runtime-fceumm PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
)

target_link_options(retropack-runtime-fceumm PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

