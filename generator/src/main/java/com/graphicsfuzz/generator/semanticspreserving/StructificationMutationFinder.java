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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.generator.mutateapi.MutationFinderBase;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.util.Constants;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StructificationMutationFinder extends MutationFinderBase<StructificationMutation> {

  private final IdGenerator idGenerator;
  private final IRandom random;
  private final GenerationParams generationParams;

  public StructificationMutationFinder(TranslationUnit tu,
                                       IRandom random,
                                       GenerationParams generationParams) {
    super(tu);
    this.idGenerator = new IdGenerator(getIdsAlreadyUsedForStructification());
    this.random = random;
    this.generationParams = generationParams;
  }

  /**
   * Looks through the translation unit for all ids that have already been used for naming in
   * structification, and yields the set of all such ids.
   * @return The set of all ids that have been used in structification.
   */
  private Set<Integer> getIdsAlreadyUsedForStructification() {
    final Set<Integer> result = new HashSet<>();
    new StandardVisitor() {
      @Override
      public void visitStructNameType(StructNameType structNameType) {
        super.visitStructNameType(structNameType);
        if (structNameType.getName().startsWith(Constants.STRUCTIFICATION_STRUCT_PREFIX)) {
          result.add(new Integer(
              StructificationMutation.getIdFromGeneratedStructName(structNameType)));
        }
      }
    }.visit(getTranslationUnit());
    return result;
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
    addMutation(new StructificationMutation(declarationStmt, currentBlock(),
        getTranslationUnit(), idGenerator, random, generationParams));
  }

  private boolean hasQualifiers(Type type) {
    return type instanceof QualifiedType
          && ((QualifiedType) type).hasQualifiers();
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    if (!getTranslationUnit().getShadingLanguageVersion().restrictedForLoops()
        || !getTranslationUnit().getShadingLanguageVersion().isWebGl()) {
      super.visitForStmt(forStmt);
    } else {
      // GLSL 1.00 + WebGL does not allow us to structify for loop guards, so we skip the for loop
      // header.
      visitForStmtBodyOnly(forStmt);
    }
  }

}
