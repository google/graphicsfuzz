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
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RemoveStructFieldReductionOpportunities extends ScopeTreeBuilder {

  private final List<RemoveStructFieldReductionOpportunity> opportunities;
  private final TranslationUnit translationUnit;

  private RemoveStructFieldReductionOpportunities(TranslationUnit translationUnit) {
    this.opportunities = new LinkedList<>();
    this.translationUnit = translationUnit;
    visit(translationUnit);
  }

  static List<RemoveStructFieldReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReductionOpportunityContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<RemoveStructFieldReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu) {
    RemoveStructFieldReductionOpportunities finder =
          new RemoveStructFieldReductionOpportunities(tu);
    return finder.opportunities;
  }

  @Override
  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    super.visitDeclarationStmt(declarationStmt);
    if (!(declarationStmt.getVariablesDeclaration().getBaseType()
          .getWithoutQualifiers() instanceof NamedStructType)) {
      return;
    }
    getOpportunitiesForStruct((NamedStructType) declarationStmt.getVariablesDeclaration()
          .getBaseType().getWithoutQualifiers(), getVistitationDepth());
  }

  private void getOpportunitiesForStruct(NamedStructType structType,
                                         VisitationDepth visitationDepth) {

    if (!(structType.getName().startsWith(Constants
          .STRUCTIFICATION_STRUCT_PREFIX))) {
      return;
    }

    final StructDeclaration structDeclaration = structDeclarations.get(structType);

    for (String field : structDeclaration.getFieldNames()) {
      if (!reachesOriginalVariable(structDeclaration, field)
          && structDeclaration.getNumFields() > 1) {
        final RemoveStructFieldReductionOpportunity op =
              new RemoveStructFieldReductionOpportunity(
                    structDeclaration, field, translationUnit, visitationDepth);
        if (op.preconditionHolds()) {
          opportunities.add(op);
        }
      }
      if (structDeclaration.getFieldType(field).getWithoutQualifiers()
          instanceof NamedStructType) {
        getOpportunitiesForStruct(
              (NamedStructType) structDeclaration.getFieldType(field).getWithoutQualifiers(),
              visitationDepth.deeper());
      }
    }
  }

  private boolean reachesOriginalVariable(StructDeclaration structDeclaration,
                                                 String field) {
    if (!(field.startsWith(Constants.STRUCTIFICATION_FIELD_PREFIX))) {
      return true;
    }
    if (!(structDeclaration.getFieldType(field).getWithoutQualifiers()
        instanceof NamedStructType)) {
      return false;
    }
    final NamedStructType fieldType =
        (NamedStructType) structDeclaration.getFieldType(field).getWithoutQualifiers();
    final StructDeclaration nestedStruct =
        structDeclarations.get(fieldType);
    return nestedStruct.getFieldNames().stream()
          .anyMatch(item -> reachesOriginalVariable(nestedStruct, item));
  }

}
