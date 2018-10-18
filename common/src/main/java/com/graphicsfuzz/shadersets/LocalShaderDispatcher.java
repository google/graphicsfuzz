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

import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import java.io.File;
import java.io.IOException;

public class LocalShaderDispatcher implements IShaderDispatcher {

  private final boolean usingSwiftShader;
  private final ShaderJobFileOperations fileOps;
  private final File tempDir;

  public LocalShaderDispatcher(
      boolean usingSwiftShader,
      ShaderJobFileOperations fileOps,
      File tempDir) {
    this.usingSwiftShader = usingSwiftShader;
    this.fileOps = fileOps;
    this.tempDir = tempDir;
  }

  @Override
  public ImageJobResult getImage(ImageJob imageJob)
      throws ShaderDispatchException, InterruptedException {

    // For running shaders locally, we write the necessary files to a temp directory,
    // run the get_image tool, and then collect the results in an ImageJobResult.

    // Due to strange Thrift behaviour, we set this default value explicitly
    // otherwise "isSetSkipRender()" is false.
    if (!imageJob.isSetSkipRender()) {
      imageJob.setSkipRender(false);
    }

    File localTempShaderJobFile =
        new File(
            tempDir,
            (imageJob.getName() != null ? imageJob.getName() + ".json" : "temp.json")
        );

    try {
      return fileOps.runGetImageOnImageJob(imageJob, localTempShaderJobFile, usingSwiftShader);
    } catch (IOException exception) {
      throw new ShaderDispatchException(exception);
    }
  }

}
