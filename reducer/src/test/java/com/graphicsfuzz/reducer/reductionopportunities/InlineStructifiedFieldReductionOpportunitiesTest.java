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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InlineStructifiedFieldReductionOpportunitiesTest {

  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void checkInliningAppliedCorrectly() throws Exception {
    String program = "struct _GLF_struct_1687 {\n"
        + "    float lerp;\n"
        + "    vec3 _f0;\n"
        + "};\n"
        + "struct _GLF_struct_1698 {\n"
        + "    ivec4 _f0;\n"
        + "    _GLF_struct_1687 _f1;\n"
        + "    vec4 _f2;\n"
        + "    ivec2 _f3;\n"
        + "};\n"
        + "void main() {\n"
        + "  _GLF_struct_1698 " + Constants.STRUCTIFICATION_STRUCT_PREFIX + "_mine = "
                + "_GLF_struct_1698(0, _GLF_struct_1687(1.0, vec3(1.0)), vec4(1.0), ivec2(1));\n"
        + "}\n";

    String programAfter = "struct _GLF_struct_1687 {\n"
        + "    float lerp;\n"
        + "    vec3 _f0;\n"
        + "};\n"
        + "struct _GLF_struct_1698 {\n"
        + "    ivec4 _f0;\n"
        + "    float lerp;\n"
        + "    vec3 _f1_f0;\n"
        + "    vec4 _f2;\n"
        + "    ivec2 _f3;\n"
        + "};\n"
        + "void main() {\n"
        + "  _GLF_struct_1698 " + Constants.STRUCTIFICATION_STRUCT_PREFIX + "_mine = "
                + "_GLF_struct_1698(0, 1.0, vec3(1.0), vec4(1.0), ivec2(1));\n"
        + "}\n";

    TranslationUnit tu = ParseHelper.parse(program);
    assertEquals(1,
        InlineStructifiedFieldReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false, true, ShadingLanguageVersion.ESSL_100,
                    new RandomWrapper(0), new IdGenerator())).size());
    InlineStructifiedFieldReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false, true, ShadingLanguageVersion.ESSL_100,
              new RandomWrapper(0), new IdGenerator())).get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(programAfter)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void inlineRegressionTest() throws Exception {
    String program = "precision mediump float;\n"
        + "struct _GLF_struct_D {\n"
        + "    bool _f0;\n"
        + "    mat4 _f1;\n"
        + "};\n"
        + "struct _GLF_struct_F {\n"
        + "    float GLF_live41j0;\n"
        + "    _GLF_struct_D _f1;\n"
        + "};\n"
        + "struct _GLF_struct_B {\n"
        + "    _GLF_struct_F _f0;\n"
        + "};\n"
        + "struct _GLF_struct_A {\n"
        + "    float _f0;\n"
        + "    _GLF_struct_B _f1;\n"
        + "};\n"
        + "float f()\n"
        + "{\n"
        + "    return 0.0;\n"
        + "}\n"
        + "void main()\n"
        + "{\n"
        + "    _GLF_struct_A _GLF_struct_replacement_838 = _GLF_struct_A(1.0, "
                + "_GLF_struct_B(_GLF_struct_F(f(), _GLF_struct_D(true, mat4(1.0)))));\n"
        + "}\n";

    TranslationUnit tu = ParseHelper.parse(program).clone();

    List<InlineStructifiedFieldReductionOpportunity> ops =
        InlineStructifiedFieldReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false, true, ShadingLanguageVersion.ESSL_100,
                    new RandomWrapper(0), new IdGenerator()));
    assertEquals(3, ops.size());

    File tempFile = testFolder.newFile("temp.frag");
    for (InlineStructifiedFieldReductionOpportunity op : ops) {
      if (!op.getOuterStructName().equals("_GLF_struct_B")) {
        continue;
      }
      op.applyReduction();

      final File shaderJobFile = testFolder.newFile("temp.json");
      fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(), new PipelineInfo(), tu),
          shaderJobFile);
      assertTrue(fileOps.areShadersValid(shaderJobFile, false));
      break;
    }

  }


  @Test
  public void inlineRegressionTest2() throws Exception {
    String program = "struct _GLF_struct_761 {\n"
        + "    bvec2 _f0;\n"
        + "};\n"
        + "struct _GLF_struct_762 {\n"
        + "    _GLF_struct_761 _f0;\n"
        + "};\n"
        + "void f()\n"
        + "{\n"
        + "    _GLF_struct_762 _GLF_struct_replacement_763 = _GLF_struct_762(_GLF_struct_761("
                + "bvec2(true)));\n"
        + "    _GLF_struct_762(_GLF_struct_761(bvec2(true)))._f0._f0;\n"
        + "}\n";

    TranslationUnit tu = ParseHelper.parse(program).clone();

    List<InlineStructifiedFieldReductionOpportunity> ops =
        InlineStructifiedFieldReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false, true, ShadingLanguageVersion.ESSL_100,
                    new RandomWrapper(0),
              new IdGenerator()));
    assertEquals(1, ops.size());

    ops.get(0).applyReduction();

    final File shaderJobFile = testFolder.newFile("temp.json");
    fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(), new PipelineInfo(), tu),
        shaderJobFile);
    assertTrue(fileOps.areShadersValid(shaderJobFile, false));

  }

}
