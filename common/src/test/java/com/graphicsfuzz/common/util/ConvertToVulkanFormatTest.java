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

package com.graphicsfuzz.common.util;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;

public class ConvertToVulkanFormatTest {

  @Test
  public void testShadowing() throws Exception {
    UniformsInfo uniformsInfo = new UniformsInfo();
    uniformsInfo.addUniform("t", BasicType.FLOAT, Optional.empty(), Arrays.asList(1.0));
    final String program = "uniform float t;"
          + "void foo(float t) {"
          + "  t = 1.0;"
          + "}"
          + ""
          + "void main() {"
          + "  foo(t);"
          + "}";
    final String expected = "layout(binding = 0) uniform _GLF_UniformBufferObject0 {\n"
          + " float t;\n"
          + "} _GLF_ubo0;\n"
          + "void foo(float t)\n"
          + "{\n"
          + " t = 1.0;\n"
          + "}\n"
          + ""
          + "void main()\n"
          + "{\n"
          + " foo(_GLF_ubo0.t);\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program, false);
    ConvertToVulkanFormat.convert(tu, uniformsInfo);
    assertEquals(expected,
          PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

}