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

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.Map.Entry;
import com.graphicsfuzz.repackaged.com.google.gson.JsonArray;
import com.graphicsfuzz.repackaged.com.google.gson.JsonElement;
import com.graphicsfuzz.repackaged.com.google.gson.JsonObject;
import com.graphicsfuzz.repackaged.com.google.gson.JsonParser;

public class UniformSetter {

  public static int[] getIntArray(JsonArray argsJson) {
    int[] intArgs = new int[argsJson.size()];
    Iterator<JsonElement> it = argsJson.iterator();
    for(int i=0; i < intArgs.length; ++i) {
      intArgs[i] = it.next().getAsInt();
    }
    return intArgs;
  }

  private static float[] getFloatArray(JsonArray argsJson) {
    float[] floatArgs = new float[argsJson.size()];
    Iterator<JsonElement> it = argsJson.iterator();
    for(int i=0; i < floatArgs.length; ++i) {
      floatArgs[i] = it.next().getAsFloat();
    }
    return floatArgs;
  }

  private static IntBuffer getIntBuffer(JsonArray argsJson) {
    int[] intArgs = getIntArray(argsJson);
    IntBuffer intBuffer = BufferUtils.newIntBuffer(intArgs.length);
    intBuffer.put(intArgs);
    intBuffer.position(0);
    return intBuffer;
  }

  private static FloatBuffer getFloatBuffer(JsonArray argsJson) {
    float[] floatArgs = getFloatArray(argsJson);
    FloatBuffer floatBuffer = BufferUtils.newFloatBuffer(floatArgs.length);
    floatBuffer.put(floatArgs);
    floatBuffer.position(0);
    return floatBuffer;
  }

  public static JsonObject getUniformDict(String uniformsJSON) {
    return new JsonParser().parse(uniformsJSON).getAsJsonObject();
  }

  public static void setUniforms(MyShaderProgram program, JsonObject uniformDict, GL30 gl30)
      throws GlErrorException {

    if(uniformDict == null) {
      return;
    }

    for(Entry<String, JsonElement> entry : uniformDict.entrySet()) {
      String uniformName = entry.getKey();
      JsonObject uniformInfo = entry.getValue().getAsJsonObject();

      int loc = Gdx.gl.glGetUniformLocation(program.getProgramHandle(), uniformName);
      if(loc == -1) {
        continue;
      }
      String func = uniformInfo.get("func").getAsString();
      final JsonArray argsJson = uniformInfo.get("args").getAsJsonArray();

      boolean transpose = false;

      JsonElement te = uniformInfo.get("transpose");
      if(te != null) {
        transpose = te.getAsBoolean();
      }

      int count = -1;

      JsonElement ce = uniformInfo.get("count");
      if(ce != null) {
        count = ce.getAsInt();
      }

      if ("glUniform1f".equals(func)) {
        float[] floatArgs = getFloatArray(argsJson);
        Gdx.gl.glUniform1f(loc, floatArgs[0]);
      } else if ("glUniform2f".equals(func)) {
        float[] floatArgs = getFloatArray(argsJson);
        Gdx.gl.glUniform2f(loc, floatArgs[0], floatArgs[1]);
      } else if ("glUniform3f".equals(func)) {
        float[] floatArgs = getFloatArray(argsJson);
        Gdx.gl.glUniform3f(loc, floatArgs[0], floatArgs[1], floatArgs[2]);
      } else if ("glUniform4f".equals(func)) {
        float[] floatArgs = getFloatArray(argsJson);
        Gdx.gl.glUniform4f(loc, floatArgs[0], floatArgs[1], floatArgs[2], floatArgs[3]);

      } else if ("glUniform1i".equals(func)) {
        int[] intArgs = getIntArray(argsJson);
        Gdx.gl.glUniform1i(loc, intArgs[0]);
      } else if ("glUniform2i".equals(func)) {
        int[] intArgs = getIntArray(argsJson);
        Gdx.gl.glUniform2i(loc, intArgs[0], intArgs[1]);
      } else if ("glUniform3i".equals(func)) {
        int[] intArgs = getIntArray(argsJson);
        Gdx.gl.glUniform3i(loc, intArgs[0], intArgs[1], intArgs[2]);
      } else if ("glUniform4i".equals(func)) {
        int[] intArgs = getIntArray(argsJson);
        Gdx.gl.glUniform4i(loc, intArgs[0], intArgs[1], intArgs[2], intArgs[3]);

      } else if ("glUniform1ui".equals(func)) {
        throw new RuntimeException("Not supported: glUniform1ui");
      } else if ("glUniform2ui".equals(func)) {
        throw new RuntimeException("Not supported: glUniform2ui");
      } else if ("glUniform3ui".equals(func)) {
        throw new RuntimeException("Not supported: glUniform3ui");
      } else if ("glUniform4ui".equals(func)) {
        throw new RuntimeException("Not supported: glUniform4ui");

      } else if ("glUniform1fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        Gdx.gl.glUniform1fv(loc, count, floatBuffer);
      } else if ("glUniform2fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        Gdx.gl.glUniform2fv(loc, count, floatBuffer);
      } else if ("glUniform3fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        Gdx.gl.glUniform3fv(loc, count, floatBuffer);
      } else if ("glUniform4fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        Gdx.gl.glUniform4fv(loc, count, floatBuffer);

      } else if ("glUniform1iv".equals(func)) {
        IntBuffer intBuffer = getIntBuffer(argsJson);
        Gdx.gl.glUniform1iv(loc, count, intBuffer);
      } else if ("glUniform2iv".equals(func)) {
        IntBuffer intBuffer = getIntBuffer(argsJson);
        Gdx.gl.glUniform2iv(loc, count, intBuffer);
      } else if ("glUniform3iv".equals(func)) {
        IntBuffer intBuffer = getIntBuffer(argsJson);
        Gdx.gl.glUniform3iv(loc, count, intBuffer);
      } else if ("glUniform4iv".equals(func)) {
        IntBuffer intBuffer = getIntBuffer(argsJson);
        Gdx.gl.glUniform4iv(loc, count, intBuffer);

      } else if ("glUniform1uiv".equals(func)) {
        IntBuffer intBuffer = getIntBuffer(argsJson);
        gl30.glUniform1uiv(loc, count, intBuffer);
      } else if ("glUniform2uiv".equals(func)) {
        throw new RuntimeException("Not supported: glUniform2uiv");
      } else if ("glUniform3uiv".equals(func)) {
        IntBuffer intBuffer = getIntBuffer(argsJson);
        gl30.glUniform3uiv(loc, count, intBuffer);
      } else if ("glUniform4uiv".equals(func)) {
        IntBuffer intBuffer = getIntBuffer(argsJson);
        gl30.glUniform4uiv(loc, count, intBuffer);

      } else if ("glUniformMatrix2fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        Gdx.gl.glUniformMatrix2fv(loc, count, transpose, floatBuffer);
      } else if ("glUniformMatrix3fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        Gdx.gl.glUniformMatrix3fv(loc, count, transpose, floatBuffer);
      } else if ("glUniformMatrix4fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        Gdx.gl.glUniformMatrix4fv(loc, count, transpose, floatBuffer);

      } else if ("glUniformMatrix2x3fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        gl30.glUniformMatrix2x3fv(loc, count, transpose, floatBuffer);
      } else if ("glUniformMatrix3x2fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        gl30.glUniformMatrix3x2fv(loc, count, transpose, floatBuffer);
      } else if ("glUniformMatrix2x4fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        gl30.glUniformMatrix2x4fv(loc, count, transpose, floatBuffer);
      } else if ("glUniformMatrix4x2fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        gl30.glUniformMatrix4x2fv(loc, count, transpose, floatBuffer);
      } else if ("glUniformMatrix3x4fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        gl30.glUniformMatrix3x4fv(loc, count, transpose, floatBuffer);
      } else if ("glUniformMatrix4x3fv".equals(func)) {
        FloatBuffer floatBuffer = getFloatBuffer(argsJson);
        gl30.glUniformMatrix4x3fv(loc, count, transpose, floatBuffer);
      }

      Main.checkForGlError();

    }
  }
}
