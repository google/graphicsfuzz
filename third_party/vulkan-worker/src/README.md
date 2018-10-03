Vulkan API samples
==================

Demonstrates basic usages of Vulkan APIs.

Introduction
------------
The project includes series of samples demonstrating a basic usage of Vulkan APIs.
This repository is a replication of [LunarG sample kit](https://github.com/LunarG/VulkanSamples), refer to [wiki](https://github.com/googlesamples/vulkan-basic-samples/wiki) for more background.

Getting Started
---------------
Refer to the [Getting Started guide](https://developer.android.com/ndk/guides/graphics/getting-started.html).

Screenshots
-----------
![screenshot](image/screen.png)


## Prerequisites
- [Android Studio](https://developer.android.com/studio/index.html): 2.3.0 or higher.
- [Android SDK](https://developer.android.com/studio/index.html): N Developer Preview or higher.
- [Android NDK](https://developer.android.com/ndk/downloads/index.html): r12 beta or higher.

## Sample Import
To import the samples, follow the steps below:

### Step 1: Build shaderc in the NDK
From the command-prompt, navigate to the `${ndk_root}/sources/third_party/shaderc` directory.
Then, run the following command:

~~~
../../../ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk APP_STL:=[gnustl_static|gnustl_shared|c++_static|c++_shared] APP_ABI=[armeabi-v7a|arm64-v8a|x86|x86_64|all] libshaderc_combined -j16
~~~

For this project, the `APP_STL` value is set to use the `gnustl_static` port, as all the project samples are using it.

### Step 2: Import the samples into Android Studio 
You can use one of the following methods to install this project in Android Studio:

* Import Android Code Sample: Choose **Import an Android code sample**, then search for and select **Vulkan API samples**. Android Studio downloads the sample code directly from Github.
* Import Project: Use this method only if you've already cloned this project from GitHub into a local repo. From Android Studio, choose **Import project (Eclipse, ADT, Gradle)** and select the `build.gradle` file located at the root of your local repo directory.

Note:  This project includes 40+ samples and may take time to load on some platforms, such as Windows OS.

Support
-------

- Google+ Community: https://plus.google.com/communities/<...>
- Stack Overflow: http://stackoverflow.com/questions/tagged/<...>

If you've found an error in this sample, please file an issue:
https://github.com/googlesamples/<...>/issues

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub.

License
-------

Copyright 2016 Google, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
