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

package com.graphicsfuzz.generator.transformation.donation;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.TruncateLoops;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TruncateLoopsTest {

  @Test
  public void testTruncateLoops() throws Exception {
    final String program = "void main() {"
          + "  int x = 0;"
          + "  for (int i = 0; i < 100; i++)"
          + "    while (x < i)"
          + "      do {"
          + "        x++;"
          + "        for (int j = 0; j < 200; j++) {"
          + "          ;"
          + "        }"
          + "      } while (x > 0);"
          + "}";
    final String expected = ""
          + "void main() {\n"
          + "  int x = 0;\n"
          + "  {\n"
          + "    int pre_looplimiter3 = 0;\n"
          + "    for (int i = 0; i < 100; i++) {\n"
          + "      if (pre_looplimiter3 >= 3) {\n"
          + "        break;\n"
          + "      }\n"
          + "      pre_looplimiter3++;\n"
          + "      int pre_looplimiter2 = 0;\n"
          + "      while (x < i) {\n"
          + "        if (pre_looplimiter2 >= 3) {\n"
          + "          break;\n"
          + "        }\n"
          + "        pre_looplimiter2++;\n"
          + "        int pre_looplimiter1 = 0;\n"
          + "        do {\n"
          + "          if (pre_looplimiter1 >= 3) {\n"
          + "            break;\n"
          + "          }\n"
          + "          pre_looplimiter1++;\n"
          + "          x++;\n"
          + "          {\n"
          + "            int pre_looplimiter0 = 0;\n"
          + "            for (int j = 0; j < 200; j++) {\n"
          + "              if (pre_looplimiter0 >= 3) {\n"
          + "                break;\n"
          + "              }\n"
          + "              pre_looplimiter0++;\n"
          + "              ;\n"
          + "            }\n"
          + "          }\n"
          + "        } while (x > 0);\n"
          + "      }\n"
          + "    }\n"
          + "  }\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    new TruncateLoops(3, "pre", tu, true);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testTruncateLoops2() throws Exception {
    final String program = "void main() {"
        + "  int x = 0;"
        + "  for (int i = 0; i < 2; i++)"
        + "    while (x < i)"
        + "      do {"
        + "        x++;"
        + "        for (int j = 0; j < 2; j++) {"
        + "          ;"
        + "        }"
        + "      } while (x > 0);"
        + "}";
    final String expected = ""
        + "void main() {\n"
        + "  int x = 0;\n"
        + "  {\n"
        + "    int pre_looplimiter3 = 0;\n"
        + "    for (int i = 0; i < 2; i++) {\n"
        + "      if (pre_looplimiter3 >= 3) {\n"
        + "        break;\n"
        + "      }\n"
        + "      pre_looplimiter3++;\n"
        + "      int pre_looplimiter2 = 0;\n"
        + "      while (x < i) {\n"
        + "        if (pre_looplimiter2 >= 3) {\n"
        + "          break;\n"
        + "        }\n"
        + "        pre_looplimiter2++;\n"
        + "        int pre_looplimiter1 = 0;\n"
        + "        do {\n"
        + "          if (pre_looplimiter1 >= 3) {\n"
        + "            break;\n"
        + "          }\n"
        + "          pre_looplimiter1++;\n"
        + "          x++;\n"
        + "          {\n"
        + "            int pre_looplimiter0 = 0;\n"
        + "            for (int j = 0; j < 2; j++) {\n"
        + "              if (pre_looplimiter0 >= 3) {\n"
        + "                break;\n"
        + "              }\n"
        + "              pre_looplimiter0++;\n"
        + "              ;\n"
        + "            }\n"
        + "          }\n"
        + "        } while (x > 0);\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    new TruncateLoops(3, "pre", tu, false);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testTruncateLoops3() throws Exception {
    final String program = "void main() {"
        + "  int x = 0;"
        + "  for (int i = 0; i < 2; i++)"
        + "    while (x < i)"
        + "      do {"
        + "        x++;"
        + "        for (int j = 0; j < 2; j++) {"
        + "          ;"
        + "        }"
        + "      } while (x > 0);"
        + "}";
    final String expected = ""
        + "void main() {\n"
        + "  int x = 0;\n"
        + "  for (int i = 0; i < 2; i++) {\n"
        + "    int pre_looplimiter1 = 0;\n"
        + "    while (x < i) {\n"
        + "      if (pre_looplimiter1 >= 3) {\n"
        + "        break;\n"
        + "      }\n"
        + "      pre_looplimiter1++;\n"
        + "      int pre_looplimiter0 = 0;\n"
        + "      do {\n"
        + "        if (pre_looplimiter0 >= 3) {\n"
        + "          break;\n"
        + "        }\n"
        + "        pre_looplimiter0++;\n"
        + "        x++;\n"
        + "        for (int j = 0; j < 2; j++) {\n"
        + "          ;\n"
        + "        }\n"
        + "      } while (x > 0);\n"
        + "    }\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    new TruncateLoops(3, "pre", tu, true);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}