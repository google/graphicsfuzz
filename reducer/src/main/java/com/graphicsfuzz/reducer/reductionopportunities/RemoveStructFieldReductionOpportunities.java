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
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.util.Constants;
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
        ReducerContext context) {
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
          .getWithoutQualifiers() instanceof StructNameType)) {
      return;
    }
    getOpportunitiesForStruct((StructNameType) declarationStmt.getVariablesDeclaration()
          .getBaseType().getWithoutQualifiers(), getVistitationDepth());
  }

  private void getOpportunitiesForStruct(StructNameType structType,
                                         VisitationDepth visitationDepth) {

    if (!(structType.getName().startsWith(Constants
          .STRUCTIFICATION_STRUCT_PREFIX))) {
      return;
    }

    final StructDefinitionType structDefinitionType =
        currentScope.lookupStructName(structType.getName());

    for (String field : structDefinitionType.getFieldNames()) {
      if (!reachesOriginalVariable(structDefinitionType, field)
          && structDefinitionType.getNumFields() > 1) {
        final RemoveStructFieldReductionOpportunity op =
              new RemoveStructFieldReductionOpportunity(
                  structDefinitionType, field, translationUnit, visitationDepth);
        if (op.preconditionHolds()) {
          opportunities.add(op);
        }
      }
      if (structDefinitionType.getFieldType(field).getWithoutQualifiers()
          instanceof StructNameType) {
        getOpportunitiesForStruct(
              (StructNameType) structDefinitionType.getFieldType(field).getWithoutQualifiers(),
              visitationDepth.deeper());
      }
    }
  }

  private boolean reachesOriginalVariable(StructDefinitionType structDefinitionType,
                                          String field) {
    if (!(field.startsWith(Constants.STRUCTIFICATION_FIELD_PREFIX))) {
      return true;
    }
    if (!(structDefinitionType.getFieldType(field).getWithoutQualifiers()
        instanceof StructNameType)) {
      return false;
    }
    final StructNameType fieldType =
        (StructNameType) structDefinitionType.getFieldType(field).getWithoutQualifiers();
    final StructDefinitionType nestedStruct =
        currentScope.lookupStructName(fieldType.getName());
    return nestedStruct.getFieldNames().stream()
          .anyMatch(item -> reachesOriginalVariable(nestedStruct, item));
  }

}
