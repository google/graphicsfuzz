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

package com.graphicsfuzz.libgdxclient;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.graphicsfuzz.repackaged.com.google.gson.JsonObject;
import com.graphicsfuzz.repackaged.org.apache.commons.io.FileUtils;
import com.graphicsfuzz.server.thrift.ImageJob;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class AndroidLauncher extends AndroidApplication {

	Main main = null;
	private static final boolean USE_GLES3 = true;
	private static final boolean STANDALONE = false;

	@Override
	protected void onPause() {
		Log.d("AndroidLauncher", "onPause");
		super.onPause();
	}

	@Override
	protected void onResume() {
		Log.d("AndroidLauncher", "onResume");
		super.onResume();
	}

	@Override
	public void onBackPressed() {
		Log.d("AndroidLauncher", "onBackPressed");
		Intent intent = new Intent(this, AndroidService.class);
		stopService(intent);
		if (main.persistentData != null) {
			main.persistentData.reset();
		}
		if (main.perFrameWatchdog != null) {
			main.perFrameWatchdog.stop();
		}
		super.onBackPressed();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.d("AndroidLauncher", "onSaveInstanceState");
//		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		savedInstanceState = null;
		super.onCreate(savedInstanceState);

		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		main = new Main();


		main.watchdog = new AndroidWatchdog();
		main.perFrameWatchdog = new AndroidWatchdog(true);

		if (main.persistentData == null) {
			main.persistentData = new PersistentData(new AndroidPersistentFile(this));
		}

		JsonObject platformInfoJson = new JsonObject();

//		platformInfoJson.add("active_codenames", gson.toJsonTree(Build.VERSION.ACTIVE_CODENAMES));
//		platformInfoJson.addProperty("all_codenames", Build.VERSION.ALL_CODENAMES);
		try {
			platformInfoJson.addProperty("base_os", Build.VERSION.BASE_OS);
		} catch(NoSuchFieldError ex) {

		}

		try {
			platformInfoJson.addProperty("codename", Build.VERSION.CODENAME);
		} catch(NoSuchFieldError ex) {

		}
		try {
			platformInfoJson.addProperty("incremental", Build.VERSION.INCREMENTAL);
		} catch(NoSuchFieldError ex) {

		}
		try {
			platformInfoJson.addProperty("preview_sdk_int", Build.VERSION.PREVIEW_SDK_INT);
		} catch(NoSuchFieldError ex) {

		}
		try {
			platformInfoJson.addProperty("release", Build.VERSION.RELEASE);
		} catch(NoSuchFieldError ex) {

		}

//		platformInfoJson.addProperty("resources_sdk_int", Build.VERSION.RESOURCES_SDK_INT);
//		platformInfoJson.addProperty("sdk", Build.VERSION.SDK);
		try {
			platformInfoJson.addProperty("sdk_int", Build.VERSION.SDK_INT);
		} catch(NoSuchFieldError ex) {

		}
		try {
			platformInfoJson.addProperty("security_patch", Build.VERSION.SECURITY_PATCH);
		} catch(NoSuchFieldError ex) {

		}


		try {
			platformInfoJson.addProperty("board", Build.BOARD);
		} catch(NoSuchFieldError ex) {

		}
		try {
			platformInfoJson.addProperty("bootloader", Build.BOOTLOADER);
		} catch(NoSuchFieldError ex) {

		}

		try {
			platformInfoJson.addProperty("brand", Build.BRAND);
		} catch(NoSuchFieldError ex) {
		}
//		platformInfoJson.addProperty("cpu_abi", Build.CPU_ABI);
//		platformInfoJson.addProperty("cpu_abi2", Build.CPU_ABI2);
		try {
			platformInfoJson.addProperty("device", Build.DEVICE);
		} catch(NoSuchFieldError ex) {
		}

		try {
			platformInfoJson.addProperty("display", Build.DISPLAY);
		} catch(NoSuchFieldError ex) {
		}
		try {
			platformInfoJson.addProperty("fingerprint", Build.FINGERPRINT);
		} catch(NoSuchFieldError ex) {
		}
		try {
			platformInfoJson.addProperty("hardware", Build.HARDWARE);
		} catch(NoSuchFieldError ex) {
		}
		try {
			platformInfoJson.addProperty("host", Build.HOST);
		} catch(NoSuchFieldError ex) {
		}
		try {
			platformInfoJson.addProperty("id", Build.ID);
		} catch(NoSuchFieldError ex) {
		}
		try {
			platformInfoJson.addProperty("manufacturer", Build.MANUFACTURER);
		} catch(NoSuchFieldError ex) {
		}
		try {
			platformInfoJson.addProperty("model", Build.MODEL);
		} catch(NoSuchFieldError ex) {
		}
		try {
			platformInfoJson.addProperty("product", Build.PRODUCT);
		} catch(NoSuchFieldError ex) {
		}
		// The radio can disappear, so don't use it!
//		try {
//			platformInfoJson.addProperty("radio", Build.getRadioVersion());
//		} catch(NoSuchMethodError ex) {
//		}
		try {
			platformInfoJson.addProperty("serial", Build.SERIAL);
		} catch(NoSuchFieldError ex) {
		}

		// These require API level 21
//		platformInfoJson.add("supported_32_bit_abis", gson.toJsonTree(Build.SUPPORTED_32_BIT_ABIS));
//		platformInfoJson.add("supported_64_bit_abis", gson.toJsonTree(Build.SUPPORTED_64_BIT_ABIS));
//		platformInfoJson.add("supported_abis", gson.toJsonTree(Build.SUPPORTED_ABIS));

		try {
			platformInfoJson.addProperty("tags", Build.TAGS);
		} catch(NoSuchFieldError ex) {
		}
		try {
			platformInfoJson.addProperty("time", Build.TIME);
		} catch(NoSuchFieldError ex) {
		}
		try {
			platformInfoJson.addProperty("type", Build.TYPE);
		} catch(NoSuchFieldError ex) {
		}
//		platformInfoJson.addProperty("unknown", Build.UNKNOWN);
		try {
			platformInfoJson.addProperty("user", Build.USER);
		} catch(NoSuchFieldError ex) {
		}

		main.setPlatformInfoJson(platformInfoJson);

		main.programBinaryGetter = new AndroidProgramBinaryGetter();

		if (STANDALONE) {
			ImageJob standaloneRenderJob = new ImageJob();
			try {
				standaloneRenderJob
						.setName("standalone")
						.setFragmentSource(FileUtils.readFileToString(new File("/sdcard/graphicsfuzz/test.frag"), Charset.defaultCharset()))
						.setVertexSource(FileUtils.readFileToString(new File("/sdcard/graphicsfuzz/test.vert"), Charset.defaultCharset()))
						.setUniformsInfo(FileUtils.readFileToString(new File("/sdcard/graphicsfuzz/test.json"), Charset.defaultCharset()));
			} catch (IOException exception) {
				throw new RuntimeException(exception);
			}

			main.standaloneRenderJob = standaloneRenderJob;
			main.standaloneOutputFilename = "/sdcard/graphicsfuzz/image.png";
		}

		config.depth = 24;
		config.stencil = 8;
		config.r = 8;
		config.g = 8;
		config.b = 8;

		config.disableAudio = true;
		config.useWakelock = true; // uses FLAG_KEEP_SCREEN_ON under the hood (better than wakelock)
		config.useCompass = false;
		config.useAccelerometer = false;
		config.useImmersiveMode = true;
//		config.hideStatusBar = true;
		config.useGL30 = USE_GLES3;
		initialize(main, config);
	}
}
