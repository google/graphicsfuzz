/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class GlobalPrecisionDeclarationReductionOpportunities {

  private static List<GlobalPrecisionDeclarationReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    final List<GlobalPrecisionDeclarationReductionOpportunity> opportunities = new ArrayList<>();
    List<Declaration> globalDeclarations = tu.getTopLevelDeclarations();
    final HashMap<String, Scope> precisionMap = new HashMap<String, Scope>();

    for (Declaration precisionDeclaration : globalDeclarations) {
      if (precisionDeclaration instanceof PrecisionDeclaration) {
        // Create signature from the precision declaration, skipping the precision (highp etc)
        final String[] precisionTokens = precisionDeclaration.getText().split(" ");
        String signature = new String();
        for (int i = 0; i < precisionTokens.length; i++) {
          if (i != 1) {
            if (i != 0) {
              signature += " ";
            }
            signature += precisionTokens[i];
          }
        }
        if (precisionMap.containsKey(signature)) {
          opportunities.add(new GlobalPrecisionDeclarationReductionOpportunity(
              tu,
              (PrecisionDeclaration) precisionDeclaration,
              new VisitationDepth(0)));
        } else {
          precisionMap.put(signature, null);
        }
      } else {
        // If non-precision declaration, clear our map
        precisionMap.clear();
      }
    }

    return opportunities;
  }

  static List<GlobalPrecisionDeclarationReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

}
