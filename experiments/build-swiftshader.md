# Building SwiftShader with Coverage Enabled

The SwiftShader CMakeLists.txt file defines the SWIFTSHADER\_EMIT\_COVERAGE flag to generate coverage information. However, it was designed for a very specific toolchain (clang-10 toolchain, CMake option SWIFTSHADER\_LLVM\_VERSION=10.0).

The build at https://swiftshader-review.googlesource.com/c/SwiftShader/+/44728 can be built with coverage information without these restrictions.

## Steps:

1. Clone the [repo](https://swiftshader-review.googlesource.com/c/SwiftShader/+/44728)
2. Enable the flag `SWIFTSHADER_EMIT_COVERAGE` in CMakeLists.txt
3. Build as described in the SwiftShader README

# Running Vulkan programs with SwiftShader

In order to generate the complete coverage information for a Vulkan sample, the sample needs to be built with coverage as well. This will create the appropriate .gcno files for the sample. In order to create the .gcda files for both the sample and SwiftShader, we need to link the Vulkan sample with the SwiftShader library when we run the Vulkan samples.

This is done via the Vulkan Loader. The Vulkan Loader is used to run a given Vulkan application with a variety of Vulkan drivers present on the platform. In order to use SwiftShader with the Vulkan Loader, simply specify the environment variable `VK_ICD_FILENAMEs` to "/path/to/SwiftShader/build/Linux/vk\_swiftshader\_icd.json".

# Data File Locations (.gcda and .gcno files)

`/path/to/SwiftShader/build/src/CMakeFiles/vk_swiftshader.dir`

