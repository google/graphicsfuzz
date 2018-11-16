# GraphicsFuzz vulkan-worker

Vulkan worker for the GraphicsFuzz project. Contents of this repo will
eventually be merged into the google/graphicsfuzz GitHub repo.

## Source code layout

Android entry point: `src/android/src/main/cpp/main.cc`

Linux entry point: `src/linux/main.cc`

All platform-agnostic vulkan things are in: `src/common/vulkan_worker.cc`

The few platform-specifics are in:
 - Android: `src/android/src/main/cpp/platform.cc`
 - Linux: `src/linux/platform.cc`

## How to build

For Android, use Android Studio to open the `build.gradle` file at the root of
this repo. On the command line, from the vulkan-worker directory: `./gradlew assembleDebug`
generates the apk in `./src/android/build/outputs/apk/debug/android-debug.apk`.

For Linux, use CMake with the top-level CMakeLists.txt. E.g.

```sh
# From vulkan-worker directory:

mkdir build
cd build

# As with all CMake projects, there are three steps: (configure, build, and
# install) and they can all be executed using cmake.

cmake .. -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Debug
cmake --build . --config Debug
cmake -DCMAKE_INSTALL_PREFIX=./install -DBUILD_TYPE=Debug -P cmake_install.cmake

# Execute vkworker on the samples:

cd ..
build/install/bin/vkworker samples/shader.vert.spv samples/shader.frag.spv samples/shader.json

```

## Third party sources

We are relying on, and grateful to, the following open-source projects:
 - cJSON: https://github.com/DaveGamble/cJSON
 - lodepng: https://github.com/lvandeve/lodepng
 - gflags: https://github.com/gflags/gflags
