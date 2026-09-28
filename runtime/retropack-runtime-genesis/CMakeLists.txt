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

if(EXISTS "${GENESIS_SOURCE_DIR}/core/genesis.h")
    message(STATUS "Found upstream Genesis Plus GX submodule at ${GENESIS_SOURCE_DIR}")
    add_definitions(
        -DHAVE_GENESIS_CORE=1
        -DLSB_FIRST=1
        -DBYTE_ORDER=LITTLE_ENDIAN
        -DMAXROMSIZE=33554432
        -DUSE_16BPP_RENDERING=1
        -DHAVE_ZLIB=1
    )

    file(GLOB GENESIS_CORE_SRCS
        "${GENESIS_SOURCE_DIR}/core/*.c"
        "${GENESIS_SOURCE_DIR}/core/m68k/*.c"
        "${GENESIS_SOURCE_DIR}/core/z80/*.c"
        "${GENESIS_SOURCE_DIR}/core/sound/*.c"
        "${GENESIS_SOURCE_DIR}/core/cart_hw/*.c"
        "${GENESIS_SOURCE_DIR}/core/cart_hw/svp/*.c"
        "${GENESIS_SOURCE_DIR}/core/input_hw/*.c"
        "${GENESIS_SOURCE_DIR}/core/ntsc/*.c"
        "${GENESIS_SOURCE_DIR}/core/cd_hw/*.c"
    )
    list(APPEND GENESIS_SRCS ${GENESIS_CORE_SRCS})
endif()

add_library(retropack-runtime-genesis SHARED
    ${GENESIS_SRCS}
)

target_include_directories(retropack-runtime-genesis PRIVATE
    src/main/cpp
    "${GENESIS_SOURCE_DIR}/core"
    "${GENESIS_SOURCE_DIR}/core/m68k"
    "${GENESIS_SOURCE_DIR}/core/z80"
    "${GENESIS_SOURCE_DIR}/core/sound"
    "${GENESIS_SOURCE_DIR}/core/sound/minimp3"
    "${GENESIS_SOURCE_DIR}/core/cart_hw"
    "${GENESIS_SOURCE_DIR}/core/cart_hw/svp"
    "${GENESIS_SOURCE_DIR}/core/input_hw"
    "${GENESIS_SOURCE_DIR}/core/ntsc"
    "${GENESIS_SOURCE_DIR}/core/cd_hw"
)

target_link_libraries(retropack-runtime-genesis PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
    m
    z
)

target_link_options(retropack-runtime-genesis PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

