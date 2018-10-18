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

    opencv_core.Mat mat = opencv_imgcodecs.imread(file);
    opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_BGR2HSV);
    opencv_core.Mat hist = new opencv_core.Mat();
    opencv_imgproc.calcHist(
        new opencv_core.MatVector(new opencv_core.Mat[]{mat}),
        new int[]{0, 1},
        new opencv_core.Mat(),
        hist,
        new int[]{50, 60},
        new float[]{0, 256, 0, 256}
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

  public static boolean identicalImages(File file1, File file2) {

    BufferedImage img1 = null;
    BufferedImage img2 = null;

    try {
      img1 = ImageIO.read(file1);
      img2 = ImageIO.read(file2);
    } catch (IOException exception) {
      exception.printStackTrace();
    }

    int height = img1.getHeight();
    int width = img1.getWidth();
    if (img2.getHeight() != height || img2.getWidth() != width) {
      return false;
    }

    for (int i = 0; i < height; i++) {
      for (int j = 0; j < width; j++) {
        if (img1.getRGB(i, j) != img2.getRGB(i, j)) {
          return false;
        }
      }
    }

    return true;
  }

}
