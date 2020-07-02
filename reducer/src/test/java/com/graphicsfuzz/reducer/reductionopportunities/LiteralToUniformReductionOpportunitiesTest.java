/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiteralToUniformReductionOpportunitiesTest {

  @Test
  public void testReplaceSimpleInt() throws Exception {

    final String vertexShader = "void main() { "
        + "int a = 1;}";

    final String vertexShaderReplaced = "uniform int _GLF_uniform_int_values[2];"
        + "void main()"
        + "{"
        + "  int a = _GLF_uniform_int_values[0];"
        + "}";

    final String fragmentShader = "void main() { "
        + "int a = 2;}";

    final String fragmentShaderReplaced = "uniform int _GLF_uniform_int_values[2];"
        + "void main()"
        + "{"
        + "  int a = _GLF_uniform_int_values[1];"
        + "}";

    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(ParseHelper.parse(vertexShader, ShaderKind.VERTEX));
    shaders.add(ParseHelper.parse(fragmentShader, ShaderKind.FRAGMENT));

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, shaders);
    assertEquals(0, pipelineInfo.getNumUniforms());

    final List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
        .findOpportunities(shaderJob,
            new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                new IdGenerator()));

    assertEquals("There should be two opportunities", 2, ops.size());
    assertEquals(0, pipelineInfo.getNumUniforms());

    ops.forEach(AbstractReductionOpportunity::applyReduction);

    CompareAsts.assertEqualAsts(fragmentShaderReplaced, shaderJob.getFragmentShader().get());
    CompareAsts.assertEqualAsts(vertexShaderReplaced, shaderJob.getVertexShader().get());

    assertTrue(shaderJob.getPipelineInfo().hasUniform(Constants.INT_LITERAL_UNIFORM_VALUES));
  }

  @Test
  public void testReplaceIntAndFloat() throws Exception {

    final String vertexShader = "void main() { "
        + "int a = 1;}";

    final String vertexShaderReplaced =
        "uniform int _GLF_uniform_int_values[1];"
        + "void main()"
        + "{"
        + "  int a = _GLF_uniform_int_values[0];"
        + "}";

    final String fragmentShader = "void main() { "
        + "float a = 2.0;}";

    final String fragmentShaderReplaced =
          "uniform float _GLF_uniform_float_values[1];"
        + "void main()"
        + "{"
        + "  float a = _GLF_uniform_float_values[0];"
        + "}";

    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(ParseHelper.parse(vertexShader, ShaderKind.VERTEX));
    shaders.add(ParseHelper.parse(fragmentShader, ShaderKind.FRAGMENT));

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, shaders);
    assertEquals(0, pipelineInfo.getNumUniforms());

    final List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
            .findOpportunities(shaderJob,
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                    new IdGenerator()));

    assertEquals("There should be two opportunities", 2, ops.size());
    assertEquals(0, pipelineInfo.getNumUniforms());

    ops.forEach(AbstractReductionOpportunity::applyReduction);

    CompareAsts.assertEqualAsts(fragmentShaderReplaced, shaderJob.getFragmentShader().get());
    CompareAsts.assertEqualAsts(vertexShaderReplaced, shaderJob.getVertexShader().get());

    assertTrue(shaderJob.getPipelineInfo().hasUniform(Constants.INT_LITERAL_UNIFORM_VALUES));
    assertTrue(shaderJob.getPipelineInfo().hasUniform(Constants.FLOAT_LITERAL_UNIFORM_VALUES));
  }

  @Test
  public void testReplaceSameInt() throws Exception {

    final String vertexShader = "void main() { "
        + "int a = 1;}";

    final String vertexShaderReplaced = "uniform int _GLF_uniform_int_values[1];"
        + "void main()"
        + "{"
        + "  int a = _GLF_uniform_int_values[0];"
        + "}";

    final String fragmentShader = "void main() { "
        + "int b = 1;}";

    final String fragmentShaderReplaced = "uniform int _GLF_uniform_int_values[1];"
        + "void main()"
        + "{"
        + "  int b = _GLF_uniform_int_values[0];"
        + "}";

    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(ParseHelper.parse(vertexShader, ShaderKind.VERTEX));
    shaders.add(ParseHelper.parse(fragmentShader, ShaderKind.FRAGMENT));

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, shaders);
    assertEquals(0, pipelineInfo.getNumUniforms());

    final List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
            .findOpportunities(shaderJob,
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                    new IdGenerator()));

    assertEquals("There should be two opportunities", 2, ops.size());
    assertEquals(0, pipelineInfo.getNumUniforms());

    ops.forEach(AbstractReductionOpportunity::applyReduction);

    CompareAsts.assertEqualAsts(fragmentShaderReplaced, shaderJob.getFragmentShader().get());
    CompareAsts.assertEqualAsts(vertexShaderReplaced, shaderJob.getVertexShader().get());

    assertTrue(shaderJob.getPipelineInfo().hasUniform(Constants.INT_LITERAL_UNIFORM_VALUES));
  }

  @Test
  public void testReplaceSameFloat() throws Exception {

    final String vertexShader = "void main() { "
        + "float a = 1.0;}";

    final String vertexShaderReplaced = "uniform float _GLF_uniform_float_values[1];"
        + "void main()"
        + "{"
        + "  float a = _GLF_uniform_float_values[0];"
        + "}";

    final String fragmentShader = "void main() { "
        + "float a = 1.0;}";

    final String fragmentShaderReplaced = "uniform float _GLF_uniform_float_values[1];"
        + "void main()"
        + "{"
        + "  float a = _GLF_uniform_float_values[0];"
        + "}";

    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(ParseHelper.parse(vertexShader, ShaderKind.VERTEX));
    shaders.add(ParseHelper.parse(fragmentShader, ShaderKind.FRAGMENT));

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, shaders);
    assertEquals(0, pipelineInfo.getNumUniforms());

    final List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
            .findOpportunities(shaderJob,
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                    new IdGenerator()));

    assertEquals("There should be two opportunities", 2, ops.size());
    assertEquals(0, pipelineInfo.getNumUniforms());

    ops.forEach(AbstractReductionOpportunity::applyReduction);

    CompareAsts.assertEqualAsts(fragmentShaderReplaced, shaderJob.getFragmentShader().get());
    CompareAsts.assertEqualAsts(vertexShaderReplaced, shaderJob.getVertexShader().get());

    assertTrue(shaderJob.getPipelineInfo().hasUniform(Constants.FLOAT_LITERAL_UNIFORM_VALUES));
  }

  @Test
  public void testReplaceUInts() throws Exception {

    final String vertexShader = "void main() { "
        + "uint a = 44u;}";

    final String vertexShaderReplaced = "uniform uint _GLF_uniform_uint_values[2];"
        + "void main()"
        + "{"
        + "  uint a = _GLF_uniform_uint_values[0];"
        + "}";

    final String fragmentShader = "void main() { "
        + "uint a = 33u;}";

    final String fragmentShaderReplaced = "uniform uint _GLF_uniform_uint_values[2];"
        + "void main()"
        + "{"
        + "  uint a = _GLF_uniform_uint_values[1];"
        + "}";

    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(ParseHelper.parse(vertexShader, ShaderKind.VERTEX));
    shaders.add(ParseHelper.parse(fragmentShader, ShaderKind.FRAGMENT));

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, shaders);
    assertEquals(0, pipelineInfo.getNumUniforms());

    final List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
            .findOpportunities(shaderJob,
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                    new IdGenerator()));

    assertEquals("There should be two opportunities", 2, ops.size());
    assertEquals(0, pipelineInfo.getNumUniforms());

    ops.forEach(AbstractReductionOpportunity::applyReduction);

    CompareAsts.assertEqualAsts(fragmentShaderReplaced, shaderJob.getFragmentShader().get());
    CompareAsts.assertEqualAsts(vertexShaderReplaced, shaderJob.getVertexShader().get());

    assertTrue(shaderJob.getPipelineInfo().hasUniform(Constants.UINT_LITERAL_UNIFORM_VALUES));
  }

  /*
   * This test adds two shaders to the shader job and applies only one reduction
   * opportunity at first. Then checks that the one shader had the array uniform added,
   * and the other shader stays the same. After that, another uniform is manually added to
   * the pipeline info and the rest of the opportunities are applied. The test succeeds if
   * the bindings have not changed, the amount of opportunies is correct and literals in the
   * shaders are replaced as expected.
   */
  @Test
  public void testManuallyAddedUniform() throws Exception {

    final String vertexShader =
        "void main()"
        + "{ "
        + "  int a = 1;"
        + "  int b = 1;"
        + "}";

    final String vertexShaderReplaced = "uniform int _GLF_uniform_int_values[1];"
        + "void main()"
        + "{"
        + "  int a = _GLF_uniform_int_values[0];"
        + "  int b = 1;"
        + "}";

    final String vertexShaderReplaced2 = "uniform int _GLF_uniform_int_values[2];"
        + "void main()"
        + "{"
        + "  int a = _GLF_uniform_int_values[0];"
        + "  int b = _GLF_uniform_int_values[0];"
        + "}";

    final String fragmentShader =
        "void main() "
        + "{ "
        + "  int b = 2;"
        + "}";

    final String fragmentShaderNotReplaced =
        "void main()"
            + "{"
            + "  int b = 2;"
            + "}";

    final String fragmentShaderReplaced =
        "uniform int _GLF_uniform_int_values[2];"
            + "void main()"
            + "{"
            + "  int b = _GLF_uniform_int_values[1];"
            + "}";

    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(ParseHelper.parse(vertexShader, ShaderKind.VERTEX));
    shaders.add(ParseHelper.parse(fragmentShader, ShaderKind.FRAGMENT));

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, shaders);
    assertEquals(0, pipelineInfo.getNumUniforms());

    final List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
            .findOpportunities(shaderJob,
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                    new IdGenerator()));

    assertEquals("There should be three opportunities", 3, ops.size());
    assertEquals(0, pipelineInfo.getNumUniforms());

    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(vertexShaderReplaced, shaderJob.getVertexShader().get());
    CompareAsts.assertEqualAsts(fragmentShaderNotReplaced, shaderJob.getFragmentShader().get());
    assertEquals(1, pipelineInfo.getNumUniforms());

    final int bindingNumber = shaderJob.getPipelineInfo().getBinding("_GLF_uniform_int_values");

    final int nextUnusedBindingNumber = shaderJob.getPipelineInfo().getUnusedBindingNumber();
    shaderJob.getPipelineInfo().addUniform("TEST", BasicType.INT,
        Optional.of(0), new ArrayList<>());
    shaderJob.getPipelineInfo().addUniformBinding("TEST", false,
        nextUnusedBindingNumber);

    ops.forEach(AbstractReductionOpportunity::applyReduction);
    assertEquals("There should be three opportunities", 3, ops.size());
    assertEquals(2, pipelineInfo.getNumUniforms());

    CompareAsts.assertEqualAsts(vertexShaderReplaced2, shaderJob.getVertexShader().get());
    CompareAsts.assertEqualAsts(fragmentShaderReplaced, shaderJob.getFragmentShader().get());

    // check that the binding numbers remain the same.
    assertEquals(bindingNumber, shaderJob.getPipelineInfo().getBinding("_GLF_uniform_int_values"));
    assertEquals(nextUnusedBindingNumber, shaderJob.getPipelineInfo().getBinding("TEST"));
  }

  @Test
  public void testReplaceMultipleLiterals() throws Exception {

    final String vertexShader = "void main() { "
        + "float a = 1.0;"
        + "float b = 2.0;"
        + "int c = 1;"
        + "int d = 2;"
        + "int e = 3;"
        + "}";

    final String vertexShaderReplaced =
        "uniform int _GLF_uniform_int_values[3];"
        + "uniform float _GLF_uniform_float_values[3];"
        + "void main()"
        + "{"
        + "  float a = _GLF_uniform_float_values[0];"
        + "  float b = _GLF_uniform_float_values[1];"
        + "  int c = _GLF_uniform_int_values[0];"
        + "  int d = _GLF_uniform_int_values[1];"
        + "  int e = _GLF_uniform_int_values[2];"
        + "}";

    final String fragmentShader = "void main() { "
        + "float a = 3.0;"
        + "float b = 2.0;"
        + "float c = 1.0;"
        + "int d = 1;"
        + "int e = 2;"
        + "}";

    final String fragmentShaderReplaced =
          "uniform int _GLF_uniform_int_values[3];"
        + "uniform float _GLF_uniform_float_values[3];"
        + "void main()"
        + "{"
        + "  float a = _GLF_uniform_float_values[2];"
        + "  float b = _GLF_uniform_float_values[1];"
        + "  float c = _GLF_uniform_float_values[0];"
        + "  int d = _GLF_uniform_int_values[0];"
        + "  int e = _GLF_uniform_int_values[1];"
        + "}";

    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(ParseHelper.parse(vertexShader, ShaderKind.VERTEX));
    shaders.add(ParseHelper.parse(fragmentShader, ShaderKind.FRAGMENT));

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, shaders);
    assertEquals(0, pipelineInfo.getNumUniforms());

    final List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
            .findOpportunities(shaderJob,
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                    new IdGenerator()));

    assertEquals("There should be 10 opportunities", 10, ops.size());
    assertEquals(0, pipelineInfo.getNumUniforms());

    ops.forEach(AbstractReductionOpportunity::applyReduction);

    CompareAsts.assertEqualAsts(fragmentShaderReplaced, shaderJob.getFragmentShader().get());
    CompareAsts.assertEqualAsts(vertexShaderReplaced, shaderJob.getVertexShader().get());

    assertTrue(shaderJob.getPipelineInfo().hasUniform(Constants.FLOAT_LITERAL_UNIFORM_VALUES));
    assertTrue(shaderJob.getPipelineInfo().hasUniform(Constants.INT_LITERAL_UNIFORM_VALUES));
  }
}
