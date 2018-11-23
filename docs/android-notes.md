# Android notes

The Android SDK is needed for building and developing the
OpenGL worker (gles-worker).

The Android SDK and NDK are needed for building and developing the
Vulkan worker (vulkan-worker).

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

## Android networking

See [Android networking guide](android-networking-guide.md).

## Installing apps via `adb`

Once you have `adb` on your path,
you can install apps using:

`adb install the-app.apk`

## Other notes

* On an Android device,
open `Settings`, `About device`, and keep tapping build number until developer settings are enabled.
The build number might be under a further `Software information` option.
* In developer settings, enable `USB debugging` and `Stay awake`.
* In security settings (which might be under `Lock screen and security`), enable installing apps from unknown sources.
* You can download the android worker directly on the phone from the [releases page](glsl-fuzz-releases.md).
* In IntelliJ, open `Android Monitor` to view log output.
Enter `com.graphicsfuzz.` in the search text box and select `No filters` in the drop down.
This will ensure you see output from all processes; our worker uses three processes on Android.
* Press the back button on the device to exit the OpenGL worker app on Android. Pressing home or
other buttons won't work, as the app tries to stay in the foreground.


