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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.util.Constants;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class VariableDeclReductionOpportunities
    extends ReductionOpportunitiesBase<VariableDeclReductionOpportunity> {

  private final Deque<Set<ScopeEntry>> referencedScopeEntries;

  private VariableDeclReductionOpportunities(TranslationUnit tu,
                                             ReducerContext context) {
    super(tu, context);
    this.referencedScopeEntries = new LinkedList<>();
    this.referencedScopeEntries.addFirst(new HashSet<>());
  }

  private void getReductionOpportunitiesForUnusedGlobals() {
    for (String name : currentScope.keys()) {
      ScopeEntry entry = currentScope.lookupScopeEntry(name);
      assert entry.hasVariableDeclInfo();
      assert referencedScopeEntries.peek() != null;
      if (!referencedScopeEntries.peek().contains(entry)) {
        addOpportunity(new VariableDeclReductionOpportunity(entry.getVariableDeclInfo(),
            entry.getVariablesDeclaration(),
            getVistitationDepth()));
      }
    }
  }

  @Override
  protected void pushScope() {
    referencedScopeEntries.addFirst(new HashSet<>());
    super.pushScope();
  }

  @Override
  protected void popScope() {
    for (String name : currentScope.keys()) {
      ScopeEntry entry = currentScope.lookupScopeEntry(name);
      if (entry.hasVariableDeclInfo() && !referencedScopeEntries.peek().contains(entry)) {
        if (allowedToReduceLocalDecl(entry.getVariableDeclInfo())) {
          addOpportunity(
              new VariableDeclReductionOpportunity(
                  entry.getVariableDeclInfo(),
                  entry.getVariablesDeclaration(),
                  getVistitationDepth()));
        }
      }
    }
    Set<ScopeEntry> removedScopeEntries = referencedScopeEntries.removeFirst();
    // We now have to decide which of these referred to variables that are just about to go out of
    // scope -- we are done with those -- vs. variables that will still be in scope -- we need to
    // add those to the prevous set of scope entries
    for (ScopeEntry entry : removedScopeEntries) {
      assert entry != null;
      if (!entry.hasVariableDeclInfo()) {
        continue;
      }
      // Assert that the current or some previous scope has the entry.
      // The reason for the disjunction is that the variable reference could
      // refer to a variable x in the parent scope, even though the current
      // scope also declares a variable x after the usage of x
      String name = entry.getVariableDeclInfo().getName();
      assert currentScope.lookupScopeEntry(name) == entry
          || currentScope.getParent().lookupScopeEntry(name) == entry;
      if (!currentScope.keys().contains(name)) {
        addReferencedScopeEntry(entry);
      }
    }

    super.popScope();
  }

  private void addReferencedScopeEntry(ScopeEntry scopeEntry) {
    assert scopeEntry != null;
    referencedScopeEntries.peek().add(scopeEntry);
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    ScopeEntry scopeEntry = currentScope.lookupScopeEntry(variableIdentifierExpr.getName());
    if (scopeEntry != null) {
      addReferencedScopeEntry(scopeEntry);
    }
  }

  private boolean allowedToReduceLocalDecl(VariableDeclInfo variableDeclInfo) {
    // Check that the variable declaration is part of a declaration statement
    // inside a block
    VariablesDeclaration vd = (VariablesDeclaration) parentMap.getParent(variableDeclInfo);
    if (!(parentMap.getParent(vd) instanceof DeclarationStmt)) {
      return false;
    }
    if (!(parentMap.getParent(parentMap.getParent(vd)) instanceof BlockStmt)) {
      return false;
    }
    // Fine to remove if in a dead context, a live context, if no initializer, or if
    // initializer does not have side effects.
    return context.reduceEverywhere() || enclosingFunctionIsDead()
        || injectionTracker.enclosedByDeadCodeInjection()
        || isLiveInjection(variableDeclInfo)
        || !variableDeclInfo.hasInitializer()
        || initializerIsScalarAndSideEffectFree(variableDeclInfo);
  }

  private boolean isLiveInjection(VariableDeclInfo variableDeclInfo) {
    return variableDeclInfo.getName().startsWith(Constants.LIVE_PREFIX);
  }

  /**
   * Find all unused declaration opportunities for the given translation unit.
   *
   * @param shaderJob The shader job to be searched.
   * @param context Determines info such as whether we reduce everywhere or only reduce injections
   * @return The declaration opportunities that can be reduced
   */
  static List<VariableDeclReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<VariableDeclReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    VariableDeclReductionOpportunities finder =
        new VariableDeclReductionOpportunities(tu, context);
    finder.visit(tu);
    finder.getReductionOpportunitiesForUnusedGlobals();
    return finder.getOpportunities();
  }

}
