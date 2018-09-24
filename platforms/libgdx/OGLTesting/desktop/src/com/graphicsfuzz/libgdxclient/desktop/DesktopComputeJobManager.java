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

package com.graphicsfuzz.libgdxclient.desktop;

import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;

import com.badlogic.gdx.Gdx;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import com.graphicsfuzz.libgdxclient.ComputeJobManager;
import com.graphicsfuzz.libgdxclient.UniformSetter;
import com.graphicsfuzz.repackaged.com.google.gson.JsonArray;
import com.graphicsfuzz.repackaged.com.google.gson.JsonObject;

class DesktopComputeJobManager extends ComputeJobManager {

  private int shader;
  private int program;
  private int shaderStorageBufferObject;
  private int[] numGroups;

  DesktopComputeJobManager() {
    super();
    reset();
  }

  @Override
  public void reset() {
    // TODO: do we need to do memory management on these?
    shader = -1;
    program = -1;
    shaderStorageBufferObject = -1;
    numGroups = null;
  }

  @Override
  public void createAndCompileComputeShader(String computeShaderSource) {
    Gdx.app.log("DesktopComputeJobManager", "Compiling compute shader.");
    shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
    checkError();
    GL20.glShaderSource(shader, computeShaderSource);
    checkError();
    GL20.glCompileShader(shader);
    checkError();
  }

  @Override
  public boolean compilationSucceeded() {
    int compiled = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
    return compiled != 0;
  }

  @Override
  public void createAndLinkComputeProgram() {
    program = GL20.glCreateProgram();
    checkError();
    GL20.glAttachShader(program, shader);
    checkError();
    GL20.glLinkProgram(program);
    checkError();
  }

  @Override
  public boolean linkingSucceeded() {
    int linked = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
    return linked != 0;
  }

  @Override
  public String getCompilationLog() {
    return GL20.glGetShaderInfoLog(shader);
  }

  @Override
  public void prepareEnvironment(JsonObject environmentJson) {
    final JsonObject bufferJson = environmentJson.get("buffer").getAsJsonObject();
    final int bufferBinding = bufferJson.get("binding").getAsInt();
    final int[] bufferInput = UniformSetter.getIntArray(bufferJson.get("input").getAsJsonArray());
    final IntBuffer intBufferData = BufferUtils.createIntBuffer(bufferInput.length);
    intBufferData.put(bufferInput);
    intBufferData.flip();
    shaderStorageBufferObject = GL15.glGenBuffers();
    checkError();
    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, shaderStorageBufferObject);
    checkError();
    GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, intBufferData, GL15.GL_STATIC_DRAW);
    checkError();
    GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bufferBinding, shaderStorageBufferObject);
    checkError();
    numGroups = UniformSetter.getIntArray(environmentJson.get("num_groups").getAsJsonArray());
  }

  @Override
  public JsonObject executeComputeShader() {
    GL20.glUseProgram(program);
    checkError();
    GL43.glDispatchCompute(numGroups[0], numGroups[1], numGroups[2]);
    checkError();
    GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    checkError();
    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, shaderStorageBufferObject);
    checkError();
    final IntBuffer dataFromComputeShader = GL15.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_READ_ONLY)
        .asIntBuffer();
    checkError();
    final JsonArray jsonArray = new JsonArray();
    for (int i = 0; i < dataFromComputeShader.limit(); i++) {
      jsonArray.add(dataFromComputeShader.get(i));
    }
    final JsonObject result = new JsonObject();
    result.add("output", jsonArray);
    return result;
  }

  private void checkError() {
    final int errorCode = GL11.glGetError();
    if (errorCode != GL11.GL_NO_ERROR) {
      throw new RuntimeException("GL error " + errorCode);
    }
  }

}