/*
 * Copyright 2018 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphicsfuzz.glesworker;

import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration;
import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.uikit.UIApplication;
import org.robovm.apple.uikit.UIDevice;
import com.graphicsfuzz.repackaged.com.google.gson.JsonObject;

public class IOSLauncher extends IOSApplication.Delegate {
    @Override
    protected IOSApplication createApplication() {

        JsonObject platformInfoJson = new JsonObject();

        platformInfoJson.addProperty("device.name", UIDevice.getCurrentDevice().getName());
        platformInfoJson.addProperty("device.model", UIDevice.getCurrentDevice().getModel());
        platformInfoJson.addProperty("device.localized_model", UIDevice.getCurrentDevice().getLocalizedModel());
        platformInfoJson.addProperty("device.system_name", UIDevice.getCurrentDevice().getSystemName());
        platformInfoJson.addProperty("device.system_version", UIDevice.getCurrentDevice().getSystemVersion());
        platformInfoJson.addProperty("device.identifier_for_vendor",
            UIDevice.getCurrentDevice().getIdentifierForVendor().toString());

        Main main = new Main();
        main.persistentData = new PersistentData();
        main.setPlatformInfoJson(platformInfoJson);

        IOSApplicationConfiguration config = new IOSApplicationConfiguration();
        config.orientationPortrait = false;
//        config.preferredFramesPerSecond = 10;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.allowIpod = true;
        config.useGL30 = true;
        return new IOSApplication(main, config);
    }

    public static void main(String[] argv) {
        NSAutoreleasePool pool = new NSAutoreleasePool();
        UIApplication.main(argv, null, IOSLauncher.class);
        pool.close();
    }
}