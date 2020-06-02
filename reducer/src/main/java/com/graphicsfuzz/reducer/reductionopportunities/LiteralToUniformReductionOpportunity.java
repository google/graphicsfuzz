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

import com.graphicsfuzz.common.ast.ChildDoesNotExistException;
import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.BindingLayoutQualifier;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.SetLayoutQualifier;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LiteralToUniformReductionOpportunity
    extends AbstractReductionOpportunity {

  private final Expr literalExpr;
  private final PipelineInfo pipelineInfo;
  private final TranslationUnit translationUnit;
  private final int index;

  LiteralToUniformReductionOpportunity(TranslationUnit translationUnit,
                                       Expr literalExpr,
                                       int index,
                                       PipelineInfo pipelineInfo,
                                       VisitationDepth depth) {
    super(depth);

    this.index = index;
    this.literalExpr = literalExpr;
    this.pipelineInfo = pipelineInfo;
    this.translationUnit = translationUnit;
  }

  public Expr getLiteral() {
    return this.literalExpr;
  }

  public int getIndex() {
    return this.index;
  }

  @Override
  void applyReductionImpl() {

    IParentMap parentMap = IParentMap.createParentMap(translationUnit);

    ArrayIndexExpr aie = new ArrayIndexExpr(new VariableIdentifierExpr("values"),
        new IntConstantExpr(String.valueOf(this.index)));

    parentMap.getParent(this.literalExpr).replaceChild(this.literalExpr,
        aie);
  }

  @Override
  public boolean preconditionHolds() {
    return true;
  }

}
