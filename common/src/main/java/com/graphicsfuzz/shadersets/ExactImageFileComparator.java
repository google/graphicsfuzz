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
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExactImageFileComparator implements IImageFileComparator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExactImageFileComparator.class);

  private final boolean identicalIsInteresting;
  private final ShaderJobFileOperations fileOps;

  public ExactImageFileComparator(
      boolean identicalIsInteresting,
      ShaderJobFileOperations fileOps) {
    this.identicalIsInteresting = identicalIsInteresting;
    this.fileOps = fileOps;
  }

  @Override
  public boolean areFilesInteresting(
      File shaderResultFileReference,
      File shaderResultFileVariant) {

    try {

      boolean equalContent = fileOps.areImagesOfShaderResultsIdentical(
          shaderResultFileReference,
          shaderResultFileVariant);

      if (!equalContent && identicalIsInteresting) {
        LOGGER.info("Not interesting: images do not match");
        return false;
      }
      if (equalContent && !identicalIsInteresting) {
        LOGGER.info("Not interesting: images match");
        return false;
      }
      return true;
    } catch (IOException exception) {
      LOGGER.error("Not interesting: exception while comparing files", exception);
      throw new RuntimeException(exception);
    }
  }
}
