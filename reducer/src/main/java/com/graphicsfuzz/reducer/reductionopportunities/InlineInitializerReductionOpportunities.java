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
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.StatsVisitor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class InlineInitializerReductionOpportunities
      extends ReductionOpportunitiesBase<SimplifyExprReductionOpportunity> {

  private static final int INITIALIZER_NODE_LIMIT = 10;

  // All variable identifier expressions that can be potentially replaced with initializers.
  private final List<Pair<VariableDeclInfo, VariableIdentifierExpr>> inlineableUsages;

  // A blacklist of variable declarations that we realise we must not inline references to after
  // all (used to filter the list of potentially inlineable uses).
  private final Set<VariableDeclInfo> blackList;

  private InlineInitializerReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
    this.inlineableUsages = new LinkedList<>();
    this.blackList = new HashSet<>();
  }

  static List<SimplifyExprReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<SimplifyExprReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    InlineInitializerReductionOpportunities finder =
          new InlineInitializerReductionOpportunities(tu, context);
    finder.visit(tu);
    finder.addOpportunities();
    return finder.getOpportunities();
  }

  private void addOpportunities() {
    for (Pair<VariableDeclInfo, VariableIdentifierExpr> pair : inlineableUsages) {
      if (!blackList.contains(pair.getLeft())) {
        addOpportunity(new SimplifyExprReductionOpportunity(
            parentMap.getParent(pair.getRight()),
            new ParenExpr(((ScalarInitializer) pair.getLeft().getInitializer()).getExpr().clone()),
            pair.getRight(),
            getVistitationDepth()));
      }
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
    if (!allowedToReduce(variableDeclInfo)) {
      return;
    }

    // For each variable name referenced in the initializer, we check that there is no name
    // shadowing of the name in the current scope.  We could do something more refined here, to
    // allow some shadowing, but it is simpler to just disallow shadowing completely.
    if (new CheckPredicateVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        if (currentScope.isShadowed(variableIdentifierExpr.getName())) {
          predicateHolds();
        }
      }
    }.test(variableDeclInfo.getInitializer())) {
      return;
    }

    if (inLValueContext()) {
      if (!context.reduceEverywhere() && !currentProgramPointHasNoEffect()) {
        // The declaration is used as an l-value.  To preserve semantics we cannot inline its
        // initializer.  For example, in:
        //   int x = 2;
        //   x += 2;
        //   x += x;
        // we end up with x == 8, but if we would inline the initializer to the r-value usage of
        // x we would get x == 6.
        blackList.add(variableDeclInfo);
      }
      return;
    }
    inlineableUsages.add(new ImmutablePair<>(variableDeclInfo, variableIdentifierExpr));
  }

  private boolean allowedToReduce(VariableDeclInfo variableDeclInfo) {
    if (!variableDeclInfo.hasInitializer()) {
      return false;
    }
    if (initializerIsTooBig(variableDeclInfo.getInitializer())) {
      return false;
    }
    if (context.reduceEverywhere()) {
      return true;
    }
    if (currentProgramPointHasNoEffect()) {
      return true;
    }
    if (StmtReductionOpportunities.isLooplimiter(variableDeclInfo.getName())) {
      // Do not mess with loop limiters.
      return false;
    }
    if (initializerIsScalarAndSideEffectFree(variableDeclInfo)
        && !referencesVariableIdentifier(variableDeclInfo.getInitializer())) {
      // We need to be careful about inlining e.g.: "int x = y;", because if y is then modified,
      // inlined uses of x would get the new value of y.
      return true;
    }
    return false;
  }

  private boolean referencesVariableIdentifier(Initializer initializer) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        predicateHolds();
      }
    }.test(initializer);
  }

  private boolean currentProgramPointHasNoEffect() {
    if (injectionTracker.enclosedByDeadCodeInjection()) {
      return true;
    }
    if (injectionTracker.underUnreachableSwitchCase()) {
      return true;
    }
    if (enclosingFunctionIsDead()) {
      return true;
    }
    return false;
  }

  private boolean initializerIsTooBig(Initializer initializer) {
    // We need to be careful not to inline large initializers.  Doing so could really slow down
    // reduction.  E.g. we might have a declaration that we'll soon be able to delete once we hack
    // away all of its uses.  But if it has a huge initializer we might first inline that in many
    // places and then spend a terribly long time performing expression simplification on the
    // resulting expansions.
    return new StatsVisitor(initializer).getNumNodes() > INITIALIZER_NODE_LIMIT;
  }

}
