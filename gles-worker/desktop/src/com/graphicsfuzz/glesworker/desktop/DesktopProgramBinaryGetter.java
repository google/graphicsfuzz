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

package com.graphicsfuzz.glesworker.desktop;

import java.nio.ByteBuffer;

import com.graphicsfuzz.glesworker.IProgramBinaryGetter;
import org.lwjgl.opengl.ARBGetProgramBinary;
import org.lwjgl.opengl.GL41;

public class DesktopProgramBinaryGetter implements IProgramBinaryGetter {

  @Override
  public void glGetProgramBinary(
      int program,
      int[] length,
      int[] binaryFormat,
      ByteBuffer binary) {

    try {
      GL41.glGetProgramBinary(program, length, binaryFormat, binary);
    } catch (IllegalStateException e) {
      ARBGetProgramBinary.glGetProgramBinary(program, length, binaryFormat, binary);
    }
  }
}
