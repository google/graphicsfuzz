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
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.CannedRandom;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ZeroCannedRandom;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VectorizationReductionOpportunitiesTest {

  @Test
  public void testInDead() throws Exception {
    final String shader = "void main() {\n"
          + "  if(_GLF_DEAD(false)) {\n"
          + "    vec4 GLF_merged1_3_1_10GLF_dead3r;\n"
          + "    GLF_merged1_3_1_10GLF_dead3r.w;\n"
          + "    for(int GLF_dead3r = 0;\n"
          + "        GLF_dead3r < 15;\n"
          + "        GLF_dead3r ++\n"
          + "        ) { }\n"
          + "  }\n"
          + "}\n";
    final String expected = "void main() {\n"
          + "  if(_GLF_DEAD(false)) {\n"
          + "    float GLF_dead3r;\n"
          + "    vec4 GLF_merged1_3_1_10GLF_dead3r;\n"
          + "    GLF_dead3r;\n"
          + "    for(int GLF_dead3r = 0;\n"
          + "        GLF_dead3r < 15;\n"
          + "        GLF_dead3r ++\n"
          + "        ) { }\n"
          + "  }\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440, new RandomWrapper(0), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)), PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

  @Test
  public void testIncompatibleDeclaration() throws Exception {
    final String original =
          "float f()\n"
                + "{\n"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    int b;\n"
                + "    bool c;\n"
                + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(0, ops.size());
  }

  @Test
  public void testProblematicDeclarationPreviousScope() throws Exception {
    final String original =
          "float f()\n"
                + "{"
                + "  float b;"
                + "  bool c;"
                + "  {\n"
                + "      vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "      b = 2.0;\n"
                + "      c = false;\n"
                + "  }\n"
                + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(0, ops.size());
  }

  @Test
  public void testOneProblematicDeclarationPreviousScope() throws Exception {
    final String original =
          "void f()\n"
                + "{"
                + "  float b;"
                + "  bool c;"
                + "  {\n"
                + "      vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "      GLF_merged2_0_1_1_1_1_1bc.x;\n"
                + "      GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "      c = false;\n"
                + "  }\n"
                + "}\n";
    final String expected =
          "void f()\n"
                + "{"
                + "  float b;"
                + "  bool c;"
                + "  {\n"
                + "      float b;\n"
                + "      vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "      b;\n"
                + "      GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "      c = false;\n"
                + "  }\n"
                + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)), PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void misc1() throws Exception {
    final String original =
          "void f()\n"
                + "{"
                + "  int GLF_merged1_1_1_1c;\n"
                + "  int GLF_merged1_0_1_1b;\n"
                + "  {\n"
                + "      vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "      GLF_merged2_0_1_1_1_1_1bc.x;\n"
                + "      GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "      GLF_merged1_1_1_1c = 2;\n"
                + "  }\n"
                + "}\n";
    final String expected1 =
          "void f()\n"
                + "{"
                + "  int GLF_merged1_1_1_1c;\n"
                + "  int GLF_merged1_0_1_1b;\n"
                + "  {\n"
                + "      float b;\n"
                + "      vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "      b;\n"
                + "      GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "      GLF_merged1_1_1_1c = 2;\n"
                + "  }\n"
                + "}\n";
    final String expected2 =
          "void f()\n"
                + "{"
                + "  int GLF_merged1_1_1_1c;\n"
                + "  int GLF_merged1_0_1_1b;\n"
                + "  {\n"
                + "      float c;\n"
                + "      float b;\n"
                + "      vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "      b;\n"
                + "      c;\n"
                + "      GLF_merged1_1_1_1c = 2;\n"
                + "  }\n"
                + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(2, ops.size());
    ops = ops.stream().filter(item -> item.getComponentType() == BasicType.FLOAT).collect(Collectors.toList());
    assertEquals(2, ops.size());
    ops.stream().filter(item -> item.getComponentName().equals("b")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected1)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops.stream().filter(item -> item.getComponentName().equals("c")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected2)), PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void misc2() throws Exception {
    final String original =
          "void f()\n"
                + "{\n"
                + "    int GLF_merged1_1_1_1c;\n"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.x;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "}\n";
    final String expected1 =
          "void f()\n"
                + "{\n"
                + "    float b;\n"
                + "    int GLF_merged1_1_1_1c;\n"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    b;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "}\n";
    final String expected2 =
          "void f()\n"
                + "{\n"
                + "    float c;\n"
                + "    float b;\n"
                + "    int GLF_merged1_1_1_1c;\n"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    b;\n"
                + "    c;\n"
                + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(2, ops.size());
    ops.stream().filter(item -> item.getVectorName().equals("GLF_merged2_0_1_1_1_1_1bc")
          && item.getComponentName().equals("b")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected1)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops.stream().filter(item -> item.getVectorName().equals("GLF_merged2_0_1_1_1_1_1bc")
          && item.getComponentName().equals("c")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected2)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(0, ops.size());
  }

  @Test
  public void misc3() throws Exception {
    final String shader = "void f()\n"
          + "{\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.x;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.y;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw;\n"
          + "}\n";
    final String expected1 = "void f()\n"
          + "{\n"
          + "  float P;\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  P;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.y;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw;\n"
          + "}\n";
    final String expected2 = "void f()\n"
          + "{\n"
          + "  float Q;\n"
          + "  float P;\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  P;\n"
          + "  Q;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw;\n"
          + "}\n";
    final String expected3 = "void f()\n"
          + "{\n"
          + "  vec2 GLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  float Q;\n"
          + "  float P;\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  P;\n"
          + "  Q;\n"
          + "  GLF_merged2_0_1_1_1_1_1PQ;\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final IRandom cannedRandom = new ZeroCannedRandom();
    List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440, cannedRandom, null, true));
    assertEquals(3, ops.size());
    ops.stream().filter(item -> item.getComponentName().equals("P")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected1)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops.stream().filter(item -> item.getComponentName().equals("Q")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected2)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops.stream().filter(item -> item.getComponentName().equals("GLF_merged2_0_1_1_1_1_1PQ")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected3)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440, cannedRandom, null, true));
    assertEquals(0, ops.size());
  }

  @Test
  public void misc4() throws Exception {
    final String shader = "void f()\n"
          + "{\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_3_1_1abc;\n"
          + "  GLF_merged3_0_1_1_1_1_1_3_1_1abc.x = 2.0;\n"
          + "  GLF_merged3_0_1_1_1_1_1_3_1_1abc.y = 3.0;\n"
          + "  GLF_merged3_0_1_1_1_1_1_3_1_1abc.w = 1.0;\n"
          + "}\n";
    final String expected1 = "void f()\n"
          + "{\n"
          + "  float a;\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_3_1_1abc;\n"
          + "  a = 2.0;\n"
          + "  GLF_merged3_0_1_1_1_1_1_3_1_1abc.y = 3.0;\n"
          + "  GLF_merged3_0_1_1_1_1_1_3_1_1abc.w = 1.0;\n"
          + "}\n";
    final String expected2 = "void f()\n"
          + "{\n"
          + "  float c;\n"
          + "  float a;\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_3_1_1abc;\n"
          + "  a = 2.0;\n"
          + "  GLF_merged3_0_1_1_1_1_1_3_1_1abc.y = 3.0;\n"
          + "  c = 1.0;\n"
          + "}\n";
    final String expected3 = "void f()\n"
          + "{\n"
          + "  float b;\n"
          + "  float c;\n"
          + "  float a;\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_3_1_1abc;\n"
          + "  a = 2.0;\n"
          + "  b = 3.0;\n"
          + "  c = 1.0;\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final IRandom cannedRandom = new CannedRandom(2);
    final List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440, cannedRandom, null, true));
    ops.stream().filter(item -> item.getComponentName().equals("a")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected1)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops.stream().filter(item -> item.getComponentName().equals("c")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected2)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops.stream().filter(item -> item.getComponentName().equals("b")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected3)), PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void misc5() throws Exception {
    final String shader = "void f() {\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  vec2 GLF_merged1_1_1_1Q;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw = GLF_merged1_1_1_1Q;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.x;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.y;\n"
          + "}\n";
    final String expected1 = "void f() {\n"
          + "  vec2 GLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  vec2 GLF_merged1_1_1_1Q;\n"
          + "  GLF_merged2_0_1_1_1_1_1PQ = GLF_merged1_1_1_1Q;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.x;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.y;\n"
          + "}\n";
    final String expected2 = "void f() {\n"
          + "  float P;\n"
          + "  vec2 GLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  vec2 GLF_merged1_1_1_1Q;\n"
          + "  GLF_merged2_0_1_1_1_1_1PQ = GLF_merged1_1_1_1Q;\n"
          + "  P;\n"
          + "  GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.y;\n"
          + "}\n";
    final String expected3 = "void f() {\n"
          + "  float Q;\n"
          + "  float P;\n"
          + "  vec2 GLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  vec4 GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ;\n"
          + "  vec2 GLF_merged1_1_1_1Q;\n"
          + "  GLF_merged2_0_1_1_1_1_1PQ = GLF_merged1_1_1_1Q;\n"
          + "  P;\n"
          + "  Q;\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(3, ops.size());
    ops.stream().filter(item -> item.getComponentName().equals("GLF_merged2_0_1_1_1_1_1PQ")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected1)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops.stream().filter(item -> item.getComponentName().equals("P")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected2)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops.stream().filter(item -> item.getComponentName().equals("Q")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected3)), PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testCompatibleDeclaration() throws Exception {
    final String original =
          "void f()\n"
                + "{\n"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    float b;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.x;\n"
                + "}\n";
    final String expected1 =
          "void f()\n"
                + "{\n"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    float b;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "    b;\n"
                + "}\n";
    final String expected2 =
          "void f()\n"
                + "{\n"
                + "    float c;"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    float b;\n"
                + "    c;\n"
                + "    b;\n"
                + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(2, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected1)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected2)), PrettyPrinterVisitor.prettyPrintAsString(tu));
  }


  @Test
  public void testBasicBehaviour() throws Exception {
    final String original =
          "void f() {\n"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    float b, c;\n"
                + "    b = 2;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.x = b;\n"
                + "    c = 3;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.y = c;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.x = GLF_merged2_0_1_1_1_1_1bc.x + GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "}\n";
    final String expected1 =
          "void f() {\n"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    float b, c;\n"
                + "    b = 2;\n"
                + "    b = b;\n"
                + "    c = 3;\n"
                + "    GLF_merged2_0_1_1_1_1_1bc.y = c;\n"
                + "    b = b + GLF_merged2_0_1_1_1_1_1bc.y;\n"
                + "}\n";
    final String expected2 =
          "void f() {\n"
                + "    vec2 GLF_merged2_0_1_1_1_1_1bc;\n"
                + "    float b, c;\n"
                + "    b = 2;\n"
                + "    b = b;\n"
                + "    c = 3;\n"
                + "    c = c;\n"
                + "    b = b + c;\n"
                + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    List<VectorizationReductionOpportunity> ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(2, ops.size());
    ops.stream().filter(item -> item.getComponentName().equals("b")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected1)), PrettyPrinterVisitor.prettyPrintAsString(tu));
    ops = VectorizationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
          new ZeroCannedRandom(), null, true));
    assertEquals(1, ops.size());
    ops.stream().filter(item -> item.getComponentName().equals("c")).findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected2)), PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}
