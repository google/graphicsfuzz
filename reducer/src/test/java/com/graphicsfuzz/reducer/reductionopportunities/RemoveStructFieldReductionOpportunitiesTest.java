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
import static org.junit.Assert.assertFalse;

public class RemoveStructFieldReductionOpportunitiesTest {

  @Test
  public void nestedStruct() throws Exception {
    final String program = "struct _GLF_struct_15 {\n"
        + "    int _f0;\n"
        + "    float x1;\n"
        + "    int _f1;\n"
        + "};\n"
        + "struct _GLF_struct_16 {\n"
        + "    _GLF_struct_15 _f0;\n"
        + "};\n"
        + "void f(float x)\n"
        + "{\n"
        + "    _GLF_struct_16 _GLF_struct_replacement_17 = _GLF_struct_16(_GLF_struct_15(1, x + 1.0, mat3(1.0)));\n"
        + "}\n";

    TranslationUnit tu = ParseHelper.parse(program);

    assertEquals(2, RemoveStructFieldReductionOpportunities
          .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, null, null, null, true)).size());

  }

  @Test
  public void singleField() throws Exception {
    final String program = "struct _GLF_struct_15 {\n"
        + "    int _f0;\n"
        + "};\n"
        + "struct _GLF_struct_16 {\n"
        + "    _GLF_struct_15 _f0;\n"
        + "};\n"
        + "void f(float x)\n"
        + "{\n"
        + "    _GLF_struct_16 _GLF_struct_replacement_17 = _GLF_struct_16(_GLF_struct_15(1));\n"
        + "}\n";

    TranslationUnit tu = ParseHelper.parse(program);

    assertEquals(0, RemoveStructFieldReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false, null, null, null, true)).size());

  }

  @Test
  public void testNonLocalInitialization() throws Exception {
    final String shader = "struct _GLF_struct_28 {\n"
          + "    mat4 _f0;\n"
          + "    vec4 a;\n"
          + "};\n"
          + "vec4 f(_GLF_struct_28 _GLF_struct_replacement_29)\n"
          + "{\n"
          + "    return _GLF_struct_28(mat4(1.0), vec4(1.0)).a;\n"
          + "}\n"
          + "void main()\n"
          + "{\n"
          + "        _GLF_struct_28 _GLF_struct_replacement_29;\n"
          + "}\n";
    final String expected = "struct _GLF_struct_28 {\n"
          + "    vec4 a;\n"
          + "};\n"
          + "vec4 f(_GLF_struct_28 _GLF_struct_replacement_29)\n"
          + "{\n"
          + "    return _GLF_struct_28(vec4(1.0)).a;\n"
          + "}\n"
          + "void main()\n"
          + "{\n"
          + "        _GLF_struct_28 _GLF_struct_replacement_29;\n"
          + "}\n";
    TranslationUnit tu = ParseHelper.parse(shader);
    List<RemoveStructFieldReductionOpportunity> ops = RemoveStructFieldReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
              new ReducerContext(true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(tu), PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)));
  }

  @Test
  public void twoFields() throws Exception {
    final String program = "struct _GLF_struct_15 {\n"
          + "    int _f0;\n"
          + "    int _f1;\n"
          + "};\n"
          + "struct _GLF_struct_16 {\n"
          + "    _GLF_struct_15 _f0;\n"
          + "};\n"
          + "void f(float x)\n"
          + "{\n"
          + "    _GLF_struct_16 _GLF_struct_replacement_17 = _GLF_struct_16(_GLF_struct_15(1, 2));\n"
          + "}\n";
    final String expected = "struct _GLF_struct_15 {\n"
          + "    int _f1;\n"
          + "};\n"
          + "struct _GLF_struct_16 {\n"
          + "    _GLF_struct_15 _f0;\n"
          + "};\n"
          + "void f(float x)\n"
          + "{\n"
          + "    _GLF_struct_16 _GLF_struct_replacement_17 = _GLF_struct_16(_GLF_struct_15(2));\n"
          + "}\n";

    TranslationUnit tu = ParseHelper.parse(program);

    final List<RemoveStructFieldReductionOpportunity> ops = RemoveStructFieldReductionOpportunities
          .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, null, null, null, true));
    assertEquals(2, ops
          .size());
    ops.stream().filter(item -> item.getFieldToRemove().equals("_f0"))
          .findAny().get().applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
    // Now that we have removed this field, removing the other field should have no effect
    // as the reduction opportunity's precondititon should no longer hold.
    final RemoveStructFieldReductionOpportunity op = ops.stream().filter(item -> item.getFieldToRemove().equals("_f1"))
          .findAny().get();
    assertFalse(op.preconditionHolds());
    op.applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}