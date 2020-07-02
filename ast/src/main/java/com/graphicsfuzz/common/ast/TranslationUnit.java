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
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.StructUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TranslationUnit implements IAstNode {

  private ShaderKind shaderKind;
  private Optional<ShadingLanguageVersion> shadingLanguageVersion;
  private List<Declaration> topLevelDeclarations;

  /**
   * Constructs a translation unit representing a given type of shader.
   * @param shaderKind The kind of shader represented, e.g. fragment, vertex, compute.
   * @param shadingLanguageVersion The shading language version; optional.
   * @param topLevelDeclarations The content of the shader.
   */
  public TranslationUnit(ShaderKind shaderKind,
                         Optional<ShadingLanguageVersion> shadingLanguageVersion,
                         List<Declaration> topLevelDeclarations) {
    this.shaderKind = shaderKind;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.topLevelDeclarations = new ArrayList<>();
    this.topLevelDeclarations.addAll(topLevelDeclarations);
  }

  /**
   * Constructs a translation unit representing a fragment shader.
   * @param shadingLanguageVersion The shading language version; optional.
   * @param topLevelDeclarations The content of the shader.
   */
  public TranslationUnit(Optional<ShadingLanguageVersion> shadingLanguageVersion,
                         List<Declaration> topLevelDeclarations) {
    this(ShaderKind.FRAGMENT, shadingLanguageVersion, topLevelDeclarations);
  }

  public boolean hasMainFunction() {
    return getFunctionDefinitions().anyMatch(TranslationUnit::isMain);
  }

  public FunctionDefinition getMainFunction() {
    assert hasMainFunction();
    return getFunctionDefinitions().filter(TranslationUnit::isMain)
        .findAny()
        .get();
  }

  public ShaderKind getShaderKind() {
    return shaderKind;
  }

  public void setShaderKind(ShaderKind shaderKind) {
    this.shaderKind = shaderKind;
  }

  public List<Declaration> getTopLevelDeclarations() {
    return Collections.unmodifiableList(topLevelDeclarations);
  }

  /**
   * Determines whether the translation unit records an explicit GLSL version.
   * @return Whether an explicit GLSL version is present in the translation unit.
   */
  public boolean hasShadingLanguageVersion() {
    return shadingLanguageVersion.isPresent();
  }

  /**
   * Yields the shading language version for the translation unit, which is GLSL ES 100 if not
   * explicitly specified.
   * @return The shading language version associated with the translation unit.
   */
  public ShadingLanguageVersion getShadingLanguageVersion() {
    if (hasShadingLanguageVersion()) {
      return shadingLanguageVersion.get();
    }
    return ShadingLanguageVersion.ESSL_100;
  }

  public void setShadingLanguageVersion(ShadingLanguageVersion shadingLanguageVersion) {
    this.shadingLanguageVersion = Optional.of(shadingLanguageVersion);
  }

  public void setTopLevelDeclarations(List<Declaration> topLevelDeclarations) {
    this.topLevelDeclarations = topLevelDeclarations;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitTranslationUnit(this);
  }

  /**
   * Inserts the given declaration into the translation unit, but after any precision qualifiers
   * that appear contiguously at the start of the translation unit.
   * @param decl A declaration to be added.
   */
  public void addDeclaration(Declaration decl) {
    int index;
    for (index = 0; index < topLevelDeclarations.size(); index++) {
      if (!(topLevelDeclarations.get(index) instanceof PrecisionDeclaration)) {
        break;
      }
    }
    topLevelDeclarations.add(index, decl);
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

  /**
   * Replaces an existing top level declaration with another top level declaration.
   * @param newDecl The new declaration.
   * @param existingDecl The existing declaration to be replaced.
   */
  public void updateTopLevelDeclaration(Declaration newDecl, Declaration existingDecl) {
    for (int i = 0; i < topLevelDeclarations.size(); i++) {
      if (topLevelDeclarations.get(i) == existingDecl) {
        topLevelDeclarations.set(i, newDecl);
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
    return new TranslationUnit(shaderKind, shadingLanguageVersion,
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

  /**
   * Returns variable declarations of the given uniform. A precondition of this method is
   * that the uniform exists. NoSuchElementException is thrown in the case of a non-existent
   * uniform.
   * @param name The name of the uniform of which variable declarations are returned.
   * @throws java.util.NoSuchElementException thrown if the given uniform doesn't exist.
   */
  public VariablesDeclaration getUniformDeclaration(String name) {
    return getGlobalVariablesDeclarations().stream()
        .filter(variablesDeclaration ->
            variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.UNIFORM)
                && variablesDeclaration.getDeclInfos().stream()
                .anyMatch(variableDecl -> variableDecl.getName().equals(name))).findAny().get();
  }

  /**
   * Checks if a uniform declaration for the given uniform exists.
   * @param name The name of the uniform declaration to be checked.
   */
  public boolean hasUniformDeclaration(String name) {
    return getGlobalVariablesDeclarations().stream()
        .anyMatch(variablesDeclaration ->
            variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.UNIFORM)
            && variablesDeclaration.getDeclInfos().stream()
            .anyMatch(variableDecl -> variableDecl.getName().equals(name)));
  }

  public List<StructDefinitionType> getStructDefinitions() {
    return StructUtils.getStructDefinitions(this);
  }

  public StructDefinitionType getStructDefinition(StructNameType type) {
    return getStructDefinitions()
        .stream()
        .filter(item -> item.hasStructNameType() && item.getStructNameType().equals(type))
        .findAny()
        .get();
  }

  private Stream<FunctionDefinition> getFunctionDefinitions() {
    return topLevelDeclarations.stream()
        .filter(item -> item instanceof FunctionDefinition)
        .map(item -> (FunctionDefinition) item);
  }

  private static boolean isMain(FunctionDefinition functionDefinition) {
    return functionDefinition.getPrototype().getName().equals("main");
  }

}
