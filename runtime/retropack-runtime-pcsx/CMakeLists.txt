cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-pcsx C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

add_compile_options(-w)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(PCSX_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/pcsx_rearmed")

set(PCSX_SRCS
    src/main/cpp/pcsx-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${PCSX_SOURCE_DIR}/libpcsxcore/psxcommon.h" OR EXISTS "${PCSX_SOURCE_DIR}/libpcsxcore/psxmem.c")
    message(STATUS "Found upstream PCSX ReARMed submodule at ${PCSX_SOURCE_DIR}")
    add_definitions(
        -DHAVE_PCSX_CORE=1
        -D__LIBRETRO__=1
        -DDRC_DISABLE=1
        -DNO_FRONTEND=1
        -DP_HAVE_MMAP=0
        -DP_HAVE_POSIX_MEMALIGN=0
        -DDISABLE_MEM_LUTS=0
        -DGPU_PEOPS=1
        -DUSE_LIBRETRO_VFS=1
        -DNO_DYLIB=1
    )

    set(PCSX_CORE_SRCS
        "${PCSX_SOURCE_DIR}/libpcsxcore/cdriso.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/cdrom.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/cdrom-async.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/cheat.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/database.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/decode_xa.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/mdec.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/misc.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/plugins.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/ppf.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/psxbios.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/psxcommon.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/psxcounters.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/psxdma.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/psxhw.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/psxinterpreter.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/psxmem.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/psxevents.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/r3000a.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/sio.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/spu.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/gpu.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/pad.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/gte.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/gte_nf.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/gte_divider.c"
        "${PCSX_SOURCE_DIR}/libpcsxcore/new_dynarec/emu_if.c"
    )

    set(PCSX_SPU_SRCS
        "${PCSX_SOURCE_DIR}/plugins/dfsound/dma.c"
        "${PCSX_SOURCE_DIR}/plugins/dfsound/freeze.c"
        "${PCSX_SOURCE_DIR}/plugins/dfsound/registers.c"
        "${PCSX_SOURCE_DIR}/plugins/dfsound/spu.c"
        "${PCSX_SOURCE_DIR}/plugins/dfsound/out.c"
        "${PCSX_SOURCE_DIR}/plugins/dfsound/nullsnd.c"
    )

    set(PCSX_GPU_SRCS
        "${PCSX_SOURCE_DIR}/plugins/gpulib/gpu.c"
        "${PCSX_SOURCE_DIR}/plugins/gpulib/vout_pl.c"
        "${PCSX_SOURCE_DIR}/plugins/gpulib/prim.c"
        "${PCSX_SOURCE_DIR}/plugins/dfxvideo/gpulib_if.c"
    )

    set(PCSX_FRONTEND_SRCS
        "${PCSX_SOURCE_DIR}/frontend/cspace.c"
    )

    list(APPEND PCSX_SRCS
        ${PCSX_CORE_SRCS}
        ${PCSX_SPU_SRCS}
        ${PCSX_GPU_SRCS}
        ${PCSX_FRONTEND_SRCS}
    )
endif()

add_library(retropack-runtime-pcsx SHARED
    ${PCSX_SRCS}
)

target_include_directories(retropack-runtime-pcsx PRIVATE
    src/main/cpp
    "${PCSX_SOURCE_DIR}"
    "${PCSX_SOURCE_DIR}/include"
    "${PCSX_SOURCE_DIR}/libpcsxcore"
    "${PCSX_SOURCE_DIR}/frontend"
    "${PCSX_SOURCE_DIR}/plugins"
    "${PCSX_SOURCE_DIR}/plugins/gpulib"
    "${PCSX_SOURCE_DIR}/plugins/dfxvideo"
    "${PCSX_SOURCE_DIR}/plugins/dfsound"
    "${PCSX_SOURCE_DIR}/deps/libretro-common/include"
)

target_link_libraries(retropack-runtime-pcsx PRIVATE
    GLESv2
    EGL
    OpenSLES
    android
    log
    m
    z
)

target_link_options(retropack-runtime-pcsx PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
