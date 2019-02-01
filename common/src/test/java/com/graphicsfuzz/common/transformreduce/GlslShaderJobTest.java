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

package com.graphicsfuzz.common.transformreduce;

import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

public class GlslShaderJobTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final String VERT_SHADER_NO_BINDINGS =
        "uniform float a;"
      + "uniform float b;"
      + "uniform int c;"
      + "uniform vec2 d;"
      + "void main() { }";

  private static final String FRAG_SHADER_NO_BINDINGS = "" +
        "uniform float b;"
      + "uniform int e;"
      + "uniform vec2 d;"
      + "uniform float f;"
      + "void main() { }";

  private static final String JSON_NO_BINDINGS = "{" +
      "  \"a\": {" +
      "    \"args\": [" +
      "      1.0" +
      "    ], " +
      "    \"func\": \"glUniform1f\"" +
      "  }, " +
      "  \"b\": {" +
      "    \"args\": [" +
      "      0.0" +
      "    ], " +
      "    \"func\": \"glUniform1f\"" +
      "  }, " +
      "  \"c\": {" +
      "    \"args\": [" +
      "      2" +
      "    ], " +
      "    \"func\": \"glUniform1i\"" +
      "  }, " +
      "  \"d\": {" +
      "    \"args\": [" +
      "      0.0, " +
      "      1.0" +
      "    ], " +
      "    \"func\": \"glUniform2f\"" +
      "  }, " +
      "  \"e\": {" +
      "    \"args\": [" +
      "      12" +
      "    ], " +
      "    \"func\": \"glUniform1i\"" +
      "  }, " +
      "  \"f\": {" +
      "    \"args\": [" +
      "      100.0" +
      "    ], " +
      "    \"func\": \"glUniform1f\"" +
      "  }," +
      "  \"g\": {" +
      "    \"args\": [" +
      "      100.0" +
      "    ], " +
      "    \"func\": \"glUniform1f\"" +
      "  }" +
      "}";

  private static final String VERT_SHADER_WITH_BINDINGS =
        "layout(set = 0, binding = 0) uniform buf0 { float a; };"
      + "layout(set = 0, binding = 1) uniform buf1 { float b; };"
      + "layout(set = 0, binding = 2) uniform buf2 { int c; };"
      + "layout(set = 0, binding = 3) uniform buf3 { vec2 d; };"
      + "void main() { }";

  private static final String FRAG_SHADER_WITH_BINDINGS = "" +
        "layout(set = 0, binding = 1) uniform buf1 { float b; };"
      + "layout(set = 0, binding = 4) uniform buf4 { int e; };"
      + "layout(set = 0, binding = 3) uniform buf3 { vec2 d; };"
      + "layout(set = 0, binding = 5) uniform buf5 { float f; };"
      + "void main() { }";

  private static final String JSON_WITH_BINDINGS = "{" +
      "  \"a\": {" +
      "    \"args\": [" +
      "      1.0" +
      "    ], " +
      "    \"func\": \"glUniform1f\", " +
      "    \"binding\": 0" +
      "  }, " +
      "  \"b\": {" +
      "    \"args\": [" +
      "      0.0" +
      "    ], " +
      "    \"func\": \"glUniform1f\", " +
      "    \"binding\": 1" +
      "  }, " +
      "  \"c\": {" +
      "    \"args\": [" +
      "      2" +
      "    ], " +
      "    \"func\": \"glUniform1i\", " +
      "    \"binding\": 2" +
      "  }, " +
      "  \"d\": {" +
      "    \"args\": [" +
      "      0.0, " +
      "      1.0" +
      "    ], " +
      "    \"func\": \"glUniform2f\", " +
      "    \"binding\": 3" +
      "  }, " +
      "  \"e\": {" +
      "    \"args\": [" +
      "      12" +
      "    ], " +
      "    \"func\": \"glUniform1i\", " +
      "    \"binding\": 4" +
      "  }, " +
      "  \"f\": {" +
      "    \"args\": [" +
      "      100.0" +
      "    ], " +
      "    \"func\": \"glUniform1f\", " +
      "    \"binding\": 5" +
      "  }," +
      "  \"g\": {" +
      "    \"args\": [" +
      "      100.0" +
      "    ], " +
      "    \"func\": \"glUniform1f\", " +
      "    \"binding\": 6" +
      "  }" +
      "}";


  @Test
  public void testMakeUniformBindings() throws Exception {

    final GlslShaderJob job = new GlslShaderJob(
        Optional.empty(),
        new PipelineInfo(JSON_NO_BINDINGS),
        ParseHelper.parse(getShaderFile("vert", VERT_SHADER_NO_BINDINGS)),
        ParseHelper.parse(getShaderFile("frag", FRAG_SHADER_NO_BINDINGS)));

    job.makeUniformBindings();

    CompareAsts.assertEqualAsts(VERT_SHADER_WITH_BINDINGS, job.getShaders().get(0));
    CompareAsts.assertEqualAsts(FRAG_SHADER_WITH_BINDINGS, job.getShaders().get(1));
    assertEquals(new PipelineInfo(JSON_WITH_BINDINGS).toString(), job.getPipelineInfo().toString());
  }

  @Test
  public void testRemoveUniformBindings() throws Exception {

    final GlslShaderJob job = new GlslShaderJob(
        Optional.empty(),
        new PipelineInfo(JSON_WITH_BINDINGS),
        ParseHelper.parse(getShaderFile("vert", VERT_SHADER_WITH_BINDINGS)),
        ParseHelper.parse(getShaderFile("frag", FRAG_SHADER_WITH_BINDINGS)));

    job.removeUniformBindings();

    CompareAsts.assertEqualAsts(VERT_SHADER_NO_BINDINGS, job.getShaders().get(0));
    CompareAsts.assertEqualAsts(FRAG_SHADER_NO_BINDINGS, job.getShaders().get(1));
    assertEquals(new PipelineInfo(JSON_NO_BINDINGS).toString(), job.getPipelineInfo().toString());

  }

  private File getShaderFile(String extension, String content) throws IOException {
    final File file = temporaryFolder.newFile("shader." + extension);
    FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
    return file;
  }

}
