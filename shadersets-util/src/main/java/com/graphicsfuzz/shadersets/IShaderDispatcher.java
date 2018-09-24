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

import com.graphicsfuzz.server.thrift.ComputeJobResult;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import java.io.File;

public interface IShaderDispatcher {

  /**
   * Gets an image from the shaders prefixed shaderFilesPrefix.
   * If the returned ImageJobResult.getStatus() == SUCCESS,
   * then the image will EITHER be in ImageJobResult.getImageContents() OR
   * have been written to tempImageFile.
   */
  ImageJobResult getImage(
      String shaderFilesPrefix,
      File tempImageFile,
      boolean skipRender) throws ShaderDispatchException, InterruptedException;

  /**
   * Gets a result from computeShaderFile.
   */
  ComputeJobResult dispatchCompute(
      File computeShaderFile,
      boolean skipExecution) throws ShaderDispatchException, InterruptedException;

}
