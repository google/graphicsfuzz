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

import com.graphicsfuzz.alphanumcomparator.AlphanumComparator;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LocalShaderSet implements IShaderSet {

  private final File shaderSetDirectory;

  public LocalShaderSet(File shaderSetDirectory) {
    this.shaderSetDirectory = shaderSetDirectory;
  }

  @Override
  public List<File> getVariants() {
    File[] variants =
        shaderSetDirectory.listFiles(pathname -> pathname.isFile()
            && pathname.getName().startsWith("variant")
            && pathname.getName().endsWith(".frag"));

    Arrays.sort(variants, (o1, o2) -> new AlphanumComparator().compare(o1.getName(), o2.getName()));

    return new ArrayList<>(Arrays.asList(variants));
  }

  @Override
  public File getReference() {
    File[] referenceFiles = shaderSetDirectory
        .listFiles(pathname -> pathname.isFile() && pathname.getName()
            .equals(REFERENCE_FILENAME));
    assert referenceFiles.length <= 1;
    if (referenceFiles.length == 0) {
      throw new RuntimeException(
          "Failed to find reference shader in shader set: " + shaderSetDirectory);
    }
    return referenceFiles[0];
  }

  @Override
  public String getName() {
    return shaderSetDirectory.getName();
  }

  @Override
  public String toString() {
    return getName();
  }

}
