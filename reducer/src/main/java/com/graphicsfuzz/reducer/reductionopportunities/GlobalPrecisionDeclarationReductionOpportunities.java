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
import com.graphicsfuzz.common.util.ListConcat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GlobalPrecisionDeclarationReductionOpportunities {

  private static List<GlobalPrecisionDeclarationReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    final List<GlobalPrecisionDeclarationReductionOpportunity> opportunities = new ArrayList<>();
    final List<Declaration> globalDeclarations = tu.getTopLevelDeclarations();
    final Set<String> precisionSet = new HashSet<String>();
    // Scan declarations from end to beginning so we leave the last ones alone.
    for (int i = globalDeclarations.size() - 1; i >= 0; i--) {
      if (globalDeclarations.get(i) instanceof PrecisionDeclaration) {
        final PrecisionDeclaration precisionDeclaration =
            (PrecisionDeclaration) globalDeclarations.get(i);
        // TODO(https://github.com/google/graphicsfuzz/issues/972): Rework this when precision
        //  declarations are handled properly in the AST.
        //  In the meantime, assume that "precision QUALIFIER TYPE" appears in a single line, so
        //  that TYPE is the 3rd token when we split the declaration's text on spaces.
        final String type = precisionDeclaration.getText().split(" ")[2];
        if (precisionSet.contains(type)) {
          opportunities.add(new GlobalPrecisionDeclarationReductionOpportunity(
              tu,
              precisionDeclaration,
              new VisitationDepth(0)));
        } else {
          precisionSet.add(type);
        }
      } else {
        // This is a non-precision declaration, and might depend on earlier precision declarations.
        // Thus clear our knowledge of precision declarations.
        precisionSet.clear();
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
