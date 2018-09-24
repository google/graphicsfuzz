# Android networking guide: connecting the Android worker app to the server

There are two main ways to connect the Android worker to the server.

## Using the hostname or IP address of the server

The most straightforward approach
can be used if the Android device and server are on the same network,
and can "see" each other.
This does not always work on university, public,
or corporate networks, as communication is often blocked.
However, this approach should work, for example, if you are at home with both
your desktop/laptop and
Android phone/tablet connected to your router via WiFi and/or ethernet cable.

* Open the GraphicsFuzz app on your device.
* Enter the hostname of your desktop/laptop, plus the port on which
the server application is listening (8080 by default).

E.g. `paul-laptop:8080`

* Alternatively, you can use the IP address of your desktop/laptop:

E.g. `192.168.0.4:8080`

## Using USB via `adb reverse`

You may alternatively connect the device using a USB cable.
In this case,
you should install the Android platform tools,
as described in the [Android notes section](development.md#Android_notes)
of the developer documentation, so that you have access to the `adb` tool.
Then:

* Connect the Android device to your desktop/laptop via USB.
* Ensure you unlock the phone/tablet (e.g. by using your finger print,
entering a PIN code, or just by swiping the lockscreen) and tap "Trust" on the dialogue
that pops up on the screen.
* From a terminal on your desktop/laptop, execute `adb devices` and ensure the device shows up.
We assume you just have one device connected.
* Execute `adb reverse tcp:8080 tcp:8080`.
* Now open the GraphicsFuzz app (if not open already)
and use the default server address: `localhost:8080`
