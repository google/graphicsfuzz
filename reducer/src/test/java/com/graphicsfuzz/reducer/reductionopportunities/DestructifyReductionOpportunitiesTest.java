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
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DestructifyReductionOpportunitiesTest {

  @Test
  public void checkForVariableInScope() throws Exception {
    final String program = ""
          + "struct _GLF_struct_1 {\n"
          + "  float dist;\n"
          + "};\n"
          + "void main() {\n"
          + "  int dist;\n"
          + "  _GLF_struct_1 _GLF_struct_replacement_2 = _GLF_struct_1(1.0);\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<DestructifyReductionOpportunity> ops = DestructifyReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
          ShadingLanguageVersion.ESSL_100, new RandomWrapper(), null, true));
    // There should be no opportunities as there is already a variable called 'dist' in scope
    assertEquals(0, ops.size());
  }

  @Test
  public void checkForVariableInScope2() throws Exception {
    final String program = ""
          + "struct _GLF_struct_1 {\n"
          + "  float dist;\n"
          + "};\n"
          + "void main() {\n"
          + "  int dist;\n"
          + "  {\n"
          + "    _GLF_struct_1 _GLF_struct_replacement_2 = _GLF_struct_1(1.0);\n"
          + "  }\n"
          + "}\n";
    final String expected = ""
          + "struct _GLF_struct_1 {\n"
          + "  float dist;\n"
          + "};\n"
          + "void main() {\n"
          + "  int dist;\n"
          + "  {\n"
          + "    float dist = 1.0;\n"
          + "  }\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<DestructifyReductionOpportunity> ops = DestructifyReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false,
          ShadingLanguageVersion.ESSL_100, new RandomWrapper(), null, true));
    // There should be one opportunity as variable dist is in a different scope and not used in this scope.
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void checkForVariableInScope3() throws Exception {
    final String program = ""
          + "struct _GLF_struct_1 {\n"
          + "  float dist;\n"
          + "};\n"
          + "void main() {\n"
          + "  float dist;\n"
          + "  {\n"
          + "    _GLF_struct_1 _GLF_struct_replacement_2 = _GLF_struct_1(1.0);\n"
          + "    dist = 2.0;\n"
          + "  }\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<DestructifyReductionOpportunity> ops = DestructifyReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false,
          ShadingLanguageVersion.ESSL_100, new RandomWrapper(), null, true));
    // There should be no opportunities as there is already a variable called 'dist' in scope,
    // and it is used.
    assertEquals(0, ops.size());
  }

  @Test
  public void misc() throws Exception {
    final String program = "struct _GLF_struct_65 {\n"
          + "    vec3 GLF_live6p2;\n"
          + "    mat2 _f1;\n"
          + "    bvec3 _f2;\n"
          + "    bvec3 _f3;\n"
          + "};\n"
          + "void main() {\n"
          + "  float GLF_live6formuparam = 31.97;\n"
          + "  _GLF_struct_65 _GLF_struct_replacement_66 = _GLF_struct_65(vec3(1.0), mat2(1.0), bvec3(true), bvec3(true));\n"
          + "  _GLF_struct_replacement_66.GLF_live6p2 = abs(_GLF_struct_replacement_66.GLF_live6p2) / dot(_GLF_struct_replacement_66.GLF_live6p2, _GLF_struct_replacement_66.GLF_live6p2) - GLF_live6formuparam;\n"
          + "}\n";
    final String expected = "struct _GLF_struct_65 {\n"
          + "    vec3 GLF_live6p2;\n"
          + "    mat2 _f1;\n"
          + "    bvec3 _f2;\n"
          + "    bvec3 _f3;\n"
          + "};\n"
          + "void main() {\n"
          + "  float GLF_live6formuparam = 31.97;\n"
          + "  vec3 GLF_live6p2 = vec3(1.0);\n"
          + "  GLF_live6p2 = abs(GLF_live6p2) / dot(GLF_live6p2, GLF_live6p2) - GLF_live6formuparam;\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<DestructifyReductionOpportunity> ops = DestructifyReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
          ShadingLanguageVersion.ESSL_100, new RandomWrapper(), null, true));
    // There should be no opportunities as there is already a variable called 'dist' in scope,
    // and it is used.
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}