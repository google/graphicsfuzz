# Building the Google Test Applications with Coverage

The Google Test Applications can be built with coverage by making a small edit to the CMakeLists.txt.

Add the following snippet to towards the end of the CMakeLists.txt before the other directories are included. Then, compile as described in the README and BUILD files.

`
if (CMAKE_CXX_COMPILER_ID MATCHES "GNU")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -coverage")
endif()
`
# Data File Locations

/path/to/GoogleTestApplications/build/application\_sandbox/{application\_name}/CMakeFiles/{application\_name.dir}
