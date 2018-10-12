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

package com.graphicsfuzz.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ToolHelper {

  public static ExecResult runValidatorOnShader(ExecHelper.RedirectType redirectType, File file)
        throws IOException, InterruptedException {
    return new ExecHelper().exec(
          redirectType,
          null,
          false,
          ToolPaths.glslangValidator(),
          file.toString());
  }

  public static ExecResult runShaderTranslatorOnShader(ExecHelper.RedirectType redirectType,
        File file,
        String arg)
        throws IOException, InterruptedException {
    return new ExecHelper().exec(
          redirectType,
          null,
          false,
          ToolPaths.shaderTranslator(),
          arg,
          file.toString());
  }

  public static ExecResult runGenerateImageOnShader(ExecHelper.RedirectType redirectType,
        File fragmentShader, File imageOutput, boolean skipRender)
        throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(Arrays.asList(
          ToolPaths.getImageGlfw(),
          fragmentShader.toString(),
          "--output", imageOutput.toString()));

    if (skipRender) {
      command.add("--exit-linking");
    }

    return new ExecHelper().exec(
          redirectType,
          null,
          false,
          command.toArray(new String[]{}));
  }

  public static ExecResult runSwiftshaderOnShader(
      ExecHelper.RedirectType redirectType,
      File fragmentShader,
      File imageOutput,
      boolean skipRender)
        throws IOException, InterruptedException {
    return runSwiftshaderOnShader(redirectType, fragmentShader,
        imageOutput, skipRender, 32, 32);
  }

  public static ExecResult runSwiftshaderOnShader(
      ExecHelper.RedirectType redirectType,
      File fragmentShader,
      File imageOutput,
      boolean skipRender,
      int width,
      int height)
        throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(Arrays.asList(
          ToolPaths.getImageEglSwiftshader(),
          fragmentShader.toString(),
          "--output", imageOutput.toString(),
          "--resolution",
          String.valueOf(width),
          String.valueOf(height)));

    if (skipRender) {
      command.add("--exit-linking");
    }

    return new ExecHelper().exec(
          redirectType,
          null,
          false,
          command.toArray(new String[]{}));
  }

}
