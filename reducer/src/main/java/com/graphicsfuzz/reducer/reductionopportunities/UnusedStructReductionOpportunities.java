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
import com.graphicsfuzz.common.ast.type.NamedStructType;
import com.graphicsfuzz.common.ast.type.StructType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UnusedStructReductionOpportunities extends StandardVisitor {

  private final Set<StructType> referencedStructs;

  private UnusedStructReductionOpportunities() {
    this.referencedStructs = new HashSet<>();
  }

  /**
   * Finds all opportunities to remove unreferenced structs from the translation unit.
   *
   * @param shaderJob The shader job to be analysed
   * @return The opportunities for struct removal
   */
  static List<FunctionOrStructReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReductionOpportunityContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<FunctionOrStructReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu) {
    UnusedStructReductionOpportunities unusedStructReductionOpportunities =
        new UnusedStructReductionOpportunities();
    unusedStructReductionOpportunities.visit(tu);
    return unusedStructReductionOpportunities
        .getReductionOpportunitiesForUnusedStructs(tu);
  }

  @Override
  public void visitStructDeclaration(StructDeclaration structDeclaration) {
    super.visitStructDeclaration(structDeclaration);
    for (String field : structDeclaration.getFieldNames()) {
      checkForReferencedStructs(structDeclaration.getFieldType(field));
    }
    for (Type t : structDeclaration.getFieldTypes()) {
      if (t.getWithoutQualifiers() instanceof StructType) {
        referencedStructs.add(((StructType) t.getWithoutQualifiers()));
      }
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
    referencedStructs.add(new NamedStructType(typeConstructorExpr.getTypename()));
  }

  private void checkForReferencedStructs(Type baseType) {
    referencedStructs.addAll(getReferencedStructTypeNames(baseType));
  }

  private List<FunctionOrStructReductionOpportunity> getReductionOpportunitiesForUnusedStructs(
        TranslationUnit tu) {
    return tu.getTopLevelDeclarations().stream().filter(decl -> decl instanceof StructDeclaration
          && !referencedStructs.contains(((StructDeclaration) decl).getStructType()))
          .map(decl -> new FunctionOrStructReductionOpportunity(tu, decl, getVistitationDepth()))
          .collect(Collectors.toList());
  }

  private Set<StructType> getReferencedStructTypeNames(Type type) {
    Set<StructType> result = new HashSet<>();
    Type typeOfInterest = type.getWithoutQualifiers();
    if (typeOfInterest instanceof ArrayType) {
      typeOfInterest = ((ArrayType) typeOfInterest).getBaseType().getWithoutQualifiers();
    }
    if (typeOfInterest instanceof StructType) {
      result.add((StructType) typeOfInterest);
    }
    return result;
  }

}
