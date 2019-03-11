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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.util.Constants;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.common.util.ParseHelper;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiveOutputVariableWriteReductionOpportunitiesTest {

  @Test
  public void testLiveGLFragColorWriteOpportunity() throws Exception {
    final String backupName = Constants.GLF_OUT_VAR_BACKUP_PREFIX + OpenGlConstants.GL_FRAG_COLOR;
    final String program = "void main() {"
        + "  {"
        + "      vec4 " + backupName + ";"
        + "      " + backupName + " = " + OpenGlConstants.GL_FRAG_COLOR
        + ";"
        + "      " + OpenGlConstants.GL_FRAG_COLOR + " = " + backupName
        + ";"
        + "  }"
        + "}";
    final String reducedProgram = "void main() {"
          + "  {"
          + "  }"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<LiveOutputVariableWriteReductionOpportunity> ops =
          LiveOutputVariableWriteReductionOpportunities
              .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, null, null, null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(reducedProgram, tu);
  }

  @Test
  public void testLiveGLFragColorWriteOpportunityAtRootOfMain() throws Exception {
    // Checks that we can eliminate a live color write if it is at the root of a function, i.e.
    // not enclosed in any additional block.
    final String backupName = Constants.GLF_OUT_VAR_BACKUP_PREFIX + OpenGlConstants.GL_FRAG_COLOR;
    final String program = "void main() {"
        + "  vec4 " + backupName + ";"
        + "  " + backupName + " = " + OpenGlConstants.GL_FRAG_COLOR + ";"
        + "  " + OpenGlConstants.GL_FRAG_COLOR + " = " + backupName + ";"
        + "}";
    final String reducedProgram = "void main() {"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<LiveOutputVariableWriteReductionOpportunity> ops =
        LiveOutputVariableWriteReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, null, null, null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(reducedProgram, tu);
  }

  @Test
  public void testOpportunityIsPresent1() throws Exception {
    final String backupName = Constants.GLF_OUT_VAR_BACKUP_PREFIX + OpenGlConstants.GL_FRAG_COLOR;
    final String program = "void main() {\n"
        + " {\n"
        + "        vec4 " + backupName + ";\n"
        + "        " + backupName + " = " + OpenGlConstants.GL_FRAG_COLOR + ";\n"
        + "        " + OpenGlConstants.GL_FRAG_COLOR
        + " = atan(vec4(-5.2, -8.2, 62.53, -205.377));\n"
        + "        if(_GLF_WRAPPED_IF_TRUE(true))\n"
        + "         {\n"
        + "          " + OpenGlConstants.GL_FRAG_COLOR + " = " + backupName + ";\n"
        + "         }\n"
        + " }\n"
        + "}\n";
    final String expected = "void main() { { } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<LiveOutputVariableWriteReductionOpportunity> ops =
      LiveOutputVariableWriteReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(false, null, null, null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testOpportunityIsPresent2() throws Exception {
    final String backupName = Constants.GLF_OUT_VAR_BACKUP_PREFIX + OpenGlConstants.GL_FRAG_COLOR;
    final String program = "void main() {\n"
        + " {\n"
        + "        vec4 " + backupName + ";\n"
        + "        " + backupName + " = gl_FragColor;\n"
        + "        gl_FragColor = atan(vec4(-5.2, -8.2, 62.53, -205.377));\n"
        + "        gl_FragColor = " + backupName + ";\n"
        + " }\n"
        + "}\n";
    final String expected = "void main() { { } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<LiveOutputVariableWriteReductionOpportunity> ops =
          LiveOutputVariableWriteReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false, null, null, null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testOpportunityIsPresent3() throws Exception {
    final String backupName = Constants.GLF_OUT_VAR_BACKUP_PREFIX + OpenGlConstants.GL_FRAG_COLOR;
    final String program = "void main() {\n"
          + " {\n"
          + "        vec4 " + backupName + ";\n"
          + "        " + backupName + " = gl_FragColor;\n"
          + "        gl_FragColor = atan(vec4(-5.2, -8.2, 62.53, -205.377));\n"
          + "        if(_GLF_WRAPPED_IF_TRUE(true))\n"
          + "          gl_FragColor = " + backupName + ";\n"
          + " }\n"
          + "}\n";
    final String expected = "void main() { { } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<LiveOutputVariableWriteReductionOpportunity> ops =
          LiveOutputVariableWriteReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false, null, null, null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
