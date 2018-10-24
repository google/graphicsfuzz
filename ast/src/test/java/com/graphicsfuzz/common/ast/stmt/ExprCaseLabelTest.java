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

package com.graphicsfuzz.common.ast.stmt;

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

public class ExprCaseLabelTest {

  @Test
  public void testReplaceChild() throws Exception {
    final String before = "void main() {"
          + "  switch(0) {"
          + "    case 1:"
          + "      return;"
          + "    default:"
          + "      break;"
          + "  }"
          + "}";
    final String after = "void main() {"
          + "  switch(0) {"
          + "    case 2:"
          + "      return;"
          + "    default:"
          + "      break;"
          + "  }"
          + "}";

    final TranslationUnit tu = ParseHelper.parse(before);

    new StandardVisitor() {
      @Override
      public void visitExprCaseLabel(ExprCaseLabel exprCaseLabel) {
        super.visitExprCaseLabel(exprCaseLabel);
        exprCaseLabel.replaceChild(exprCaseLabel.getExpr(), new IntConstantExpr("2"));
      }
    }.visit(tu);

    CompareAstsDuplicate.assertEqualAsts(after, tu);
  }

}