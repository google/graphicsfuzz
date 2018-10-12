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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

/**
 * This class captures the opportunity to a field of a struct that was created by the
 * structification transformation.  It does not cater for removal of arbitrary fields of
 * arbitrary structs.
 */
public class RemoveStructFieldReductionOpportunity extends AbstractReductionOpportunity {

  private final StructDefinitionType targetStruct;
  private final String fieldToRemove;
  private final IAstNode subtreeInWhichStructIsUsed;

  public RemoveStructFieldReductionOpportunity(StructDefinitionType targetStruct,
                                               String fieldToRemove,
                                               IAstNode subtreeInWhichStructIsUsed,
                                               VisitationDepth depth) {
    super(depth);
    this.targetStruct = targetStruct;
    this.fieldToRemove = fieldToRemove;
    this.subtreeInWhichStructIsUsed = subtreeInWhichStructIsUsed;
  }

  @Override
  public void applyReductionImpl() {
    final int index = targetStruct.getFieldIndex(fieldToRemove);
    targetStruct.removeField(fieldToRemove);
    new StandardVisitor() {
      @Override
      public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
        super.visitTypeConstructorExpr(typeConstructorExpr);
        if (!typeConstructorExpr.getTypename().equals(
            targetStruct.getStructNameType().getName())) {
          return;
        }
        // This is the target struct, so remove the appropriate constructor component
        typeConstructorExpr.removeArg(index);
      }
    }.visit(subtreeInWhichStructIsUsed);

  }

  public String getFieldToRemove() {
    return fieldToRemove;
  }

  @Override
  public boolean preconditionHolds() {
    return targetStruct.getNumFields() > 1 && targetStruct.hasField(fieldToRemove);
  }
}
