# GraphicsFuzz walkthrough

GraphicsFuzz is a testing framework for automatically finding and simplifying bugs in graphics shader compilers.

In this walkthrough, we will briefly demonstrate most features of GraphicsFuzz from start to finish, including our browser-based UI.

We will be using the latest release zip `graphicsfuzz-1.0.zip` and worker applications.
You can download these from the [releases page](glsl-fuzz-releases.md)
or [build them from source](glsl-fuzz-build.md).
If you want to use the Android worker you will also need an Android device
or the Android device emulator.

Add the following directories to your path:

* `graphicsfuzz-1.0/python/drivers`
* One of:
  * `graphicsfuzz-1.0/bin/Linux`
  * `graphicsfuzz-1.0/bin/Mac`
  * `graphicsfuzz-1.0/bin/Windows`

The `graphicsfuzz-1.0/` directory is the unzipped graphicsfuzz release.
If building from source, this directory can be found at `graphicsfuzz/target/graphicsfuzz-1.0/`.

You will also need to install the latest version of the Java 8 Development Kit,
either:

* From your system's package manager. E.g. `sudo apt-get install openjdk-8-jdk`.
* By [downloading and installing Oracle's binary distribution](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (look for Java SE 8uXXX then the JDK link).
* By downloading and installing some other OpenJDK binary distribution for your platform.


```sh
# To check that Java 8 is installed and in use:
java -version
# Output: openjdk version "1.8.0_181"
```

## Generating shaders using `glsl-generate`

GraphicsFuzz works by taking a *reference shader* and producing a family of *variant shaders*, where each variant should render the same image as the reference (modulo possible floating-point differences).

![reference and variants, to GPU, to many equivalent images](images/variant-same.png)

The reference shader and its variants together are referred to as a *shader family*.

The `glsl-generate` tool generates shader families. The inputs are a reference shader and a folder of *donor shaders* (not pictured above). In theory, these input shaders can be any GLSL fragment shaders. In practice, we designed our tools to support shaders from glslsandbox.com, and so we currently only support shaders with uniforms as inputs (and the values for these will be fixed). Each shader file `shader.frag` must have a corresponding `shader.json` metadata file alongside it, which contains the values for the uniforms.

We can create some shader families as follows:

```sh
# Copy the sample shaders into the current directory:
cp -r graphicsfuzz-1.0/shaders/samples samples

# Create a work directory to store our generated shader families.
# The directory structure will allow the server
# to find the shaders later.
mkdir -p work/shaderfamilies

# Generate several shader families from the set of sample shaders.
# Synopsis:
# glsl-generate [options] donors references num_variants glsl_version prefix output_folder

# Generate some GLSL version 300 es shaders.
glsl-generate --max_bytes 500000 --multi_pass samples/donors samples/300es 10 "300 es" family_300es work/shaderfamilies

# Generate some GLSL version 100 shaders.
glsl-generate --max_bytes 500000 --multi_pass samples/donors samples/100 10 "100" family_100 work/shaderfamilies

# Generate some "Vulkan-compatible" GLSL version 300 es shaders that can be translated to SPIR-V for Vulkan testing.
glsl-generate --max_bytes 500000 --multi_pass --generate_uniform_bindings --max_uniforms 10 samples/donors samples/310es 10 "310 es" family_vulkan work/shaderfamilies

# The lines above will take approx. 1-2 minutes each, and will generate a shader family for every
# shader in samples/300es or samples/100:
ls work/shaderfamilies

# Output:

# family_100_bubblesort_flag
# family_100_mandelbrot_blurry
# family_100_squares
# family_100_colorgrid_modulo
# family_100_prefix_sum

# family_300es_bubblesort_flag
# family_300es_mandelbrot_blurry
# family_300es_squares
# family_300es_colorgrid_modulo
# family_300es_prefix_sum

# family_vulkan_bubblesort_flag
# family_vulkan_mandelbrot_blurry
# family_vulkan_squares
# family_vulkan_colorgrid_modulo
# family_vulkan_prefix_sum
```

## Running the server

The `glsl-server` application is used to drive the testing of different devices by
communicating with worker applications that run on the devices.

> You do not have to use the server or worker applications;
> `glsl-generate` and `glsl-reduce` can be used as stand-alone
> command line tools, although you will need to write a script
> that can utilize your shaders.

You can start `glsl-server` as follows:

```sh
# The server uses the current directory as its working directory
# so we must change to our `work` directory.
cd work

# Check that the shader families are here.
ls
# Output:
# shaderfamilies
# processing <-- only if you have previously run the server here.

# Execute the server app.
# The server listens on port 8080 by default, but you can override
# this with e.g. --port 80
glsl-server
```

Now visit [http://localhost:8080/webui](http://localhost:8080/webui)
in your browser.
You should see several lists:
connected workers, disconnected workers,
and shader families.
You should see the shader families that we generated
in the previous section. We will later queue shader families to
some connected workers.


## Running workers

We will now run some worker applications
that connect to the server, allowing us to test the devices on which
the workers run.

### `gles-desktop-worker`

To test the OpenGL drivers on a
Mac, Linux, or Windows desktop device,
download the latest `gles-desktop-worker-1.0.jar` file from the
[releases page](glsl-fuzz-releases.md).

You will need to create a `worker-name.txt` file in the same directory with one
line containing the worker name to identify your device. E.g.

```sh
echo my-laptop > worker-name.txt
```

Then execute the following:

```sh
# Add `--help` to see options
# Add `--server` to specify a server URL (default is http://localhost:8080/)
java -ea -jar gles-desktop-worker-1.0.jar
```

You should see a small window containing some animated white text on
a black background, including the text `state: GET_JOB`. In the terminal, you
should see repeating text similar to:

```sh
JobGetter: Got a job.
Main: No job for me.
Main: Waiting 6 ticks.
```

If you see `state: NO_CONNECTION` in the window, then the worker application
is failing to connect to the server.

### `gles-worker-android`

To test the OpenGL ES drivers on an Android device,
download the latest `gles-worker-android-debug.apk` file
from the [releases page](glsl-fuzz-releases.md).
You can download the .apk file from your device directly
(e.g. using the Chrome app)
and open the .apk file to install it,
or you can install it using `adb`.

> You may need to allow installation of apps from unknown sources.
> See the [Android notes](android-notes.md)
> for
> various settings that you may need to change on your Android device,
> and for other ways of installing the app.

You can now open the GraphicsFuzz app from your app drawer;
the screen may briefly rotate and then return to normal,
as if the app has crashed,
but the app should then start and the screen will remain
black with animated text,
similar to the desktop worker.

> To exit the app, you **must use the back button**, otherwise it will automatically restart.

The app should show a dialogue where you can enter the URL of the server.
If your Android device and server are on the same network,
you can enter your desktop/laptop hostname and port,
or your desktop/laptop IP address and port.

E.g. `paul-laptop:8080` or `192.168.0.4:8080`.

However, this usually won't work
on university, public, or corporate networks.
Alternatively, you can connect your device
via USB, execute `adb reverse tcp:8080 tcp:8080` on your desktop/laptop,
and use `localhost:8080` as the server address.
See the [Android networking guide](android-networking-guide.md)
for more detailed instructions.

> If you need to enter a new server address, you will need to clear the app's data. E.g. by uninstalling and reinstalling the app.

The app will show a second dialogue where you must enter the worker name.  Once
you have entered a name, you should see a mostly black screen with animated text
that contains `state: GET_JOB`.  If you see `state: NO_CONNECTION` then the
worker application is failing to connect to the server.

### `vulkan-worker-android`

You can use the `vulkan-worker-android` app
to test the Vulkan drivers on an Android device.
This worker requires running a `glsl-to-spirv-worker`
on a desktop machine,
with an Android device (connected via USB) that has the `vulkan-worker-android` app installed.

```
glsl-server     <--- HTTP --->    glsl-to-spirv-worker    <--- adb commands --->    vulkan-worker-android app
(on a desktop)                    (on a desktop)                                    (on an Android device)
```


The `glsl-to-spirv-worker` script translates the GLSL shaders to SPIR-V
via `glslangValidator` before sending the shader to
the `vulkan-worker-android` app running on the Android device.

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

You can run the worker as follows:

```sh
# Install the apk, if not installed already.
adb install vulkan-worker-android-debug.apk

# Make sure the app can read/write /sdcard/
adb shell pm grant com.graphicsfuzz.vkworker android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.graphicsfuzz.vkworker android.permission.WRITE_EXTERNAL_STORAGE

# Execute the worker script. Pass the worker name as an argument
# and the serial number of the Android device (found using `adb devices`).
# Add `--help` to see options
# Add `--server` to specify a server URL (default is http://localhost:8080)
glsl-to-spirv-worker galaxy-s9-vulkan --adbID 21372144e90c7fae
```

You should see `No job` repeatedly output to the terminal.

If you see `Cannot connect to server`
then the worker script
is failing to connect to the server.


## Running shaders on the worker applications

Return to the Web UI
at [http://localhost:8080/webui](http://localhost:8080/webui)
and refresh the page.
You should see the workers under "Connected workers".
We can now queue some shader families to the workers:

* Click "Run shader families on workers".
* Select one or more workers via the checkboxes under "Workers".
* Select one or more shader families via the checkboxes under "Shader families".
* Click "Run jobs".

You should see the worker applications rendering images;
these images are being captured and uploaded to the server.

## Viewing shader family results

Return to the Web UI
at [http://localhost:8080/webui](http://localhost:8080/webui)
and click on one of the connected workers,
and then click on one of the shader families:
you should see a table of images.
Alternatively,
just click on one of the shader families to view
the results for this family across all workers.

![Table of image results](images/screenshot-results-table.png)

In the example above,
the image for shader `variant_001` differs from the rest.
Recall that all images should be identical,
thus `variant_001` has exposed a bug that causes the wrong image to rendered.

Clicking on `variant_001`
reveals the GLSL fragment shader source that
triggered the bug:

![Variant_001 fragment shader source](images/screenshot-variant-source.png)

However,
this shader is much larger and more complex than it needs to be
to expose the bug. Thus,
in the next section,
we will reduce the shader to obtain a smaller and simpler shader
that is more useful in understanding the root cause of the bug.



## Queuing a bad image reduction

Return to the results table view:

![Table of image results](images/screenshot-results-table.png)

Click on the image under `variant_001`
to reveal the single result page,
and click the "Reduce result" button
to reveal the reduction panel:

![Variant_001 single result](images/screenshot-single-result-bad-image.png)

From here,
we can queue a reduction of the variant shader
to find a smaller, simpler shader
that still exposes the bug.
The default reduction settings are sufficient, so just click
"Start reduction".

Once again, you should see the worker application rendering images.
Once the reduction has finished,
refresh the page and you should see the result:

![Variant_001 reduction result](images/screenshot-variant-reduction-result.png)

In particular, you can see the difference between the
reference shader and the reduced variant shader;
in the above example,
adding just 4 lines (that should have no effect) to the reference shader
was enough to cause the wrong image to be rendered.

> The diff view currently assumes that the `diff` command line tool is
> available and on the path, which may not be the case on your system.

## Queuing a crash reduction

The results table for the shader family below shows
that `variant_004` failed to render due to a crash:

![Table of image results](images/screenshot-results-table-crash.png)

Click on the red table cell to view the single result page
and click "Reduce result" to reveal the reduction panel:

![Single result page with crash](images/screenshot-single-result-crash.png)

In the "Error Regex" text box, enter
a substring from the "Run log" text box that
will confirm the issue.
For example,
in this case,
we could enter "Fatal signal 11".
Ideally, we should enter something even more specific,
such as a function name from a stack trace,
but this is only possible if a stack trace is shown
in the run log.

> The "Error Regex" text will be prepended and appended with `.*`
> and matched as a regular expression against the run log.

The other default settings are sufficient, so click "Start Reduction".

This time, you will not see the worker rendering images,
as most attempts will cause the worker to crash,
as expected.

Once the reduction has finished,
refresh the page and you should see the result.
However, for crash reductions,
the diff view makes little sense,
as the reducer will have removed as much code as possible
(due to the "Reduce Everywhere" option).
Thus, click "View reduced shader" to
see the small, simple shader that triggers the bug:


![Reduced result](images/screenshot-crash-reduction-result.png)

In the above example,
a function body that contains a somewhat complex `pow` function call
is enough to trigger the bug.

## Exploring results in the file system

You can see results in the file system within the server's working directory at the following locations:
* Shader family results are under `work/processing/<worker>/<shader_family>/`
* Reduction results are under `work/processing/<worker>/<shader_family>/reductions/<variant>/`

### Shader family results

Under `work/processing/<worker>/<shader_family>/`, each variant can lead to these files:
* `<variant>.info.json`
* `<variant>.txt`
* `<variant>.png` (only when `SUCCESS` status)
* `<variant>.gif` (only for `NONDET` status)
* `<variant>_nondet1.png` (only for `NONDET` status)
* `<variant>_nondet2.png` (only for `NONDET` status)

`<variant>.info.json` contains results overview encoded in JSON. It looks like the following:

```shell
{
  "status": "SUCCESS",
  ... other fields ...
}
```

The `status` field is a string summarizing the result, it can be of value:
* `SUCCESS`: the variant rendered an image
* `CRASH`: the variant led to a driver crash
* `NONDET`: the variant led to a non-deterministic rendering
*  `TIMEOUT`: the variant took too long to be processed (in the case of the
    vulkan worker, this may indicate glslangValidator or spirv-opt taking too
    long to process the variant)
* `UNEXPECTED_ERROR`: the variant led to an unexpected error

This JSON also contains other fields, like metrics of difference between the
variant image and the reference image. **NB:** as of November 2018, only the
`status` is considered stable.

`<variant>.txt` contains the log of the variant run. On Android, it is a dump of
the android logcat, and can contain precious information like details on a
driver crash for example.

`<variant>.png` is the image produced by this variant. This file is present only
if the variant status is `SUCCESS`.

In case of `NONDET` status, two different renderings for this same variant are
stored in `<variant>_nondet1.png` and `<variant>_nondet2.png`. An animated GIF
with this two images is produced in `<variant>.gif`.

### Reduction results

Under `work/processing/<worker>/<shader_family>/reductions/<variant>/`,
the reduction of this variant leads to the following files:

* `command.log` is the command with which the reducer was started
* `<variant>_reduced_<step_number>.*` are the files associated with this given
  step of the reduction.
* `<variant>_reduced_final.*` are the files at the final step of the
  reduction. It typically refers to the smallest shader the reducer could obtain
  for this particular reduction.

## Reducing shaders from the command line using `glsl-reduce`

Behind the scenes, the server is invoking our command line tools.
In fact, the "reduction log" shown by the WebUI
(`command.log` in the file system)
includes the command that was run on its first line.
E.g.

`glsl-reduce shaderfamilies/familiy01/variant_01.json ABOVE_THRESHOLD [etc.]`

> You can try running these commands at the command line in the `work`
> directory, although note that some arguments that have spaces may need to be
> quoted (and they will not be quoted in the reduction log).

### Invoking a bad image reductions:

```sh
glsl-reduce
```
