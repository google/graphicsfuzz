# GraphicsFuzz: developer documentation

The developer documentation describes how to build the projects
from the command line, as well as
[opening and building from IntelliJ IDEA](#opening-and-building-from-intellij-idea).
We present commands assuming a Linux/Mac environment,
but Windows users can adapt the commands or
use the Git Bash shell.

## Build from command line

Note that pre-built binaries are available on the [GitHub releases
page](glsl-fuzz-releases.md).

### Requirements:

* [JDK 1.8+](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Maven](https://maven.apache.org/)
* [Python 3.5+](https://www.python.org/)

* For our Android workers: [Android SDK & NDK](android-notes.md) (see our quick [installation scripts](android-notes.md))
* For the Vulkan desktop worker: [Vulkan SDK](https://vulkan.lunarg.com/sdk/home)

> Our *workers* are applications that run on the device you wish to test; they
> communicate with the `glsl-server` application that is typically run on a more
> powerful x86 machine.

### Get a local copy of this repository

To clone this repository:

```sh
git clone https://github.com/google/graphicsfuzz.git

# Change into the cloned directory:
cd graphicsfuzz

# Update git submodules:
git submodule update --init
```

### Build the GraphicsFuzz package

The main GraphicsFuzz package `graphicsfuzz.zip`
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

# Build, and skip tests and checkstyle.
mvn package -DskipTests=true -Dcheckstyle.skip

# Build, and also run tests, including slower image tests.
mvn package -P imageTests
```

The package is output to `graphicsfuzz/target/graphicsfuzz.zip`, and is unzipped at the same location `graphicsfuzz/target/graphicsfuzz/`.

You should add the following to your path:

* `graphicsfuzz/target/graphicsfuzz/python/drivers`
* Optionally, for third-party tools like `glslangValidator` and `spirv-opt`, add one of:
  * `graphicsfuzz/target/graphicsfuzz/bin/Linux`
  * `graphicsfuzz/target/graphicsfuzz/bin/Mac`
  * `graphicsfuzz/target/graphicsfuzz/bin/Windows`

You can now run e.g. `glsl-generate`, `glsl-reduce`, `glsl-server`, `glslangValidator`, `spirv-opt`.

See [documentation for these tools](../README.md#Tools)
or read the [walkthrough for a brief overview of using all tools
(also requires downloading or building some worker applications)](glsl-fuzz-walkthrough.md).

### Build Amber for the Vulkan worker

The Vulkan worker, `glsl-to-spv-worker`,
receives shaders from our server application (`glsl-server`)
and runs them on a device
using [Amber](https://github.com/google/amber).

Commit `c7ea9967a04b5b00aed14e1a15978e36f39a1d62` is known to work.

#### Desktop

For testing Linux, Windows, and Mac devices,
build Amber according to 
the [documentation](https://github.com/google/amber)
and add the `amber` binary to your `PATH`.

Tips:

* Note that CMake 3.7+ is recommended for automatic discovery of
an installed Vulkan SDK.
* The build instructions assume a Bash shell; on Windows, you 
can use the Git Bash shell.
* Using Ninja is optional; any CMake workflow should be fine.
* If compiling with Visual Studio and Ninja, you may need to use 
the *Command Prompt for Visual Studio* to execute the CMake commands
so that the Visual Studio C++ compiler is found.
You can also add the following arguments to the first CMake command if
there continue to be ambiguities:
`-DCMAKE_C_COMPILER=cl.exe -DCMAKE_CXX_COMPILER=cl.exe`.
* If you see build errors on Windows related to manifest files, exclude
your build directory from your anti-virus scanner.

#### Android

For Android,
follow the [documentation](https://github.com/google/amber) to
build the plain Android native executable, `amber_ndk`, and push it to
your device under `/data/local/tmp/`.
Make sure the binary is executable:

```sh
chmod +x /data/local/tmp/amber_ndk
```

* The build instructions assume a Bash shell; on Windows, you 
can use the Git Bash shell.
However, using `adb` from a Git Bash shell may give unexpected results,
so using the command prompt is recommended when executing `adb` commands such as
`adb push`.

### Build the OpenGL worker (gles-worker)

First, you must build the gles-worker-dependencies project using Maven:

```sh
mvn -am -pl gles-worker-dependencies package
```

This will already have been built if you did `mvn package` earlier without the `-pl` argument.

The OpenGL worker uses [LibGDX](https://github.com/libgdx/libgdx) to produce builds for Linux, Mac, Windows, Android, and iOS.
Android and Linux builds are actively used; other platforms are tested less regularly.

#### Android

Required: [Android SDK and
NDK](https://developer.android.com/studio/#command-tools).
See [Android Notes](android-notes.md) for a fast way to install the correct versions from the command line.

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

#### Linux, Mac, Windows

```sh
cd gles-worker/
./gradlew desktop:dist
```

The resulting JAR is here:

`gles-worker/desktop/build/libs/gles-desktop-worker.jar`

#### iOS

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

## Legacy Vulkan worker developer documentation

In March 2019, we deprecated our legacy Vulkan worker in favor of
[amber](https://github.com/google/amber).
Please build [amber](https://github.com/google/amber)
as described above and use `glsl-to-spv-worker` as described in the
[walkthrough ](glsl-fuzz-walkthrough.md).

*Original documentation:*

The Vulkan worker runs spirv shaders on Linux and Android. Its source
code can be found under `vulkan-worker`.

### Source code layout

All platform-agnostic code is under: `vulkan-worker/src/common/`

The few platform-specifics are defined in:
 - Linux: `vulkan-worker/src/linux/platform.{h,cc}`
 - Android: `vulkan-worker/src/android/src/main/cpp/platform.{h,cc}`

The actual entry points can be found in:
 - Linux: `vulkan-worker/src/linux/main.cc`
 - Android: `vulkan-worker/src/android/src/main/cpp/main.cc`

### Code style

The [Google style for C++](https://google.github.io/styleguide/cppguide.html) is used.

We try to avoid `#ifdef` for platform-specific code. All platform-specifics are
isolated in separate directories, and the relevant platform directory is used by
the build system at build time.

### Third party sources

We are relying on, and grateful to, the following open-source projects:
 - cJSON: https://github.com/DaveGamble/cJSON
 - lodepng: https://github.com/lvandeve/lodepng
 - gflags: https://github.com/gflags/gflags

## Opening and building from IntelliJ IDEA

### Requirements:

You need the build requirements, plus:

* [IntelliJ IDEA Community or Ultimate](https://www.jetbrains.com/idea/)

Follow the instructions for building from the command line above
before proceeding.

### Opening GraphicsFuzz in IntelliJ

The main GraphicsFuzz project is a Maven project
defined in `pom.xml`
at the root of the repository.
It includes the main command line tools
(`glsl-generate`, `glsl-reduce`)
and the server (`glsl-server`).

From IntelliJ, choose Open (not Import Project) and open
the `pom.xml` file in the repository root.
We exclude most IntelliJ project files
from version control, but some are committed.
Thus,
if asked about opening an existing project,
choose "Open Existing Project"
and then choose "Add as Maven Project"
from the notification to
add the root `pom.xml` as a Maven project.


You should see some run configurations
(Build, Clean, Run Server, etc.)
next to the play button.
You can run these to build and clean
from IntelliJ.
In fact, IntelliJ will automatically build Java code
and report errors,
but a manual build is required to download
dependencies, generate code, and output various scripts and binaries
to the expected directories.

> Note that the build task you need to use is 'Build' under the
> 'Run' menu, not the tasks in the top level 'Build' menu. If you
> try to use the normal Build menu initially, it will fail to compile due to
> not being able to find generated files.

### Opening the OpenGL worker (gles-worker) in IntelliJ

* In IntelliJ, open [gles-worker/build.gradle](../gles-worker/build.gradle).
If asked, choose to open as a project, not as a file.
* You should see some options for importing the gradle project.
Tick "auto-import projects" and leave the other options as they are.
It should default to using the gradle wrapper.
* There may be issues because the Android SDK can't be found.
If you are not going to build for Android right away,
you can open `build.gradle` and comment out the entire android section:

```
// project(":android") {
// ...
// }
```

and open `settings.gradle` and delete `'android'` from the list.
* If you do want to build for Android, see [Android Notes](android-notes.md) first.
* From the `Gradle projects` pane, you should be able to execute "desktop:dist"
as you would from the command line: double-click the entry: `:desktop` -> Tasks -> other -> `dist`.
The same is true for Android's "android:assembleDebug" task.
* For running on Android, you should just be able to click the run/debug button
with a special run configuration that gets created for Android (see Android notes below).
* For running/debugging on desktop, you will want to edit run configurations, click the `+` to create a new
configuration and choose "JAR Application".
This is necessary because the app is set to
not run from outside a Jar on the desktop.
You can set the configuration to automatically execute `desktop:dist` before launching
the app.
You may also want to provide the `-start` command line argument
when testing so that you can actually debug the code in `Main.java` and not just the parent
process that just creates a child process (with the `-start` command).

## Thrift

The server and workers use an RPC interface defined
in [thrift/src/main/thrift/FuzzerService.thrift](../thrift/src/main/thrift/FuzzerService.thrift).

## Manage server using Python/iPython

This section describes how you can control the server interactively
via iPython.
It can also be used as a guide for how to write a Python script
that runs at the server; e.g., a script might scan results
and start certain reductions.

```bash
# From source repo:
export PYTHONPATH=/path/to/repo/graphicsfuzz/target/graphicsfuzz/python
# OR: from unzipped graphicsfuzz package:
export PYTHONPATH=/path/to/graphicsfuzz/python
# OR: if you are writing a python script that is in `python/drivers`,
#     start with:
import os
import sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.join(HERE, os.path.pardir))



# Now run iPython:
ipython

```

```python
import manage_server_helper
from fuzzer_service.ttypes import *

manager = manage_server_helper.get_manager()

# The above gets a `manager` for the localhost:8080 server.
# Or, to connect to a different server:
manager = manage_server_helper.get_manager("http://localhost:8080")

manager.queueCommand?

# Outputs:
#  Signature: manager.queueCommand(name, command, queueName, logFile)

# Now try submitting a command.
# Commands are run from the `work` directory.

manager.queueCommand(
  "Reduction for my-laptop",
  [
    "glsl-reduce",
    "--reference", "processing/my-laptop/shaderfamily1/reference.info.json",
    "--reduce_everywhere",
    "--output", "processing/my-laptop/shaderfamily1/reductions/variant_1/",
    "shaderfamilies/shaderfamily1/variants/variant_1.json",
    "IDENTICAL",
    "--server", "http://localhost:8080",
    "--worker", "my-laptop"
   ],
   "my-laptop",
   "processing/my-laptop/shaderfamily1/reductions/variant_1/command.log")

```

Note that the command (list of strings) contains a worker name (here,
"my-laptop") which corresponds to the job queue that will receive ImageJobs that
the worker will render.  The `queueName` parameter contains the same name: this
corresponds to the queue of *commands* to which this command will be queued.
Generally, commands (executable scripts or binaries, such as `run-shader-family`
or `glsl-reduce`) in a command queue will queue jobs to the corresponding
worker's job queue or, in some cases, commands to the same command queue.
Commands like `glsl-reduce` and `run-shader-family` are intercepted by the
server so that a corresponding Java method is executed directly in the server
process, instead of launching a separate process.  Despite this, the `--server`
parameter should still be set; it can typically be set to a dummy string, but if
the command is actually a Python script that will queue additional commands,
then it should typically be set to `localhost:internal_port` (usually
`localhost:8080`) so that the Python script can queue commands to the server
that launched the Python script.
