cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-common C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Mandatory Android 15 / API 35+ 16 KB ELF Page Alignment
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

add_library(retropack-host SHARED
    src/main/cpp/libretro_host.c
    src/main/cpp/ringbuffer.c
    src/main/cpp/aaudio_bridge.c
)

target_include_directories(retropack-host PRIVATE
    src/main/cpp
)

target_link_libraries(retropack-host PRIVATE
    android
    log
    m
    dl
    aaudio
)

target_link_options(retropack-host PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
