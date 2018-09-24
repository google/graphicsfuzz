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

package com.graphicsfuzz.generator.transformation.structifier;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StructificationOpportunities extends ScopeTreeBuilder {

  private final List<StructificationOpportunity> opportunities;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final TranslationUnit tu;

  public StructificationOpportunities(TranslationUnit tu,
      ShadingLanguageVersion shadingLanguageVersion) {
    this.opportunities = new ArrayList<>();
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.tu = tu;
    visit(tu);
  }

  @Override
  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    super.visitDeclarationStmt(declarationStmt);
    // Currently we only structify solo declarations
    if (declarationStmt.getVariablesDeclaration().getNumDecls() != 1) {
      return;
    }
    // We don't currently structify arrays
    if (declarationStmt.getVariablesDeclaration().getDeclInfo(0).hasArrayInfo()) {
      return;
    }
    final Type baseType = declarationStmt.getVariablesDeclaration().getBaseType();
    if (hasQualifiers(baseType)) {
      return;
    }
    // TODO: For simplicity, at present we do not structify non-basic types.  The issue is that
    // if a struct S is to be structified, we need to declare the structs that enclose S *after*
    // S is declared, which is a bit fiddly (currently they all go at the top of the translation
    // unit).
    if (!(baseType.getWithoutQualifiers() instanceof BasicType)) {
      return;
    }
    opportunities.add(new StructificationOpportunity(declarationStmt, currentBlock(), tu,
        shadingLanguageVersion));
  }

  private boolean hasQualifiers(Type type) {
    return type instanceof QualifiedType
          && ((QualifiedType) type).hasQualifiers();
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    if (!shadingLanguageVersion.restrictedForLoops() || !shadingLanguageVersion.isWebGl()) {
      super.visitForStmt(forStmt);
    } else {
      // GLSL 1.00 + WebGL does not allow us to structify for loop guards, so we skip the for loop
      // header.
      visitForStmtBodyOnly(forStmt);
    }
  }

  public List<StructificationOpportunity> getOpportunities(
        TransformationProbabilities probabilities,
        IRandom generator) {
    return opportunities.stream().filter(opportunity -> probabilities.structify(generator)).collect(
          Collectors.toList());
  }

}
