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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.util.Constants;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IdentityMutationReductionOpportunitiesTest {

  @Test
  public void testReductionOpportunityForIdentityAsIfGuard() throws Exception {
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  if(" + Constants.GLF_IDENTITY + "(true, true && true)) { }\n"
        + "}\n";
    final String afterReduction = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  if(true) { }\n"
        + "}\n";
    checkOneReductionOpportunity(program, afterReduction);
  }

  @Test
  public void testReductionOpportunityForIdentityAsWhileGuard() throws Exception {
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  while(" + Constants.GLF_IDENTITY + "(true, true && true)) { }\n"
        + "}\n";
    final String afterReduction = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  while(true) { }\n"
        + "}\n";
    checkOneReductionOpportunity(program, afterReduction);
  }

  @Test
  public void testReductionOpportunityForIdentityAsDoWhileGuard() throws Exception {
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  do { } while(" + Constants.GLF_IDENTITY + "(true, true && true));\n"
        + "}\n";
    final String afterReduction = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  do { } while(true);\n"
        + "}\n";
    checkOneReductionOpportunity(program, afterReduction);
  }

  @Test
  public void testReductionOpportunityForIdentityAsForInit() throws Exception {
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  for(" + Constants.GLF_IDENTITY + "(i = 0, 1*(i = 0)); i < 10; i++) { }\n"
        + "}\n";
    final String afterReduction = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  for(i = 0; i < 10; i++) { }\n"
        + "}\n";
    checkOneReductionOpportunity(program, afterReduction);
  }

  @Test
  public void testReductionOpportunityForIdentityAsForGuard() throws Exception {
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  for(i = 0; " + Constants.GLF_IDENTITY + "(i < 10, true && (i < 10)); i++) { }\n"
        + "}\n";
    final String afterReduction = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  for(i = 0; i < 10; i++) { }\n"
        + "}\n";
    checkOneReductionOpportunity(program, afterReduction);
  }

  @Test
  public void testReductionOpportunityForIdentityAsForIncrement() throws Exception {
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  for(i = 0; i < 10; " + Constants.GLF_IDENTITY + "(i++, 1*(i++))) { }\n"
        + "}\n";
    final String afterReduction = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  for(i = 0; i < 10; i++) { }\n"
        + "}\n";
    checkOneReductionOpportunity(program, afterReduction);
  }

  @Test
  public void testReductionOpportunityForIdentityAsFunctionParameter() throws Exception {
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  sin(" + Constants.GLF_IDENTITY + "(2.0, 1.0 * 2.0));\n"
        + "}\n";
    final String afterReduction = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  sin(2.0);\n"
        + "}\n";
    checkOneReductionOpportunity(program, afterReduction);
  }

  @Test
  public void testReductionOpportunityForIdentityAsExprStmt() throws Exception {
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  " + Constants.GLF_IDENTITY + "(2.0, 1.0 * 2.0);\n"
        + "}\n";
    final String afterReduction = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  2.0;\n"
        + "}\n";
    checkOneReductionOpportunity(program, afterReduction);
  }

  private ReducerContext getReducerContext() {
    return new ReducerContext(false,
        ShadingLanguageVersion.ESSL_310,
        new RandomWrapper(),
        new IdGenerator(),
        true);
  }

  private void checkOneReductionOpportunity(String program, String afterReduction) throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    final ShaderJob shaderJob = MakeShaderJobFromFragmentShader.make(
        ParseHelper.parse(program));
    final List<IdentityMutationReductionOpportunity> ops =
        IdentityMutationReductionOpportunities.findOpportunities(shaderJob,
            getReducerContext());
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(afterReduction, shaderJob.getFragmentShader().get());
  }

}
