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

package com.graphicsfuzz.common.ast.inliner;

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

public class ReturnRemoverTest {

  @Test(expected = CannotRemoveReturnsException.class)
  public void testNoSwitch() throws Exception {
    final String program = "void main() { switch(2) { default: return; } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    final FunctionDefinition fd = findFunctionDefinition(tu, "main");
    ReturnRemover.removeReturns(fd, ShadingLanguageVersion.GLSL_440);
  }

  @Test
  public void testReturnsRemoved() throws Exception {
    final String program = "int foo() {"
          + "  int x = 2;"
          + "  int y = 3;"
          + "  x = y;"
          + "  if (x == y) {"
          + "    return 2;"
          + "  }"
          + "  if (x > y) {"
          + "    if (y > 4) {"
          + "      x = 3;"
          + "      return 5;"
          + "    } else {"
          + "      while(x < y) {"
          + "        x++;"
          + "        if (x == y) {"
          + "          return 3;"
          + "        }"
          + "        x++;"
          + "      }"
          + "    }"
          + "    return 0;"
          + "  }"
          + "}";
    final String expected = "int foo() {"
          + "  bool foo_has_returned = false;"
          + "  int foo_return_value;"
          + "  int x = 2;"
          + "  int y = 3;"
          + "  x = y;"
          + "  if (x == y) {"
          + "    {"
          + "      foo_return_value = 2;"
          + "      foo_has_returned = true;"
          + "    }"
          + "  }"
          + "  if(!foo_has_returned) {"
          + "    if (x > y) {"
          + "      if (y > 4) {"
          + "        x = 3;"
          + "        {"
          + "          foo_return_value = 5;"
          + "          foo_has_returned = true;"
          + "        }"
          + "      } else {"
          + "        while((!foo_has_returned) && x < y) {"
          + "          x++;"
          + "          if (x == y) {"
          + "            {"
          + "              foo_return_value = 3;"
          + "              foo_has_returned = true;"
          + "            }"
          + "          }"
          + "          if(!foo_has_returned) {"
          + "            x++;"
          + "          }"
          + "        }"
          + "      }"
          + "      if(!foo_has_returned) {"
          + "        {"
          + "          foo_return_value = 0;"
          + "          foo_has_returned = true;"
          + "        }"
          + "      }"
          + "    }"
          + "  }"
          + "  return foo_return_value;"
          + "}";

    final TranslationUnit tu = ParseHelper.parse(program);
    final FunctionDefinition fd = findFunctionDefinition(tu, "foo");
    ReturnRemover.removeReturns(fd, ShadingLanguageVersion.ESSL_300);
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }

  @Test
  public void testReturnsRemovedVoid() throws Exception {
    final String program = "void foo() {"
          + "  int x = 2;"
          + "  int y = 3;"
          + "  x = y;"
          + "  if (x == y) {"
          + "    return;"
          + "  } else {"
          + "    for (int i = 0; i < 100; i++) {"
          + "      if (i > x) {"
          + "        return;"
          + "      }"
          + "      y += i;"
          + "    }"
          + "  }"
          + "  x++;"
          + "}";
    final String expected = "void foo() {"
          + "  bool foo_has_returned = false;"
          + "  int x = 2;"
          + "  int y = 3;"
          + "  x = y;"
          + "  if (x == y) {"
          + "    foo_has_returned = true;"
          + "  } else {"
          + "    for (int i = 0; (!foo_has_returned) && i < 100; i++) {"
          + "      if (i > x) {"
          + "        foo_has_returned = true;"
          + "      }"
          + "      if (!foo_has_returned) {"
          + "        y += i;"
          + "      }"
          + "    }"
          + "  }"
          + "  if(!foo_has_returned) {"
          + "    x++;"
          + "  }"
          + "}";

    final TranslationUnit tu = ParseHelper.parse(program);
    final FunctionDefinition fd = findFunctionDefinition(tu, "foo");
    ReturnRemover.removeReturns(fd, ShadingLanguageVersion.ESSL_300);
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }

  @Test
  public void testReturnsRemovedGlsl100For() throws Exception {
    final String program = "void foo() {"
          + "  for(int i = 0; i < 10; i++) "
          + "    if (i > 5) {"
          + "      return;"
          + "    }"
          + "}";
    final String expected = "void foo() {"
          + "  bool foo_has_returned = false;"
          + "  for(int i = 0; i < 10; i++) {"
          + "    if (foo_has_returned) {"
          + "      break;"
          + "    }"
          + "    if (i > 5) {"
          + "      foo_has_returned = true;"
          + "    }"
          + "  }"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final FunctionDefinition fd = findFunctionDefinition(tu, "foo");
    ReturnRemover.removeReturns(fd, ShadingLanguageVersion.ESSL_100);
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }

  @Test
  public void testReturnsRemovedDoWhile() throws Exception {
    final String program = "int foo() {"
          + "  int x = 2;"
          + "  do {"
          + "    x++;"
          + "    if (x > 10) {"
          + "      return x;"
          + "    }"
          + "  } while (x < 100);"
          + "  return 10;"
          + "}";
    final String expected = "int foo() {"
          + "  bool foo_has_returned = false;"
          + "  int foo_return_value;"
          + "  int x = 2;"
          + "  do {"
          + "    x++;"
          + "    if (x > 10) {"
          + "      {"
          + "        foo_return_value = x;"
          + "        foo_has_returned = true;"
          + "       }"
          + "    }"
          + "  } while ((!foo_has_returned) && x < 100);"
          + "  if(!foo_has_returned) {"
          + "    {"
          + "      foo_return_value = 10;"
          + "      foo_has_returned = true;"
          + "    }"
          + "  }"
          + "  return foo_return_value;"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final FunctionDefinition fd = findFunctionDefinition(tu, "foo");
    ReturnRemover.removeReturns(fd, ShadingLanguageVersion.ESSL_300);
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }

  @Test
  public void testNoReturnNoOp() throws Exception {
    final String program = "void foo() {"
          + "  int x = 2;"
          + "  x++;"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final FunctionDefinition fd = findFunctionDefinition(tu, "foo");
    ReturnRemover.removeReturns(fd, ShadingLanguageVersion.ESSL_300);
    CompareAstsDuplicate.assertEqualAsts(program, tu);
  }

  @Test
  public void testSoloReturnNoOp() throws Exception {
    final String program = "int foo() {"
          + "  return 1 + 2 + 3 + 4;"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final FunctionDefinition fd = findFunctionDefinition(tu, "foo");
    ReturnRemover.removeReturns(fd, ShadingLanguageVersion.ESSL_300);
    CompareAstsDuplicate.assertEqualAsts(program, tu);
  }

  private FunctionDefinition findFunctionDefinition(TranslationUnit tu, String name) {
    return new StandardVisitor() {
      private FunctionDefinition fd = null;

      @Override
      public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
        super.visitFunctionDefinition(functionDefinition);
        if (functionDefinition.getPrototype().getName().equals(name)) {
          fd = functionDefinition;
        }
      }

      private FunctionDefinition find(IAstNode node) {
        visit(node);
        return fd;
      }
    }.find(tu);
  }

}