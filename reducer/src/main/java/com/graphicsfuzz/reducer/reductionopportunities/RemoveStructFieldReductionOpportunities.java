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
import com.graphicsfuzz.common.ast.type.StructType;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
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
        TranslationUnit tu,
        ReductionOpportunityContext context) {
    RemoveStructFieldReductionOpportunities finder =
          new RemoveStructFieldReductionOpportunities(tu);
    return finder.opportunities;
  }

  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    super.visitDeclarationStmt(declarationStmt);
    if (!(declarationStmt.getVariablesDeclaration().getBaseType()
          .getWithoutQualifiers() instanceof StructType)) {
      return;
    }
    getOpportunitiesForStruct((StructType) declarationStmt.getVariablesDeclaration()
          .getBaseType().getWithoutQualifiers(), getVistitationDepth());
  }

  private void getOpportunitiesForStruct(StructType structType, VisitationDepth visitationDepth) {

    if (!(structType.getName().startsWith(Constants
          .STRUCTIFICATION_STRUCT_PREFIX))) {
      return;
    }

    for (String field : structType.getFieldNames()) {
      if (!reachesOriginalVariable(structType, field) && structType.getNumFields() > 1) {
        final RemoveStructFieldReductionOpportunity op =
              new RemoveStructFieldReductionOpportunity(
                    structType, field, translationUnit, visitationDepth);
        if (op.preconditionHolds()) {
          opportunities.add(op);
        }
      }
      if (structType.getFieldType(field).getWithoutQualifiers() instanceof StructType) {
        getOpportunitiesForStruct(
              (StructType) structType.getFieldType(field).getWithoutQualifiers(),
              visitationDepth.deeper());
      }
    }
  }

  private static boolean reachesOriginalVariable(StructType structType, String field) {
    if (!(field.startsWith(Constants.STRUCTIFICATION_FIELD_PREFIX))) {
      return true;
    }
    if (!(structType.getFieldType(field).getWithoutQualifiers() instanceof StructType)) {
      return false;
    }
    StructType nestedStruct = (StructType) structType.getFieldType(field).getWithoutQualifiers();
    return nestedStruct.getFieldNames().stream()
          .anyMatch(item -> reachesOriginalVariable(nestedStruct, item));
  }

}
