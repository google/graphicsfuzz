/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.semanticschanging.astfuzzer.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public abstract class AstFuzzer {

  private final InterchangeablesGroupedBySignature functionsBySignature;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final IRandom random;

  /**
   * Constructor.
   *
   * @param shadingLanguageVersion The version of glsl you want to generate for.
   * @param random Used to generate fuzzed in a random manner.
   */
  public AstFuzzer(ShadingLanguageVersion shadingLanguageVersion,
        IRandom random) {
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.functionsBySignature = new InterchangeablesGroupedBySignature(shadingLanguageVersion);
    this.random = random;
  }

  IRandom getRandom() {
    return random;
  }

  ShadingLanguageVersion getShadingLanguageVersion() {
    return shadingLanguageVersion;
  }

  InterchangeablesGroupedBySignature getFunctionLists() {
    return functionsBySignature;
  }

  /**
   * Generates "numberOfVariants" TranslationUnit variations based on the initial TranslationUnit.
   *
   * @param tu The TranslationUnit representation of the shader to be modified
   */
  public final List<TranslationUnit> generateShaderVariations(TranslationUnit tu,
        int numberOfVariants)
        throws IOException, ParseTimeoutException {

    List<TranslationUnit> result = new ArrayList<>();

    for (int i = 0; i < numberOfVariants; i++) {
      result.add(generateNewShader(tu));
    }
    return result;
  }

  public abstract TranslationUnit generateNewShader(TranslationUnit tu);
}
