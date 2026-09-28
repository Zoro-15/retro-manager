cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-fbneo C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

add_compile_options(-w)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(FBNEO_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/fbneo")

set(FBNEO_SRCS
    src/main/cpp/fbneo-jni.cpp
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${FBNEO_SOURCE_DIR}/src/burn/burn.h" OR EXISTS "${FBNEO_SOURCE_DIR}/src/burn/burn.cpp")
    message(STATUS "Found upstream FBNeo submodule at ${FBNEO_SOURCE_DIR}")
    add_definitions(
        -DHAVE_FBNEO_CORE=1
        -DSUPPORT_SPLIT_DRIVER=1
        -DBUILD_NEOGEO=1
        -D__LIBRETRO__=1
        -D__LIBRETRO_OPTIMIZATIONS__=1
        -DINLINE=inline
    )

    set(FBNEO_CORE_SRCS
        "${FBNEO_SOURCE_DIR}/src/burn/burn.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/burn_gun.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/burn_memory.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/burn_sound.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/cheat.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/debug_track.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/hiscore.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/load.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/timer.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/burn_sha1.cpp"
        "${FBNEO_SOURCE_DIR}/src/burner/conc.cpp"
        "${FBNEO_SOURCE_DIR}/src/burner/dat.cpp"
        "${FBNEO_SOURCE_DIR}/src/burner/statec.cpp"
        "${FBNEO_SOURCE_DIR}/src/burner/zipfn.cpp"
        "${FBNEO_SOURCE_DIR}/src/burner/nvram.cpp"
        "${FBNEO_SOURCE_DIR}/src/burner/cdlist.cpp"
        "${FBNEO_SOURCE_DIR}/src/burner/ioapi.c"
        "${FBNEO_SOURCE_DIR}/src/burner/unzip.c"
    )

    set(FBNEO_NEOGEO_SRCS
        "${FBNEO_SOURCE_DIR}/src/burn/drv/neogeo/d_neogeo.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/drv/neogeo/neo_decrypt.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/drv/neogeo/neo_palette.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/drv/neogeo/neo_run.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/drv/neogeo/neo_sprite.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/drv/neogeo/neo_text.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/drv/neogeo/neo_upd4990a.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/drv/neogeo/neogeo.cpp"
    )

    set(FBNEO_SND_DEV_SRCS
        "${FBNEO_SOURCE_DIR}/src/burn/snd/fm.c"
        "${FBNEO_SOURCE_DIR}/src/burn/snd/ay8910.c"
        "${FBNEO_SOURCE_DIR}/src/burn/snd/ymdeltat.c"
        "${FBNEO_SOURCE_DIR}/src/burn/snd/burn_ym2203.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/snd/burn_ym2608.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/snd/burn_ym2610.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/snd/burn_ym2612.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/devices/joyprocess.cpp"
        "${FBNEO_SOURCE_DIR}/src/burn/devices/resnet.cpp"
    )

    set(FBNEO_CPU_SRCS
        "${FBNEO_SOURCE_DIR}/src/cpu/m68k/m68kcpu.c"
        "${FBNEO_SOURCE_DIR}/src/cpu/m68k/m68kops.c"
        "${FBNEO_SOURCE_DIR}/src/cpu/m68000_intf.cpp"
        "${FBNEO_SOURCE_DIR}/src/cpu/z80_intf.cpp"
        "${FBNEO_SOURCE_DIR}/src/cpu/z80/z80.cpp"
        "${FBNEO_SOURCE_DIR}/src/cpu/z80/z80ctc.cpp"
        "${FBNEO_SOURCE_DIR}/src/cpu/z80/z80daisy.cpp"
        "${FBNEO_SOURCE_DIR}/src/cpu/z80/z80pio.cpp"
    )

    list(APPEND FBNEO_SRCS
        ${FBNEO_CORE_SRCS}
        ${FBNEO_NEOGEO_SRCS}
        ${FBNEO_SND_DEV_SRCS}
        ${FBNEO_CPU_SRCS}
    )
endif()

add_library(retropack-runtime-fbneo SHARED
    ${FBNEO_SRCS}
)

target_include_directories(retropack-runtime-fbneo PRIVATE
    src/main/cpp
    "${CMAKE_CURRENT_SOURCE_DIR}/../retropack-runtime-common/src/main/cpp"
    "${FBNEO_SOURCE_DIR}"
    "${FBNEO_SOURCE_DIR}/src"
    "${FBNEO_SOURCE_DIR}/src/burn"
    "${FBNEO_SOURCE_DIR}/src/burn/devices"
    "${FBNEO_SOURCE_DIR}/src/burn/snd"
    "${FBNEO_SOURCE_DIR}/src/burn/drv/neogeo"
    "${FBNEO_SOURCE_DIR}/src/burner"
    "${FBNEO_SOURCE_DIR}/src/burner/libretro"
    "${FBNEO_SOURCE_DIR}/src/burner/libretro/libretro-common/include"
    "${FBNEO_SOURCE_DIR}/src/cpu"
    "${FBNEO_SOURCE_DIR}/src/cpu/m68k"
    "${FBNEO_SOURCE_DIR}/src/cpu/z80"
    "${FBNEO_SOURCE_DIR}/src/dep/generated"
    "${FBNEO_SOURCE_DIR}/src/dep/libs/zlib"
    "${FBNEO_SOURCE_DIR}/src/intf"
    "${FBNEO_SOURCE_DIR}/src/intf/cd"
    "${FBNEO_SOURCE_DIR}/src/intf/audio"
    "${FBNEO_SOURCE_DIR}/src/intf/input"
    "${FBNEO_SOURCE_DIR}/src/intf/video"
)

target_link_libraries(retropack-runtime-fbneo PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
    m
    z
)

target_link_options(retropack-runtime-fbneo PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
    "-Wl,--allow-multiple-definition"
    "-Wl,-z,muldefs"
)
