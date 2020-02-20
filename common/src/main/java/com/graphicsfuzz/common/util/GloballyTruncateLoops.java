/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.Arrays;
import java.util.Collections;

public class GloballyTruncateLoops {

  /**
   * Adds logic to limit the number of iterations that each shader in a given shader job can
   * execute.
   * - A global constant, loopBoundName, is declared and initialized to loopLimit.
   * - A global variable, loopCountName, is declared and initialized to 0.
   * - For each loop whose guard is not literally 'false':
   *   - The loop guard 'e' is changed to '(e) && (loopBoundName < loopLimiterName)'
   *   - A statement 'loopCountName++' is added at the start of the body of the loop
   *   - The loop body is changed from a single statement to a block, if needed, to
   *     accommodate this.
   * @param shaderJob A shader job to be mutated.
   * @param loopLimit The maximum number of loop iterations that any shader in the shader job
   *                  should be allowed to make.
   * @param loopCountName The name of a global variable that will be added to each shader in the
   *                        shader job to store the number of loop iterations executed so far.
   * @param loopBoundName The name of a global constant that will be set to loopLimit.
   */
  public static void truncate(ShaderJob shaderJob, int loopLimit, String loopCountName,
                              String loopBoundName) {

    // Consider each shader in the shader job.
    for (TranslationUnit tu : shaderJob.getShaders()) {

      // Truncate any non-trivial loop in the translation unit, and record whether any truncation
      // was in fact necessary.
      final boolean globalDeclarationsAreNecessary = new StandardVisitor() {

        // This field gets set to true when a truncation is applied.  Its final value tells us
        // whether we need to add global declarations for the loop limiter (we don't if there is no
        // truncation).
        private boolean appliedAtLeastOneTruncation = false;

        // Converts loop condition from "x" to "(x) && (lc<lb)".
        private Expr buildCondition(Expr originalCondition) {
          return new BinaryExpr(
              new ParenExpr(originalCondition),
              new ParenExpr(
                  new BinaryExpr(new VariableIdentifierExpr(loopCountName),
                      new VariableIdentifierExpr(loopBoundName),
                      BinOp.LT)),
              BinOp.LAND);
        }

        // Check if the loop condition is just "false".
        private boolean isFalseLiteral(Expr conditionExpr) {
          return !(conditionExpr instanceof BoolConstantExpr
              && !((BoolConstantExpr)conditionExpr).getIsTrue());
        }

        // Common code for all loop structures
        private void handleLoopStmt(LoopStmt loopStmt, boolean newScope) {
          if (isFalseLiteral(loopStmt.getCondition())) {
            // We are truncating a loop - record the fact that at least one truncation has been
            // applied.
            appliedAtLeastOneTruncation = true;
            loopStmt.setCondition(buildCondition(loopStmt.getCondition()));
            // Add block statement if it's missing.
            if (!(loopStmt.getBody() instanceof BlockStmt)) {
              loopStmt.setBody(new BlockStmt(Collections.singletonList(loopStmt.getBody()),
                  newScope));
            }
            // Add loop count increment a start of body block.
            ((BlockStmt) loopStmt.getBody()).insertStmt(0,
                new ExprStmt(new UnaryExpr(new VariableIdentifierExpr(loopCountName),
                    UnOp.POST_INC)));
          }
        }

        @Override
        public void visitForStmt(ForStmt forStmt) {
          handleLoopStmt(forStmt, true);
          super.visitForStmt(forStmt);
        }

        @Override
        public void visitWhileStmt(WhileStmt whileStmt) {
          handleLoopStmt(whileStmt, false);
          super.visitWhileStmt(whileStmt);
        }

        @Override
        public void visitDoStmt(DoStmt doStmt) {
          handleLoopStmt(doStmt, true);
          super.visitDoStmt(doStmt);
        }

        private boolean truncate(TranslationUnit tu) {
          // Traverse the shader, truncating loops as needed, and return true if and only if at
          // least one loop was truncated.
          visit(tu);
          return appliedAtLeastOneTruncation;
        }

      }.truncate(tu);

      if (globalDeclarationsAreNecessary) {
        // Some loop was truncated, so we need to declare the global loop limiter variable and loop
        // bound constant.

        // We want to add the new declarations at the top of the module, but after any leading
        // precision declarations.
        Declaration firstNonPrecisionDeclaration = null;
        for (Declaration decl : tu.getTopLevelDeclarations()) {
          if (decl instanceof PrecisionDeclaration) {
            continue;
          }
          firstNonPrecisionDeclaration = decl;
          break;
        }
        assert firstNonPrecisionDeclaration != null;
        // Add loop bound variable.
        tu.addDeclarationBefore(new VariablesDeclaration(new QualifiedType(BasicType.INT,
                Arrays.asList(TypeQualifier.CONST)), new VariableDeclInfo(loopBoundName, null,
                new Initializer(new IntConstantExpr(new Integer(loopLimit).toString())))),
            firstNonPrecisionDeclaration);
        // Add loop count variable.
        tu.addDeclarationBefore(new VariablesDeclaration(BasicType.INT,
                new VariableDeclInfo(loopCountName, null,
                    new Initializer(new IntConstantExpr("0")))),
            firstNonPrecisionDeclaration);

      }
    }
  }
}
