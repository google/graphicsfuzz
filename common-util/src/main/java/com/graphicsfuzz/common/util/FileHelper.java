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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;

public class FileHelper {

  public static File fileWithAppendedString(File file, String extra) {
    String ext = FilenameUtils.getExtension(file.toString());
    String basename = FilenameUtils.removeExtension(file.toString());
    return new File(basename + extra + "." + ext);
  }

  public static String firstLine(File file) throws IOException {
    try (BufferedReader r = new BufferedReader(new FileReader(file));) {
      return r.readLine();
    }
  }

  public static File replaceExtension(File file, String ext) {
    return new File(FilenameUtils.removeExtension(file.toString()) + ext);
  }

  public static File replaceDir(File file, File dir) {
    return new File(dir, file.getName());
  }

  public static void checkExists(File file) throws FileNotFoundException {
    if (!file.exists()) {
      throw new FileNotFoundException("Could not find: " + file);
    }
  }

  public static void checkExistsOrNull(File file) throws FileNotFoundException {
    if (file != null) {
      checkExists(file);
    }
  }

}
