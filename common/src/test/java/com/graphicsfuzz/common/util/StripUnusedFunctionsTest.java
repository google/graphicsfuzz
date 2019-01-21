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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.StripUnusedFunctions;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StripUnusedFunctionsTest {

  @Test
  public void strip() throws Exception {
    String programBefore =
          "void foo();\n"
        + "void bar() {\n"
        + "}\n"
        + "void buzz() {\n"
        + "}\n"
        + "void baz() {\n"
        + "  buzz();\n"
        + "}\n"
        + "int garb(int z) {\n"
        + "}\n"
        + "int glib() {\n"
        + "  return 2;\n"
        + "}\n"
        + "void main() {\n"
        + "  int z = garb(glib());\n"
        + "  baz();"
        + "}\n";

    String programAfter =
          "void buzz() {\n"
        + "}\n"
        + "void baz() {\n"
        + "  buzz();\n"
        + "}\n"
        + "int garb(int z) {\n"
        + "}\n"
        + "int glib() {\n"
        + "  return 2;\n"
        + "}\n"
        + "void main() {\n"
        + "  int z = garb(glib());\n"
        + "  baz();"
        + "}\n";

    TranslationUnit tuBefore = ParseHelper.parse(programBefore);
    TranslationUnit tuAfter = ParseHelper.parse(programAfter);
    StripUnusedFunctions.strip(tuBefore);
    assertEquals(
      PrettyPrinterVisitor.prettyPrintAsString(tuAfter),
        PrettyPrinterVisitor.prettyPrintAsString(tuBefore)
    );

  }

}
