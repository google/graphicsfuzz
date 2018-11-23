# GraphicsFuzz: building from the command line

Note that:
* Pre-built binaries should be available on the [GitHub releases page](glsl-fuzz-releases.md).
* See [developer documentation](glsl-fuzz-develop.md) for instructions on setting up your IDE.
* See [documentation for using the tools](../README.md#Tools)
  or read the [walkthough for a brief overview of using all tools
  (also requires downloading or building some worker applications)](glsl-fuzz-walkthrough.md).

## Requirements:

* [JDK 1.8+](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Maven](https://maven.apache.org/)
* [Python](https://www.python.org/)
* For Android workers: [Android SDK & NDK](https://developer.android.com/studio/#command-tools)
* For Vulkan worker: [Vulkan SDK](https://vulkan.lunarg.com/sdk/home)

## Get a local copy of this repository

To clone this repository:

```sh
git clone https://github.com/google/graphicsfuzz.git

# Or: git clone git@github.com:google/graphicsfuzz.git

# Change into the cloned directory:
cd graphicsfuzz
```

The Vulkan worker build also requires git submodules to be cloned:

```sh
git submodule init
git submodule update
```

## Build the GraphicsFuzz package

The main GraphicsFuzz package `graphicsfuzz-1.0.zip`
includes the main command line tools
(`glsl-generate`, `glsl-reduce`)
and the server (`glsl-server`),
as well as many third-party
tools (`glslangValidator`, `spirv-opt`).

```sh
# Build all Maven projects, and don't run tests.
mvn package -DskipTests=true
```

Alternative commands:

```sh
# Faster: just build the graphicsfuzz package and dependencies.
mvn package -am -pl graphicsfuzz -DskipTests=true

# Build, and also run tests.
mvn package

# Build, and skip checkstyle.
mvn package -DskipTests=true -Dcheckstyle.skip

# Build, and also run tests, including slower image tests.
mvn package -P imageTests
```

The package is output to `graphicsfuzz/target/graphicsfuzz-1.0.zip`, and is unzipped at the same location `graphicsfuzz/target/graphicsfuzz-1.0/`.

You should add the following to your path:

* `graphicsfuzz/target/graphicsfuzz-1.0/python/drivers`
* Optionally, for third-party tools like `glslangValidator` and `spirv-opt`, add one of:
  * `graphicsfuzz/target/graphicsfuzz-1.0/bin/Linux`
  * `graphicsfuzz/target/graphicsfuzz-1.0/bin/Mac`
  * `graphicsfuzz/target/graphicsfuzz-1.0/bin/Windows`

You can now run e.g. `glsl-generate`, `glsl-reduce`, `glsl-server`, `glslangValidator`, `spirv-opt`.

See [documentation for these tools](../README.md#Tools)
or read the [walkthough for a brief overview of using all tools
(also requires downloading or building some worker applications)](glsl-fuzz-walkthrough.md).

## Build the Vulkan worker (vulkan-worker)

### Android

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

## Build the OpenGL worker (gles-worker)

First, you must build the gles-worker-dependencies project using Maven:

```sh
mvn -am -pl gles-worker-dependencies package
```

This will already have been built if you did `mvn package` earlier without the `-pl` argument.

The OpenGL worker uses [LibGDX](https://github.com/libgdx/libgdx) to produce builds for Linux, Mac, Windows, Android, and iOS.
Android and Linux builds are actively used; other platforms are tested less regularly.

### Android

Required: [Android SDK and
NDK](https://developer.android.com/studio/#command-tools).
See our [continuous integration script](../build/travis/1-install-deps-travis.sh) for a fast way to install the correct versions from the command line.

To build for Android:

```sh
cd gles-worker/
./gradlew android:assembleDebug
```

The generated APK is here:

`gles-worker/android/build/outputs/apk/debug/gles-worker-android-debug.apk`

To install and run using gradle:

```sh
./gradlew android:installDebug android:run
```

### Linux, Mac, Windows

```sh
cd gles-worker/
./gradlew desktop:dist
```

The resulting JAR is here: 

`gles-worker/desktop/build/libs/gles-desktop-worker-1.0.jar`

### iOS

This build is **not actively supported.**.

The iOS build can be performed **only on MacOS.**

```sh
cd gles-worker/

# build
./gradlew ios:createIPA
# run on device
./gradlew ios:launchIOSDevice
# run on simulator
./gradlew ios:launchIPhoneSimulator
```
