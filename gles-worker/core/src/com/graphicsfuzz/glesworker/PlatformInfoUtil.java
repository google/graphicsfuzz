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

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;
import com.graphicsfuzz.repackaged.com.google.gson.JsonArray;
import com.graphicsfuzz.repackaged.com.google.gson.JsonObject;

public class PlatformInfoUtil {

  private static String getProperty(String prop) {
    String res = System.getProperty(prop);
    return res == null ? "" : res;
  }

  public static void getGlVersionInfo(JsonObject platformInfoJson, GL30 gl30) {

    BufferUtils.newIntBuffer(16);

    platformInfoJson.addProperty("GL_VERSION", Gdx.gl.glGetString(GL20.GL_VERSION));
    platformInfoJson.addProperty("GL_SHADING_LANGUAGE_VERSION", Gdx.gl.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
    platformInfoJson.addProperty("GL_VENDOR", Gdx.gl.glGetString(GL20.GL_VENDOR));
    platformInfoJson.addProperty("GL_RENDERER", Gdx.gl.glGetString(GL20.GL_RENDERER));

    IntBuffer buff = BufferUtils.newIntBuffer(16);

    buff.clear();
    Gdx.gl.glGetIntegerv(GL30.GL_MAJOR_VERSION, buff);
    platformInfoJson.addProperty("GL_MAJOR_VERSION", buff.get(0));

    buff.clear();
    Gdx.gl.glGetIntegerv(GL30.GL_MINOR_VERSION, buff);
    platformInfoJson.addProperty("GL_MINOR_VERSION", buff.get(0));

    JsonArray array = new JsonArray();
    try {
      final int GL_NUM_SHADING_LANGUAGE_VERSIONS = 0x82E9;
      buff.clear();
      Gdx.gl.glGetIntegerv(GL_NUM_SHADING_LANGUAGE_VERSIONS, buff);
      if(Gdx.gl.glGetError() == GL20.GL_NO_ERROR && gl30 != null) {
        int size = buff.get(0);
        for(int i=0; i < size; ++i) {
          array.add(gl30.glGetStringi(GL20.GL_SHADING_LANGUAGE_VERSION, i));
        }
      }
    }
    catch (IllegalStateException e) {
      // The above may not work depending what OpenGL version is supported.
    }
    platformInfoJson.add("Supported_GLSL_versions", array);

  }

  public static void getPlatformDetails(JsonObject platformInfoJson) {

    // Libgdx specific:

    platformInfoJson.addProperty("clientplatform", Gdx.app.getType().toString().toLowerCase());

    // System.getProperty:

    platformInfoJson.addProperty("http.agent", getProperty("http.agent"));

    platformInfoJson.addProperty("java.class.version", getProperty("java.class.version"));
    platformInfoJson.addProperty("java.runtime.name", getProperty("java.runtime.name"));
    platformInfoJson.addProperty("java.runtime.version", getProperty("java.runtime.version"));
    platformInfoJson.addProperty("java.vendor", getProperty("java.vendor"));
    platformInfoJson.addProperty("java.version", getProperty("java.version"));
    // Don't include this, as it can vary, and it isn't particularly useful information.
//    platformInfoJson.addProperty("java.vm.info", getProperty("java.vm.info"));
    platformInfoJson.addProperty("java.vm.name", getProperty("java.vm.name"));
    platformInfoJson.addProperty("java.vm.specification.vendor", getProperty("java.vm.specification.vendor"));
    platformInfoJson.addProperty("java.vm.specification.name", getProperty("java.vm.specification.name"));
    platformInfoJson.addProperty("java.vm.vendor", getProperty("java.vm.vendor"));
    platformInfoJson.addProperty("java.vm.vendor.url", getProperty("java.vm.vendor.url"));
    platformInfoJson.addProperty("java.vm.version", getProperty("java.vm.version"));
    platformInfoJson.addProperty("java.specification.name", getProperty("java.specification.name"));
    platformInfoJson.addProperty("java.specification.vendor", getProperty("java.specification.vendor"));
    platformInfoJson.addProperty("java.specification.version", getProperty("java.specification.version"));

    platformInfoJson.addProperty("os.arch", getProperty("os.arch"));
    platformInfoJson.addProperty("os.name", getProperty("os.name"));
    platformInfoJson.addProperty("os.version", getProperty("os.version"));
    platformInfoJson.addProperty("sun.cpu.endian", getProperty("sun.cpu.endian"));
    platformInfoJson.addProperty("sun.io.unicode.encoding", getProperty("sun.io.unicode.encoding"));
    platformInfoJson.addProperty("sun.desktop", getProperty("sun.desktop"));
    platformInfoJson.addProperty("sun.java.launcher", getProperty("sun.java.launcher"));
    platformInfoJson.addProperty("sun.cpu.isalist", getProperty("sun.cpu.isalist"));
    platformInfoJson.addProperty("sun.os.patch.level", getProperty("sun.os.patch.level"));
    platformInfoJson.addProperty("sun.management.compiler", getProperty("sun.management.compiler"));
    platformInfoJson.addProperty("sun.arch.data.model", getProperty("sun.arch.data.model"));

    platformInfoJson.addProperty("user.region", getProperty("user.region"));
    platformInfoJson.addProperty("user.language", getProperty("user.language"));
  }
}
