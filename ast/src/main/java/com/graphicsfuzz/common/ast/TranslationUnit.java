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

package com.graphicsfuzz.common.ast;

import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.StructDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.stmt.VersionStatement;
import com.graphicsfuzz.common.ast.type.StructType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TranslationUnit implements IAstNode {

  private VersionStatement versionStatement;
  private List<Declaration> topLevelDeclarations;

  public TranslationUnit(VersionStatement versionStatement,
      List<Declaration> topLevelDeclarations) {
    this.versionStatement = versionStatement;
    this.topLevelDeclarations = new ArrayList<>();
    this.topLevelDeclarations.addAll(topLevelDeclarations);
  }

  public List<Declaration> getTopLevelDeclarations() {
    return Collections.unmodifiableList(topLevelDeclarations);
  }

  public VersionStatement getVersionStatement() {
    return versionStatement;
  }

  public void setTopLevelDeclarations(List<Declaration> topLevelDeclarations) {
    this.topLevelDeclarations = topLevelDeclarations;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitTranslationUnit(this);
  }

  public void addDeclaration(Declaration decl) {
    topLevelDeclarations.add(0, decl);
  }

  public void addDeclarationBefore(Declaration newDecl, Declaration existingDecl) {
    for (int i = 0; i < topLevelDeclarations.size(); i++) {
      if (topLevelDeclarations.get(i) == existingDecl) {
        topLevelDeclarations.add(i, newDecl);
        return;
      }
    }
    throw new IllegalArgumentException("Existing declaration not found.");
  }

  public void removeTopLevelDeclaration(int index) {
    topLevelDeclarations.remove(index);
  }

  public void removeTopLevelDeclaration(Declaration declaration) {
    topLevelDeclarations.remove(declaration);
  }

  @Override
  public TranslationUnit clone() {
    return new TranslationUnit(versionStatement.clone(),
        topLevelDeclarations.stream().map(x -> x.clone()).collect(Collectors.toList()));
  }

  public List<VariableDeclInfo> getGlobalVarDeclInfos() {
    return getGlobalVariablesDeclarations()
        .stream()
        .map(VariablesDeclaration::getDeclInfos)
        .reduce(new ArrayList<VariableDeclInfo>(), ListConcat::concatenate);
  }

  public List<VariablesDeclaration> getGlobalVariablesDeclarations() {
    return getTopLevelDeclarations()
        .stream()
        .filter(item -> item instanceof VariablesDeclaration)
        .map(item -> (VariablesDeclaration) item)
        .collect(Collectors.toList());
  }

  public List<VariablesDeclaration> getUniformDecls() {
    return getGlobalVariablesDeclarations()
        .stream()
        .filter(item -> item.getBaseType().hasQualifier(TypeQualifier.UNIFORM))
        .collect(Collectors.toList());
  }

  public List<StructDeclaration> getStructDecls() {
    return getTopLevelDeclarations()
        .stream()
        .filter(item -> item instanceof StructDeclaration)
        .map(item -> (StructDeclaration) item)
        .collect(Collectors.toList());
  }

  public StructDeclaration getStructDecl(StructType type) {
    return getStructDecls()
        .stream()
        .filter(item -> item.getStructType().equals(type))
        .findAny()
        .get();
  }

}
