/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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
