cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-mupen64 C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+, NDK r28b+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

set(MUPEN_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/submodules/mupen64plus-next")

set(MUPEN_SRCS
    src/main/cpp/mupen64-jni.c
    src/main/cpp/ringbuffer.c
)

if(EXISTS "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/api/m64p_types.h" OR EXISTS "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/api/frontend.c")
    message(STATUS "Found upstream Mupen64Plus submodule at ${MUPEN_SOURCE_DIR}")
    add_definitions(
        -DHAVE_MUPEN64_CORE=1
        -DM64P_CORE_PROTOTYPES=1
        -DCORE_NAME=\"mupen64plus\"
        -DINLINE=inline
        -DNO_ASM=1
        -DNO_MD5=1
        -DM64P_STATIC_CORE=1
    )

    set(MUPEN_CORE_SRCS
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/api/callbacks.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/api/debugger.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/api/frontend.c"
        "${MUPEN_SOURCE_DIR}/custom/mupen64plus-core/api/config.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/backends/plugins_compat/audio_plugin_compat.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/backends/api/video_capture_backend.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/backends/plugins_compat/input_plugin_compat.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/backends/clock_ctime_plus_delta.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/backends/dummy_video_capture.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/backends/file_storage.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/cart/cart.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/cart/af_rtc.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/cart/cart_rom.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/cart/eeprom.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/cart/flashram.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/cart/is_viewer.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/cart/sram.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/controllers/game_controller.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/controllers/vru_controller.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/controllers/paks/biopak.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/controllers/paks/mempak.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/controllers/paks/rumblepak.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/controllers/paks/transferpak.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/dd/dd_controller.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/dd/disk.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/device.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/gb/gb_cart.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/gb/mbc3_rtc.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/gb/m64282fp.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/memory/memory.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/pif/bootrom_hle.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/pif/cic.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/pif/n64_cic_nus_6105.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/pif/pif.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/r4300/cached_interp.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/r4300/cp0.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/r4300/cp1.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/r4300/cp2.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/r4300/idec.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/r4300/interrupt.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/r4300/pure_interp.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/r4300/r4300_core.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/r4300/tlb.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rcp/ai/ai_controller.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rcp/mi/mi_controller.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rcp/pi/pi_controller.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rcp/rdp/fb.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rcp/rdp/rdp_core.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rcp/ri/ri_controller.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rcp/rsp/rsp_core.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rcp/si/si_controller.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rcp/vi/vi_controller.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/device/rdram/rdram.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/main/main.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/main/util.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/main/cheat.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/main/rom.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/main/savestates.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/plugin/plugin.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/plugin/dummy_audio.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/plugin/dummy_input.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/osal/files_unix.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/osal/dynamiclib_unix.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-core/subprojects/md5/md5.c"
    )

    set(MUPEN_RSP_SRCS
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/alist.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/alist_audio.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/alist_naudio.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/alist_nead.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/audio.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/cicx105.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/hle.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/hvqm.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/jpeg.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/memory.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/mp3.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/musyx.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/re2.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src/plugin.c"
    )

    set(MUPEN_RDP_SRCS
        "${MUPEN_SOURCE_DIR}/mupen64plus-video-angrylion/interface.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-video-angrylion/n64video.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-video-angrylion/n64video/rdp.c"
        "${MUPEN_SOURCE_DIR}/mupen64plus-video-angrylion/n64video/vi.c"
    )

    list(APPEND MUPEN_SRCS
        ${MUPEN_CORE_SRCS}
        ${MUPEN_RSP_SRCS}
        ${MUPEN_RDP_SRCS}
    )
endif()

add_library(retropack-runtime-mupen64 SHARED
    ${MUPEN_SRCS}
)

target_include_directories(retropack-runtime-mupen64 PRIVATE
    src/main/cpp
    "${MUPEN_SOURCE_DIR}"
    "${MUPEN_SOURCE_DIR}/custom"
    "${MUPEN_SOURCE_DIR}/custom/mupen64plus-core"
    "${MUPEN_SOURCE_DIR}/custom/android/include"
    "${MUPEN_SOURCE_DIR}/mupen64plus-core/src"
    "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/api"
    "${MUPEN_SOURCE_DIR}/mupen64plus-core/src/osal"
    "${MUPEN_SOURCE_DIR}/mupen64plus-core/subprojects/md5"
    "${MUPEN_SOURCE_DIR}/mupen64plus-rsp-hle/src"
    "${MUPEN_SOURCE_DIR}/mupen64plus-video-angrylion"
    "${MUPEN_SOURCE_DIR}/mupen64plus-video-angrylion/n64video"
    "${MUPEN_SOURCE_DIR}/libretro-common/include"
    "${MUPEN_SOURCE_DIR}/libretro"
    "${MUPEN_SOURCE_DIR}/libretro/include"
)

target_link_libraries(retropack-runtime-mupen64 PRIVATE
    GLESv3
    GLESv2
    EGL
    OpenSLES
    android
    log
    m
    z
)

target_link_options(retropack-runtime-mupen64 PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
