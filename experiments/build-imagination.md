# Building the Imaginations Demos with Coverage

The Imaginations demos can be built with coverage by making a small edit to the CMakeLists.txt.

Add the following snippet to the top of the CMakeLists.txt below the project declaration. Then, compile as described in the README shipped alongside the Imagination Demos.

`
set(CMAKE_CXX_FLAGS "-coverage")
`

# Data File Locations

/path/to/ImaginationDemos/build/examples/Vulkan/{Demo\_Name}/CMakeFiles/{VulkanDemo\_Name}.dir
