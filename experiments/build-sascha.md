# Building the Sascha Demos with Coverage

The Sascha demos can be built with coverage by making a small edit to the CMakeLists.txt that is shipped alongside the Sascha Demos.

Add the following snippet to towards the end of the CMakeLists.txt before the subdirectories are added. Then, compile as described in the README shipped alongside the Sascha demos.

`
if (CMAKE_CXX_COMPILER_ID MATCHES "GNU")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -coverage")
endif()
`
# Data File Locations

/path/to/Sascha/build/examples/CMakeFiles/{example\_name}.dir/{example\_name}
