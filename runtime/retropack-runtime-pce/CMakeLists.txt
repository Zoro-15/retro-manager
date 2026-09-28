cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-pce C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(PCE_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/beetle-pce-fast")

set(PCE_SRCS
    src/main/cpp/pce-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${PCE_SOURCE_DIR}/libretro.c")
    message(STATUS "Found upstream Beetle PCE Fast submodule at ${PCE_SOURCE_DIR}")
    add_definitions(
        -DHAVE_BEETLE_PCE_CORE=1
        -D__LIBRETRO__=1
        -DWANT_PCE_FAST_EMU=1
        -DNEED_CD=1
        -DNEED_TREMOR=1
        -DFRONTEND_SUPPORTS_RGB565=1
        -DSTATIC_LINKING=1
        -DINLINE=inline
    )

    set(PCE_CORE_SRCS
        "${PCE_SOURCE_DIR}/mednafen/hw_misc/arcade_card/arcade_card.c"
        "${PCE_SOURCE_DIR}/mednafen/pce_fast/pcecd_drive.c"
        "${PCE_SOURCE_DIR}/mednafen/pce_fast/pcecd.c"
        "${PCE_SOURCE_DIR}/mednafen/pce_fast/psg.c"
        "${PCE_SOURCE_DIR}/mednafen/pce_fast/huc6280.c"
        "${PCE_SOURCE_DIR}/mednafen/pce_fast/input.c"
        "${PCE_SOURCE_DIR}/mednafen/pce_fast/vdc.c"
        "${PCE_SOURCE_DIR}/mednafen/sound/Blip_Buffer.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/CDAccess.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/CDAccess_Image.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/CDAccess_CCD.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/audioreader.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/cdromif.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/CDUtility.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/lec.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/galois.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/l-ec.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/edc_crc32.c"
        "${PCE_SOURCE_DIR}/mednafen/cdrom/recover-raw.c"
        "${PCE_SOURCE_DIR}/mednafen/general.c"
        "${PCE_SOURCE_DIR}/mednafen/cdstream.c"
        "${PCE_SOURCE_DIR}/mednafen/mempatcher.c"
        "${PCE_SOURCE_DIR}/mednafen/okiadpcm.c"
        "${PCE_SOURCE_DIR}/mednafen/file.c"
        "${PCE_SOURCE_DIR}/mednafen/settings.c"
        "${PCE_SOURCE_DIR}/mednafen/state.c"
        "${PCE_SOURCE_DIR}/mednafen/mednafen-endian.c"
        "${PCE_SOURCE_DIR}/libretro.c"
    )

    file(GLOB PCE_TREMOR_SRCS
        "${PCE_SOURCE_DIR}/mednafen/tremor/*.c"
    )
    list(FILTER PCE_TREMOR_SRCS EXCLUDE REGEX ".*ivorbisfile_example\\.c$")

    set(PCE_LIBRETRO_COMMON_SRCS
        "${PCE_SOURCE_DIR}/libretro-common/streams/file_stream.c"
        "${PCE_SOURCE_DIR}/libretro-common/streams/file_stream_transforms.c"
        "${PCE_SOURCE_DIR}/libretro-common/file/file_path.c"
        "${PCE_SOURCE_DIR}/libretro-common/file/retro_dirent.c"
        "${PCE_SOURCE_DIR}/libretro-common/lists/string_list.c"
        "${PCE_SOURCE_DIR}/libretro-common/lists/dir_list.c"
        "${PCE_SOURCE_DIR}/libretro-common/compat/compat_strl.c"
        "${PCE_SOURCE_DIR}/libretro-common/compat/compat_snprintf.c"
        "${PCE_SOURCE_DIR}/libretro-common/compat/compat_posix_string.c"
        "${PCE_SOURCE_DIR}/libretro-common/compat/compat_strcasestr.c"
        "${PCE_SOURCE_DIR}/libretro-common/compat/fopen_utf8.c"
        "${PCE_SOURCE_DIR}/libretro-common/encodings/encoding_utf.c"
        "${PCE_SOURCE_DIR}/libretro-common/encodings/encoding_crc32.c"
        "${PCE_SOURCE_DIR}/libretro-common/memmap/memalign.c"
        "${PCE_SOURCE_DIR}/libretro-common/string/stdstring.c"
        "${PCE_SOURCE_DIR}/libretro-common/time/rtime.c"
        "${PCE_SOURCE_DIR}/libretro-common/vfs/vfs_implementation.c"
    )

    list(APPEND PCE_SRCS
        ${PCE_CORE_SRCS}
        ${PCE_TREMOR_SRCS}
        ${PCE_LIBRETRO_COMMON_SRCS}
    )
endif()

add_library(retropack-runtime-pce SHARED
    ${PCE_SRCS}
)

target_include_directories(retropack-runtime-pce PRIVATE
    src/main/cpp
    "${PCE_SOURCE_DIR}"
    "${PCE_SOURCE_DIR}/mednafen"
    "${PCE_SOURCE_DIR}/mednafen/include"
    "${PCE_SOURCE_DIR}/mednafen/pce_fast"
    "${PCE_SOURCE_DIR}/mednafen/cdrom"
    "${PCE_SOURCE_DIR}/mednafen/hw_misc"
    "${PCE_SOURCE_DIR}/mednafen/tremor"
    "${PCE_SOURCE_DIR}/libretro-common/include"
)

target_link_libraries(retropack-runtime-pce PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
    m
    z
)

target_link_options(retropack-runtime-pce PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)


