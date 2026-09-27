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
    add_definitions(
        -DHAVE_MELONDS_CORE=1
        -D_GNU_SOURCE=1
    )

    set(MELONDS_CORE_SRCS
        "${MELONDS_SOURCE_DIR}/src/ARCodeFile.cpp"
        "${MELONDS_SOURCE_DIR}/src/ARDatabaseDAT.cpp"
        "${MELONDS_SOURCE_DIR}/src/AREngine.cpp"
        "${MELONDS_SOURCE_DIR}/src/ARM.cpp"
        "${MELONDS_SOURCE_DIR}/src/ARMInterpreter.cpp"
        "${MELONDS_SOURCE_DIR}/src/ARMInterpreter_ALU.cpp"
        "${MELONDS_SOURCE_DIR}/src/ARMInterpreter_Branch.cpp"
        "${MELONDS_SOURCE_DIR}/src/ARMInterpreter_LoadStore.cpp"
        "${MELONDS_SOURCE_DIR}/src/ARM_InstrInfo.cpp"
        "${MELONDS_SOURCE_DIR}/src/CP15.cpp"
        "${MELONDS_SOURCE_DIR}/src/CRC32.cpp"
        "${MELONDS_SOURCE_DIR}/src/DMA.cpp"
        "${MELONDS_SOURCE_DIR}/src/DMA_Timings.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_AES.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_Camera.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_DSP.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_I2C.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_I2S.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_NAND.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_NDMA.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_NWifi.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_SD.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSi_SPI_TSC.cpp"
        "${MELONDS_SOURCE_DIR}/src/FATIO.cpp"
        "${MELONDS_SOURCE_DIR}/src/FATStorage.cpp"
        "${MELONDS_SOURCE_DIR}/src/FreeBIOS.cpp"
        "${MELONDS_SOURCE_DIR}/src/GBACart.cpp"
        "${MELONDS_SOURCE_DIR}/src/GBACartMotionPak.cpp"
        "${MELONDS_SOURCE_DIR}/src/GPU.cpp"
        "${MELONDS_SOURCE_DIR}/src/GPU_Soft.cpp"
        "${MELONDS_SOURCE_DIR}/src/GPU2D.cpp"
        "${MELONDS_SOURCE_DIR}/src/GPU2D_Soft.cpp"
        "${MELONDS_SOURCE_DIR}/src/GPU3D.cpp"
        "${MELONDS_SOURCE_DIR}/src/GPU3D_Soft.cpp"
        "${MELONDS_SOURCE_DIR}/src/GPU3D_Texcache.cpp"
        "${MELONDS_SOURCE_DIR}/src/Mic.cpp"
        "${MELONDS_SOURCE_DIR}/src/NDS.cpp"
        "${MELONDS_SOURCE_DIR}/src/NDSCart.cpp"
        "${MELONDS_SOURCE_DIR}/src/ROMList.cpp"
        "${MELONDS_SOURCE_DIR}/src/RTC.cpp"
        "${MELONDS_SOURCE_DIR}/src/Savestate.cpp"
        "${MELONDS_SOURCE_DIR}/src/SPI.cpp"
        "${MELONDS_SOURCE_DIR}/src/SPI_Firmware.cpp"
        "${MELONDS_SOURCE_DIR}/src/SPU.cpp"
        "${MELONDS_SOURCE_DIR}/src/Utils.cpp"
        "${MELONDS_SOURCE_DIR}/src/Wifi.cpp"
        "${MELONDS_SOURCE_DIR}/src/WifiAP.cpp"

        "${MELONDS_SOURCE_DIR}/src/DSP_HLE/UcodeBase.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSP_HLE/AACUcode.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSP_HLE/G711Ucode.cpp"
        "${MELONDS_SOURCE_DIR}/src/DSP_HLE/GraphicsUcode.cpp"

        "${MELONDS_SOURCE_DIR}/src/NDSCart/CartCommon.cpp"
        "${MELONDS_SOURCE_DIR}/src/NDSCart/CartRetail.cpp"
        "${MELONDS_SOURCE_DIR}/src/NDSCart/CartRetailNAND.cpp"
        "${MELONDS_SOURCE_DIR}/src/NDSCart/CartRetailIR.cpp"
        "${MELONDS_SOURCE_DIR}/src/NDSCart/CartRetailBT.cpp"
        "${MELONDS_SOURCE_DIR}/src/NDSCart/CartSD.cpp"
        "${MELONDS_SOURCE_DIR}/src/NDSCart/CartHomebrew.cpp"
        "${MELONDS_SOURCE_DIR}/src/NDSCart/CartR4.cpp"

        "${MELONDS_SOURCE_DIR}/src/fatfs/ff.c"
        "${MELONDS_SOURCE_DIR}/src/fatfs/ffsystem.c"
        "${MELONDS_SOURCE_DIR}/src/fatfs/ffunicode.c"

        "${MELONDS_SOURCE_DIR}/src/sha1/sha1.c"
        "${MELONDS_SOURCE_DIR}/src/tiny-AES-c/aes.c"
        "${MELONDS_SOURCE_DIR}/src/xxhash/xxhash.c"

        "${MELONDS_SOURCE_DIR}/src/blip-buf/blip_buf.c"
    )

    list(APPEND MELONDS_SRCS ${MELONDS_CORE_SRCS})
endif()

add_library(retropack-runtime-melonds SHARED
    ${MELONDS_SRCS}
)

target_include_directories(retropack-runtime-melonds PRIVATE
    src/main/cpp
    "${MELONDS_SOURCE_DIR}/src"
    "${MELONDS_SOURCE_DIR}/src/DSP_HLE"
    "${MELONDS_SOURCE_DIR}/src/NDSCart"
    "${MELONDS_SOURCE_DIR}/src/fatfs"
    "${MELONDS_SOURCE_DIR}/src/sha1"
    "${MELONDS_SOURCE_DIR}/src/tiny-AES-c"
    "${MELONDS_SOURCE_DIR}/src/xxhash"
    "${MELONDS_SOURCE_DIR}/src/blip-buf"
)

target_link_libraries(retropack-runtime-melonds PRIVATE
    GLESv3
    GLESv2
    EGL
    OpenSLES
    android
    log
    m
    z
)

target_link_options(retropack-runtime-melonds PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
