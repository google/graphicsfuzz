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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Attempts to minimally remove const qualifiers and move global declaration initializers into
 * main, to make a shader valid.
 */
class ConstCleaner extends ScopeTreeBuilder {

  private boolean atGlobalScope;
  private Optional<VariablesDeclaration> currentVariablesDeclaration;
  private final List<VariablesDeclaration> globalsToBeReInitialized;
  private final ShadingLanguageVersion shadingLanguageVersion;

  private ConstCleaner(TranslationUnit tu) {
    this.atGlobalScope = true;
    this.currentVariablesDeclaration = Optional.empty();
    this.globalsToBeReInitialized = new ArrayList<>();
    this.shadingLanguageVersion = tu.getShadingLanguageVersion();
    visit(tu);
    addGlobalInitializers(tu.getMainFunction());
  }

  public static void clean(TranslationUnit tu) {
    new ConstCleaner(tu);
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    assert atGlobalScope;
    atGlobalScope = false;
    super.visitFunctionDefinition(functionDefinition);
    assert !atGlobalScope;
    atGlobalScope = true;
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    if (!currentVariablesDeclaration.isPresent()) {
      return;
    }
    if (!nonConst(variableIdentifierExpr)) {
      return;
    }

    if (currentVariablesDeclaration.get().getBaseType().hasQualifier(TypeQualifier.CONST)) {
      ((QualifiedType) currentVariablesDeclaration.get().getBaseType())
          .removeQualifier(TypeQualifier.CONST);
      if (atGlobalScope) {
        assert !globalsToBeReInitialized.contains(currentVariablesDeclaration.get());
        globalsToBeReInitialized.add(currentVariablesDeclaration.get());
      }
    } else if (shadingLanguageVersion.globalVariableInitializersMustBeConst() && atGlobalScope) {
      if (!globalsToBeReInitialized.contains(currentVariablesDeclaration.get())) {
        globalsToBeReInitialized.add(currentVariablesDeclaration.get());
      }
    }
  }

  private boolean nonConst(VariableIdentifierExpr variableIdentifierExpr) {
    final ScopeEntry se = currentScope.lookupScopeEntry(variableIdentifierExpr.getName());
    return se != null && se.hasVariablesDeclaration() && !se.getVariablesDeclaration().getBaseType()
        .hasQualifier(TypeQualifier.CONST);
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    assert !currentVariablesDeclaration.isPresent();
    currentVariablesDeclaration = Optional.of(variablesDeclaration);
    super.visitVariablesDeclaration(variablesDeclaration);
    currentVariablesDeclaration = Optional.empty();
  }

  private void addGlobalInitializers(FunctionDefinition mainFunction) {
    assert mainFunction.getPrototype().getName().equals("main");
    for (int i = globalsToBeReInitialized.size() - 1; i >= 0; i--) {
      for (int j = globalsToBeReInitialized.get(i).getNumDecls() - 1; j >= 0; j--) {
        final VariableDeclInfo vdi = globalsToBeReInitialized.get(i).getDeclInfo(j);
        mainFunction.getBody().insertStmt(0,
            new ExprStmt(new BinaryExpr(new VariableIdentifierExpr(vdi.getName()),
                (vdi.getInitializer()).getExpr(), BinOp.ASSIGN)));
        vdi.setInitializer(null);
      }
    }
  }

}
