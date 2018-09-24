
# Development Documentation

List of all requirements:

* [JDK 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Maven](https://maven.apache.org/)
* [Python](https://www.python.org/)

Skip to the Development section if you want to set things up for development more permanently.

## Running the server

Requirements for running the server locally: JDK.

Download and extract the latest release zip. Change into the directory that contains `bin`, `jar`, etc. and execute the following:

```shell
# The server reads and writes files in the current working directory.
# Let's just create a temp directory for now.
mkdir temp
cd temp

# Copy in the sample shader families.
# HE: 12/09/2018: This is obsolete!
cp -R ../sample-shadersets shadersets

# Now we can start the server
java -ea -jar ../jar/server-1.0.jar
```

Open a browser to
[http://localhost:8080/webui](http://localhost:8080/webui).

From here, you can queue shader families to workers. However, there are no workers yet.

## Running the libgdx desktop worker

Requirements: JDK.

Download and extract the latest desktop worker jar.
You will need to create a `token.txt` file in the same directory
with one line containing the token (a name for the device you are testing). E.g.

`echo paul-laptop-windows>token.txt`.

Then execute the following:

```shell
# Use `--help` to see options
# Use `-server` to specify a server URL (default is http://localhost:8080/)
# Use `-start` to disable autorestart of the worker (can be useful).
# Let's run the worker.
java -jar desktop-1.0.jar
```

* The worker might not render images properly if it is minimized.

Now return to the webui page on the server and queue a shader family to the worker.

## Running the libgdx Android worker

Download the latest Android libgdx worker apk on your Android device.
Install and run the app.

* Enter the IP address or hostname of the machine on which you are running the server,
plus the port (default is 8080).
  * E.g. http://Paul-Laptop:8080/
  * If your Android device is connected via USB to a machine that is running the server at http://localhost:8080/, you can do `adb reverse tcp:8080 tcp:8080` and enter http://localhost:8080/ in the app. The adb command forwards the local port 8080 of your Android device to your machine's 8080 port.
* Enter a token (a name for the device you are testing).

Now return to the webui page on the server and queue a shader family to the worker.

## Building the server

Requirements: JDK, Maven.

Execute the following:

```shell
# clone this repo
git clone git@github.com:google/graphicsfuzz.git

# change into repo root
cd graphicsfuzz

# You can build immediately using Maven. This will take a while the first time.
# The fastest command is:
mvn package -DskipTests=true -Dcheckstyle.skip=true -am -pl assembly

# Of course, the above skips tests, checkstyle, and only builds the assembly package.
# You can also try:
mvn package

# The build artifact as a directory:  assembly/target/assembly-1.0/
# And as an archive:                  assembly/target/assembly-1.0.zip

# change into artifact directory
cd assembly/target/assembly-1.0/
```

(see running the server, but instead of extracting the release zip,
use the artifact directory `assembly/target/assembly-1.0/`)

Additional Maven commands and Maven profiles:

```shell
# To build just the assembly project (that contains the server and command line tools).
mvn package -am -pl assembly

# Skip tests
mvn package -am -pl assembly -DskipTests=true

# The "imageTests" profile runs additional tests using SwiftShader:
mvn package -P imageTests

```

## Building the libgdx worker (desktop, Android, iOS)

Requirements: JDK, Maven.

### Prerequisite step for all platforms

The libgdx worker supports desktop, Android, and iOS platforms, but you must perform the following prerequisite step before building for any other platform.

From the root of the repo:

```shell
# The worker requires some dependencies
# to be installed to the local maven repo.
# These are not just used for Android, despite the name.
mvn -am -pl android-client-dep install

# Note that when the Thrift spec is changed, you must
# call the above to update libgdx dependencies.
```

### Building the libgdx worker for desktop

Although our focus is testing Android devices,
building and running the desktop version of the libgdx worker
is recommended to make sure things work as expected.

```shell
# Ensure you have performed the prerequisite step before continuing.

# Change to the libgdx worker project root.
cd platforms/libgdx/OGLTesting/

# Gradle is used to build the worker, but it downloads itself.
# Let's build the desktop version of the worker.
# Omit the "./" on Windows.
# This may take a while the first time.
./gradlew desktop:dist

# The jar is in: desktop/build/libs
```

(see running the worker)

### Building and running the libgdx worker for Android

(You need to install Android SDK beforehand, see the "Android notes" below)

```shell
# Ensure you have performed the prerequisite step before continuing.

# Change to the libgdx worker project root.
cd platforms/libgdx/OGLTesting/

# build
./gradlew android:assembleRelease android:assembleDebug
# run on device
./gradlew android:installDebug android:run

```

### Building and running the libgdx worker for iOS

```shell
# iOS: (only works on macOS)

# Ensure you have performed the prerequisite step before continuing.

# Change to the libgdx worker project root.
cd platforms/libgdx/OGLTesting/

# build
./gradlew ios:createIPA
# run on device
./gradlew ios:launchIOSDevice
# run on simulator
./gradlew ios:launchIPhoneSimulator
```

# Development

## IDE

Use IntelliJ and open (not import, etc.)
the `pom.xml` file in the repo root.

Then, from the repo root:

```shell
# Copy sample shader families to the temp directory.
cp -R assembly/src/main/scripts/sample-shadersets temp/shadersets

# Copy in the run configurations for IntelliJ.
mkdir .idea/runConfigurations
cp run-configurations/* .idea/runConfigurations/
```

You should now be able to build, clean, and run the server,
from IntelliJ.
In fact, IntelliJ will automatically build the Java code,
but a manual build is required to ensure the binaries
and Python scripts
are output to `assembly/target/assembly-1.0`;
the server will expect these files to be there
when not being run from the jar.

Note that the build task you need to use is 'Build' under the
'Run' menu, not the tasks in the top level 'Build' menu. If you
try to use the normal Build menu, it will fail to compile due to
not being able to find the thrift generated files (because they
will not have been generated).

### Using the IDE with the libgdx worker (platforms/libgdx/OGLTesting)

* In IntelliJ, open [platforms/libgdx/OGLTesting/build.gradle](platforms/libgdx/OGLTesting/build.gradle).
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
* If you do want to build for Android, see "Android notes" below ("To easily set up the Android SDK, follow the...").
* From the `Gradle projects` pane, you should be able to execute "desktop:dist"
as you would from the command line: double-click the entry: `:desktop` -> Tasks -> other -> `dist`.
The same is true for Android.
* For running on Android, you should just be able to click the run/debug button
with a special run configuration that gets created for Android (see Android notes below).
* For running/debugging on desktop, you will want to edit run confirgurations, click the `+` to create a new
configuration and choose "JAR Application".
This is necessary because the app is set to
not run from outside a Jar on the desktop.
You can set the configuration to automatically execute `desktop:dist` before launching
the app.
You may also want to provide the `-start` command line argument
when testing so that you can actually debug the code in `Main.java` and not just the parent
process that just creates a child process (with the `-start` command).

# Docker
The server zip contains a `Dockerfile` suitable for running the server.
The script `docker_build_create_start.sh.template` can be modified and then executed to automatically create a docker image and container, and start the container.

# Manage server using Python/iPython

This section describes how you can control the server interactively
via iPython.
It can also be used as a guide for how to write a Python script
that runs at the server; e.g., a script might scan shader set experiments
and start certain reductions.

```bash
# From source repo:
export PYTHONPATH=/path/to/graphicsfuzz/assembly/target/assembly-1.0/python
# OR: from unzipped server package:
export PYTHONPATH=/path/to/server-1.0/python
# OR: if you are writing a python script that is in `python/drivers`:
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
  "ReduceVariant for xxx",
  [
    "reduce_variant",
    "--reference_image", "processing/7093366951813584254/test_exp/recipient.png",
    "--reduce_everywhere",
    "--output", "processing/7093366951813584254/test_variant_1_inv",
    "shadersets/28Apr16_shader_100_shader_14/variants/variant_1.frag",
    "IDENTICAL",
    "--server", "http://localhost:8080",
    "--token", "7093366951813584254"
   ],
   "7093366951813584254",
   "processing/7093366951813584254/test_variant_1_inv/command.log")

```
Note that the command (list of strings) contains a token: this token corresponds
to the job queue that will receive ImageJobs that the worker will render.
The `queueName` parameter contains the same token:
this corresponds to the queue of *commands* to which this command
will be queued.
Generally,
commands (executable scripts or binaries,
such as `run_shader_set` or `reduce_variant`) in a command queue
will queue jobs to the corresponding worker's job queue
or, in some cases, commands to the same command queue.
Commands like `reduce_variant` and `run_shader_set`
are intercepted by the server so that a corresponding
Java method is executed directly in the server process,
instead of launching a separate process.
Despite this,
the `--server` parameter should still be set;
it can typically be set to a dummy string,
but if the command is actually a Python script that will
queue additional commands, then it should typically be set to
`localhost:internal_port` (usually `localhost:8080`) so that
the Python script can queue commands to the server that launched the Python
script.

# Android notes

## Installing the Android platform tools

It is recommended that you install the Android platform tools
using the Android SDK;
there are instructions for this below.
However, if you don't want to install the SDK, you can just install the Android platform tools:

* Using your system's package manager. E.g. `sudo apt-get install android-sdk-platform-tools`.
* By [downloading them directly](https://developer.android.com/studio/releases/platform-tools).

## Other notes

* Open `Settings`, `About device`, and keep tapping build number until developer settings are enabled.  The build number might be under a further `Software information` option.
* In developer settings, enable `USB debugging` and `Stay awake`.
* In security settings (which might be under `Lock screen and security`), enable installing apps from unknown sources.
* You can download the android worker directly on the phone from the releases page
or possibly from any running server (if the server is from an automated build). E.g.
[http://localhost:8080/static/android-debug.apk](http://localhost:8080/static/android-debug.apk).

(The "release" version may not work.)
You can also open the code and run it from IntelliJ, which is described below.
* To easily set up the Android SDK, follow the
commands in the automated build [Dockerfile](../build/docker/ci/Dockerfile).
This will install the required packages using the Android SDK Manager. Note that `ANDROID_HOME` can be anywhere.
* In IntelliJ, open [platforms/libgdx/OGLTesting/build.gradle](platforms/libgdx/OGLTesting/build.gradle).
* Next to `build.gradle`, create a file called `local.properties` with the contents:

```
sdk.dir=/data/android/android-sdk-linux
```

where the given directory should contain `tools/`, `platform-tools/`, etc.
* IntelliJ should add an `android` run configuration. Press the play button
to run `android` and pick your device. The app should then start.
* In IntelliJ, open `Android Monitor` to view log output.
Enter `com.graphicsfuzz.` in the search text box and select `No filters` in the drop down.
This will ensure you see output from all processes; our worker uses three processes on Android.
* Press the back button on the device to exit the app. Pressing home or
other buttons won't work, as the app tries to stay in the foreground.
* To make the worker use a specific server,
add a line in `android/src/AndroidLauncher.java`
after `main.setPlatformInfoJson`. E.g. `main.setUrl("http://bzxc:8080");`

