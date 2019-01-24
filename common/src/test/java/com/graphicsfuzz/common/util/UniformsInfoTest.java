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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.type.BasicType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public class UniformsInfoTest {

  @Test
  public void testRename() {
    final UniformsInfo uniformsInfo = new UniformsInfo();
    uniformsInfo.addUniform("a", BasicType.FLOAT, Optional.empty(), Arrays.asList(1.0));
    uniformsInfo.addUniform("b", BasicType.FLOAT, Optional.empty(), Arrays.asList(2.0));
    uniformsInfo.addUniform("c", BasicType.FLOAT, Optional.empty(), Arrays.asList(3.0));
    uniformsInfo.addUniform("d", BasicType.FLOAT, Optional.empty(), Arrays.asList(4.0));
    uniformsInfo.addUniform("e", BasicType.FLOAT, Optional.empty(), Arrays.asList(5.0));
    Map<String, String> renaming = new HashMap<>();
    renaming.put("a", "z");
    renaming.put("b", "a");
    renaming.put("c", "d");
    renaming.put("d", "b");
    final UniformsInfo newUniformsInfo = uniformsInfo.renameUniforms(renaming);
    final String wasA = "\"z\": {\n"
          + "    \"func\": \"glUniform1f\",\n"
          + "    \"args\": [\n"
          + "      1.0\n"
          + "    ]\n"
          + "  }";

    final String wasB = "\"a\": {\n"
          + "    \"func\": \"glUniform1f\",\n"
          + "    \"args\": [\n"
          + "      2.0\n"
          + "    ]\n"
          + "  }";
    final String wasC = "\"d\": {\n"
          + "    \"func\": \"glUniform1f\",\n"
          + "    \"args\": [\n"
          + "      3.0\n"
          + "    ]\n"
          + "  }";
    final String wasD = "\"b\": {\n"
          + "    \"func\": \"glUniform1f\",\n"
          + "    \"args\": [\n"
          + "      4.0\n"
          + "    ]\n"
          + "  }";
    final String wasE = "\"e\": {\n"
          + "    \"func\": \"glUniform1f\",\n"
          + "    \"args\": [\n"
          + "      5.0\n"
          + "    ]\n"
          + "  }";
    assertTrue(newUniformsInfo.toString().contains(wasA));
    assertTrue(newUniformsInfo.toString().contains(wasB));
    assertTrue(newUniformsInfo.toString().contains(wasC));
    assertTrue(newUniformsInfo.toString().contains(wasD));
    assertTrue(newUniformsInfo.toString().contains(wasE));
  }

  @Test
  public void testBindings() {
    final String uniforms = "{" +
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
        "    \"func\": \"glUniform1f\"" +
        "  }" +
        "}";
    final UniformsInfo uniformsInfo = new UniformsInfo(uniforms);
    assertTrue(uniformsInfo.hasBinding("a"));
    assertFalse(uniformsInfo.hasBinding("b"));
    assertEquals(0, uniformsInfo.getBinding("a"));
  }

  @Test
  public void testZeroUnsetMatrices() throws Exception {
    final String expectedUniforms = "{" +
        "  \"m1\": {" +
        "    \"func\": \"glUniformMatrix2fv\"," +
        "    \"args\": [" +
        "      0.0, 0.0, 0.0, 0.0" +
        "    ]" +
        "  }, " +
        "  \"m2\": {" +
        "    \"func\": \"glUniformMatrix2x3fv\"," +
        "    \"args\": [" +
        "      0.0, 0.0, 0.0, 0.0, 0.0, 0.0" +
        "    ]" +
        "  }, " +
        "  \"m3\": {" +
        "    \"func\": \"glUniformMatrix2x4fv\"," +
        "    \"args\": [" +
        "      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0" +
        "    ]" +
        "  }, " +
        "  \"m4\": {" +
        "    \"func\": \"glUniformMatrix3x2fv\"," +
        "    \"args\": [" +
        "      0.0, 0.0, 0.0, 0.0, 0.0, 0.0" +
        "    ]" +
        "  }, " +
        "  \"m5\": {" +
        "    \"func\": \"glUniformMatrix3fv\"," +
        "    \"args\": [" +
        "      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0" +
        "    ]" +
        "  }, " +
        "  \"m6\": {" +
        "    \"func\": \"glUniformMatrix3x4fv\"," +
        "    \"args\": [" +
        "      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0" +
        "    ]" +
        "  }," +
        "  \"m7\": {" +
        "    \"func\": \"glUniformMatrix4x2fv\"," +
        "    \"args\": [" +
        "      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0" +
        "    ]" +
        "  }," +
        "  \"m8\": {" +
        "    \"func\": \"glUniformMatrix4x3fv\"," +
        "    \"args\": [" +
        "      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0" +
        "    ]" +
        "  }, " +
        "  \"m9\": {" +
        "    \"func\": \"glUniformMatrix4fv\"," +
        "    \"args\": [" +
        "      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0" +
        "    ]" +
        "  }" +
        "}";
    final String shader = "#version 310 es\n"
        + "uniform mat2x2 m1;"
        + "uniform mat2x3 m2;"
        + "uniform mat2x4 m3;"
        + "uniform mat3x2 m4;"
        + "uniform mat3x3 m5;"
        + "uniform mat3x4 m6;"
        + "uniform mat4x2 m7;"
        + "uniform mat4x3 m8;"
        + "uniform mat4x4 m9;"
        + "void main() { }";
    final UniformsInfo uniforms = new UniformsInfo("{}");
    uniforms.zeroUnsetUniforms(ParseHelper.parse(shader));
    assertEquals(new UniformsInfo(expectedUniforms).toString(), uniforms.toString());
  }

  private String zeros(int num) {
    String result = "";
    for (int i = 0; i < num; i++) {
      if (i > 0) {
        result += ", ";
      }
      result += "0.0";
    }
    return result;
  }

  @Test
  public void testZeroUnsetMatrixArrays() throws Exception {
    final String expectedUniforms = "{" +
        "  \"m1\": {" +
        "    \"func\": \"glUniformMatrix2fv\"," +
        "    \"args\": [" +
        "      " + zeros(2 * 2 * 1) +
        "    ]," +
        "  \"count\": 1" +
        "  }, " +
        "  \"m2\": {" +
        "    \"func\": \"glUniformMatrix2x3fv\"," +
        "    \"args\": [" +
        "      " + zeros(2 * 3 * 2) +
        "    ]," +
        "  \"count\": 2" +
        "  }, " +
        "  \"m3\": {" +
        "    \"func\": \"glUniformMatrix2x4fv\"," +
        "    \"args\": [" +
        "      " + zeros(2 * 4 * 3) +
        "    ]," +
        "  \"count\": 3" +
        "  }, " +
        "  \"m4\": {" +
        "    \"func\": \"glUniformMatrix3x2fv\"," +
        "    \"args\": [" +
        "      " + zeros(3 * 2 * 4) +
        "    ]," +
        "  \"count\": 4" +
        "  }, " +
        "  \"m5\": {" +
        "    \"func\": \"glUniformMatrix3fv\"," +
        "    \"args\": [" +
        "      " + zeros(3 * 3 * 5) +
        "    ]," +
        "  \"count\": 5" +
        "  }, " +
        "  \"m6\": {" +
        "    \"func\": \"glUniformMatrix3x4fv\"," +
        "    \"args\": [" +
        "      " + zeros(3 * 4 * 6) +
        "    ]," +
        "  \"count\": 6" +
        "  }," +
        "  \"m7\": {" +
        "    \"func\": \"glUniformMatrix4x2fv\"," +
        "    \"args\": [" +
        "      " + zeros(4 * 2 * 7) +
        "    ]," +
        "  \"count\": 7" +
        "  }," +
        "  \"m8\": {" +
        "    \"func\": \"glUniformMatrix4x3fv\"," +
        "    \"args\": [" +
        "      " + zeros(4 * 3 * 8) +
        "    ]," +
        "  \"count\": 8" +
        "  }, " +
        "  \"m9\": {" +
        "    \"func\": \"glUniformMatrix4fv\"," +
        "    \"args\": [" +
        "      " + zeros(4 * 4 * 9) +
        "    ]," +
        "  \"count\": 9" +
        "  }" +
        "}";
    final String shader = "#version 310 es\n"
        + "uniform mat2x2 m1[1];"
        + "uniform mat2x3 m2[2];"
        + "uniform mat2x4 m3[3];"
        + "uniform mat3x2 m4[4];"
        + "uniform mat3x3 m5[5];"
        + "uniform mat3x4 m6[6];"
        + "uniform mat4x2 m7[7];"
        + "uniform mat4x3 m8[8];"
        + "uniform mat4x4 m9[9];"
        + "void main() { }";
    final UniformsInfo uniforms = new UniformsInfo("{}");
    uniforms.zeroUnsetUniforms(ParseHelper.parse(shader));
    assertEquals(new UniformsInfo(expectedUniforms).toString(), uniforms.toString());
  }


}
