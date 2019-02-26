package com.graphicsfuzz.common.ast.visitors;

import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

import static org.junit.Assert.fail;

public class StandardVisitorTest {

  @Test
  public void testNoArrayInfo() throws Exception {
    new StandardVisitor() {

      @Override
      public void visitArrayInfo(ArrayInfo arrayInfo) {
        fail("There is no array info in the AST, so this method should not get called.");
      }

    }.visit(ParseHelper.parse("void foo(int x) { }"));
  }

}
