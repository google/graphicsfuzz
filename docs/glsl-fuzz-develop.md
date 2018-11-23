
# Developer Documentation

The developer documentation currently focuses on opening the various
projects in IntelliJ IDEA.

## Requirements:

* [JDK 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Maven](https://maven.apache.org/)
* [Python](https://www.python.org/)
* [IntelliJ IDEA Community or Ultimate](https://www.jetbrains.com/idea/)
* Android SDK and NDK: see [Android Notes](android-notes.md)

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

## Opening GraphicsFuzz in IntelliJ

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

## Opening the OpenGL worker (gles-worker) in IntelliJ

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

# Thrift

The server and workers use an RPC interface defined
in [thrift/src/main/thrift/FuzzerService.thrift](../thrift/src/main/thrift/FuzzerService.thrift).

# Manage server using Python/iPython

This section describes how you can control the server interactively
via iPython.
It can also be used as a guide for how to write a Python script
that runs at the server; e.g., a script might scan results
and start certain reductions.

```bash
# From source repo:
export PYTHONPATH=/path/to/repo/graphicsfuzz/target/graphicsfuzz-1.0/python
# OR: from unzipped graphicsfuzz package:
export PYTHONPATH=/path/to/graphicsfuzz-1.0/python
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
  "Reduction for dell-laptop",
  [
    "glsl-reduce",
    "--reference", "processing/dell-laptop/shaderfamily1/reference.info.json",
    "--reduce_everywhere",
    "--output", "processing/dell-laptop/shaderfamily1/reductions/variant_1/",
    "shaderfamilies/shaderfamily1/variants/variant_1.json",
    "IDENTICAL",
    "--server", "http://localhost:8080",
    "--token", "dell-laptop"
   ],
   "dell-laptop",
   "processing/dell-laptop/shaderfamily1/reductions/variant_1/command.log")

```
Note that the command (list of strings) contains a token: this token corresponds
to the job queue that will receive ImageJobs that the worker will render.
The `queueName` parameter contains the same token:
this corresponds to the queue of *commands* to which this command
will be queued.
Generally,
commands (executable scripts or binaries,
such as `run_shader_family` or `glsl-reduce`) in a command queue
will queue jobs to the corresponding worker's job queue
or, in some cases, commands to the same command queue.
Commands like `glsl-reduce` and `run_shader_family`
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
