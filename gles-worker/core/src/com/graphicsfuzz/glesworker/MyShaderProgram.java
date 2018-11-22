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

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class MyShaderProgram extends ShaderProgram {

  // Don't initialize fields if you want to update them in createProgram.

  private boolean calledCreatedProgram;
  private int programHandle;

  public MyShaderProgram(String vertexShader, String fragmentShader) {
    super(vertexShader, fragmentShader);
  }

  public MyShaderProgram(FileHandle vertexShader, FileHandle fragmentShader) {
    super(vertexShader, fragmentShader);
  }

  // Overriding createProgram() is a trick to distinguish a compile error from
  // a link error. Libgdx ShaderProgram compiles and links directly in its
  // constructor, so we cannot separate these steps, and it's not possible to
  // override the constructor. Still, the ShaderProgram constructor calls various
  // functions to compile and link, among them createProgram() is called only if
  // the compilation of both vertex and fragment did not fail, and before linking.
  // It turns out createPogram() is also overridable, while other involved
  // functions are not (as they are private). So by flagging the call of
  // createProgram(), we can know which of compilation or linking has failed.
  @Override
  protected int createProgram() {
    int res = super.createProgram();
    calledCreatedProgram = true;
    programHandle = res;
    return res;
  }

  public boolean fragmentOrVertexFailedToCompile() {
    return !calledCreatedProgram;
  }

  public boolean failedToLink() {
    return calledCreatedProgram && !isCompiled();
  }

  public int getProgramHandle() {
    return programHandle;
  }
}
