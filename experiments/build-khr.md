# Building the Khronos Samples with Coverage

The Khronos samples can be built with coverage by making a small edit to the CMakeLists.txt.

Add the following snippet at the top of the CMakeLists.txt after the project declaration. Then, compile as described in the README.

`
set(CMAKE_CXX_FLAGS "-coverage")
`
# Data File Locations

/path/to/KHR-Samples/build/linux/samples/{path\_to\_sample}/{sample.dir}
