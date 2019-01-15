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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StripUnusedGlobals extends ScopeTreeBuilder {

  public static void strip(TranslationUnit tu) {
    new StripUnusedGlobals(tu);
  }

  private final Set<VariableDeclInfo> unusedGlobals;
  private final Set<StructDefinitionType> unusedStructs;

  private StripUnusedGlobals(TranslationUnit tu) {
    this.unusedGlobals = new HashSet<>();
    this.unusedStructs = new HashSet<>();
    visit(tu);
    sweep(tu);
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    if (variablesDeclaration.getBaseType().getWithoutQualifiers() instanceof StructDefinitionType) {
      final StructDefinitionType sdt =
          (StructDefinitionType) variablesDeclaration.getBaseType().getWithoutQualifiers();
      if (sdt.hasStructNameType()) {
        // Initially, assume it is unused
        unusedStructs.add(sdt);
      }
    }
    super.visitVariablesDeclaration(variablesDeclaration);
  }

  @Override
  public void visitStructNameType(StructNameType structNameType) {
    super.visitStructNameType(structNameType);
    unusedStructs.remove(currentScope.lookupStructName(structNameType.getName()));
  }

  @Override
  public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
    super.visitTypeConstructorExpr(typeConstructorExpr);
    // If a struct name is used in a type constructor, the struct counts as being used.
    // The type constructor here might not be a struct type, e.g. it could be "float" or "vec2".
    // That's OK: lookupStructName will just return null so nothing will be removed.
    unusedStructs.remove(currentScope.lookupStructName(typeConstructorExpr.getTypename()));
  }

  @Override
  public void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo) {
    super.visitVariableDeclInfo(variableDeclInfo);
    if (atGlobalScope() && !variableDeclInfo.getName().equals(Constants.INJECTION_SWITCH)) {
      // Initially, assume it is unused
      unusedGlobals.add(variableDeclInfo);
    }
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    final ScopeEntry scopeEntry = currentScope.lookupScopeEntry(variableIdentifierExpr.getName());
    if (scopeEntry != null && scopeEntry.hasVariableDeclInfo()) {
      // If this is a global, mark it as used.
      unusedGlobals.remove(scopeEntry.getVariableDeclInfo());
    }
  }

  private void sweep(TranslationUnit tu) {
    final List<Declaration> oldTopLevelDecls = new ArrayList<>();
    oldTopLevelDecls.addAll(tu.getTopLevelDeclarations());
    for (Declaration decl : oldTopLevelDecls) {
      if (!(decl instanceof VariablesDeclaration)) {
        continue;
      }
      final VariablesDeclaration variablesDeclaration = (VariablesDeclaration) decl;
      int index = 0;
      while (index < variablesDeclaration.getNumDecls()) {
        if (unusedGlobals.contains(variablesDeclaration.getDeclInfo(index))) {
          variablesDeclaration.removeDeclInfo(index);
        } else {
          index++;
        }
      }
      if (variablesDeclaration.getNumDecls() == 0
          && !isUsedStructType(variablesDeclaration.getBaseType())) {
        tu.removeTopLevelDeclaration(variablesDeclaration);
      }
    }
  }

  private boolean isUsedStructType(Type type) {
    if (!(type.getWithoutQualifiers() instanceof StructDefinitionType)) {
      return false;
    }
    final StructDefinitionType sdt = (StructDefinitionType) type.getWithoutQualifiers();
    return !unusedStructs.contains(sdt);
  }

}
