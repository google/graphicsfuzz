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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import java.util.ArrayList;
import java.util.List;

public final class StructUtils {

  private StructUtils() {
    // Utility class.
  }

  public static int countStructReferences(IAstNode node, StructNameType toLookFor) {
    return new StandardVisitor() {

      private int counter = 0;

      @Override
      public void visitStructNameType(StructNameType structNameType) {
        if (structNameType.equals(toLookFor)) {
          counter++;
        }
      }

      @Override
      public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
        super.visitTypeConstructorExpr(typeConstructorExpr);
        if (typeConstructorExpr.getTypename().equals(toLookFor.getName())) {
          counter++;
        }
      }

      private int count() {
        visit(node);
        return counter;
      }

    }.count();

  }

  public static List<StructDefinitionType> getStructDefinitions(IAstNode node) {
    return new StandardVisitor() {

      private final List<StructDefinitionType> structDefinitionTypes = new ArrayList<>();

      @Override
      public void visitStructDefinitionType(StructDefinitionType structDefinitionType) {
        super.visitStructDefinitionType(structDefinitionType);
        structDefinitionTypes.add(structDefinitionType);
      }

      private List<StructDefinitionType> find() {
        visit(node);
        return structDefinitionTypes;
      }

    }.find();

  }

  public static boolean declaresReferencedStruct(TranslationUnit tu,
                                          VariablesDeclaration variablesDeclaration) {
    return StructUtils.getStructDefinitions(variablesDeclaration)
        .stream()
        .filter(item -> item.hasStructNameType())
        .map(item -> item.getStructNameType())
        .anyMatch(item -> countStructReferences(tu, item) > 1);
  }

}
