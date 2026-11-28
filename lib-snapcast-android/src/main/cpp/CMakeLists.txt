cmake_minimum_required(VERSION 3.14)

project(lib_snapcast_android LANGUAGES CXX)

add_subdirectory(snapcast)

# x86/x86_64 on API 26-28: let snapclient survive the app seccomp filter trapping legacy
# syscalls made by system libraries it loads (see the file header). No-op on other ABIs.
target_sources(libsnapclient.so PRIVATE seccomp_compat/legacy_syscall_shim.cpp)

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