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

package com.graphicsfuzz.generator.transformation.outliner;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OutlineStatementOpportunitiesTest {

  @Test
  public void outlineStatementOpportunities() throws Exception {
    String program = "struct S { int a; };"
        + "void main() {"
        + "  int x;"
        + "  int y = 4;" // Does not count - it's an initializer
        + "  int z = 5;" // Does not count - it's an initializer
        + "  x = x + y + z;" // Counts
        + "  float s, t, u;"
        + "  if (x > y) {"
        + "    s = 3.0;" // Counts
        + "    t = t + s * u;" // Counts
        + "  }"
        + "  int A[5];"
        + "  A[0] = z;" // Does not count; not direct variable assignment
        + "  z = A[0];" // Does not count; array used on RHS
        + "  S myS;"
        + "  myS = S(1);" // Counts
        + "  myS.a = 3;" // Does not count; not direct variable assignment
        + "  vec3 v;"
        + "  v.y = 1.2;" // Does not count; not direct variable assignment
        + "  gl_FragColor = vec4(0.0);" // Counts
        + "}";
    TranslationUnit tu = ParseHelper.parse(program);
    List<OutlineStatementOpportunity> opportunities = new OutlineStatementOpportunities(tu).getAllOpportunities();
    assertEquals(5, opportunities.size());

    // Check that we can apply them without throwing and exception.
    IdGenerator idGenerator = new IdGenerator();
    for (OutlineStatementOpportunity op : opportunities) {
      op.apply(idGenerator);
    }

  }

  @Test
  public void outlineStatementOpportunities2() throws Exception {
    String program = "void main() {"
        + "  gl_FragColor = gl_FragColor + vec4(0.0);"
        + "}";

    String expectedProgram = ""
        + "vec4 _GLF_outlined_0() {"
        + "  return gl_FragColor + vec4(0.0);"
        + "}"
        + "void main() {"
        + "  gl_FragColor = _GLF_outlined_0();"
        + "}";

    TranslationUnit tu = ParseHelper.parse(program);
    List<OutlineStatementOpportunity> ops = new OutlineStatementOpportunities(tu).getAllOpportunities();
    assertEquals(1, ops.size());
    ops.get(0).apply(new IdGenerator());

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProgram)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}