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

package com.graphicsfuzz.generator.transformation.controlflow;

import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public abstract class AddOutputVariableWrites implements ITransformation {

  Pair<String, Type> chooseOutputVariable(IInjectionPoint injectionPoint, IRandom generator,
      ShadingLanguageVersion shadingLanguageVersion, GenerationParams generationParams) {
    final Map<String, Type> availableOutVars =
        getAvailableOutVars(injectionPoint.scopeAtInjectionPoint(),
            shadingLanguageVersion, generationParams);
    assert !availableOutVars.isEmpty();

    final List<String> keys = availableOutVars.keySet().stream().collect(Collectors.toList());
    keys.sort(String::compareTo);
    final int index = generator.nextInt(keys.size());

    final String name = keys.get(index);
    final Type type = availableOutVars.get(name);
    return new ImmutablePair<>(name, type);
  }

  boolean hasAvailableOutVars(IInjectionPoint injectionPoint,
      ShadingLanguageVersion shadingLanguageVersion, GenerationParams generationParams) {
    return !getAvailableOutVars(injectionPoint.scopeAtInjectionPoint(),
        shadingLanguageVersion, generationParams).isEmpty();
  }

  private Map<String,Type> getAvailableOutVars(Scope scope,
      ShadingLanguageVersion shadingLanguageVersion, GenerationParams generationParams) {
    final Map<String, Type> result = new HashMap<>();
    if (generationParams.getShaderKind() == ShaderKind.FRAGMENT) {
      if (shadingLanguageVersion.supportedGlFragColor()) {
        result.put(OpenGlConstants.GL_FRAG_COLOR, BasicType.VEC4);
      }
    }
    if (generationParams.getShaderKind() == ShaderKind.VERTEX) {
      result.put(OpenGlConstants.GL_POSITION, BasicType.VEC4);
      result.put(OpenGlConstants.GL_POINT_SIZE, BasicType.FLOAT);
    }
    for (String name : scope.namesOfAllVariablesInScope()) {
      final Type type = scope.lookupType(name);
      if (type instanceof QualifiedType && ((QualifiedType) type)
          .hasQualifier(TypeQualifier.SHADER_OUTPUT)) {
        result.put(name, type.getWithoutQualifiers());
      }
    }
    return result;
  }

}
