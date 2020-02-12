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
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.Arrays;

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
    for (TranslationUnit tu : shaderJob.getShaders()) {
      Declaration firstNonPrecisionDeclaration = null;
      for (Declaration decl : tu.getTopLevelDeclarations()) {
        if (decl instanceof PrecisionDeclaration) {
          continue;
        }
        firstNonPrecisionDeclaration = decl;
        break;
      }
      // Add loop bound variable
      tu.addDeclarationBefore(new VariablesDeclaration(new QualifiedType(BasicType.INT,
              Arrays.asList(TypeQualifier.CONST)),new VariableDeclInfo(loopBoundName,null,
              new Initializer(new IntConstantExpr(new Integer(loopLimit).toString())))),
          firstNonPrecisionDeclaration);
      // Add loop count variable
      tu.addDeclarationBefore(new VariablesDeclaration(BasicType.INT,
              new VariableDeclInfo(loopCountName,null,
              new Initializer(new IntConstantExpr("0")))),
          firstNonPrecisionDeclaration);
      // Traverse tree for the rest of the transformations
      new StandardVisitor() {
        // Converts loop condition from "x" to "(x) && (lc<lb)"
        Expr buildCondition(Expr originalCondition) {
          return new BinaryExpr(
              new ParenExpr(originalCondition),
              new ParenExpr(
                  new BinaryExpr(new VariableIdentifierExpr(loopCountName),
                      new VariableIdentifierExpr(loopBoundName),
                      BinOp.LT)),
              BinOp.LAND);
        }

        // Check if the loop condition is just "false"
        boolean isTrueLoop(Expr conditionExpr) {
          return !(conditionExpr instanceof BoolConstantExpr
              && !((BoolConstantExpr)conditionExpr).getIsTrue());
        }

        @Override
        public void visitForStmt(ForStmt forStmt) {
          if (isTrueLoop(forStmt.getCondition())) {
            forStmt.setCondition(buildCondition(forStmt.getCondition()));
            // Add block statement if it's missing
            if (!(forStmt.getBody() instanceof BlockStmt)) {
              forStmt.setBody(new BlockStmt(Arrays.asList(forStmt.getBody()), true));
            }
            // Add loop count increment a start of body block
            ((BlockStmt) forStmt.getBody()).insertStmt(0,
                new ExprStmt(new UnaryExpr(new VariableIdentifierExpr(loopCountName),
                    UnOp.POST_INC)));
          }
          super.visitForStmt(forStmt);
        }

        @Override
        public void visitWhileStmt(WhileStmt whileStmt) {
          if (isTrueLoop(whileStmt.getCondition())) {
            whileStmt.setCondition(buildCondition(whileStmt.getCondition()));
            // Add block statement if it's missing. Note that in while loops new scope starts
            // before the braces, so the braces do not introduce new scope.
            if (!(whileStmt.getBody() instanceof BlockStmt)) {
              whileStmt.setBody(new BlockStmt(Arrays.asList(whileStmt.getBody()), false));
            }
            // Add loop count increment a start of body block
            ((BlockStmt) whileStmt.getBody()).insertStmt(0,
                new ExprStmt(new UnaryExpr(new VariableIdentifierExpr(loopCountName),
                    UnOp.POST_INC)));
          }
          super.visitWhileStmt(whileStmt);
        }

        @Override
        public void visitDoStmt(DoStmt doStmt) {
          if (isTrueLoop(doStmt.getCondition())) {
            doStmt.setCondition(buildCondition(doStmt.getCondition()));
            // Do statement always has a block statement, so it's not checked for here
            // Add loop count increment a start of body block
            ((BlockStmt) doStmt.getBody()).insertStmt(0,
                new ExprStmt(new UnaryExpr(new VariableIdentifierExpr(loopCountName),
                    UnOp.POST_INC)));
          }
          super.visitDoStmt(doStmt);
        }
      }.visit(tu);
    }
  }
}
