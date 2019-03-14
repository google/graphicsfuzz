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
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UnwrapReductionOpportunities
      extends ReductionOpportunitiesBase<UnwrapReductionOpportunity> {

  private UnwrapReductionOpportunities(TranslationUnit tu, ReducerContext context) {
    super(tu, context);
  }

  /**
   * Find all unwrap opportunities for the given translation unit.
   *
   * @param shaderJob The shader job to be searched.
   * @param context Includes info such as whether we reduce everywhere or only reduce injections
   * @return The opportunities that can be reduced
   */
  static List<UnwrapReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<UnwrapReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    UnwrapReductionOpportunities finder =
          new UnwrapReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    super.visitIfStmt(ifStmt);
    if (MacroNames.isIfWrapperTrue(ifStmt.getCondition())) {
      addOpportunity(new UnwrapReductionOpportunity(ifStmt, ifStmt.getThenStmt(), parentMap,
            getVistitationDepth()));
    } else if (MacroNames.isIfWrapperFalse(ifStmt.getCondition())) {
      addOpportunity(new UnwrapReductionOpportunity(ifStmt, ifStmt.getElseStmt(), parentMap,
            getVistitationDepth()));
    }
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    super.visitForStmt(forStmt);
    if (isUnwrappable(forStmt)) {
      Stmt wrappee = forStmt.getBody();
      if (wrappee instanceof BlockStmt) {
        // A ForStmt's block does not introduce a new scope, but we need it to when unwrapped.
        assert !((BlockStmt) wrappee).introducesNewScope();
        wrappee = new BlockStmt(((BlockStmt) wrappee).getStmts(), true);
      }
      addOpportunity(new UnwrapReductionOpportunity(forStmt, wrappee, parentMap,
            getVistitationDepth()));
    }
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    super.visitDoStmt(doStmt);
    if (isUnwrappable(doStmt)) {
      addOpportunity(new UnwrapReductionOpportunity(doStmt, doStmt.getBody(), parentMap,
            getVistitationDepth()));
    }
  }

  @Override
  protected void visitChildOfBlock(BlockStmt block, int childIndex) {
    final Stmt child = block.getStmt(childIndex);
    if (!(child instanceof BlockStmt)) {
      return;
    }
    final BlockStmt childAsBlock = (BlockStmt) child;
    if (childAsBlock.getNumStmts() == 0) {
      // We do not unwrap an empty block statement, as there would be nothing to unwrap.
      // Other reductions take responsibility for removing a redundant { }.
      return;
    }
    // If unwrapping the child block may lead to name collisions then it cannot be done.
    // A collision can be with a variable name already in scope, or a variable name that will come
    // into scope later in the parent block.
    final Set<String> namesDeclaredDirectlyByParentOrInScopeAlready = new HashSet<>();
    namesDeclaredDirectlyByParentOrInScopeAlready.addAll(
        UnwrapReductionOpportunity.getNamesDeclaredDirectlyByBlock(block));
    namesDeclaredDirectlyByParentOrInScopeAlready.addAll(currentScope.namesOfAllVariablesInScope());
    final Set<String> namesDeclaredDirectlyByChild =
        UnwrapReductionOpportunity.getNamesDeclaredDirectlyByBlock(childAsBlock);
    if (Collections.disjoint(namesDeclaredDirectlyByParentOrInScopeAlready,
        namesDeclaredDirectlyByChild)) {
      addOpportunity(
          new UnwrapReductionOpportunity(child, childAsBlock.getStmts(),
              parentMap,
              getVistitationDepth()));
    }
  }

  private boolean isUnwrappable(LoopStmt loop) {
    return MacroNames.isLoopWrapper(loop.getCondition()) && !loop
          .containsDirectBreakOrContinueStmt();
  }

}
