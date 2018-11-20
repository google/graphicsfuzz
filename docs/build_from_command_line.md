# GraphicsFuzz: build from command line

This page describes how to build from command line.

Note that:
* Pre-build binaries should be available on the [GitHub releases page](https://github.com/google/graphicsfuzz/releases).
* See [developer documentation](development.md) on how to build from IDE.

## Requirements:

* [JDK 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Maven](https://maven.apache.org/)
* [Python](https://www.python.org/)
* For Android workers: [Android SDK & NDK](https://developer.android.com/studio/#command-tools)
* For Vulkan worker: [Vulkan SDK](https://vulkan.lunarg.com/sdk/home)

## Get a local copy of this repository

To clone this repository in a directory named `graphicsfuzz`:

```shell
git clone git@github.com:google/graphicsfuzz.git
```

In the following, we assume you have checked out the graphicsfuzz repository in
a directory called `graphicsfuzz`. All shell snippets assume to start in that
directory. We also assume you are on a Linux host, Windows commands may differ
slightly.

## Build the server

Use maven:

```shell
# Regular build
mvn package -am -pl assembly

# An alternative that skips tests, to build faster
mvn package -am -pl assembly -DskipTests=true

# An alternative that runs additional 'image' tests
mvn package -P imageTests
```

The build output is available in `assembly/target/assembly-1.0`. For instance, the server JAR is here:
`assembly/target/assembly-1.0/jar/server-1.0.jar`.

**Stand-alone archive:** The build also creates an archive of this directory as
`assembly/target/assembly-1.0.zip`. This archive can be copied to an other host,
it should contain everything needed to run the server.

## Build the OpenGL worker

First, you need to run this command to build Thrift-related dependencies:

```shell
mvn -am -pl android-client-dep package
```

**Note**: everytime the Thrift specification is changed, this command should be
run again.

The OpenGL worker relies on [LibGDX](https://github.com/libgdx/libgdx) to be
portable across several platforms. Android and Linux desktop are actively
supported. Windows and iOS builds are documented, but please be aware that hey
are less tested.

Gradle builds can take several minutes to set up the first time you run them, as
gradle is gathering dependencies. Subsequent builds are faster.

### Android

Make sure the [Android SDK /
NDK](https://developer.android.com/studio/#command-tools) are installed.

Use gradle:

```shell
cd platforms/libgdx/OGLTesting/
./gradlew android:assembleDebug
```

The generated APK is here: `android/build/outputs/apk/debug/android-debug.apk`

To install and run using gradle:

```shell
./gradlew android:installDebug android:run
```

### Linux

Use gradle:

```shell
cd platforms/libgdx/OGLTesting/
./gradlew desktop:dist
```

The resulting JAR is here: `desktop/build/libs/desktop-1.0.jar`

### Windows

The JAR produced by the Linux build should run fine on Windows.

### iOS

This build is **not actively supported.**.

The iOS build can be performed **only on MacOS.**

```shell
cd platforms/libgdx/OGLTesting/

# build
./gradlew ios:createIPA
# run on device
./gradlew ios:launchIOSDevice
# run on simulator
./gradlew ios:launchIPhoneSimulator
```
