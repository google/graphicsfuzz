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

package com.graphicsfuzz.tool;

import com.graphicsfuzz.common.util.ImageColorComponents;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class ImageCompare {

  public static int compareImages(
      File left,
      File right,
      int componentThreshold,
      int thresholdDistance) throws IOException {

    BufferedImage leftImage = ImageIO.read(left);
    BufferedImage rightImage = ImageIO.read(right);
    if (leftImage.getWidth() != rightImage.getWidth()
        || leftImage.getHeight() != rightImage.getHeight()) {
      throw new IllegalArgumentException("Images have different sizes! \n" + left + "\n" + right);
    }
    int[] colorsLeft = ImageColorComponents.getRgb(leftImage);
    int[] colorsRight = ImageColorComponents.getRgb(rightImage);

    // Compare in both directions.

    int res1 = compareImageColors(
        colorsLeft,
        colorsRight,
        leftImage.getWidth(),
        leftImage.getHeight(),
        componentThreshold,
        thresholdDistance);
    int res2 = compareImageColors(
        colorsRight,
        colorsLeft,
        leftImage.getWidth(),
        leftImage.getHeight(),
        componentThreshold,
        thresholdDistance);

    return Math.max(res1, res2);
  }

  public static void main(String[] args) throws IOException {

    int numBadPixels = compareImages(
        new File(args[0]),
        new File(args[1]),
        60,
        4
    );

    System.out.println(numBadPixels);
  }

}
