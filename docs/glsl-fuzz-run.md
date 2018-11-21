# GraphicsFuzz: run tests

Running a test consists in rendering a fragment shader on a given
GPU. GraphicsFuzz offers *workers* which run tests on different platforms. A
worker is typically connected to a server which feeds it tests out of shader
families. Workers can also be used on their own to run a single test.

TODO image: server + connected workers

We first describe how to start the server and connect the workers, then how to
queue a series of tests to be ran on the workers. We use `GRAPHICSFUZZ` to refer
to the directory where build outputs are, i.e. either the directory where the
pre-built `assembly-1.0.zip` is extracted, or
`/path/to/graphicsfuzz/assembly/target/assembly-1.0/` in case of a local build.

## Starting the server

The server can be started with the following command:

```shell
java -ea -jar GRAPHICSFUZZ/jar/server-1.0.jar
```

The server is typically started in its own working directory where it will read
shader families from `shaderfamilies` and write results under
`processing/<worker-name>`:

```shell
mkdir -p server-work-dir/shaderfamilies
cp -r /path/to/some/shader_family_000 shaderfamilies/
mkdir -p server-work-dir/processing
cd server-work-dir
java -ea -jar GRAPHICSFUZZ/jar/server-1.0.jar
```

The server listens on port 8080 by default, you can use an other port with the
`--port` option.

## Connect a Vulkan worker

The Vulkan worker requires the Android device under tests to be accessible from
a host via ADB. On the host machine, a python scripts communicates with the
server to gather tests, and interacts via ADB with the Android app installed on
the device.

TODO: an image

First, you need to install the Vulkan worker app on the device and make sure it
can read and write to external storage (the app read and writes files under
`/sdcard/graphicsfuzz/`). A pre-built of the APK is available on the [release
page](https://github.com/google/graphicsfuzz/releases).

```shell
adb install android-vulkan-worker.apk

# If you built from source:
# adb install /path/to/graphicsfuzz/vulkan-worker/src/android/build/outputs/apk/debug/android-debug.apk

adb shell pm grant com.graphicsfuzz.vkworker android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.graphicsfuzz.vkworker android.permission.WRITE_EXTERNAL_STORAGE
```

On the machine which can access the device via ADB, you can now start the
wrapper script which communicates with the server, with the appropriate

```shell
GRAPHICSFUZZ/python/drivers/worker_vk.py
```

You can now start the host-side script which ensure communication between the server and the app:

- launch


## Connect a GLSL worker

- overview

- launch

## Run tests via the server

### Run tests via the server Web UI

## Vulkan worker without the server

## GLSL worker without the server
