# Building SwiftShader with Coverage Enabled
## Steps:

1. `git clone https://github.com/google/swiftshader.git 9e718f962f87c30d08e91053f0e9ce3467cbd488`
2. Enable the flag `SWIFTSHADER_EMIT_COVERAGE` in CMakeLists.txt
3. Build as described in the SwiftShader README

This can be built by the following script.

## Script:

```
mkdir -p out/build_coverage
cd out/build_coverage
cmake -G Ninja ../.. -DCMAKE_BUILD_TYPE=Debug -DSWIFTSHADER_EMIT_COVERAGE=1
cmake --build . --config Debug
```

The resulting SwiftShader ICD can be found here:
`/path/to/SwiftShader/out/build_coverage/Linux/{libvk_swiftshader.so, vk_swiftshader_icd.json}`

# Running Vulkan programs with SwiftShader

This is done via the Vulkan Loader. The Vulkan Loader is used to run a given Vulkan application with a variety of Vulkan drivers present on the platform. In order to use SwiftShader with the Vulkan Loader, simply specify the environment variable `VK_ICD_FILENAMES` to `/path/to/SwiftShader/build/Linux/vk_swiftshader_icd.json`.

`export VK_ICD_FILENAMES=/path/to/SwiftShader/build/Linux/vk_swiftshader_icd.json`
`./vulkan-app`

# Data File Locations (.gcda and .gcno files)

`/path/to/SwiftShader/build/src/CMakeFiles/vk_swiftshader.dir`

