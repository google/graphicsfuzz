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
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

public final class ReductionStepHelper {

  private ReductionStepHelper() {
    // Utility class
  }

  static int compareReductionTemporaryFiles(String first, String second,
        String shaderPrefix) {
    return getReductionStepFromFile(first, shaderPrefix)
          .compareTo(getReductionStepFromFile(second, shaderPrefix));
  }

  static int compareReductionTemporaryFiles(File first, File second, String shaderPrefix) {
    return compareReductionTemporaryFiles(first.getName(), second.getName(), shaderPrefix);
  }

  static boolean isAReductionStepFile(String name, String shaderPrefix, boolean restrictToSuccess) {
    if (!name.startsWith(shaderPrefix)) {
      return false;
    }
    if (!name.contains("reduced")) {
      return false;
    }
    if (!name.endsWith("success.frag")) {
      if (restrictToSuccess) {
        return false;
      }
      if (!name.endsWith("fail.frag")) {
        return false;
      }
    }
    final String[] components = getNameComponents(name);
    return components.length >= 2 && StringUtils.isNumeric(components[components.length - 2]);
  }

  private static Integer getReductionStepFromFile(String name, String shaderPrefix) {
    assert isAReductionStepFile(name, shaderPrefix, false);
    final String[] components = getNameComponents(name);
    return new Integer(components[components.length - 2]);
  }

  private static String[] getNameComponents(String name) {
    return FilenameUtils.removeExtension(name).split("_");
  }

  public static Optional<Integer> getLatestReductionStep(boolean restrictToSuccess,
        File reductionDir,
        String shaderPrefix) {
    final File[] fragmentShaders = reductionDir
          .listFiles((dir, name) -> isAReductionStepFile(name, shaderPrefix, restrictToSuccess));
    return Arrays.stream(fragmentShaders)
          .map(item -> item.getName())
          .max((item1, item2) -> compareReductionTemporaryFiles(item1, item2, shaderPrefix))
          .flatMap(item -> Optional.of(getReductionStepFromFile(item, shaderPrefix)));
  }

  public static Optional<Integer> getLatestReductionStepAny(File reductionDir,
        String shaderPrefix) {
    return getLatestReductionStep(false, reductionDir, shaderPrefix);
  }

  public static Optional<Integer> getLatestReductionStepSuccess(File reductionDir,
        String shaderPrefix) {
    return getLatestReductionStep(true, reductionDir, shaderPrefix);
  }

}
