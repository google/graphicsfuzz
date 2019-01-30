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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public final class AddBraces {

  private AddBraces() {
    // Utility class; should not be constructable.
  }

  /**
   * Add braces to all conditional and loop statements.
   *
   * @param tu The translation unit to be transformed
   * @return A cloned translation unit with braces added
   */
  public static TranslationUnit transform(TranslationUnit tu) {

    return new StandardVisitor() {

      @Override
      public void visitIfStmt(IfStmt ifStmt) {
        super.visitIfStmt(ifStmt);
        if (!isBlock(ifStmt.getThenStmt())) {
          ifStmt.setThenStmt(makeBlock(ifStmt.getThenStmt()));
        }
        if (ifStmt.hasElseStmt() && !isBlock(ifStmt.getElseStmt())) {
          ifStmt.setElseStmt(makeBlock(ifStmt.getElseStmt()));
        }
      }

      @Override
      public void visitForStmt(ForStmt forStmt) {
        super.visitForStmt(forStmt);
        handleLoopStmt(forStmt);
      }

      @Override
      public void visitWhileStmt(WhileStmt whileStmt) {
        super.visitWhileStmt(whileStmt);
        handleLoopStmt(whileStmt);
      }

      @Override
      public void visitDoStmt(DoStmt doStmt) {
        super.visitDoStmt(doStmt);
        handleLoopStmt(doStmt);
      }

      private boolean isBlock(Stmt stmt) {
        return stmt instanceof BlockStmt;
      }

      private Stmt makeBlock(Stmt stmt) {
        return new BlockStmt(Arrays.asList(stmt), true);
      }

      private void handleLoopStmt(LoopStmt loopStmt) {
        if (!isBlock(loopStmt.getBody())) {
          loopStmt.setBody(makeBlock(loopStmt.getBody()));
        }
      }

      public TranslationUnit addBraces(TranslationUnit tu) {
        visit(tu);
        return tu;
      }

    }.addBraces(tu.clone());

  }

  public static void main(String[] args) throws IOException, ParseTimeoutException,
      InterruptedException, GlslParserException {
    TranslationUnit tu = ParseHelper.parse(new File(args[0]));
    new PrettyPrinterVisitor(System.out).visit(transform(tu));
  }

}
