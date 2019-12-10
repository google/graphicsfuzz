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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Collections;
import java.util.List;

public class SwitchToLoopReductionOpportunities extends
    ReductionOpportunitiesBase<SwitchToLoopReductionOpportunity> {

  /**
   * Find all switch-to-loop opportunities for the given translation unit.
   *
   * @param shaderJob The shader job to be searched.
   * @param context Includes info such as whether we reduce everywhere
   * @return The opportunities that can be reduced
   */
  static List<SwitchToLoopReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Collections.emptyList(), ListConcat::concatenate);
  }

  private static List<SwitchToLoopReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    final SwitchToLoopReductionOpportunities finder =
        new SwitchToLoopReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  private SwitchToLoopReductionOpportunities(TranslationUnit tu, ReducerContext context) {
    super(tu, context);
  }

  @Override
  public void visitSwitchStmt(SwitchStmt switchStmt) {
    super.visitSwitchStmt(switchStmt);
    if (context.reduceEverywhere() || injectionTracker.enclosedByDeadCodeInjection()) {
      addOpportunity(new SwitchToLoopReductionOpportunity(getVistitationDepth(),
          parentMap.getParent(switchStmt),
          switchStmt));
    }
  }

}
