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

package com.graphicsfuzz.util;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ToolPaths {

  public static String glslangValidator() {
    return Paths.get(getBinDir(), "glslangValidator").toString();
  }

  public static String shaderTranslator() {
    return Paths.get(getBinDir(), "shader_translator").toString();
  }

  public static String getImageGlfw() {
    return Paths.get(ToolPaths.getBinDir(), "get_image_glfw").toString();
  }

  public static String getImageEglSwiftshader() {
    return Paths.get(ToolPaths.getBinDir(), "swiftshader", "get_image_egl").toString();
  }

  public static String getPythonDir() {
    return Paths.get(ToolPaths.getInstallDirectory(), "python").toString();
  }

  public static String getPythonDriversDir() {
    return Paths.get(getPythonDir(), "drivers").toString();
  }

  public static String getStaticDir() {
    return Paths.get(ToolPaths.getInstallDirectory(), "server-static").toString();
  }

  public static String getBinDir() {
    String osName = System.getProperty("os.name").split(" ")[0];
    File jarDir = getJarDirectory();
    if (isRunningFromIde(jarDir)) {
      return Paths.get(
            getSourceRoot(jarDir),
            "assembly-binaries",
            "target",
            "assembly-binaries-1.0",
            "bin",
            osName).toString();
    }
    return Paths.get(getInstallDirectory(), "bin", osName).toString();
  }

  public static String getSourceRoot(File jarDir) {
    if (!isRunningFromIde(jarDir)) {
      throw new IllegalStateException();
    }
    return jarDir.getParentFile().getParentFile().toString();
  }

  public static File getJarDirectory() {
    try {
      File file = new File(ToolPaths.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI()).getAbsoluteFile().getParentFile();
      return file;
    } catch (URISyntaxException exception) {
      throw new RuntimeException(exception);
    }
  }

  public static boolean isRunningFromIde(File jarDir) {
    return jarDir.getName().equals("target");
  }

  public static String getInstallDirectory() {
    File jarDir = getJarDirectory();

    if (isRunningFromIde(jarDir)) {
      return Paths.get(
          getSourceRoot(jarDir),
          "graphicsfuzz",
          "target",
          "graphicsfuzz-1.0").toString();
    }

    return jarDir.getParentFile().toString();
  }

}
