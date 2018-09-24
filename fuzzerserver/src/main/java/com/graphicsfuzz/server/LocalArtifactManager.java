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

package com.graphicsfuzz.server;

import com.graphicsfuzz.common.util.IShaderSet;
import com.graphicsfuzz.common.util.IShaderSetExperiment;
import com.graphicsfuzz.common.util.LocalShaderSet;
import com.graphicsfuzz.common.util.LocalShaderSetExperiement;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

public class LocalArtifactManager implements IArtifactManager {

  private final String shaderSetsDir;
  private final String processingDir;

  public LocalArtifactManager(String shaderSetsDir, String processingDir) {
    this.shaderSetsDir = shaderSetsDir;
    this.processingDir = processingDir;
  }

  @Override
  public Stream<IShaderSet> getShaderSets() {

    if (!new File(shaderSetsDir).isDirectory()) {
      return Stream.empty();
    }

    return Arrays.stream(Paths.get(shaderSetsDir).toFile()
        .listFiles(File::isDirectory))
        .sorted((o1, o2) -> o1.getName().compareTo(o2.getName()))
        .map(file ->
            new LocalShaderSet(Paths.get(shaderSetsDir, file.getName()).toFile()));
  }

  @Override
  public IShaderSet getShaderSet(String name) {
    return new LocalShaderSet(Paths.get(shaderSetsDir, name).toFile());
  }

  @Override
  public IShaderSetExperiment getShaderSetExperiment(IShaderSet shaderSet, String token) {
    return new LocalShaderSetExperiement(
        Paths.get(
            processingDir,
            token,
            shaderSet.getName() + "_exp"
        ).toString(),
        shaderSet
    );
  }

  @Override
  public Stream<IShaderSetExperiment> getShaderSetExperiments(String token) {
    return Arrays.stream(
        new File(processingDir, token).listFiles(f -> f.getName().endsWith("_exp")))
        .map(f -> {
          String name = f.getName();
          name = name.substring(0, name.length() - 4);
          return new LocalShaderSetExperiement(f.toString(), getShaderSet(name));
        });
  }

}
