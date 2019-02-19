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

package com.graphicsfuzz.common.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class FuzzyImageComparison {

  public static final class ThresholdConfiguration {
    public int componentThreshold;
    public int distanceThreshold;
    public int numDifferentResult;

    public ThresholdConfiguration(int componentThreshold, int distanceThreshold) {
      this.componentThreshold = componentThreshold;
      this.distanceThreshold = distanceThreshold;
      this.numDifferentResult = 0;
    }
  }

  private static boolean arePixelsSimilarComponentThreshold(
      int colorLeft,
      int colorRight,
      int componentThreshold) {

    for (int i = 0; i < 32; i += 8) {
      int left = ImageColorComponents.getComponent(colorLeft, i);
      int right = ImageColorComponents.getComponent(colorRight, i);
      assert left >= 0 && left <= 0xff;
      assert right >= 0 && right <= 0xff;
      if (Math.abs(left - right) > componentThreshold) {
        return false;
      }
    }
    return true;
  }

  private static boolean doesSimilarNearPixelExist(
      int[] colorsLeft,
      int[] colorsRight,
      int height,
      int componentThreshold,
      int distanceThreshold,
      int middleX,
      int middleY) {

    int middlePos = middleY * height + middleX;
    for (int y = middleY - distanceThreshold; y < middleY + distanceThreshold; ++y) {
      for (int x = middleX - distanceThreshold; x < middleX + distanceThreshold; ++x) {
        int pos = y * height + x;
        if (arePixelsSimilarComponentThreshold(
            colorsLeft[middlePos],
            colorsRight[pos],
            componentThreshold)) {
          return true;
        }
      }
    }
    return false;
  }

  public static int compareImageColors(
      int[] colorsLeft,
      int[] colorsRight,
      int width,
      int height,
      int componentThreshold,
      int distanceThreshold,
      int[] outColorsBad) {

    assert colorsLeft.length == colorsRight.length;

    int numBad = 0;

    // Skip pixels around the edge (distanceThreshold).

    for (int y = distanceThreshold; y < height - distanceThreshold; ++y) {
      for (int x = distanceThreshold; x < width - distanceThreshold; ++x) {

        // Compare in both directions, and take the worst case (whichever order gives "different").
        // Reason 1: we probably want the comparison to be independent of order (commutative).
        // Reason 2: given a reference image that is black, and a variant image that has gained
        // a small patch of white pixels; the black pixels in the original image could all be
        // matched to black pixels in the variant image, so we would not detect any difference.
        // However, the white pixels in the variant image would not be matched to any pixels in
        // the original image, so we would detect a different. Thus, comparing both directions and
        // taking the worst case is typically more useful.
        if (!doesSimilarNearPixelExist(
            colorsLeft,
            colorsRight,
            height,
            componentThreshold,
            distanceThreshold,
            x,
            y)
            ||
            !doesSimilarNearPixelExist(
            colorsRight,
            colorsLeft,
            height,
            componentThreshold,
            distanceThreshold,
            x,
            y)
        ) {
          // We could include more information, but for now, if bad, we set to 1.
          outColorsBad[y * height + x] = 1;
          ++numBad;
        }
      }
    }
    return numBad;
  }

  public static void compareImages(
      File left,
      File right,
      ThresholdConfiguration[] thresholdConfigurations) throws IOException {

    BufferedImage leftImage = ImageIO.read(left);
    BufferedImage rightImage = ImageIO.read(right);
    if (leftImage.getWidth() != rightImage.getWidth()
        || leftImage.getHeight() != rightImage.getHeight()) {
      throw new IllegalArgumentException("Images have different sizes! \n" + left + "\n" + right);
    }

    int[] colorsLeft = ImageColorComponents.getRgb(leftImage);
    int[] colorsRight = ImageColorComponents.getRgb(rightImage);

    for (ThresholdConfiguration thresholdConfiguration : thresholdConfigurations) {
      // int[] values are 0 by default.
      int[] badPixels = new int[colorsLeft.length];

      thresholdConfiguration.numDifferentResult = compareImageColors(
          colorsLeft,
          colorsRight,
          leftImage.getWidth(),
          leftImage.getHeight(),
          thresholdConfiguration.componentThreshold,
          thresholdConfiguration.distanceThreshold,
          badPixels);

      // TODO: Could post-process here (using badPixels array) to remove sparse bad pixels; i.e.
      //  only clusters of bad pixels are considered bad.
    }

  }

  public static ThresholdConfiguration[] compareImagesDefault(
      File left,
      File right) throws IOException {

    // Based on some sample images of 256x256:
    // distance threshold: 4
    // component threshold: 60
    //   - diff of 10+ is probably bad
    // component threshold: 10
    //   - diff of 10000+ is probably bad.
    //     E.g. to catch an image that is mostly grey but has a *lot* of small changes in the shades
    //     of grey.

    ThresholdConfiguration[] configs = new ThresholdConfiguration[] {
        new ThresholdConfiguration(60, 4),
        new ThresholdConfiguration(10, 4)
    };
    compareImages(left, right, configs);
    return configs;
  }

  public static void mainHelper(String[] args) throws IOException {

    // See FuzzyImageComparisonTool for main.

    FuzzyImageComparison.ThresholdConfiguration[] configs = compareImagesDefault(new File(args[0]), new File(args[1]));
    boolean different =
        configs[0].numDifferentResult > 10 || configs[1].numDifferentResult > 10000;
    System.out.println(configs[0].numDifferentResult + " " + configs[1].numDifferentResult + " "
        + (different ? "different" : "similar"));
  }

}
