cmake_minimum_required(VERSION 3.14)

project(lib_snapcast_android LANGUAGES CXX)

add_subdirectory(snapcast)

add_executable(snapcontrol snapcontrol/main.cpp)

set_target_properties(snapcontrol PROPERTIES
        OUTPUT_NAME "snapcontrol"
        PREFIX "lib"
        SUFFIX ".so"
)

target_link_options(snapcontrol PRIVATE
        "-Wl,-z,max-page-size=16384"
        # Omit -Wl,-z,common-page-size=16384: on tiny binaries it pads PT_GNU_RELRO past
        # the last mapped page of PT_LOAD; Android 9's linker can't mprotect the gap.
)