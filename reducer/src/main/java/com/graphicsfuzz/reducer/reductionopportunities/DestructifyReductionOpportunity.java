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

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.util.Constants;
import java.util.Arrays;
import java.util.Optional;

public class DestructifyReductionOpportunity extends AbstractReductionOpportunity {

  private final DeclarationStmt declaration;
  private final BlockStmt block;
  private final TranslationUnit tu;

  // A quick fix to guard against mutations; we need a better solution.
  private final int originalNumVariables;

  public DestructifyReductionOpportunity(DeclarationStmt declaration,
        TranslationUnit tu,
        BlockStmt block,
        VisitationDepth depth) {
    super(depth);
    assert declaration.getVariablesDeclaration().getNumDecls() == 1;
    this.declaration = declaration;
    this.tu = tu;
    this.block = block;
    this.originalNumVariables = declaration.getVariablesDeclaration().getNumDecls();
  }

  @Override
  public void applyReductionImpl() {
    if (originalNumVariables != declaration.getVariablesDeclaration().getNumDecls()) {
      // Something else changed how many declarations there are, so bail out.
      return;
    }

    final StructifiedVariableInfo originalVariableInfo = findOriginalVariableInfo();

    // First, replace all occurrences in the translation unit
    deStructify(originalVariableInfo);

    // Now change the declaration
    declaration.getVariablesDeclaration().getDeclInfo(0).setName(originalVariableInfo.getName());
    declaration.getVariablesDeclaration().setBaseType(originalVariableInfo.getType());
    declaration.getVariablesDeclaration().getDeclInfo(0).setInitializer(originalVariableInfo
        .getInitializer().orElse(null));

  }

  private StructifiedVariableInfo findOriginalVariableInfo() {
    return findOriginalVariableInfo(
        (StructNameType) declaration.getVariablesDeclaration().getBaseType()
            .getWithoutQualifiers(),
        Optional.ofNullable((ScalarInitializer) declaration.getVariablesDeclaration()
            .getDeclInfo(0).getInitializer()))
        .get();
  }

  private Optional<StructifiedVariableInfo> findOriginalVariableInfo(
      StructNameType type,
      Optional<ScalarInitializer> initializer) {

    final StructDefinitionType structDefinitionType = tu.getStructDefinition(type);

    for (int i = 0; i < structDefinitionType.getNumFields(); i++) {

      final int currentIndex = i;
      final Optional<ScalarInitializer> componentInitializer = initializer.map(item ->
          new ScalarInitializer(((TypeConstructorExpr) item.getExpr())
              .getArg(currentIndex)));

      if (structDefinitionType.getFieldName(i).startsWith(Constants.STRUCTIFICATION_FIELD_PREFIX)) {
        // It is not the desired field, but...
        Type fieldTypeWithoutQualifiers = structDefinitionType.getFieldType(i)
            .getWithoutQualifiers();
        if (fieldTypeWithoutQualifiers instanceof StructNameType) {
          // If it is a struct field then the desired field might be in a sub-struct.
          Optional<StructifiedVariableInfo> possibleResult =
              findOriginalVariableInfo((StructNameType) fieldTypeWithoutQualifiers,
                  componentInitializer);
          if (possibleResult.isPresent()) {
            return possibleResult;
          }
        } else {
          assert fieldTypeWithoutQualifiers instanceof BasicType
              : "Expect only struct and basic types in structification; found "
              + fieldTypeWithoutQualifiers.getClass();
        }
      } else {
        // It is the desired field!
        return Optional.of(new StructifiedVariableInfo(structDefinitionType.getFieldName(i),
            structDefinitionType.getFieldType(i),
            componentInitializer));
      }
    }
    return Optional.empty();
  }

  private void deStructify(StructifiedVariableInfo originalVariableInfo) {

    final IParentMap parentMap = IParentMap.createParentMap(tu);

    new ScopeTreeBuilder() {

      @Override
      public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
        super.visitFunctionPrototype(functionPrototype);
        for (int i = 0; i < functionPrototype.getNumParameters(); i++) {
          if (functionPrototype.getParameter(i).getType().getWithoutQualifiers()
              .equals(declaration.getVariablesDeclaration().getBaseType().getWithoutQualifiers())) {
            assert declaration.getVariablesDeclaration().getDeclInfo(0).getName()
                .equals(functionPrototype.getParameter(i).getName());
            functionPrototype.getParameter(i).setName(originalVariableInfo.getName());
            functionPrototype.getParameter(i).setType(originalVariableInfo.getType());
          }
        }
      }

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        for (int i = 0; i < functionCallExpr.getNumArgs(); i++) {
          Expr arg = functionCallExpr.getArg(i);
          if (arg instanceof VariableIdentifierExpr && ((VariableIdentifierExpr) arg)
              .getName().equals(declaration.getVariablesDeclaration().getDeclInfo(0).getName())) {
            functionCallExpr.setArg(i, new VariableIdentifierExpr(originalVariableInfo.getName()));
          }

        }
      }

      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        if (!(memberLookupExpr.getStructure() instanceof VariableIdentifierExpr)) {
          return;
        }
        VariableIdentifierExpr structVariable = ((VariableIdentifierExpr) memberLookupExpr
            .getStructure());
        ScopeEntry se = currentScope.lookupScopeEntry(structVariable.getName());
        if (se == null) {
          return;
        }
        if (se.getType().getWithoutQualifiers().equals(declaration.getVariablesDeclaration()
            .getBaseType().getWithoutQualifiers())) {
          // We've found the variable reference, but now we might have a chain, like:
          // s._f0._f2._f1._f0.v
          // We need to find the member expression that has .v and replace that with v
          MemberLookupExpr current = memberLookupExpr;
          while (current.getMember().startsWith(Constants.STRUCTIFICATION_FIELD_PREFIX)) {
            current = (MemberLookupExpr) parentMap.getParent(current);
          }
          parentMap.getParent(current).replaceChild(
              current, new VariableIdentifierExpr(originalVariableInfo.getName()));
        }
      }
    }.visit(tu);
  }

  @Override
  public boolean preconditionHolds() {
    if (replacementVariableNameDeclaredInThisBlock()) {
      return false;
    }

    if (replacementVariableMightShadowExistingVariable()) {
      return false;
    }
    return true;
  }

  private boolean replacementVariableMightShadowExistingVariable() {
    return !new ShadowChecker(block, findOriginalVariableInfo().getName()).isOk(tu);
  }

  private boolean replacementVariableNameDeclaredInThisBlock() {
    return block.getStmts().stream()
          .filter(item -> item instanceof DeclarationStmt)
          .map(item -> (DeclarationStmt) item)
          .map(item -> item.getVariablesDeclaration())
          .map(item -> item.getDeclInfos())
          .reduce(Arrays.asList(), ListConcat::concatenate)
          .stream()
          .anyMatch(item -> item.getName().equals(findOriginalVariableInfo().getName()));
  }

}
