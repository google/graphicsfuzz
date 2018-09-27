package com.graphicsfuzz.common.transformreduce;

import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.*;

public class GlslShaderJobTest {

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
        Optional.of(Helper.parse(VERT_SHADER_NO_BINDINGS, false)),
        Optional.of(Helper.parse(FRAG_SHADER_NO_BINDINGS, false)),
        new UniformsInfo(JSON_NO_BINDINGS));

    job.makeUniformBindings();

    CompareAsts.assertEqualAsts(VERT_SHADER_WITH_BINDINGS, job.getVertexShader());
    CompareAsts.assertEqualAsts(FRAG_SHADER_WITH_BINDINGS, job.getFragmentShader());
    assertEquals(new UniformsInfo(JSON_WITH_BINDINGS).toString(), job.getUniformsInfo().toString());
  }


  @Test
  public void testRemoveUniformBindings() throws Exception {

    final GlslShaderJob job = new GlslShaderJob(
        Optional.of(Helper.parse(VERT_SHADER_WITH_BINDINGS, false)),
        Optional.of(Helper.parse(FRAG_SHADER_WITH_BINDINGS, false)),
        new UniformsInfo(JSON_WITH_BINDINGS));

    job.removeUniformBindings();

    CompareAsts.assertEqualAsts(VERT_SHADER_NO_BINDINGS, job.getVertexShader());
    CompareAsts.assertEqualAsts(FRAG_SHADER_NO_BINDINGS, job.getFragmentShader());
    assertEquals(new UniformsInfo(JSON_NO_BINDINGS).toString(), job.getUniformsInfo().toString());

  }

}