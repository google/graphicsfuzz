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

import static org.junit.Assert.assertEquals;

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

public class GlslShaderJobTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final String VERT_SHADER_NO_BINDINGS = ""
      + "uniform float a;"
      + "uniform float b;"
      + "uniform int c;"
      + "uniform vec2 d;"
      + "void main() { }";

  private static final String FRAG_SHADER_NO_BINDINGS = ""
      + "uniform float b;"
      + "uniform int e;"
      + "uniform vec2 d;"
      + "uniform float f;"
      + "void main() { }";

  private static final String JSON_NO_BINDINGS = "{"
      + "  \"a\": {"
      + "    \"args\": ["
      + "      1.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  }, "
      + "  \"b\": {"
      + "    \"args\": ["
      + "      0.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  }, "
      + "  \"c\": {"
      + "    \"args\": ["
      + "      2"
      + "    ], "
      + "    \"func\": \"glUniform1i\""
      + "  }, "
      + "  \"d\": {"
      + "    \"args\": ["
      + "      0.0, "
      + "      1.0"
      + "    ], "
      + "    \"func\": \"glUniform2f\""
      + "  }, "
      + "  \"e\": {"
      + "    \"args\": ["
      + "      12"
      + "    ], "
      + "    \"func\": \"glUniform1i\""
      + "  }, "
      + "  \"f\": {"
      + "    \"args\": ["
      + "      100.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  },"
      + "  \"g\": {"
      + "    \"args\": ["
      + "      100.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  }"
      + "}";

  private static final String VERT_SHADER_WITH_BINDINGS = ""
      + "layout(set = 0, binding = 0) uniform buf0 { float a; };"
      + "layout(set = 0, binding = 1) uniform buf1 { float b; };"
      + "layout(set = 0, binding = 2) uniform buf2 { int c; };"
      + "layout(set = 0, binding = 3) uniform buf3 { vec2 d; };"
      + "void main() { }";

  private static final String FRAG_SHADER_WITH_BINDINGS = ""
        + "layout(set = 0, binding = 1) uniform buf1 { float b; };"
      + "layout(set = 0, binding = 4) uniform buf4 { int e; };"
      + "layout(set = 0, binding = 3) uniform buf3 { vec2 d; };"
      + "layout(set = 0, binding = 5) uniform buf5 { float f; };"
      + "void main() { }";

  private static final String JSON_WITH_BINDINGS = "{"
      + "  \"a\": {"
      + "    \"args\": ["
      + "      1.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\", "
      + "    \"binding\": 0"
      + "  }, "
      + "  \"b\": {"
      + "    \"args\": ["
      + "      0.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\", "
      + "    \"binding\": 1"
      + "  }, "
      + "  \"c\": {"
      + "    \"args\": ["
      + "      2"
      + "    ], "
      + "    \"func\": \"glUniform1i\", "
      + "    \"binding\": 2"
      + "  }, "
      + "  \"d\": {"
      + "    \"args\": ["
      + "      0.0, "
      + "      1.0"
      + "    ], "
      + "    \"func\": \"glUniform2f\", "
      + "    \"binding\": 3"
      + "  }, "
      + "  \"e\": {"
      + "    \"args\": ["
      + "      12"
      + "    ], "
      + "    \"func\": \"glUniform1i\", "
      + "    \"binding\": 4"
      + "  }, "
      + "  \"f\": {"
      + "    \"args\": ["
      + "      100.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\", "
      + "    \"binding\": 5"
      + "  },"
      + "  \"g\": {"
      + "    \"args\": ["
      + "      100.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\", "
      + "    \"binding\": 6"
      + "  }"
      + "}";


  @Test
  public void testMakeUniformBindings() throws Exception {

    final GlslShaderJob job = new GlslShaderJob(
        Optional.empty(),
        new PipelineInfo(JSON_NO_BINDINGS),
        ParseHelper.parse(getShaderFile("vert", VERT_SHADER_NO_BINDINGS)),
        ParseHelper.parse(getShaderFile("frag", FRAG_SHADER_NO_BINDINGS)));

    job.makeUniformBindings(Optional.empty());

    CompareAsts.assertEqualAsts(VERT_SHADER_WITH_BINDINGS, job.getVertexShader().get());
    CompareAsts.assertEqualAsts(FRAG_SHADER_WITH_BINDINGS, job.getFragmentShader().get());
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

    CompareAsts.assertEqualAsts(VERT_SHADER_NO_BINDINGS, job.getVertexShader().get());
    CompareAsts.assertEqualAsts(FRAG_SHADER_NO_BINDINGS, job.getFragmentShader().get());
    assertEquals(new PipelineInfo(JSON_NO_BINDINGS).toString(), job.getPipelineInfo().toString());

  }

  private static final String VERT_SHADER_NO_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION = ""
      + "uniform float a, b;"
      + "uniform int c, d;"
      + "uniform vec2 e;"
      + "void main() { }";

  private static final String FRAG_SHADER_NO_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION = ""
      + "uniform float b, f;"
      + "uniform int c, d, h;"
      + "uniform vec2 e, i;"
      + "void main() { }";

  private static final String JSON_NO_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION = "{"
      + "  \"a\": {"
      + "    \"args\": ["
      + "      1.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  }, "
      + "  \"b\": {"
      + "    \"args\": ["
      + "      0.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  }, "
      + "  \"c\": {"
      + "    \"args\": ["
      + "      2"
      + "    ], "
      + "    \"func\": \"glUniform1i\""
      + "  }, "
      + "  \"d\": {"
      + "    \"args\": ["
      + "      5"
      + "    ], "
      + "    \"func\": \"glUniform1i\""
      + "  }, "
      + "  \"e\": {"
      + "    \"args\": ["
      + "      12.0,"
      + "      13.0"
      + "    ], "
      + "    \"func\": \"glUniform2f\""
      + "  }, "
      + "  \"f\": {"
      + "    \"args\": ["
      + "      6.6"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  }, "
      + "  \"h\": {"
      + "    \"args\": ["
      + "      18"
      + "    ], "
      + "    \"func\": \"glUniform1i\""
      + "  }, "
      + "  \"i\": {"
      + "    \"args\": ["
      + "      129.0,"
      + "      138.0"
      + "    ], "
      + "    \"func\": \"glUniform2f\""
      + "  } "
      + "}";

  private static final String VERT_SHADER_WITH_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION = ""
      + "layout(set = 0, binding = 0) uniform buf0 { float a; };\n"
      + "layout(set = 0, binding = 1) uniform buf1 { float b; };\n"
      + "layout(set = 0, binding = 2) uniform buf2 { int c; };\n"
      + "layout(set = 0, binding = 3) uniform buf3 { int d; };\n"
      + "layout(set = 0, binding = 4) uniform buf4 { vec2 e; };\n"
      + "void main() { }\n";

  private static final String FRAG_SHADER_WITH_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION = ""
      + "layout(set = 0, binding = 1) uniform buf1 { float b; };\n"
      + "layout(set = 0, binding = 5) uniform buf5 { float f; };\n"
      + "layout(set = 0, binding = 2) uniform buf2 { int c; };\n"
      + "layout(set = 0, binding = 3) uniform buf3 { int d; };\n"
      + "layout(set = 0, binding = 6) uniform buf6 { int h; };\n"
      + "layout(set = 0, binding = 4) uniform buf4 { vec2 e; };\n"
      + "layout(set = 0, binding = 7) uniform buf7 { vec2 i; };\n"
      + "void main() { }";

  private static final String JSON_WITH_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION = "{"
      + "  \"a\": {"
      + "    \"args\": ["
      + "      1.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\","
      + "    \"binding\": 0"
      + "  }, "
      + "  \"b\": {"
      + "    \"args\": ["
      + "      0.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\","
      + "    \"binding\": 1"
      + "  }, "
      + "  \"c\": {"
      + "    \"args\": ["
      + "      2"
      + "    ], "
      + "    \"func\": \"glUniform1i\","
      + "    \"binding\": 2"
      + "  }, "
      + "  \"d\": {"
      + "    \"args\": ["
      + "      5"
      + "    ], "
      + "    \"func\": \"glUniform1i\","
      + "    \"binding\": 3"
      + "  }, "
      + "  \"e\": {"
      + "    \"args\": ["
      + "      12.0,"
      + "      13.0"
      + "    ], "
      + "    \"func\": \"glUniform2f\","
      + "    \"binding\": 4"
      + "  }, "
      + "  \"f\": {"
      + "    \"args\": ["
      + "      6.6"
      + "    ], "
      + "    \"func\": \"glUniform1f\","
      + "    \"binding\": 5"
      + "  }, "
      + "  \"h\": {"
      + "    \"args\": ["
      + "      18"
      + "    ], "
      + "    \"func\": \"glUniform1i\","
      + "    \"binding\": 6"
      + "  }, "
      + "  \"i\": {"
      + "    \"args\": ["
      + "      129.0,"
      + "      138.0"
      + "    ], "
      + "    \"func\": \"glUniform2f\","
      + "    \"binding\": 7"
      + "  } "
      + "}";

  @Test
  public void testMakeUniformBindingsMultipleVariablesInSingleDeclaration() throws Exception {

    final GlslShaderJob job = new GlslShaderJob(
        Optional.empty(),
        new PipelineInfo(JSON_NO_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION),
        ParseHelper.parse(getShaderFile("vert",
            VERT_SHADER_NO_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION)),
        ParseHelper.parse(getShaderFile("frag",
            FRAG_SHADER_NO_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION)));

    job.makeUniformBindings(Optional.empty());

    CompareAsts.assertEqualAsts(VERT_SHADER_WITH_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION,
        job.getVertexShader().get());
    CompareAsts.assertEqualAsts(FRAG_SHADER_WITH_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION,
        job.getFragmentShader().get());
    assertEquals(new PipelineInfo(JSON_WITH_BINDINGS_MULTIPLE_VARIABLES_PER_DECLARATION).toString(),
        job.getPipelineInfo().toString());
  }

  private static final String VERT_SHADER_NO_BINDINGS_ARRAYS = ""
      + "uniform float a[2];\n"
      + "uniform float b[3];\n"
      + "uniform int c[1];\n"
      + "uniform vec2 d[2];\n"
      + "void main() { }\n";

  private static final String FRAG_SHADER_NO_BINDINGS_ARRAYS = ""
      + "uniform float b[3];\n"
      + "uniform float f;\n"
      + "uniform float g[2];\n"
      + "uniform int e[6];\n"
      + "uniform vec2 d[2];\n"
      + "void main() { }\n";

  private static final String FRAG_SHADER_NO_BINDINGS_ARRAYS_MULTIPLE_VARS_PER_DECL = ""
      + "uniform float b[3], f, g[2];\n"
      + "uniform int e[6];\n"
      + "uniform vec2 d[2];\n"
      + "void main() { }\n";

  private static final String JSON_NO_BINDINGS_ARRAYS = "{"
      + "  \"a\": {\n"
      + "    \"args\": [\n"
      + "      1.0, 2.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1fv\"\n"
      + "  },\n"
      + "  \"b\": {\n"
      + "    \"args\": [\n"
      + "      0.0, 10.0, 20.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1fv\"\n"
      + "  },\n"
      + "  \"c\": {\n"
      + "    \"args\": [\n"
      + "      2\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1iv\"\n"
      + "  },\n"
      + "  \"d\": {\n"
      + "    \"args\": [\n"
      + "      0.0, 1.0, 2.0, 3.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform2fv\"\n"
      + "  },\n"
      + "  \"f\": {\n"
      + "    \"args\": [\n"
      + "      100.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1f\"\n"
      + "  },\n"
      + "  \"g\": {\n"
      + "    \"args\": [\n"
      + "      100.0, 200.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1fv\"\n"
      + "  },\n"
      + "  \"e\": {\n"
      + "    \"args\": [\n"
      + "      12, 10, 8, 6, 4, 2\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1iv\"\n"
      + "  }\n"
      + "}\n";

  private static final String VERT_SHADER_WITH_BINDINGS_ARRAYS = ""
      + "layout(set = 0, binding = 0) uniform buf0 { float a[2]; };\n"
      + "layout(set = 0, binding = 1) uniform buf1 { float b[3]; };\n"
      + "layout(set = 0, binding = 2) uniform buf2 { int c[1]; };\n"
      + "layout(set = 0, binding = 3) uniform buf3 { vec2 d[2]; };\n"
      + "void main() { }\n";

  private static final String FRAG_SHADER_WITH_BINDINGS_ARRAYS = ""
      + "layout(set = 0, binding = 1) uniform buf1 { float b[3]; };\n"
      + "layout(set = 0, binding = 4) uniform buf4 { float f; };\n"
      + "layout(set = 0, binding = 5) uniform buf5 { float g[2]; };\n"
      + "layout(set = 0, binding = 6) uniform buf6 { int e[6]; };\n"
      + "layout(set = 0, binding = 3) uniform buf3 { vec2 d[2]; };\n"
      + "void main() { }\n";

  private static final String JSON_WITH_BINDINGS_ARRAYS = "{\n"
      + "  \"a\": {\n"
      + "    \"args\": [\n"
      + "      1.0, 2.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1fv\",\n"
      + "    \"binding\": 0\n"
      + "  },\n"
      + "  \"b\": {\n"
      + "    \"args\": [\n"
      + "      0.0, 10.0, 20.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1fv\",\n"
      + "    \"binding\": 1\n"
      + "  },\n"
      + "  \"c\": {\n"
      + "    \"args\": [\n"
      + "      2\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1iv\",\n"
      + "    \"binding\": 2\n"
      + "  },\n"
      + "  \"d\": {\n"
      + "    \"args\": [\n"
      + "      0.0, 1.0, 2.0, 3.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform2fv\",\n"
      + "    \"binding\": 3\n"
      + "  },\n"
      + "  \"f\": {\n"
      + "    \"args\": [\n"
      + "      100.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1f\",\n"
      + "    \"binding\": 4\n"
      + "  },\n"
      + "  \"g\": {\n"
      + "    \"args\": [\n"
      + "      100.0, 200.0\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1fv\",\n"
      + "    \"binding\": 5\n"
      + "  },\n"
      + "  \"e\": {\n"
      + "    \"args\": [\n"
      + "      12, 10, 8, 6, 4, 2\n"
      + "    ],\n"
      + "    \"func\": \"glUniform1iv\",\n"
      + "    \"binding\": 6\n"
      + "  }\n"
      + "}\n";

  @Test
  public void testMakeUniformBindingsArrays() throws Exception {

    final GlslShaderJob job = new GlslShaderJob(
        Optional.empty(),
        new PipelineInfo(JSON_NO_BINDINGS_ARRAYS),
        ParseHelper.parse(getShaderFile("vert", VERT_SHADER_NO_BINDINGS_ARRAYS)),
        ParseHelper.parse(getShaderFile("frag",
            FRAG_SHADER_NO_BINDINGS_ARRAYS_MULTIPLE_VARS_PER_DECL)));

    job.makeUniformBindings(Optional.empty());

    CompareAsts.assertEqualAsts(VERT_SHADER_WITH_BINDINGS_ARRAYS, job.getVertexShader().get());
    CompareAsts.assertEqualAsts(FRAG_SHADER_WITH_BINDINGS_ARRAYS, job.getFragmentShader().get());
    assertEquals(new PipelineInfo(JSON_WITH_BINDINGS_ARRAYS).toString(),
        job.getPipelineInfo().toString());
  }

  @Test
  public void testRemoveUniformBindingsArrays() throws Exception {

    final GlslShaderJob job = new GlslShaderJob(
        Optional.empty(),
        new PipelineInfo(JSON_WITH_BINDINGS_ARRAYS),
        ParseHelper.parse(getShaderFile("vert", VERT_SHADER_WITH_BINDINGS_ARRAYS)),
        ParseHelper.parse(getShaderFile("frag", FRAG_SHADER_WITH_BINDINGS_ARRAYS)));

    job.removeUniformBindings();

    CompareAsts.assertEqualAsts(VERT_SHADER_NO_BINDINGS_ARRAYS, job.getVertexShader().get());
    CompareAsts.assertEqualAsts(FRAG_SHADER_NO_BINDINGS_ARRAYS, job.getFragmentShader().get());
    assertEquals(new PipelineInfo(JSON_NO_BINDINGS_ARRAYS).toString(),
        job.getPipelineInfo().toString());

  }

  private static final String VERT_SHADER_WITH_SAMPLERS_NO_BINDINGS = ""
      + "uniform float a;"
      + "uniform sampler2D vtex;"
      + "uniform float b;"
      + "void main() { }";

  private static final String FRAG_SHADER_WITH_SAMPLERS_NO_BINDINGS = ""
      + "uniform float b;"
      + "uniform sampler3D ftex;"
      + "uniform float f[2];"
      + "void main() { }";

  private static final String JSON_WITH_SAMPLERS_NO_BINDINGS = "{"
      + "  \"a\": {"
      + "    \"args\": ["
      + "      1.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  }, "
      + "  \"b\": {"
      + "    \"args\": ["
      + "      0.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  }, "
      + "  \"f\": {"
      + "    \"args\": ["
      + "      100.0, 50.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\""
      + "  },"
      + "  \"vtex\": {"
      + "    \"func\": \"sampler2D\","
      + "    \"texture\": \"DEFAULT\""
      + "  },"
      + "  \"ftex\": {"
      + "    \"func\": \"sampler3D\","
      + "    \"texture\": \"DEFAULT\""
      + "  }"
      + "}";

  private static final String VERT_SHADER_WITH_SAMPLERS_WITH_BINDINGS = ""
      + "layout(set = 0, binding = 0) uniform buf0 { float a; };"
      + "layout(set = 0, binding = 1) uniform sampler2D vtex;"
      + "layout(set = 0, binding = 2) uniform buf2 { float b; };"
      + "void main() { }";

  private static final String FRAG_SHADER_WITH_SAMPLERS_WITH_BINDINGS = ""
      + "layout(set = 0, binding = 2) uniform buf2 { float b; };"
      + "layout(set = 0, binding = 3) uniform sampler3D ftex;"
      + "layout(set = 0, binding = 4) uniform buf4 { float f[2]; };"
      + "void main() { }";

  private static final String JSON_WITH_SAMPLERS_WITH_BINDINGS = "{"
      + "  \"a\": {"
      + "    \"args\": ["
      + "      1.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\", "
      + "    \"binding\": 0"
      + "  }, "
      + "  \"b\": {"
      + "    \"args\": ["
      + "      0.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\", "
      + "    \"binding\": 2"
      + "  }, "
      + "  \"f\": {"
      + "    \"args\": ["
      + "      100.0, 50.0"
      + "    ], "
      + "    \"func\": \"glUniform1f\", "
      + "    \"binding\": 4"
      + "  },"
      + "  \"vtex\": {"
      + "    \"func\": \"sampler2D\","
      + "    \"texture\": \"DEFAULT\","
      + "    \"binding\": 1"
      + "  },"
      + "  \"ftex\": {"
      + "    \"func\": \"sampler3D\","
      + "    \"texture\": \"DEFAULT\","
      + "    \"binding\": 3"
      + "  }"
      + "}";


  @Test
  public void testMakeUniformBindingsWithSamplers() throws Exception {

    final GlslShaderJob job = new GlslShaderJob(
        Optional.empty(),
        new PipelineInfo(JSON_WITH_SAMPLERS_NO_BINDINGS),
        ParseHelper.parse(getShaderFile("vert", VERT_SHADER_WITH_SAMPLERS_NO_BINDINGS)),
        ParseHelper.parse(getShaderFile("frag", FRAG_SHADER_WITH_SAMPLERS_NO_BINDINGS)));

    job.makeUniformBindings(Optional.empty());

    CompareAsts.assertEqualAsts(VERT_SHADER_WITH_SAMPLERS_WITH_BINDINGS,
        job.getVertexShader().get());
    CompareAsts.assertEqualAsts(FRAG_SHADER_WITH_SAMPLERS_WITH_BINDINGS,
        job.getFragmentShader().get());
    assertEquals(new PipelineInfo(JSON_WITH_SAMPLERS_WITH_BINDINGS).toString(),
        job.getPipelineInfo().toString());
  }

  @Test
  public void testRemoveUniformBindingsWithSamplers() throws Exception {

    final GlslShaderJob job = new GlslShaderJob(
        Optional.empty(),
        new PipelineInfo(JSON_WITH_SAMPLERS_WITH_BINDINGS),
        ParseHelper.parse(getShaderFile("vert", VERT_SHADER_WITH_SAMPLERS_WITH_BINDINGS)),
        ParseHelper.parse(getShaderFile("frag", FRAG_SHADER_WITH_SAMPLERS_WITH_BINDINGS)));

    job.removeUniformBindings();

    CompareAsts.assertEqualAsts(VERT_SHADER_WITH_SAMPLERS_NO_BINDINGS,
        job.getVertexShader().get());
    CompareAsts.assertEqualAsts(FRAG_SHADER_WITH_SAMPLERS_NO_BINDINGS,
        job.getFragmentShader().get());
    assertEquals(new PipelineInfo(JSON_WITH_SAMPLERS_NO_BINDINGS).toString(),
        job.getPipelineInfo().toString());

  }


  private File getShaderFile(String extension, String content) throws IOException {
    final File file = temporaryFolder.newFile("shader." + extension);
    FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
    return file;
  }

}
