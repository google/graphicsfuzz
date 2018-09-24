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

package com.graphicsfuzz.reducer.tool;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import java.util.ArrayList;
import java.util.List;

public class ReportAstDifferences {

  private class TraversalVisitor extends StandardVisitor {

    private List<IAstNode> nodes = new ArrayList<>();

    TraversalVisitor(TranslationUnit tu) {
      visit(tu);
    }

    @Override
    public void visit(IAstNode node) {
      nodes.add(node);
      super.visit(node);
    }

    List<IAstNode> getNodes() {
      return nodes;
    }

  }

  private class InformativeScopeTreeBuilder extends ScopeTreeBuilder {

    InformativeScopeTreeBuilder(TranslationUnit tu) {
      visit(tu);
    }

    @Override
    protected void pushScope() {
      System.out.println("Entering a scope");
      super.pushScope();
    }

    @Override
    protected void popScope() {
      super.popScope();
      System.out.println("Leaving a scope");
    }

    @Override
    public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
      System.out.println("Entering function " + functionDefinition.getPrototype().getName());
      super.visitFunctionDefinition(functionDefinition);
      System.out.println("Leaving function " + functionDefinition.getPrototype().getName());
    }

    @Override
    public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
      System.out.println("Entering variables declaration");
      for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
        System.out.println("  " + vdi.getName());
      }
      super.visitVariablesDeclaration(variablesDeclaration);
      System.out.println("Leaving variables declaration");
    }
  }

  public ReportAstDifferences(TranslationUnit tu1, TranslationUnit tu2) {
    try {
      new InformativeScopeTreeBuilder(tu1);
    } catch (Exception exception) {
      System.out.println("Exception was thrown:");
      exception.printStackTrace(System.out);
    }
    try {
      new InformativeScopeTreeBuilder(tu2);
    } catch (Exception exception) {
      System.out.println("Exception was thrown:");
      exception.printStackTrace(System.out);
    }

    /*List<IAstNode> nodes1 = new TraversalVisitor(tu1).getNodes();
    List<IAstNode> nodes2 = new TraversalVisitor(tu2).getNodes();
    Map<IAstNode, IAstNode> oneToTwo = new HashMap<>();
    Map<IAstNode, IAstNode> twoToOne = new HashMap<>();
    IParentMap parentMap1 = IParentMap.createParentMap(tu1);
    IParentMap parentMap2 = IParentMap.createParentMap(tu2);
    for (int i = 0; i < Math.min(nodes1.size(), nodes2.size()); i++) {
      IAstNode one = nodes1.get(i);
      IAstNode two = nodes1.get(i);
      if (oneToTwo.containsKey(one) || twoToOne.containsKey(two)) {
        if (oneToTwo.get(one) != two || twoToOne.get(two) != one) {
          throw new RuntimeException();
        }
      } else {
        oneToTwo.put(one, two);
        twoToOne.put(two, one);
      }
    }
    if (nodes1.size() != nodes2.size()) {
      throw new RuntimeException();
    }*/
  }

}
