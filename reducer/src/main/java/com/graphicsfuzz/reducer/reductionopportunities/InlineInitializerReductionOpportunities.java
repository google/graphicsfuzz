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
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.SideEffectChecker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InlineInitializerReductionOpportunities
      extends ReductionOpportunitiesBase<InlineInitializerReductionOpportunity> {

  private final List<List<VariableDeclInfo>> relevantDeclInfosStack;
  private final Set<VariableDeclInfo> referenced;
  private final TranslationUnit tu;

  private InlineInitializerReductionOpportunities(
        TranslationUnit tu,
        ReductionOpportunityContext context) {
    super(tu, context);
    this.relevantDeclInfosStack = new ArrayList<>();
    relevantDeclInfosStack.add(new ArrayList<>());
    this.referenced = new HashSet<>();
    this.tu = tu;
  }

  static List<InlineInitializerReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReductionOpportunityContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<InlineInitializerReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReductionOpportunityContext context) {
    InlineInitializerReductionOpportunities finder =
          new InlineInitializerReductionOpportunities(tu, context);
    finder.visit(tu);
    // Do this for global variables
    finder.addOpportunities();
    return finder.getOpportunities();
  }

  @Override
  protected void pushScope() {
    super.pushScope();
    relevantDeclInfosStack.add(new ArrayList<>());
  }

  @Override
  protected void popScope() {
    addOpportunities();
    super.popScope();
  }

  private void addOpportunities() {
    for (VariableDeclInfo vdi : topOfStack()) {
      // To avoid a trivial reduction opportunity, we only add variable declaration infos in
      // cases where they are actually referenced.
      if (referenced.contains(vdi)) {
        addOpportunity(new InlineInitializerReductionOpportunity(
              tu, vdi, getVistitationDepth()));
      }
    }
    relevantDeclInfosStack.remove(relevantDeclInfosStack.size() - 1);
  }

  @Override
  public void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo) {
    super.visitVariableDeclInfo(variableDeclInfo);
    if (!variableDeclInfo.hasInitializer()) {
      return;
    }
    if (!(variableDeclInfo.getInitializer() instanceof ScalarInitializer)) {
      return;
    }
    assert !topOfStack().contains(variableDeclInfo);
    if (allowedToReduce(variableDeclInfo)) {
      topOfStack().add(variableDeclInfo);
    }
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    final ScopeEntry se = currentScope.lookupScopeEntry(variableIdentifierExpr.getName());
    if (se == null || !se.hasVariableDeclInfo()) {
      return;
    }
    final VariableDeclInfo variableDeclInfo = se.getVariableDeclInfo();
    // We mark this variableDeclInfo as being referenced.
    referenced.add(variableDeclInfo);

    // If we find that a variable is used in an l-value context, we cannot inline its initializer.
    if (!inLValueContext()) {
      // This is fine.
      return;
    }
    for (List<VariableDeclInfo> vdis : relevantDeclInfosStack) {
      if (vdis.contains(variableDeclInfo)) {
        vdis.remove(variableDeclInfo);
        return;
      }
    }
  }

  private boolean allowedToReduce(VariableDeclInfo variableDeclInfo) {
    if (context.reduceEverywhere()) {
      return true;
    }
    if (injectionTracker.enclosedByDeadCodeInjection()) {
      return true;
    }
    if (injectionTracker.underUnreachableSwitchCase()) {
      return true;
    }
    if (enclosingFunctionIsDead()) {
      return true;
    }
    if (StmtReductionOpportunities.isLooplimiter(variableDeclInfo.getName())) {
      // Do not mess with loop limiters.
      return false;
    }
    if (initializerIsScalarAndSideEffectFree(variableDeclInfo)) {
      return true;
    }
    return false;
  }

  private List<VariableDeclInfo> topOfStack() {
    return relevantDeclInfosStack.get(relevantDeclInfosStack.size() - 1);
  }

}
