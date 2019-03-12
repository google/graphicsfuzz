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
import java.util.List;

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
  protected void visitChildOfBlock(BlockStmt block, int index) {
    final Stmt child = block.getStmt(index);
    if (isNonEmptyBlockStmtWithoutTopLevelDeclaration(child)) {
      addOpportunity(
            new UnwrapReductionOpportunity(child, ((BlockStmt) child).getStmts(),
                  parentMap,
                  getVistitationDepth()));
    }
  }

  private boolean isNonEmptyBlockStmtWithoutTopLevelDeclaration(Stmt stmt) {
    if (!(stmt instanceof BlockStmt)) {
      return false;
    }
    final BlockStmt blockStmt = (BlockStmt) stmt;
    return blockStmt.getNumStmts() > 0 && blockStmt.getStmts().stream()
          .noneMatch(item -> item instanceof DeclarationStmt);
  }

  private boolean isUnwrappable(LoopStmt loop) {
    return MacroNames.isLoopWrapper(loop.getCondition()) && !loop
          .containsDirectBreakOrContinueStmt();
  }

}
