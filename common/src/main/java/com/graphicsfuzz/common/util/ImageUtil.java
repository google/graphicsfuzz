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
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.opencv_imgproc;

public class ImageUtil {

  public static opencv_core.Mat getImage(File file) throws FileNotFoundException {
    if (!file.isFile()) {
      throw new FileNotFoundException();
    }

    System.gc();

    opencv_core.Mat mat = opencv_imgcodecs.imread(file.toString());
    opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_BGR2HSV);
    return mat;
  }

  public static opencv_core.Mat getHistogram(String file) throws FileNotFoundException {
    if (!new File(file).isFile()) {
      throw new FileNotFoundException();
    }

    System.gc();

    // Load PNG image.
    // IMREAD_UNCHANGED causes the alpha channel to be included, if present.
    opencv_core.Mat matWithAlpha = opencv_imgcodecs.imread(
        file,
        opencv_imgcodecs.IMREAD_UNCHANGED);

    // The image might not have four channels (i.e. no alpha channel).
    if (matWithAlpha.type() != opencv_core.CV_8UC4) {
      // The file could be in several different formats, including greyscale.
      // Reload the image, converting to BGR.
      matWithAlpha = opencv_imgcodecs.imread(file, opencv_imgcodecs.IMREAD_COLOR);
      assert matWithAlpha.type() == opencv_core.CV_8UC3;
      // Add (opaque) alpha channel.
      opencv_imgproc.cvtColor(matWithAlpha, matWithAlpha, opencv_imgproc.COLOR_BGR2BGRA);
    }

    // matWithAlpha has four channels.
    assert matWithAlpha.type() == opencv_core.CV_8UC4;

    opencv_core.Mat mat = new opencv_core.Mat();
    // Remove alpha so we can convert colors to HSV.
    opencv_imgproc.cvtColor(matWithAlpha, mat, opencv_imgproc.COLOR_BGRA2BGR);
    // Convert to HSV.
    opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_BGR2HSV);

    // Re-add alpha for histogram function.
    opencv_core.MatVector bgraChannels = new opencv_core.MatVector();
    opencv_core.split(matWithAlpha, bgraChannels);
    opencv_core.MatVector hsvChannels = new opencv_core.MatVector();
    opencv_core.split(mat, hsvChannels);

    hsvChannels.push_back(bgraChannels.get(3));

    opencv_core.merge(hsvChannels, mat);

    // mat now contains:
    // HSVA
    // 0123

    opencv_core.Mat hist = new opencv_core.Mat();

    opencv_imgproc.calcHist(
        // Source array.
        new opencv_core.MatVector(new opencv_core.Mat[]{mat}),
        // Channels: hue, saturation, value, alpha.
        new IntPointer(0, 1, 2, 3),
        // Mask (none).
        new opencv_core.Mat(),
        // Output.
        hist,
        // Histogram size: number of levels for hue, saturation, value, alpha.
        // We use a low number of levels for value as we don't want it to have as much impact.
        new IntPointer(50, 60, 5, 60),
        // Input ranges for: hue, saturation, value, alpha.
        new FloatPointer(0, 256, 0, 256, 0, 256, 0, 256)
    );
    return hist;
  }

  public static double compareHistograms(opencv_core.Mat mat1, opencv_core.Mat mat2) {
    return opencv_imgproc.compareHist(mat1, mat2, opencv_imgproc.HISTCMP_CHISQR);
  }

  public static double compareHistograms(File file1, File file2) throws FileNotFoundException {
    return compareHistograms(
        getHistogram(file1.getAbsolutePath()),
        getHistogram(file2.getAbsolutePath()));
  }

  public static double comparePSNR(File file1, File file2) throws FileNotFoundException {
    opencv_core.Mat image1 = getImage(file1);
    opencv_core.Mat image2 = getImage(file2);
    return opencv_core.PSNR(image1, image2);
  }

  public static boolean identicalImages(File file1, File file2) throws IOException {

    BufferedImage img1 = ImageIO.read(file1);
    BufferedImage img2 = ImageIO.read(file2);

    int height = img1.getHeight();
    int width = img1.getWidth();
    if (img2.getHeight() != height || img2.getWidth() != width) {
      return false;
    }

    for (int y = 0; y < height; ++y) {
      for (int x = 0; x < width; ++x) {
        if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
          return false;
        }
      }
    }

    return true;
  }

}
