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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.reducer.ReductionDriver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ReductionOpportunities {

  private ReductionOpportunities() {
    // Utility class
  }

  /**
   * Collects all the reduction opportunities for the given translation unit, with respect to a GLSL
   * version.  There is an option to reduce *everywhere*, rather than just reducing GLFuzz
   * transformations.
   *
   * @param shaderJob The shader job to be analysed for reduction opportunities
   * @param context Information to shape the opportunities that are gathered
   */
  public static List<IReductionOpportunity> getReductionOpportunities(
      ShaderJob shaderJob,
      ReducerContext context,
      ShaderJobFileOperations fileOps) {
    final List<IReductionOpportunity> opportunities = new ArrayList<>();
    for (IReductionOpportunityFinder<?> ros : Arrays.asList(
        IReductionOpportunityFinder.loopMergeFinder(),
        IReductionOpportunityFinder.removeStructFieldFinder(),
        IReductionOpportunityFinder.destructifyFinder(),
        IReductionOpportunityFinder.inlineStructFieldFinder(),
        IReductionOpportunityFinder.outlinedStatementFinder(),
        IReductionOpportunityFinder.variableDeclFinder(),
        IReductionOpportunityFinder.globalVariablesDeclarationFinder(),
        IReductionOpportunityFinder.unwrapFinder(),
        IReductionOpportunityFinder.unswitchifyFinder(),
        IReductionOpportunityFinder.vectorizationFinder(),
        IReductionOpportunityFinder.functionFinder(),
        IReductionOpportunityFinder.stmtFinder(),
        IReductionOpportunityFinder.exprToConstantFinder(),
        IReductionOpportunityFinder.compoundExprToSubExprFinder(),
        IReductionOpportunityFinder.mutationFinder(),
        IReductionOpportunityFinder.compoundToBlockFinder(),
        IReductionOpportunityFinder.inlineInitializerFinder(),
        IReductionOpportunityFinder.inlineFunctionFinder(),
        IReductionOpportunityFinder.liveFragColorWriteFinder(),
        IReductionOpportunityFinder.unusedParamFinder(),
        IReductionOpportunityFinder.foldConstantFinder(),
        IReductionOpportunityFinder.inlineUniformFinder())) {
      final List<? extends IReductionOpportunity> currentOpportunities = ros
            .findOpportunities(shaderJob, context);
      if (ReductionDriver.DEBUG_REDUCER) {
        opportunities.addAll(currentOpportunities.stream()
              .map(item -> new CheckValidReductionOpportunityDecorator(item, shaderJob,
                    context.getShadingLanguageVersion(), fileOps))
              .collect(Collectors.toList()));
      } else {
        opportunities.addAll(currentOpportunities);
      }
    }
    return opportunities;
  }

}
