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

package com.graphicsfuzz.common.typing;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

public class ScopeTrackingVisitorTest {

  @Test
  public void testSwitch() throws Exception {
    final String program = "void main() {"
        + "  switch(1) {"
        + "    default:"
        + "      int v0;"
        + "      switch(1) {"
        + "        default:"
        + "          int v0;"
        + "      }"
        + "  }"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    new ScopeTrackingVisitor() {
    }.visit(tu);

  }

}
