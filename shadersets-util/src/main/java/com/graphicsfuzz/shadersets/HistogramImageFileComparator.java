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

import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistogramImageFileComparator implements IImageFileComparator {

  private static final Logger LOGGER = LoggerFactory.getLogger(HistogramImageFileComparator.class);

  private final double threshold;
  private final boolean above;

  public HistogramImageFileComparator(double threshold, boolean above) {
    this.threshold = threshold;
    this.above = above;
  }

  @Override
  public boolean areFilesInteresting(File reference, File variant) {
    try {
      LOGGER.info("Comparing: {} and {}.", reference, variant);
      double diff = ImageUtil.compareHistograms(
          ImageUtil.getHistogram(reference.toString()), ImageUtil.getHistogram(variant.toString()));
      boolean result = (above ? diff > threshold : diff < threshold);
      if (result) {
        LOGGER.info("Interesting");
      } else {
        LOGGER.info("Not interesting");
      }
      LOGGER.info(": difference is " + diff);
      return result;
    } catch (IOException exception) {
      LOGGER.info("Not interesting - exception occurred during histogram comparison.");
      return false;
    }
  }
}
