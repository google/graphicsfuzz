# Android notes

The Android SDK is needed for building and developing the OpenGL worker
(gles-worker).

The Android SDK and NDK are needed for building and developing the Vulkan worker
(vulkan-worker).

## Installing the Android SDK and NDK

You can download and install the Android SDK
(with Android Studio or stand-alone)
from [https://developer.android.com/studio/#downloads](https://developer.android.com/studio/#downloads).
You can then use Android Studio or the SDK manager
to install missing packages and the Android NDK.

**Alternatively**, you can install the exact configuration
of the Android SDK and NDK that we use from the command line
by following our
[continuous integration script](../build/travis/1-install-deps-travis.sh);
the download of the Android SDK starts about halfway down.
You may need to set some environment variables as specified in the comments.

Ensure that the `ANDROID_HOME=/path/to/android-sdk` and `ANDROID_NDK_HOME=$ANDROID_HOME/ndk-bundle` environment variables are set;
you may need to open IntelliJ IDEA from the terminal depending on how
you set the environment variables.

You should also add the `android-sdk/platform-tools` directory
to your path so that you can use tools like `adb`.

## Android networking: connecting Android apps to the server

There are two main ways to connect the gles worker
Android app to the server.

> Tip: Recall that you must exit the gles worker app
> using the back button, otherwise it will restart.

> Tip: This section also applies to the Vulkan worker Android app.

### Using the hostname or IP address of the server

The most straightforward approach can be used if the Android device and server
are on the same network, and can "see" each other. This does not always work on
university, public, or corporate networks, as communication is often blocked.
However, this approach should work, for example, if you are at home with both
your desktop/laptop and Android phone/tablet connected to your router via WiFi
and/or ethernet cable.

* Open the GraphicsFuzz app on your device.
* Enter the hostname of your desktop/laptop, plus the port on which
the server application is listening (8080 by default).

E.g. `paul-laptop:8080`

* Alternatively, you can use the IP address of your desktop/laptop:

E.g. `192.168.0.4:8080`

### Using USB via `adb reverse`

You may alternatively connect the device to the machine running the server using
a USB cable. In this case, you will need to have access to the `adb` tool on
your desktop/laptop (one approach is to install the Android SDK,
as described above), and you will need to
enable USB debugging in the developer settings on
the Android device (described below in [useful device settings](#useful-device-settings)). Then:

* Connect the Android device via USB to the desktop/laptop running the server.
* Ensure you unlock the phone/tablet (e.g. by using your finger print, entering
  a PIN code, or just by swiping the lockscreen), so you can see the "Allow USB
  debugging?" pop-up window.
* Tap the "Always allow from this computer" button option, and press "OK" to
  allow USB debugging.
* From a terminal on your desktop/laptop, execute `adb devices` and ensure the
  device shows up.
* Assuming the server is listening on port 8080, execute `adb reverse tcp:8080
  tcp:8080`.
* Now open the gles-worker app and use the default server address:
  `localhost:8080`

> Tip: To change the server address,
> you must uninstall and reinstall the app,
> as described below.


**Warning:**
if the app initially cannot connect to the server
then it will complain that the worker name is invalid
because it cannot contact the server to validate the worker name.
Thus, note the following:

* We recommend that you execute `adb reverse` *before* starting the
gles-worker app for the first time.
* If the app cannot connect to the server
then a dialogue will continue to show, making it impossible
to exit the app.
If you forget to execute `adb reverse tcp:8080 tcp:8080` then
doing this may fix the issue.
Otherwise,
to close the app,
use `adb install gles-worker-android-debug.apk` to reinstall the app (which stops the currently running instance),
or `adb uninstall com.graphicsfuzz.glesworker` to uninstall the app,
as described below.


## Installing and uninstalling apps via `adb`

Once you have `adb` on your path,
you can install apps using:

`adb install myapp.apk`

If the app is already installed, you may get an error like:

`adb: failed to install myapp.apk: Failure [INSTALL_FAILED_ALREADY_EXISTS: Attempt to re-install com.example.myapp without first uninstalling.]`

The app should still have installed correctly, but to be safe
you can uninstall the app first, using:

`adb uninstall com.example.myapp`

Note that the package name (e.g. `com.example.myapp`) can be found in the error
message.

You can use the following one-liner to reinstall an APK
(silencing the uninstall output,
which can be quite verbose if the app is not already installed):

`adb uninstall com.example.myapp >/dev/null 2>&1 ; adb install myapp.apk`

## Multiple devices plugged at the same time

If you have more than one device connected, you will have to tell `adb` which
device to target with the option `-s <device-id>` on every command (e.g. `adb -s
<device-id> logcat`).

Use `adb devices -l` to get the device id alongside the model name of plugged
devices.

## Useful device settings

* On an Android device, open `Settings`, `About device`, and keep tapping `Build
  number` until developer settings are enabled.  The build number might be under
  a further `Software information` option.
* In developer settings, enable `USB debugging` and `Stay awake` to make sure
  the phone does not goes to sleep while it is plugged in.
* In security settings (which might be under `Lock screen and security`), enable
  installing apps from unknown sources.
* You can download and install graphicsfuzz APKs directly on the phone from the
  [releases page](glsl-fuzz-releases.md).
* In IntelliJ, open `Android Monitor` to view log output.  Enter
  `com.graphicsfuzz.` in the search text box and select `No filters` in the drop
  down.  This will ensure you see output from all processes; our worker uses
  three processes on Android.
* Press the back button on the device to exit the gles-worker app on
  Android. Pressing home or other buttons won't work, as the app tries to stay
  in the foreground.
