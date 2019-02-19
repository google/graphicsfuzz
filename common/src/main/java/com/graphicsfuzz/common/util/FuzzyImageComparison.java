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

/**
 * A fuzzy image comparison algorithm.
 *
 * Inputs: two images (image A and image B) of the same size, a distance threshold "d", a component
 * threshold "c".
 * Output: the number of "bad" pixels that "differ" according to the description below.
 * Roughly: the number of "bad" pixels is the number of pixel coordinates (x,y) where for (x,y)
 * in image A, there is not a nearby similar pixel in image B *or* vice-versa (i.e. even if only
 * one direction is bad, the pixel coordinate is bad).
 *
 * Example values: d=4, c=60. Color component values (RGBA) are 0-255.
 *
 * For each pixel at (x,y) in image A that is not within distance "d" from the edge of the image,
 * we try to find a similar pixel within the square of pixels (of size 2"d") centered at (x,y) in
 * image B. We also try the reverse (consider (x,y) in image B and search for a pixel in image A);
 * if *either* search fails, this pixel coordinate is considered "bad". The result is the number
 * of bad pixels.
 * Given two pixels p and q, they are similar if they have similar color values; specifically, p
 * and q are similar iff for *all* color components m in [R,B,G,A]:
 * abs(p[m]-q[m]) <= "c"
 *
 * There are two reasons why we consider a pixel as bad if *either* direction fails (searching in
 * image A or in image B):
 * Reason 1: we probably want the comparison to be independent of image input order (commutative).
 * Reason 2: given a reference image that is black, and a variant image that has gained
 * a small patch of white pixels; the black pixels in the original image could all be
 * matched to black pixels in the variant image, so we would not detect any difference.
 * However, the white pixels in the variant image would not be matched to any pixels in
 * the original image, so we would detect a different. Thus, comparing both directions and
 * taking the worst case is typically more useful.
 *
 */
public class FuzzyImageComparison {

  /**
   * See {@link FuzzyImageComparison}. Set of parameters used by the {@link FuzzyImageComparison}
   * algorithm, plus the result if the algorithm completed (numDifferentResult).
   */
  public static final class ThresholdConfiguration {
    public int componentThreshold;
    public int distanceThreshold;

    /**
     * The output from running the fuzzy image comparison with this configuration.
     */
    public int numDifferentResult;

    public ThresholdConfiguration(int componentThreshold, int distanceThreshold) {
      this.componentThreshold = componentThreshold;
      this.distanceThreshold = distanceThreshold;
      this.numDifferentResult = -1;
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
      int distanceThreshold) {

    assert colorsLeft.length == colorsRight.length;

    int numBad = 0;

    // Skip pixels around the edge (distanceThreshold).

    for (int y = distanceThreshold; y < height - distanceThreshold; ++y) {
      for (int x = distanceThreshold; x < width - distanceThreshold; ++x) {

        // Given pixel in image A, find similar, nearby pixel in image B, and vice-versa.
        // If either fails (hence || below), then the pixel coordinate is bad.
        // See comments at top of file for justification.
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

      thresholdConfiguration.numDifferentResult = compareImageColors(
          colorsLeft,
          colorsRight,
          leftImage.getWidth(),
          leftImage.getHeight(),
          thresholdConfiguration.componentThreshold,
          thresholdConfiguration.distanceThreshold);

      // TODO: Could post-process here (using a badPixels array) to remove sparse bad pixels; i.e.
      //  only clusters of bad pixels are considered bad.
    }

  }

  /**
   * Compares two images using the {@link FuzzyImageComparison} algorithm with some default
   * configurations. Two default configurations are set by default, the first with a larger
   * component threshold, and the second with a smaller component threshold.
   *
   * Typically, the first configuration has found a bad image if there were 10+ different pixels,
   * and the second configuration has found a bad image if there were 10000+ different pixels.
   * Thus, if either of the above is true, the image is bad.
   *
   * The justification is that the second configuration is too sensitive in most cases, but it
   * can catch cases where an image has a *lot* of slightly different pixels, such as where the
   * images are mostly different shades of grey.
   *
   * @param left first image file
   * @param right second image file
   * @return an array of configurations
   * @throws IOException
   */
  public static ThresholdConfiguration[] compareImagesDefault(
      File left,
      File right) throws IOException {

    // Default parameters based on some sample images of 256x256:
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
