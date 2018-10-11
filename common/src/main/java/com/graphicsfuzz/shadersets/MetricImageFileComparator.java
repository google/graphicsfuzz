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
import com.graphicsfuzz.server.thrift.ImageComparisonMetric;
import java.io.File;
import java.io.FileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricImageFileComparator implements IImageFileComparator {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetricImageFileComparator.class);

  private final double threshold;
  private final boolean above;
  private final ImageComparisonMetric metric;
  private final ShaderJobFileOperations fileOps;

  public MetricImageFileComparator(
      double threshold,
      boolean above,
      ImageComparisonMetric metric,
      ShaderJobFileOperations fileOps) {
    this.threshold = threshold;
    this.above = above;
    this.metric = metric;
    this.fileOps = fileOps;
  }

  @Override
  public boolean areFilesInteresting(File shaderResultFileReference, File shaderResultFileVariant) {
    try {

      return fileOps.areImagesOfShaderResultsSimilar(
          shaderResultFileReference,
          shaderResultFileVariant,
          metric,
          threshold,
          above);

    } catch (FileNotFoundException exception) {
      LOGGER.error(
          "Exception occurred during image comparison using metric {}. {}",
          metric.toString(),
          exception);
      throw new RuntimeException(exception);
    }
  }
}
