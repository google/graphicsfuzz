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

package com.graphicsfuzz.tester;

import com.graphicsfuzz.util.ToolPaths;
import java.io.File;
import java.nio.file.Paths;

public final class TestShadersDirectory {

  private TestShadersDirectory() {
    // Utility class
  }

  public static String getTestShadersDirectory() {
    File jarDir = ToolPaths.getJarDirectory();
    if (ToolPaths.isRunningFromIde(jarDir)) {
      return Paths.get(
            ToolPaths.getSourceRoot(jarDir),
            "tester",
            "src",
            "main",
            "glsl").toString();
    }

    return Paths.get(ToolPaths.getInstallDirectory(), "test-shaders").toString();
  }

}
