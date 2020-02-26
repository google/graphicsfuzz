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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.Optional;
import org.junit.Test;

public class GloballyTruncateLoopsTest {

  @Test
  public void basicTest() throws Exception {

    final String shader = "#version 310 es\n"
        + "void foo(int x) {\n"
        + "  for (int i = 0; i < 100; ) ;\n"
        + "  for (int i = 0; i < 100; ) {\n"
        + "    int j = 0;\n"
        + "    while (j < x) {\n"
        + "      j++;\n"
        + "    }\n"
        + "    while (false) {\n"
        + "      j++;\n"
        + "    }\n"
        + "  }\n"
        + "}\n"
        + "void main() {\n"
        + "  do {\n"
        + "    foo(1);\n"
        + "  } while(false);\n"
        + "  do {\n"
        + "    foo(1);\n"
        + "  } while(true);\n"
        + "  for (int i = 0; false; i++) {\n"
        + "  }\n"
        + "  for (int i = 0; false; i++) ;\n"
        + "}\n";

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(), ParseHelper.parse(shader, ShaderKind.VERTEX),
        ParseHelper.parse(shader, ShaderKind.FRAGMENT));

    GloballyTruncateLoops.truncate(shaderJob, 100, "LOOP_COUNT", "LOOP_BOUND");

    final String expectedShader = "#version 310 es\n"
        + "const int LOOP_BOUND = 100;\n"
        + "int LOOP_COUNT = 0;\n"
        + "void foo(int x) {\n"
        + "  for (int i = 0; (i < 100) && (LOOP_COUNT < LOOP_BOUND); ) {\n"
        + "    LOOP_COUNT++;\n"
        + "    ;\n"
        + "  }\n"
        + "  for (int i = 0; (i < 100) && (LOOP_COUNT < LOOP_BOUND); ) {\n"
        + "    LOOP_COUNT++;\n"
        + "    int j = 0;\n"
        + "    while ((j < x) && (LOOP_COUNT < LOOP_BOUND)) {\n"
        + "      LOOP_COUNT++;\n"
        + "      j++;\n"
        + "    }\n"
        + "    while (false) {\n"
        + "      j++;\n"
        + "    }\n"
        + "  }\n"
        + "}\n"
        + "void main() {\n"
        + "  do {\n"
        + "    foo(1);\n"
        + "  } while(false);\n"
        + "  do {\n"
        + "    LOOP_COUNT++;\n"
        + "    foo(1);\n"
        + "  } while((true) && (LOOP_COUNT < LOOP_BOUND));\n"
        + "  for (int i = 0; false; i++) {\n"
        + "  }\n"
        + "  for (int i = 0; false; i++) ;\n"
        + "}\n";

    for (TranslationUnit tu : shaderJob.getShaders()) {
      CompareAsts.assertEqualAsts(expectedShader, tu);
    }

  }

  @Test
  public void doNotAddDeclarationsIfNothingToTruncate() throws Exception {

    // If no loop truncation is required, the loop bound and loop count declarations
    // should not be emitted.

    final String shader = "#version 310 es\n"
        + "void main() {\n"
        + "  do {\n"
        + "  } while(false);\n"
        + "}\n";

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(), ParseHelper.parse(shader, ShaderKind.VERTEX),
        ParseHelper.parse(shader, ShaderKind.FRAGMENT));

    GloballyTruncateLoops.truncate(shaderJob, 100, "LOOP_COUNT", "LOOP_BOUND");

    for (TranslationUnit tu : shaderJob.getShaders()) {
      CompareAsts.assertEqualAsts(shader, tu);
    }

  }

}
