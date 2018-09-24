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
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.StructDeclaration;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.StructType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UnusedStructReductionOpportunities extends StandardVisitor {

  private final Set<String> referencedStructs;

  private UnusedStructReductionOpportunities() {
    this.referencedStructs = new HashSet<>();
  }

  /**
   * Finds all opportunities to remove unreferenced structs from the translation unit.
   *
   * @param tu The translation unit to be analysed
   * @return The opportunities for struct removal
   */
  static List<FunctionOrStructReductionOpportunity> findOpportunities(
        TranslationUnit tu,
        ReductionOpportunityContext context) {
    UnusedStructReductionOpportunities unusedStructReductionOpportunities =
          new UnusedStructReductionOpportunities();
    unusedStructReductionOpportunities.visit(tu);
    return unusedStructReductionOpportunities.getReductionOpportunitiesForUnusedStructs(tu);
  }

  @Override
  public void visitStructDeclaration(StructDeclaration structDeclaration) {
    super.visitStructDeclaration(structDeclaration);
    for (Type t : structDeclaration.getType().getFieldTypes()) {
      if (t.getWithoutQualifiers() instanceof StructType) {
        referencedStructs.add(((StructType) t.getWithoutQualifiers()).getName());
      }
    }
  }

  @Override
  public void visitStructType(StructType structType) {
    super.visitStructType(structType);
    for (String field : structType.getFieldNames()) {
      checkForReferencedStructs(structType.getFieldType(field));
    }
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    super.visitVariablesDeclaration(variablesDeclaration);
    checkForReferencedStructs(variablesDeclaration.getBaseType());
  }

  @Override
  public void visitParameterDecl(ParameterDecl parameterDecl) {
    super.visitParameterDecl(parameterDecl);
    checkForReferencedStructs(parameterDecl.getType());
  }

  @Override
  public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
    super.visitTypeConstructorExpr(typeConstructorExpr);
    // This will add non-struct typenames such as "float" and "vec2", but it doesn't matter
    referencedStructs.add(typeConstructorExpr.getTypename());
  }

  private void checkForReferencedStructs(Type baseType) {
    referencedStructs.addAll(getReferencedStructTypeNames(baseType));
  }

  private List<FunctionOrStructReductionOpportunity> getReductionOpportunitiesForUnusedStructs(
        TranslationUnit tu) {
    return tu.getTopLevelDeclarations().stream().filter(decl -> decl instanceof StructDeclaration
          && !referencedStructs.contains(((StructDeclaration) decl).getType().getName()))
          .map(decl -> new FunctionOrStructReductionOpportunity(tu, decl, getVistitationDepth()))
          .collect(Collectors.toList());
  }

  private Set<String> getReferencedStructTypeNames(Type type) {
    Set<String> result = new HashSet<>();
    Type typeOfInterest = type.getWithoutQualifiers();
    if (typeOfInterest instanceof ArrayType) {
      typeOfInterest = ((ArrayType) typeOfInterest).getBaseType().getWithoutQualifiers();
    }
    if (typeOfInterest instanceof StructType) {
      result.add(((StructType) typeOfInterest).getName());
      for (Type fieldType : ((StructType) typeOfInterest).getFieldTypes()) {
        result.addAll(getReferencedStructTypeNames(fieldType));
      }
    }
    return result;
  }

}
