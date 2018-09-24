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
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.opencv_imgproc;

/**
 * Stores data about an image; right now its file and histogram.
 * Could be extended in due course with e.g. PSNR
 */
public class ImageData {

  public final File imageFile;
  private final opencv_core.Mat imageMat;
  private final opencv_core.Mat histogram;

  public ImageData(File imageFile) throws FileNotFoundException {
    this.imageFile = imageFile;
    this.imageMat = opencv_imgcodecs.imread(imageFile.getAbsolutePath());
    opencv_imgproc.cvtColor(this.imageMat, this.imageMat, opencv_imgproc.COLOR_BGR2HSV);
    this.histogram = ImageUtil.getHistogram(imageFile.getAbsolutePath());
  }

  public ImageData(String imageFileName) throws FileNotFoundException {
    this(new File(imageFileName));
  }

  public Map<String, Double> getImageDiffStats(ImageData other) {
    Map<String, Double> result = new HashMap<>();
    result.put("histogramDistance", ImageUtil.compareHistograms(this.histogram, other.histogram));
    result.put("psnr", opencv_core.PSNR(this.imageMat, other.imageMat));
    return result;
  }

}
