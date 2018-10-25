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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.transformreduce.MergeSet;
import com.graphicsfuzz.common.transformreduce.MergedVariablesComponentData;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.List;

public class VectorizationReductionOpportunities
      extends ReductionOpportunitiesBase<VectorizationReductionOpportunity> {

  private final TranslationUnit tu;
  private VariablesDeclaration enclosingVariablesDeclaration;

  private VectorizationReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
    this.tu = tu;
    this.enclosingVariablesDeclaration = null;
  }

  /**
   * Find all vectorization opportunities for the given translation unit.
   *
   * @param shaderJob The shader job to be searched.
   * @param context Includes info such as whether we reduce everywhere or only reduce injections
   * @return The opportunities that can be reduced
   */
  static List<VectorizationReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<VectorizationReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    VectorizationReductionOpportunities finder =
          new VectorizationReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    assert enclosingVariablesDeclaration == null;
    enclosingVariablesDeclaration = variablesDeclaration;
    super.visitVariablesDeclaration(variablesDeclaration);
    assert enclosingVariablesDeclaration != null;
    enclosingVariablesDeclaration = null;
  }

  @Override
  public void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo) {
    super.visitVariableDeclInfo(variableDeclInfo);
    String name = variableDeclInfo.getName();
    if (MergeSet.isMergedVariable(name)) {
      List<MergedVariablesComponentData> componentsData = MergeSet.getComponentData(name);
      assert inBlock();
      for (MergedVariablesComponentData data : componentsData) {
        assert enclosingVariablesDeclaration != null;
        final VectorizationReductionOpportunity potentialOpportunity
              = new VectorizationReductionOpportunity(
              tu,
              currentBlock(),
              enclosingVariablesDeclaration,
              variableDeclInfo,
              data,
              parentMap,
              getVistitationDepth());
        if (potentialOpportunity.preconditionHolds()) {
          addOpportunity(potentialOpportunity);
        }
      }
    }
  }

}
