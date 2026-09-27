cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-snes9x C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(SNES9X_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/snes9x")

set(SNES9X_SRCS
    src/main/cpp/snes9x-jni.cpp
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${SNES9X_SOURCE_DIR}/snes9x.h")
    message(STATUS "Found upstream Snes9x submodule at ${SNES9X_SOURCE_DIR}")
    add_definitions(
        -DHAVE_SNES9X_CORE=1
        -DSTATIC_LINKING=1
        -DPIXEL_FORMAT=RGB565
        -DZLIB=1
    )

    list(APPEND SNES9X_SRCS
        ${SNES9X_SOURCE_DIR}/apu/apu.cpp
        ${SNES9X_SOURCE_DIR}/apu/bapu/dsp/sdsp.cpp
        ${SNES9X_SOURCE_DIR}/apu/bapu/smp/smp.cpp
        ${SNES9X_SOURCE_DIR}/apu/bapu/smp/smp_state.cpp
        ${SNES9X_SOURCE_DIR}/bsx.cpp
        ${SNES9X_SOURCE_DIR}/c4.cpp
        ${SNES9X_SOURCE_DIR}/c4emu.cpp
        ${SNES9X_SOURCE_DIR}/cheats.cpp
        ${SNES9X_SOURCE_DIR}/cheats2.cpp
        ${SNES9X_SOURCE_DIR}/clip.cpp
        ${SNES9X_SOURCE_DIR}/conffile.cpp
        ${SNES9X_SOURCE_DIR}/controls.cpp
        ${SNES9X_SOURCE_DIR}/cpu.cpp
        ${SNES9X_SOURCE_DIR}/cpuexec.cpp
        ${SNES9X_SOURCE_DIR}/cpuops.cpp
        ${SNES9X_SOURCE_DIR}/crosshairs.cpp
        ${SNES9X_SOURCE_DIR}/dma.cpp
        ${SNES9X_SOURCE_DIR}/dsp.cpp
        ${SNES9X_SOURCE_DIR}/dsp1.cpp
        ${SNES9X_SOURCE_DIR}/dsp2.cpp
        ${SNES9X_SOURCE_DIR}/dsp3.cpp
        ${SNES9X_SOURCE_DIR}/dsp4.cpp
        ${SNES9X_SOURCE_DIR}/fxinst.cpp
        ${SNES9X_SOURCE_DIR}/fxemu.cpp
        ${SNES9X_SOURCE_DIR}/gfx.cpp
        ${SNES9X_SOURCE_DIR}/globals.cpp
        ${SNES9X_SOURCE_DIR}/memmap.cpp
        ${SNES9X_SOURCE_DIR}/obc1.cpp
        ${SNES9X_SOURCE_DIR}/msu1.cpp
        ${SNES9X_SOURCE_DIR}/ppu.cpp
        ${SNES9X_SOURCE_DIR}/stream.cpp
        ${SNES9X_SOURCE_DIR}/sa1.cpp
        ${SNES9X_SOURCE_DIR}/sa1cpu.cpp
        ${SNES9X_SOURCE_DIR}/screenshot.cpp
        ${SNES9X_SOURCE_DIR}/sdd1.cpp
        ${SNES9X_SOURCE_DIR}/sdd1emu.cpp
        ${SNES9X_SOURCE_DIR}/seta.cpp
        ${SNES9X_SOURCE_DIR}/seta010.cpp
        ${SNES9X_SOURCE_DIR}/seta011.cpp
        ${SNES9X_SOURCE_DIR}/seta018.cpp
        ${SNES9X_SOURCE_DIR}/snapshot.cpp
        ${SNES9X_SOURCE_DIR}/snes9x.cpp
        ${SNES9X_SOURCE_DIR}/spc7110.cpp
        ${SNES9X_SOURCE_DIR}/srtc.cpp
        ${SNES9X_SOURCE_DIR}/tile.cpp
        ${SNES9X_SOURCE_DIR}/tileimpl-n1x1.cpp
        ${SNES9X_SOURCE_DIR}/tileimpl-n2x1.cpp
        ${SNES9X_SOURCE_DIR}/tileimpl-h2x1.cpp
        ${SNES9X_SOURCE_DIR}/sha256.cpp
        ${SNES9X_SOURCE_DIR}/bml.cpp
        ${SNES9X_SOURCE_DIR}/movie.cpp
        ${SNES9X_SOURCE_DIR}/fscompat.cpp
    )
endif()

add_library(retropack-runtime-snes9x SHARED
    ${SNES9X_SRCS}
)

target_include_directories(retropack-runtime-snes9x PRIVATE
    src/main/cpp
    "${SNES9X_SOURCE_DIR}"
    "${SNES9X_SOURCE_DIR}/apu"
    "${SNES9X_SOURCE_DIR}/apu/bapu"
)

target_link_libraries(retropack-runtime-snes9x PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
    m
    z
)

target_link_options(retropack-runtime-snes9x PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

