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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;

public class BreakIntLiterals implements ITransformation {

  @Override
  public void apply(TranslationUnit tu, TransformationProbabilities probabilities,
        ShadingLanguageVersion shadingLanguageVersion, IRandom generator,
      GenerationParams generationParams) {


    // For int literal x, we can write x as:
    // a + b
    // a - b; // be careful that a is not chosen to be too large
    // a / b; // be careful that a is not chosen to be too large
    // a * b;
    // a << b; - under appropriate conditions
    // a >> b; - under appropriate conditions

    // Further, for any base int literal we can decide to represent it:
    // in its original form
    // as a uniform (or computed from some uniform)
    // as the return value of a function that somehow computes the value, e.g.
    //    using loops

    throw new RuntimeException();

  }

  @Override
  public String getName() {
    return "break_int_literals";
  }
}
