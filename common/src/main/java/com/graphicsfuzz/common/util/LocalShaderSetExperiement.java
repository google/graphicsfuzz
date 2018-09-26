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

import java.io.File;
import java.util.Arrays;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalShaderSetExperiement implements IShaderSetExperiment {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalShaderSetExperiement.class);

  private static final String REFERENCE_IMAGE_FILENAME = "reference.png";
  private static final String REFERENCE_TEXT_FILE_FILENAME = "reference.txt";
  private static final String VARIANT_STARTSWITH = "variant";
  private static final String IMAGE_EXT = ".png";
  private static final String TEXT_EXT = ".txt";

  private String dir;

  private final IShaderSet shaderSet;

  public LocalShaderSetExperiement(String dir, IShaderSet shaderSet) {
    this.dir = dir;
    this.shaderSet = shaderSet;
  }

  @Override
  public File getReferenceImage() {
    File res = new File(dir, REFERENCE_IMAGE_FILENAME);
    LOGGER.info("Looking for reference image: " + res);
    return res.isFile() ? res : null;
  }

  @Override
  public File getReferenceTextFile() {
    File res = new File(dir, REFERENCE_TEXT_FILE_FILENAME);
    LOGGER.info("Looking for reference text file: " + res);
    return res.isFile() ? res : null;
  }

  @Override
  public Stream<File> getVariantImages() {
    return Arrays.stream(new File(dir).listFiles())
        .filter(file ->
            file.isFile()
                && file.getName().endsWith(IMAGE_EXT)
                && file.getName().startsWith(VARIANT_STARTSWITH));
  }

  @Override
  public Stream<File> getVariantTextFiles() {
    return Arrays.stream(new File(dir).listFiles())
        .filter(file ->
            file.isFile()
                && file.getName().endsWith(TEXT_EXT)
                && file.getName().startsWith(VARIANT_STARTSWITH));
  }

  @Override
  public IShaderSet getShaderSet() {
    return shaderSet;
  }

  @Override
  public String toString() {
    return dir.toString();
  }
}
