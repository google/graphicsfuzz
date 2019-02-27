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

import com.google.gson.annotations.SerializedName;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * A fuzzy image comparison algorithm.
 *
 * <p>The algorithm is run N times for the N threshold configurations provided, but here we only
 * discuss the inputs and outputs for one configuration.
 *
 * <p>Inputs: two images (image A and image B) of the same size, a distance threshold "d", a
 * component threshold "c".
 * Output: the number of "bad" pixels that "differ" according to the description below. Also, the
 * number of "sparse" bad pixels; that is, the number of bad pixels when we remove those that are
 * not near to other bad pixels; this allows us to ignore bad pixels that are likely to be less
 * interesting. e.g. bad pixels that are due to aliasing.
 *
 * <p>"Bad pixels" are roughly: pixel coordinates (x,y) where for (x,y) in image A, there is not a
 * nearby similar pixel in image B *or* vice-versa (i.e. even if only one direction is bad, the
 * pixel coordinate is bad).
 *
 * <p>Example values: d=4, c=60. Color component values (RGBA) are 0-255.
 *
 * <p>For each pixel at (x,y) in image A, we try to find a similar pixel within the square of
 * pixels (of size 2"d") centered at (x,y) in image B. We also try the reverse (consider (x,y) in
 * image B and search for a pixel in image A); if *either* search fails, this pixel coordinate is
 * considered "bad". The result is the number of bad pixels.
 *
 * <p>Two pixels p and q are similar iff they have similar color values; specifically, p
 * and q are similar iff for *all* color components m in [R,B,G,A]: abs(p[m]-q[m]) <= "c". In
 * other words, if *any* component differs by more than "c", the pixels are different.
 *
 * <p>Why do we consider a pixel to be "bad" if *either* search direction fails (searching in
 * image A or in image B):
 *
 * <p>Reason 1: we probably want the comparison to be independent of image input order
 * (commutative).
 *
 * <p>Reason 2: given a reference image that is black, and a variant image that has gained
 * a *small* patch of white pixels; the black pixels in the original image could all be
 * matched to black pixels in the variant image (by finding pixels *around* the white pixels),
 * so we would not detect any difference. However, the white pixels in the variant image would
 * not be matched to any pixels in the original image, so we would detect a different. Thus,
 * comparing both directions and taking the worst case is typically more useful.
 */
@SuppressWarnings({"Duplicates", "BooleanMethodIsAlwaysInverted", "WeakerAccess",
    "SameParameterValue"})
public class FuzzyImageComparison {

  private static final int GOOD_PIXEL_VALUE = 0;

  public static final int CONFIG_NUM_ARGS = 4;

  /**
   * See {@link FuzzyImageComparison}. Set of parameters used by the {@link FuzzyImageComparison}
   * algorithm, plus the outputs (e.g. number of bad pixels) for this configuration.
   */
  @SuppressWarnings("WeakerAccess")
  public static final class ThresholdConfiguration {

    /**
     * See {@link FuzzyImageComparison}.
     */
    public int componentThreshold;

    /**
     * See {@link FuzzyImageComparison}.
     */
    public int distanceThreshold;

    /**
     * More than this many bad pixels implies the images are different.
     */
    public int numBadPixelsThreshold;

    /**
     * More than this many sparse bad pixels implies the images are different.
     */
    public int numBadSparsePixelsThreshold;

    /**
     * See {@link FuzzyImageComparison}.
     */
    public int outNumBadPixels;

    /**
     * See {@link FuzzyImageComparison}.
     */
    public int outNumBadSparsePixels;

    public ThresholdConfiguration(
        int componentThreshold,
        int distanceThreshold,
        int numBadPixelsThreshold,
        int numBadSparsePixelsThreshold) {
      this.componentThreshold = componentThreshold;
      this.distanceThreshold = distanceThreshold;
      this.numBadPixelsThreshold = numBadPixelsThreshold;
      this.numBadSparsePixelsThreshold = numBadSparsePixelsThreshold;
      this.outNumBadPixels = -1;
      this.outNumBadSparsePixels = -1;
    }

    public boolean areImagesDifferent() {
      if (outNumBadPixels < 0 || outNumBadSparsePixels < 0) {
        throw new IllegalStateException("Checked if images are different under configuration that"
            + " was not run");
      }

      return outNumBadPixels > numBadPixelsThreshold
          || outNumBadSparsePixels > numBadSparsePixelsThreshold;
    }

    public String outputsString() {
      return String.join(
          " ",
          String.valueOf(outNumBadPixels),
          String.valueOf(outNumBadSparsePixels));
    }

  }

  /**
   * Wrapper for result of {@link FuzzyImageComparison#mainHelper}.
   */
  @SuppressWarnings("WeakerAccess")
  public static final class MainResult {

    // TODO: Yet another use of untyped JSON. We should use Thrift or protobufs.
    public static final String ARE_IMAGES_DIFFERENT_KEY = "are_images_different";

    @SerializedName(ARE_IMAGES_DIFFERENT_KEY)
    public boolean areImagesDifferent;

    @SerializedName("exit_status")
    public int exitStatus;

    @SerializedName("configurations")
    public List<ThresholdConfiguration> configurations;

    public static int EXIT_STATUS_SIMILAR = 0;
    public static int EXIT_STATUS_DIFFERENT = 1;

    public MainResult(
        boolean areImagesDifferent,
        int exitStatus,
        List<ThresholdConfiguration> configurations) {
      this.areImagesDifferent = areImagesDifferent;
      this.exitStatus = exitStatus;
      this.configurations = configurations;
    }

    public String outputsString() {
      return configurations
          .stream()
          .map(FuzzyImageComparison.ThresholdConfiguration::outputsString)
          .collect(Collectors.joining(" "));
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
      final int[] colorsLeft,
      final int[] colorsRight,
      final int width,
      final int height,
      final int componentThreshold,
      final int distanceThreshold,
      final int middleX,
      final int middleY) {

    final int middlePos = middleY * height + middleX;

    final int ystart = Math.max(0, middleY - distanceThreshold);
    final int xstart = Math.max(0, middleX - distanceThreshold);
    final int yend = Math.min(height, middleY + distanceThreshold);
    final int xend = Math.min(width, middleX + distanceThreshold);

    for (int y = ystart; y < yend; ++y) {
      for (int x = xstart; x < xend; ++x) {
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

  /**
   * @param badPixels will contain a non-zero value at every bad pixel found
   * @return the number of bad pixels found according to the thresholds provided
   */
  private static int compareImageColors(
      int[] colorsLeft,
      int[] colorsRight,
      int width,
      int height,
      int componentThreshold,
      int distanceThreshold,
      int[] badPixels) {

    assert colorsLeft.length == colorsRight.length;

    int numBad = 0;

    for (int y = 0; y < height; ++y) {
      for (int x = 0; x < width; ++x) {

        // Given pixel in image A, find similar, nearby pixel in image B, and vice-versa.
        // If either fails (hence || below), then the pixel coordinate is bad.
        // See comments at top of file for justification.
        if (!doesSimilarNearPixelExist(
                colorsLeft,
                colorsRight,
                width,
                height,
                componentThreshold,
                distanceThreshold,
                x,
                y)
            ||
            !doesSimilarNearPixelExist(
                colorsRight,
                colorsLeft,
                width,
                height,
                componentThreshold,
                distanceThreshold,
                x,
                y)
        ) {
          ++numBad;
          badPixels[y * height + x] = 0x88888888;
        }
      }
    }
    return numBad;
  }

  private static int clusterBadPixelCount(
      final int[] badPixels,
      final int goodPixelValue,
      final int width,
      final int height,
      final int clusterBoxSize,
      final int middleX,
      final int middleY) {

    final int ystart = Math.max(0, middleY - clusterBoxSize);
    final int xstart = Math.max(0, middleX - clusterBoxSize);
    final int yend = Math.min(height, middleY + clusterBoxSize);
    final int xend = Math.min(width, middleX + clusterBoxSize);

    int badPixelCount = 0;

    for (int y = ystart; y < yend; ++y) {
      for (int x = xstart; x < xend; ++x) {
        int pos = y * height + x;
        if (badPixels[pos] != goodPixelValue) {
          ++badPixelCount;
        }
      }
    }
    return badPixelCount;
  }

  private static int removeSparseBadPixelsInCluster(
      final int[] badPixels,
      final int goodPixelValue,
      final int width,
      final int height,
      final int clusterBoxSize,
      final int numBadPixelsDense) {

    int removedCount = 0;

    for (int y = 0; y < height; ++y) {
      for (int x = 0; x < width; ++x) {
        final int pos = y * height + x;
        if (badPixels[pos] == goodPixelValue) {
          continue;
        }
        final int badPixelCount =
            clusterBadPixelCount(
                badPixels,
                goodPixelValue,
                width,
                height,
                clusterBoxSize,
                x,
                y
            );
        if (badPixelCount < numBadPixelsDense) {
          // Mark to be removed.
          assert badPixels[pos] != Integer.MAX_VALUE;
          badPixels[pos] = Integer.MAX_VALUE;
        }
      }
    }

    for (int i = 0; i < badPixels.length; ++i) {
      if (badPixels[i] == Integer.MAX_VALUE) {
        badPixels[i] = goodPixelValue;
        ++removedCount;
      }
    }

    return removedCount;
  }

  private static int countBad(
      final int[] badPixels,
      final int goodPixelValue) {
    int res = 0;
    for (int badPixel : badPixels) {
      if (badPixel != goodPixelValue) {
        ++res;
      }
    }
    return res;
  }

  /**
   * For debugging.
   */
  @SuppressWarnings("unused")
  private static void writeBadPixels(
      final int[] badPixels,
      final int width,
      final int height) throws IOException {

    BufferedImage out = new BufferedImage(
        width,
        height,
        BufferedImage.TYPE_INT_ARGB);

    for (int y = 0; y < height; ++y) {
      for (int x = 0; x < height; ++x) {
        out.setRGB(x, y, badPixels[y * height + x]);
      }
    }
    ImageIO.write(out, "png", new File("out.png"));
  }

  /**
   * @param thresholdConfigurations the input thresholds. The results will also be written to each
   *                                configuration.
   */
  public static void compareImages(
      File left,
      File right,
      List<ThresholdConfiguration> thresholdConfigurations) throws IOException {

    BufferedImage leftImage = ImageIO.read(left);
    BufferedImage rightImage = ImageIO.read(right);
    if (leftImage.getWidth() != rightImage.getWidth()
        || leftImage.getHeight() != rightImage.getHeight()) {
      throw new IllegalArgumentException("Images have different sizes! \n" + left + "\n" + right);
    }

    int[] colorsLeft = ImageColorComponents.getRgb(leftImage);
    int[] colorsRight = ImageColorComponents.getRgb(rightImage);

    for (ThresholdConfiguration thresholdConfiguration : thresholdConfigurations) {

      int[] badPixels = new int[colorsLeft.length];

      // int[] values are 0 by default.
      assert badPixels[0] == GOOD_PIXEL_VALUE;

      thresholdConfiguration.outNumBadPixels = compareImageColors(
          colorsLeft,
          colorsRight,
          leftImage.getWidth(),
          leftImage.getHeight(),
          thresholdConfiguration.componentThreshold,
          thresholdConfiguration.distanceThreshold,
          badPixels);

      // Remove sparse bad pixels from the count.

      int removedCount = removeSparseBadPixelsInCluster(
          badPixels,
          GOOD_PIXEL_VALUE,
          leftImage.getWidth(),
          leftImage.getHeight(),
          thresholdConfiguration.distanceThreshold,
          thresholdConfiguration.distanceThreshold * 2);

      thresholdConfiguration.outNumBadSparsePixels =
          thresholdConfiguration.outNumBadPixels - removedCount;

      assert countBad(badPixels, GOOD_PIXEL_VALUE)
          == thresholdConfiguration.outNumBadSparsePixels;
    }

  }

  /**
   * Adds some default configurations to the provided list.
   *
   * @param configurations some default configurations will be added to this list
   */
  public static void addDefaultConfigurations(List<ThresholdConfiguration> configurations) {
    configurations.add(
        new ThresholdConfiguration(
            25,
            4,
            100,
            10));

    configurations.add(
        new ThresholdConfiguration(
            60,
            4,
            10,
            10));
  }

  public static MainResult mainHelper(String[] args) throws IOException,
      ArgumentParserException {

    // See FuzzyImageComparisonTool for main.

    ArgumentParser parser = ArgumentParsers.newArgumentParser("FuzzyImageComparison")
        .description("Compare two images using a fuzzy pixel comparison. The exit status is "
            + MainResult.EXIT_STATUS_SIMILAR + " if the images are similar, "
            + MainResult.EXIT_STATUS_DIFFERENT + " if the images are different, or another value "
            + "if an error occurs. "
            + "Example: FuzzyImageComparison imageA.png imageB.png 25 4 100 10");

    parser.addArgument("imageA")
        .help("Path to first image file")
        .type(File.class);

    parser.addArgument("imageB")
        .help("Path to second image file")
        .type(File.class);

    parser.addArgument("configurations")
        .help("zero or more configurations (each configuration is a "
            + CONFIG_NUM_ARGS
            + "-tuple of integer arguments): "
            + "componentThreshold "
            + "distanceThreshold "
            + "numBadPixelsThreshold "
            + "numBadSparsePixelsThreshold")
        .nargs("*");

    Namespace ns = parser.parseArgs(args);

    final File imageA = ns.get("imageA");
    final File imageB = ns.get("imageB");
    final List<String> stringConfigurations = new ArrayList<>(ns.get("configurations"));

    if ((stringConfigurations.size() % CONFIG_NUM_ARGS) != 0) {
      throw new ArgumentParserException(
          "Configuration list must be a list of " + CONFIG_NUM_ARGS + "-tuples", parser);
    }

    List<ThresholdConfiguration> configurations = new ArrayList<>();

    for (int i = 0; i < stringConfigurations.size(); i += CONFIG_NUM_ARGS) {
      configurations.add(
          new ThresholdConfiguration(
              Integer.parseInt(stringConfigurations.get(i)),
              Integer.parseInt(stringConfigurations.get(i + 1)),
              Integer.parseInt(stringConfigurations.get(i + 2)),
              Integer.parseInt(stringConfigurations.get(i + 3))
          ));
    }

    if (configurations.isEmpty()) {
      addDefaultConfigurations(configurations);
    }

    compareImages(imageA, imageB, configurations);

    boolean different =
        configurations.stream().anyMatch(ThresholdConfiguration::areImagesDifferent);

    return new MainResult(
        different,
        different ? MainResult.EXIT_STATUS_DIFFERENT : MainResult.EXIT_STATUS_SIMILAR,
        configurations
    );
  }

}
