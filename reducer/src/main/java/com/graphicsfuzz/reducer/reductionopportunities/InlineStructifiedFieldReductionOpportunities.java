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
import com.graphicsfuzz.common.ast.decl.StructDeclaration;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.type.NamedStructType;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class InlineStructifiedFieldReductionOpportunities extends ScopeTreeBuilder {

  private final List<InlineStructifiedFieldReductionOpportunity> opportunities;
  private final TranslationUnit tu;

  private InlineStructifiedFieldReductionOpportunities(TranslationUnit tu) {
    this.opportunities = new LinkedList<>();
    this.tu = tu;
  }

  static List<InlineStructifiedFieldReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReductionOpportunityContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<InlineStructifiedFieldReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu) {
    InlineStructifiedFieldReductionOpportunities finder =
          new InlineStructifiedFieldReductionOpportunities(tu);
    finder.visit(tu);
    return finder.opportunities;
  }

  @Override
  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    super.visitDeclarationStmt(declarationStmt);
    if (!Util.isStructifiedDeclaration(declarationStmt)) {
      return;
    }
    final NamedStructType structType =
        (NamedStructType) declarationStmt.getVariablesDeclaration().getBaseType()
        .getWithoutQualifiers();
    findInliningOpportunities(structType);

  }

  public void findInliningOpportunities(NamedStructType structType) {
    assert structType.getName().startsWith(Constants.STRUCTIFICATION_STRUCT_PREFIX);
    final StructDeclaration structDeclaration = structDeclarations.get(structType);
    for (String f : structDeclaration.getFieldNames()) {
      if (!f.startsWith(Constants.STRUCTIFICATION_FIELD_PREFIX)) {
        continue;
      }
      if (structDeclaration.getFieldType(f).getWithoutQualifiers()
          instanceof NamedStructType) {
        final NamedStructType innerStructType =
            (NamedStructType) structDeclaration.getFieldType(f).getWithoutQualifiers();
        opportunities.add(new InlineStructifiedFieldReductionOpportunity(
              structDeclaration, structDeclarations.get(innerStructType), f, tu,
            getVistitationDepth()));
        findInliningOpportunities(innerStructType);
      }
    }
  }

}
