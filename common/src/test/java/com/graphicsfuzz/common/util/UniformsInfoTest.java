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


}