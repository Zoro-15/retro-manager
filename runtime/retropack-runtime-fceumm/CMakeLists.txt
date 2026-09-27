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

if(EXISTS "${FCEUMM_SOURCE_DIR}/src/fceu.h")
    message(STATUS "Found upstream FCEUmm submodule at ${FCEUMM_SOURCE_DIR}")
    add_definitions(
        -DHAVE_FCEUMM_CORE=1
        -D__LIBRETRO__=1
        -DPATH_MAX=1024
        -DFCEU_VERSION_NUMERIC=9900
        -DFRONTEND_SUPPORTS_RGB565=1
        -DSTATIC_LINKING=1
    )

    set(FCEUMM_CORE_SRCS
        "${FCEUMM_SOURCE_DIR}/src/cart.c"
        "${FCEUMM_SOURCE_DIR}/src/cheat.c"
        "${FCEUMM_SOURCE_DIR}/src/crc32.c"
        "${FCEUMM_SOURCE_DIR}/src/fceu-endian.c"
        "${FCEUMM_SOURCE_DIR}/src/fceu-memory.c"
        "${FCEUMM_SOURCE_DIR}/src/fceu.c"
        "${FCEUMM_SOURCE_DIR}/src/fds.c"
        "${FCEUMM_SOURCE_DIR}/src/fds_apu.c"
        "${FCEUMM_SOURCE_DIR}/src/file.c"
        "${FCEUMM_SOURCE_DIR}/src/filter.c"
        "${FCEUMM_SOURCE_DIR}/src/general.c"
        "${FCEUMM_SOURCE_DIR}/src/input.c"
        "${FCEUMM_SOURCE_DIR}/src/md5.c"
        "${FCEUMM_SOURCE_DIR}/src/nsf.c"
        "${FCEUMM_SOURCE_DIR}/src/palette.c"
        "${FCEUMM_SOURCE_DIR}/src/ppu.c"
        "${FCEUMM_SOURCE_DIR}/src/sound.c"
        "${FCEUMM_SOURCE_DIR}/src/state.c"
        "${FCEUMM_SOURCE_DIR}/src/video.c"
        "${FCEUMM_SOURCE_DIR}/src/vsuni.c"
        "${FCEUMM_SOURCE_DIR}/src/ines.c"
        "${FCEUMM_SOURCE_DIR}/src/unif.c"
        "${FCEUMM_SOURCE_DIR}/src/x6502.c"
    )

    file(GLOB FCEUMM_BOARD_SRCS "${FCEUMM_SOURCE_DIR}/src/boards/*.c")
    file(GLOB FCEUMM_INPUT_SRCS "${FCEUMM_SOURCE_DIR}/src/input/*.c")

    set(FCEUMM_LIBRETRO_COMMON_SRCS
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/streams/memory_stream.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/compat/compat_posix_string.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/compat/compat_snprintf.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/compat/compat_strcasestr.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/compat/compat_strl.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/compat/fopen_utf8.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/encodings/encoding_utf.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/file/file_path.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/file/file_path_io.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/streams/file_stream.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/streams/file_stream_transforms.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/string/stdstring.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/time/rtime.c"
        "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/vfs/vfs_implementation.c"
    )

    list(APPEND FCEUMM_SRCS
        ${FCEUMM_CORE_SRCS}
        ${FCEUMM_BOARD_SRCS}
        ${FCEUMM_INPUT_SRCS}
        ${FCEUMM_LIBRETRO_COMMON_SRCS}
    )
endif()

add_library(retropack-runtime-fceumm SHARED
    ${FCEUMM_SRCS}
)

target_include_directories(retropack-runtime-fceumm PRIVATE
    src/main/cpp
    "${FCEUMM_SOURCE_DIR}/src"
    "${FCEUMM_SOURCE_DIR}/src/input"
    "${FCEUMM_SOURCE_DIR}/src/boards"
    "${FCEUMM_SOURCE_DIR}/src/drivers/libretro"
    "${FCEUMM_SOURCE_DIR}/src/drivers/libretro/libretro-common/include"
)

target_link_libraries(retropack-runtime-fceumm PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
    m
    z
)

target_link_options(retropack-runtime-fceumm PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)


