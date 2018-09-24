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

package com.graphicsfuzz.generator.transformation.mutator;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.transformation.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.util.GenerationParams;

public final class MutationPoint implements IMutationPoint {

  private final Expr parent;
  private final int indexOfChildToMutate;
  private final Type type;
  private final Scope scope;

  // indicates that the mutation point is in a context where only const data is allowed
  private final boolean constContext;

  private final ShadingLanguageVersion shadingLanguageVersion;
  private final IRandom generator;
  private final GenerationParams generationParams;

  public MutationPoint(Expr parent, int indexOfChildToMutate, Type type,
        Scope scope, boolean constContext, ShadingLanguageVersion shadingLanguageVersion,
        IRandom generator,
        GenerationParams generationParams) {
    this.parent = parent;
    this.indexOfChildToMutate = indexOfChildToMutate;
    this.type = type;
    this.scope = scope;
    this.constContext = constContext;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.generator = generator;
    this.generationParams = generationParams;
  }

  @Override
  public final void applyMutation() {
    Type typeToMutate =
          (type instanceof QualifiedType) ? ((QualifiedType) type).getTargetType() : type;
    if (BasicType.allScalarTypes().contains(typeToMutate) || BasicType.allVectorTypes()
          .contains(typeToMutate)) {
      // For now, restrict mutation to scalar and vector types.
      // TODO: add support for mutation of matrix types
      parent.setChild(indexOfChildToMutate,
            new OpaqueExpressionGenerator(generator, generationParams, shadingLanguageVersion)
                  .applyIdentityFunction(
                  parent.getChild(indexOfChildToMutate), (BasicType) typeToMutate, constContext,
                  0,
                  new Fuzzer(new FuzzingContext(scope), shadingLanguageVersion, generator,
                      generationParams)));
    }
  }

}
