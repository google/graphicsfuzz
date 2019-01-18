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

package com.graphicsfuzz.generator.transformation.injection;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InjectionPointsTest {

  @Test
  public void testNoInjectAtStartOfSwitch() throws Exception {
    final String prog = "void main() { /* injection point */ switch(1) { /* not injection point */ default: /* injection point */ break; /* injection point */ } /* injection point */ }";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<IInjectionPoint> injectionPointList = new InjectionPoints(tu, new RandomWrapper(0), item -> true).getAllInjectionPoints();
    assertEquals(4, injectionPointList.size());
  }

  @Test
  public void testNullStmtInjectionPoints() throws Exception {
    final String prog = "void main() { ; ; ; ; ; ; }";
    final String expected = "void main() { ; ; ; 1; ; ; }";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<IInjectionPoint> injectionPointList = new InjectionPoints(tu, new RandomWrapper(0), item -> true).getAllInjectionPoints();
    assertEquals(7, injectionPointList.size());
    injectionPointList.get(3).replaceNext(new ExprStmt(new IntConstantExpr("1")));
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testDiscardStmtInjectionPoints() throws Exception {
    final String prog = "void main() { discard; discard; discard; discard; discard; discard; }";
    final String expected = "void main() { discard; discard; discard; 1; discard; discard; }";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<IInjectionPoint> injectionPointList = new InjectionPoints(tu, new RandomWrapper(0), item -> true).getAllInjectionPoints();
    assertEquals(7, injectionPointList.size());
    injectionPointList.get(3).replaceNext(new ExprStmt(new IntConstantExpr("1")));
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testBreakStmtInjectionPoints() throws Exception {
    final String prog = "void main() { for(;;) { break; break; break; } };";
    final String expected = "void main() { for(;;) { 1; break; break; } };";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<IInjectionPoint> injectionPointList = new InjectionPoints(tu, new RandomWrapper(0), item -> true).getAllInjectionPoints();
    assertEquals(6, injectionPointList.size());
    injectionPointList.get(2).replaceNext(new ExprStmt(new IntConstantExpr("1")));
    //CompareAsts.assertEqualAsts(expected, tu);
  }

}
