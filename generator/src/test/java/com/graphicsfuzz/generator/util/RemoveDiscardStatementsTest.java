/*
 * Copyright 2021 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

public class RemoveDiscardStatementsTest {

  @Test
  public void testBasicRemoval() throws Exception {
    final String shader = "#version 320 es\n"
        + "void main() {\n"
        + "  discard;\n"
        + "}\n";
    final String expected = "#version 320 es\n"
        + "void main() {\n"
        + "  1;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    new RemoveDiscardStatements(tu);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testRemovalInSwitch() throws Exception {
    final String shader = "#version 320 es\n"
        + "void main() {\n"
        + "  switch(0) {"
        + "    case 0:"
        + "      discard;\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 320 es\n"
        + "void main() {\n"
        + "  switch(0) {"
        + "    case 0:"
        + "      1;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    new RemoveDiscardStatements(tu);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testRemovalInShader() throws Exception {
    final String shader = "float patternize(vec2 uv) {\n"
        + "    vec2 size = vec2(0.45);\n"
        + "    vec2 st = smoothstep(size, size, uv);\n"
        + "     switch (int(mod(gl_FragCoord.y, 5.0))) {\n"
        + "        case 0:\n"
        + "            return mix(pow(st.x, injectionSwitch.y), st.x, size.y);\n"
        + "        break;\n"
        + "        case 1:\n"
        + "            return mix(pow(uv.y, injectionSwitch.y), st.y, size.x);\n"
        + "        break;\n"
        + "        case 2:\n"
        + "            discard;\n"
        + "        break;\n"
        + "        case 3:\n"
        + "            return mix(pow(uv.y, injectionSwitch.y), uv.y, size.y);\n"
        + "        break;\n"
        + "        case 4:\n"
        + "            return mix(pow(st.y, injectionSwitch.y), st.x, size.x); \n"
        + "        break;\n"
        + "     }\n"
        + "}\n";
    final String expected = "float patternize(vec2 uv) {\n"
        + "    vec2 size = vec2(0.45);\n"
        + "    vec2 st = smoothstep(size, size, uv);\n"
        + "     switch (int(mod(gl_FragCoord.y, 5.0))) {\n"
        + "        case 0:\n"
        + "            return mix(pow(st.x, injectionSwitch.y), st.x, size.y);\n"
        + "        break;\n"
        + "        case 1:\n"
        + "            return mix(pow(uv.y, injectionSwitch.y), st.y, size.x);\n"
        + "        break;\n"
        + "        case 2:\n"
        + "            1;\n"
        + "        break;\n"
        + "        case 3:\n"
        + "            return mix(pow(uv.y, injectionSwitch.y), uv.y, size.y);\n"
        + "        break;\n"
        + "        case 4:\n"
        + "            return mix(pow(st.y, injectionSwitch.y), st.x, size.x); \n"
        + "        break;\n"
        + "     }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    new RemoveDiscardStatements(tu);
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
