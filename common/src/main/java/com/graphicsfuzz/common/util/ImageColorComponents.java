/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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
import java.util.Arrays;
import java.util.List;

public class ImageColorComponents {

  public static final int R_OFFSET = 16;
  public static final int G_OFFSET = 8;
  public static final int B_OFFSET = 0;
  public static final int A_OFFSET = 24;

  /**
   * Determines whether the given buffered image comprises pixels whose components come only from
   * the given component values.
   * @param image The image to be considered.
   * @param allowedComponentValues The allowed component values.
   * @return True if and only if the pixels in the image only use the given components.
   */
  public static boolean containsOnlyGivenComponentValues(BufferedImage image,
                                                         List<Integer> allowedComponentValues) {
    final int[] colors = getRgb(image);
    for (int color : colors) {
      for (int componentValue : Arrays.asList(getComponentR(color), getComponentG(color),
          getComponentB(color), getComponentA(color))) {
        if (!allowedComponentValues.contains(componentValue)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Gets the R component from a pixel.
   * @param pixel A pixel.
   * @return The pixel's R component.
   */
  public static int getComponentR(int pixel) {
    return getComponent(pixel, R_OFFSET);
  }

  /**
   * Gets the G component from a pixel.
   * @param pixel A pixel.
   * @return The pixel's G component.
   */
  public static int getComponentG(int pixel) {
    return getComponent(pixel, G_OFFSET);
  }

  /**
   * Gets the B component from a pixel.
   * @param pixel A pixel.
   * @return The pixel's B component.
   */
  public static int getComponentB(int pixel) {
    return getComponent(pixel, B_OFFSET);
  }

  /**
   * Gets the A component from a pixel.
   * @param pixel A pixel.
   * @return The pixel's A component.
   */
  public static int getComponentA(int pixel) {
    return getComponent(pixel, A_OFFSET);
  }

  /**
   * Returns an array of pixel values for the given image.
   * @param image Image for which pixel values are required.
   * @return Pixel values for the image.
   */
  public static int[] getRgb(BufferedImage image) {
    return image.getRGB(
        0,
        0,
        image.getWidth(),
        image.getHeight(),
        null,
        0,
        image.getWidth());
  }

  public static int getComponent(int pixel, int componentBitOffset) {
    return (pixel >> componentBitOffset) & 0xff;
  }

}
