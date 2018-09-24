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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.ContinueStmt;
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.MutationReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunities;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunitiesBase;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunityContext;
import com.graphicsfuzz.reducer.reductionopportunities.StmtReductionOpportunities;
import com.graphicsfuzz.reducer.reductionopportunities.StmtReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.UnwrapReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.VectorizationReductionOpportunity;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportTransformationsPresent {

  public static void main(String[] args) throws IOException, ParseTimeoutException {
    if (args.length != 1) {
      System.err.println("Usage: ReportShaderSize <file>");
      System.exit(1);
    }
    File inputFile = new File(args[0]);
    TranslationUnit tu = ParseHelper.parse(inputFile, true);
    List<IReductionOpportunity> ros = ReductionOpportunities
          .getReductionOpportunities(tu,
                new ReductionOpportunityContext(false,
                ShadingLanguageVersion.getGlslVersionFromShader(inputFile), new RandomWrapper(),
                      new IdGenerator()));

    boolean mutationsPresent = false;
    boolean wrappingPresent = false;
    boolean vectorizationPresent = false;
    boolean liveCodePresent = false;
    boolean deadJumpPresent = false;
    boolean deadInjectionPresent = false;
    boolean jumpInsideDeadInjectionPresent = false;

    Map<Class, Integer> countPerClass = new HashMap<>();

    for (IReductionOpportunity opportunity : ros) {

      if (!countPerClass.containsKey(opportunity.getClass())) {
        countPerClass.put(opportunity.getClass(), 0);
      }
      countPerClass.put(opportunity.getClass(), countPerClass.get(opportunity.getClass()) + 1);

      if (opportunity instanceof MutationReductionOpportunity) {
        mutationsPresent = true;
      }
      if (opportunity instanceof UnwrapReductionOpportunity) {
        wrappingPresent = true;
      }
      if (opportunity instanceof VectorizationReductionOpportunity) {
        vectorizationPresent = true;
      }
      if (opportunity instanceof StmtReductionOpportunity) {
        StmtReductionOpportunity stmtReductionOpportunity = (StmtReductionOpportunity) opportunity;
        if (StmtReductionOpportunities.isLiveCodeInjection(stmtReductionOpportunity.getChild())) {
          liveCodePresent = true;
        }
        if (ReductionOpportunitiesBase.isDeadCodeInjection(stmtReductionOpportunity.getChild())) {
          if (isExactlyDeadJump(stmtReductionOpportunity.getChild())) {
            deadJumpPresent = true;
          } else {
            deadInjectionPresent = true;
            if (containsJump(stmtReductionOpportunity.getChild())) {
              jumpInsideDeadInjectionPresent = true;
            }
          }
        }

      }
    }

    System.out.println("{\n"
          + "\"mutations\": " + mutationsPresent + ",\n"
          + "\"live\": " + liveCodePresent + ",\n"
          + "\"vectorization\": " + vectorizationPresent + ",\n"
          + "\"wrap\": " + wrappingPresent + ",\n"
          + "\"solo_dead_jump\": " + deadJumpPresent + ",\n"
          + "\"dead\": " + deadInjectionPresent + ",\n"
          + "\"dead_w_non_solo_jump\": " + jumpInsideDeadInjectionPresent + "\n"
          + "}");

    System.out.println(countPerClass);

  }

  private static boolean containsJump(Stmt stmt) {
    return new StandardVisitor() {
      private boolean found = false;

      @Override
      public void visitBreakStmt(BreakStmt breakStmt) {
        found = true;
      }

      @Override
      public void visitContinueStmt(ContinueStmt continueStmt) {
        found = true;
      }

      @Override
      public void visitReturnStmt(ReturnStmt returnStmt) {
        found = true;
      }

      @Override
      public void visitDiscardStmt(DiscardStmt discardStmt) {
        found = true;
      }

      public boolean hasJump() {
        visit(stmt);
        return found;
      }
    }.hasJump();
  }

  private static boolean isExactlyDeadJump(Stmt stmt) {
    assert ReductionOpportunitiesBase.isDeadCodeInjection(stmt);
    if (isJump(((IfStmt) stmt).getThenStmt())) {
      return true;
    }
    assert ((IfStmt) stmt).getThenStmt() instanceof BlockStmt;
    BlockStmt block = (BlockStmt) ((IfStmt) stmt).getThenStmt();
    return block.getNumStmts() == 1 && isJump(block.getStmt(0));
  }

  private static boolean isJump(Stmt stmt) {
    return stmt instanceof ReturnStmt || stmt instanceof DiscardStmt
          || stmt instanceof BreakStmt || stmt instanceof ContinueStmt;
  }

}
