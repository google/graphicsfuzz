# GraphicsFuzz legacy Vulkan worker

The legacy vulkan worker has been deprecated in March 2019. This documentation
is an archive that is not meant to be up-to-date.

## Using the worker

The legacy vulkan worker comes as an APK available from the [releases page](glsl-fuzz-releases.md).

> Warning: you must ensure the screen of your Android device stays on.
> You should therefore enable "Stay awake" in developer settings.
> See [Android notes](android-notes.md#useful-device-settings) for a description of how to enable this setting.

Download the latest `vulkan-worker-android-debug.apk` file
from the [releases page](glsl-fuzz-releases.md)
and install it on your Android device.
You can download the .apk file from your device directly
(e.g. using the Chrome app) and open the .apk file to install it,
or you can install it using `adb`.

> You may need to allow installation of apps from unknown sources. See the
> [Android notes](android-notes.md) for various settings that you may need to change on your Android device, and for other ways of installing the app.

There is no point in manually running this app from the Android device; it will crash unless
it finds shaders in the `/sdcard/graphicsfuzz` directory.

You can run the worker as follows.

> Note that `glsl-to-spv-worker` assumes `adb` is on your PATH.

```sh
# Install the apk, if not installed already.
adb install vulkan-worker-android-debug.apk

# Make sure the app can read/write /sdcard/
adb shell pm grant com.graphicsfuzz.vkworker android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.graphicsfuzz.vkworker android.permission.WRITE_EXTERNAL_STORAGE

# Execute the worker script. Pass the worker name as an argument
# and the serial number (or "IP:port") of the Android device (found using `adb devices -l`).
# For more information on adb and serial numbers, see:
#  https://developer.android.com/studio/command-line/adb
# Add `--help` to see options
# Add `--server` to specify a server URL (default is http://localhost:8080)
# Add `--spirvopt=-O` to run `spirv-opt -O` on every shader.
glsl-to-spv-worker galaxy-s9-vulkan --serial 21372144e90c7fae
```

Note that running `spirv-opt` on each shader by adding the `--spirvopt=ARGS` argument
can help find additional bugs that would otherwise not be found.
This approach can also find bugs in `spirv-opt` itself.

You should see `No job` repeatedly output to the terminal.

If you see `Cannot connect to server`
then the worker script
is failing to connect to the server.

## Building the legacy worker

### Requirements:

* For our Android workers: [Android SDK & NDK](android-notes.md)
* For the Vulkan desktop worker: [Vulkan SDK](https://vulkan.lunarg.com/sdk/home)

### Android

Ensure that the `ANDROID_HOME=/path/to/android-sdk` and
`ANDROID_NDK_HOME=$ANDROID_HOME/ndk-bundle` environment variables are set.

```shell
cd vulkan-worker
./gradlew assembleDebug
```

The resulting APK is here:

`vulkan-worker/src/android/build/outputs/apk/debug/vulkan-worker-android-debug.apk`

### Linux

Make sure the [Vulkan SDK](https://vulkan.lunarg.com/sdk/home)
is installed and the environment variable `VULKAN_SDK` is
properly set.

```sh
cd vulkan-worker

mkdir build
cd build

cmake .. -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Debug  # Or: -G "Ninja"
cmake --build . --config Debug
cmake -DCMAKE_INSTALL_PREFIX=./install -DBUILD_TYPE=Debug -P cmake_install.cmake
```

The resulting binary is here: `vulkan-worker/build/install/vkworker`.

The last step (install) is optional, in which case the binary can be found here: `vulkan-worker/build/vkworker`.
