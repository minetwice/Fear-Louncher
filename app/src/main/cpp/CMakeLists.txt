cmake_minimum_required(VERSION 3.22.1)
project("fearlauncher_native")

# Add native source file
add_library(native_launch SHARED native_launch.cpp)

# Find standard Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Link them
target_link_libraries(native_launch
        ${log-lib}
        ${android-lib})

# C++ Standard
set_target_properties(native_launch PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        POSITION_INDEPENDENT_CODE ON)
