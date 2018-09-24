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

package com.graphicsfuzz.generator.transformation.vectorizer;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.transformreduce.MergeSet;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VectorizationOpportunity {

  private final BlockStmt block;
  private final MergeSet mergeSet;
  private final IParentMap parentMap;

  public VectorizationOpportunity(BlockStmt block, MergeSet mergeSet, IParentMap parentMap) {
    this.block = block;
    this.mergeSet = mergeSet;
    this.parentMap = parentMap;
  }

  public void apply() {

    if (blockAlreadyDeclaresVector()) {
      return;
    }

    for (ScopeEntry se : mergeSet.getIndidualScopeEntries()) {
      new VectorizerVisitor(se).visit(block);
    }
    block.insertStmt(0, new DeclarationStmt(new VariablesDeclaration(
        mergeSet.getMergedType(), new VariableDeclInfo(mergeSet.getMergedName(), null, null))));
  }

  private class VectorizerVisitor extends ScopeTreeBuilder {

    private boolean inDeclarationOfTargetVariable = false;
    private final ScopeEntry currentComponent;

    VectorizerVisitor(ScopeEntry currentComponent) {
      this.currentComponent = currentComponent;
    }

    @Override
    public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
      assert !inDeclarationOfTargetVariable;
      for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
        if (currentComponent.getVariableDeclInfo() == vdi) {
          inDeclarationOfTargetVariable = true;
          break;
        }
      }
      super.visitVariablesDeclaration(variablesDeclaration);
      inDeclarationOfTargetVariable = false;
    }

    @Override
    public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
      super.visitVariableIdentifierExpr(variableIdentifierExpr);
      if (isCurrentComponentVariable(variableIdentifierExpr.getName())
          && !inDeclarationOfTargetVariable) {
        parentMap.getParent(variableIdentifierExpr).replaceChild(
            variableIdentifierExpr,
            new MemberLookupExpr(new VariableIdentifierExpr(mergeSet.getMergedName()),
                getSwizzle(variableIdentifierExpr.getName())));
      }
    }

    private boolean isCurrentComponentVariable(String name) {
      ScopeEntry entry = currentScope.lookupScopeEntry(name);
      return entry != null && entry.hasVariableDeclInfo()
          && currentComponent.getVariableDeclInfo() == entry.getVariableDeclInfo();
    }

    @Override
    public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
      List<String> existingKeys = new ArrayList<>();
      existingKeys.addAll(currentScope.keys());
      super.visitDeclarationStmt(declarationStmt);
      List<String> newKeys = currentScope.keys().stream().filter(key -> !existingKeys.contains(key))
          .collect(Collectors.toList());
      newKeys.sort(String::compareTo);
      for (String newKey : newKeys) {
        if (isCurrentComponentVariable(newKey)) {
          final ExprStmt insertedStmt = new ExprStmt(
              new BinaryExpr(
                  new MemberLookupExpr(
                      new VariableIdentifierExpr(mergeSet.getMergedName()),
                      getSwizzle(newKey)),
                  new VariableIdentifierExpr(newKey),
                  BinOp.ASSIGN
              ));
          assert parentMap.getParent(declarationStmt) instanceof BlockStmt;
          ((BlockStmt) parentMap.getParent(declarationStmt)).insertAfter(declarationStmt,
                insertedStmt);
        }
      }

    }
  }

  private String getSwizzle(String name) {
    return String.join("", mergeSet.getIndices(name).stream().map(index ->
        (new String[]{"x", "y", "z", "w"})[index]).collect(Collectors.toList()));
  }

  private boolean blockAlreadyDeclaresVector() {
    // When we do multiple transformation passes, the same vectorization attempt might be applied
    // multiple times.  If we would create a vector whose name is already declared in this block,
    // we bail out.
    return block.getStmts().stream().filter(item -> item instanceof DeclarationStmt)
          .map(item -> ((DeclarationStmt) item).getVariablesDeclaration().getDeclInfos())
          .reduce(new ArrayList<>(), ListConcat::concatenate)
          .stream()
          .anyMatch(item -> item.getName().equals(mergeSet.getMergedName()));
  }

}
