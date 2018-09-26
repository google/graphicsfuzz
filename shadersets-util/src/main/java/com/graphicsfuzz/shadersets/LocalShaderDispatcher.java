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

package com.graphicsfuzz.shadersets;

import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import com.graphicsfuzz.server.thrift.ComputeJobResult;
import com.graphicsfuzz.server.thrift.FuzzerServiceConstants;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.server.thrift.ResultConstant;
import java.io.File;
import java.io.IOException;

public class LocalShaderDispatcher implements IShaderDispatcher {

  private final boolean usingSwiftshader;

  public LocalShaderDispatcher(boolean usingSwiftshader) {
    this.usingSwiftshader = usingSwiftshader;
  }

  @Override
  public ImageJobResult getImage(
      String shaderFilesPrefix,
      File tempImageFile,
      boolean skipRender) throws ShaderDispatchException, InterruptedException {

    final File fragmentShaderFile = new File(shaderFilesPrefix + ".frag");
    final File vertexShaderFile = new File(shaderFilesPrefix + ".vert");

    if (vertexShaderFile.isFile()) {
      throw new RuntimeException("Not yet supporting vertex shaders in local image generation.");
    }

    try {
      ExecResult res = usingSwiftshader
          ? ToolHelper.runSwiftshaderOnShader(RedirectType.TO_BUFFER, fragmentShaderFile,
              tempImageFile, skipRender)
          : ToolHelper.runGenerateImageOnShader(RedirectType.TO_BUFFER, fragmentShaderFile,
          tempImageFile, skipRender);

      ImageJobResult imageJobResult = new ImageJobResult();

      if (res.res == 0) {
        imageJobResult
            .setStatus(JobStatus.SUCCESS);

        return imageJobResult;
      }

      ResultConstant resultConstant = ResultConstant.ERROR;
      JobStatus status = JobStatus.UNEXPECTED_ERROR;

      if (res.res == FuzzerServiceConstants.COMPILE_ERROR_EXIT_CODE) {
        resultConstant = ResultConstant.COMPILE_ERROR;
        status = JobStatus.COMPILE_ERROR;
      } else if (res.res == FuzzerServiceConstants.LINK_ERROR_EXIT_CODE) {
        resultConstant = ResultConstant.LINK_ERROR;
        status = JobStatus.LINK_ERROR;
      }

      res.stdout.append(res.stderr);
      imageJobResult
          .setStatus(status)
          .setLog(resultConstant + "\n" + res.stdout.toString());

      return imageJobResult;

    } catch (IOException exception) {
      throw new ShaderDispatchException(exception);
    }
  }

  @Override
  public ComputeJobResult dispatchCompute(File computeShaderFile, boolean skipExecution) {
    throw new RuntimeException("Compute shaders are not supported locally.");
  }

}
