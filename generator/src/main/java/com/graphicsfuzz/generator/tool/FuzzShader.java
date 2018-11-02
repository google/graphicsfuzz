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

package com.graphicsfuzz.generator.tool;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.util.GenerationParams;

public class FuzzShader {

  public static void main(String[] args) {

    if (args.length != 1) {
      System.err.print("Usage: seed");
      System.exit(1);
    }

    new PrettyPrinterVisitor(System.out)
          .visit(new Fuzzer(new FuzzingContext(),
                ShadingLanguageVersion.GLSL_440,
                new RandomWrapper(Integer.parseInt(args[0])),
                GenerationParams.normal(ShaderKind.FRAGMENT, true)).fuzzTranslationUnit());

  }

}
