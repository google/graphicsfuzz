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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.tool.UniformValueSupplier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PipelineUniformValueSupplierTest {

  @Test
  public void testPrettyPrinting() throws Exception {

    final String shader = ""
        + "uniform int A[3], B[2], C[1];"
        + "\n"
        + "uniform float D[3], E[1];\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + " float a = A[0];\n"
        + " float b = B[1];\n"
        + " float c = C[0];\n"
        + " float d = D[0];\n"
        + "}\n";

    final String shaderPrettyPrinted = ""
        + "// Contents of A: [0, 1, 2]\n"
        + "// Contents of B: [3, 4, 5]\n"
        + "// Contents of C: 1\n"
        + "uniform int A[3], B[2], C[1];\n"
        + "\n"
        + "// Contents of D: 1.0\n"
        + "// Contents of E: \n"
        + "uniform float D[3], E[1];\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + " float a = A[0];\n"
        + " float b = B[1];\n"
        + " float c = C[0];\n"
        + " float d = D[0];\n"
        + "}\n";

    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("A", BasicType.INT, Optional.empty(), Arrays.asList(0, 1, 2));
    pipelineInfo.addUniform("B", BasicType.INT, Optional.empty(), Arrays.asList(3, 4, 5));
    pipelineInfo.addUniform("C", BasicType.INT, Optional.empty(), Arrays.asList(1));
    pipelineInfo.addUniform("D", BasicType.FLOAT, Optional.empty(), Arrays.asList(1.0));
    pipelineInfo.addUniform("E", BasicType.FLOAT, Optional.empty(), new ArrayList<>());

    UniformValueSupplier uniformValues = new PipelineUniformValueSupplier(pipelineInfo);

    assertEquals(shaderPrettyPrinted,
        PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(shader), uniformValues));
  }
}
