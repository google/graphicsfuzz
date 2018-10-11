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
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

public final class ReductionProgressHelper {

  private ReductionProgressHelper() {
    // Utility class
  }

  static int compareReductionTemporaryFiles(String first, String second,
        String shaderJobShortName) {
    return getReductionStepFromFile(first, shaderJobShortName)
          .compareTo(getReductionStepFromFile(second, shaderJobShortName));
  }

  static boolean isAReductionStepFile(String name, String shaderJobShortName,
                                      boolean restrictToSuccess) {
    if (!name.startsWith(shaderJobShortName)) {
      return false;
    }
    if (!name.contains("reduced")) {
      return false;
    }
    if (!name.endsWith("success.json")) {
      if (restrictToSuccess) {
        return false;
      }
      if (!name.endsWith("fail.json")) {
        return false;
      }
    }
    final String[] components = getNameComponents(name);
    return components.length >= 2 && StringUtils.isNumeric(components[components.length - 2]);
  }

  private static Integer getReductionStepFromFile(String name, String shaderJobShortName) {
    assert isAReductionStepFile(name, shaderJobShortName, false);
    final String[] components = getNameComponents(name);
    return new Integer(components[components.length - 2]);
  }

  private static String[] getNameComponents(String name) {
    return FilenameUtils.removeExtension(name).split("_");
  }

  public static Optional<Integer> getLatestReductionStep(
      boolean restrictToSuccess,
      File workDir,
      String shaderJobShortName,
      ShaderJobFileOperations fileOps) throws IOException {

    File[] jsonFiles =
        fileOps
            .listShaderJobFiles(
                workDir,
                (dir, name) -> isAReductionStepFile(name, shaderJobShortName, restrictToSuccess));

    return Arrays.stream(jsonFiles)
          .map(File::getName)
          .max((item1, item2) -> compareReductionTemporaryFiles(item1, item2, shaderJobShortName))
          .flatMap(item -> Optional.of(getReductionStepFromFile(item, shaderJobShortName)));
  }

  public static Optional<Integer> getLatestReductionStepAny(File workDir,
        String shaderJobShortName, ShaderJobFileOperations fileOps) throws IOException {
    return getLatestReductionStep(false, workDir, shaderJobShortName, fileOps);
  }

  public static Optional<Integer> getLatestReductionStepSuccess(File workDir,
        String shaderJobShortName, ShaderJobFileOperations fileOps) throws IOException {
    return getLatestReductionStep(true, workDir, shaderJobShortName, fileOps);
  }

  public static File getReductionExceptionFile(File workDir, String shaderJobShortName) {
    return new File(workDir, shaderJobShortName + ".exception");
  }

}
