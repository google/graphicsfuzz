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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.io.File;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OutlineStatementMutationFinderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void outlineStatementMutations() throws Exception {
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
    List<OutlineStatementMutation> opportunities =
        new OutlineStatementMutationFinder(tu).findMutations();
    assertEquals(5, opportunities.size());

    // Check that we can apply them without throwing and exception.
    for (OutlineStatementMutation op : opportunities) {
      op.apply();
    }

  }

  @Test
  public void outlineStatementMutations2() throws Exception {
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
    List<OutlineStatementMutation> ops =
        new OutlineStatementMutationFinder(tu).findMutations();
    assertEquals(1, ops.size());
    ops.get(0).apply();

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProgram)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testApplyRepeatedly() throws Exception {
    // Checks that applying outlining a few times does not lead to an invalid shader.
    final int limit = 4;
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    StringBuilder program = new StringBuilder("#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {\n");
    for (int i = 0; i < limit; i++) {
      program.append("  int x" + i + ";\n");
    }
    for (int i = 0; i < limit; i++) {
      program.append("  x" + i + " = 0;\n");
    }
    program.append("}\n");
    final TranslationUnit tu = ParseHelper.parse(program.toString());

    // Check that structifying many times, with no memory in-between (other than the AST itself),
    // leads to valid shaders.
    for (int i = 0; i < limit; i++) {
      new OutlineStatementMutationFinder(tu)
          .findMutations()
          .get(0).apply();

      final File shaderJobFile = temporaryFolder.newFile("shaderjob" + i + ".json");
      fileOperations.writeShaderJobFile(new GlslShaderJob(Optional.empty(),
          new PipelineInfo("{}"),
          tu), shaderJobFile);
      assertTrue(fileOperations.areShadersValid(shaderJobFile, false));
    }
  }

}
