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
this repo. On the command line, from root directory: `./gradlew assembleDebug`
generates the apk in `./src/android/build/outputs/apk/debug/android-debug.apk`.

For Linux, use cmake with the top-level CMakeLists.txt

## Third party sources

We are relying on, and grateful to, the following open-source projects:
 - cJSON: https://github.com/DaveGamble/cJSON
 - lodepng: https://github.com/lvandeve/lodepng
 - gflags: https://github.com/gflags/gflags
