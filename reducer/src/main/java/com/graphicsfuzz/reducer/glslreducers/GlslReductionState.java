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

package com.graphicsfuzz.reducer.glslreducers;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.reducer.IReductionState;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GlslReductionState implements IReductionState {

  private final Optional<TranslationUnit> fragmentShader;
  private final Optional<TranslationUnit> vertexShader;

  public GlslReductionState(Optional<TranslationUnit> fragmentShader,
      Optional<TranslationUnit> vertexShader) {
    this.fragmentShader = fragmentShader;
    this.vertexShader = vertexShader;
  }

  public GlslReductionState(Optional<TranslationUnit> fragmentShader) {
    this(fragmentShader, Optional.empty());
  }

  @Override
  public boolean hasFragmentShader() {
    return fragmentShader.isPresent();
  }

  @Override
  public boolean hasVertexShader() {
    return vertexShader.isPresent();
  }

  @Override
  public TranslationUnit getFragmentShader() {
    assert hasFragmentShader();
    return fragmentShader.get();
  }

  @Override
  public TranslationUnit getVertexShader() {
    assert hasVertexShader();
    return vertexShader.get();
  }

  @Override
  public UniformsInfo computeRemainingUniforms(File uniformsJson) throws FileNotFoundException {
    assert fragmentShader.isPresent();
    final UniformsInfo result = new UniformsInfo(
          new File(uniformsJson.getAbsolutePath()));

    Set<String> remainingUniforms = new HashSet<>();
    remainingUniforms.addAll(getUniforms(fragmentShader));
    remainingUniforms.addAll(getUniforms(vertexShader));

    for (String name : result.getUniformNames().stream()
        .filter(item -> !remainingUniforms.contains(item)).collect(
        Collectors.toList())) {
      result.removeUniform(name);
    }
    return result;
  }

  private Set<String> getUniforms(Optional<TranslationUnit> maybeTu) {
    return maybeTu.isPresent() ? maybeTu.get().getUniformDecls().stream()
            .map(item -> item.getDeclInfos())
            .reduce(new ArrayList<VariableDeclInfo>(), ListConcat::concatenate)
            .stream()
            .map(item -> item.getName()).collect(Collectors.toSet())
        : new HashSet<>();
  }

}
