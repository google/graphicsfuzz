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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import java.io.IOException;

public class CompareAsts {

  public static void assertEqualAsts(String first, String second)
      throws IOException, ParseTimeoutException, InterruptedException {
    assertEquals(
          PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(first)),
          PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(second))
    );
  }

  public static void assertEqualAsts(String string, TranslationUnit tu)
      throws IOException, ParseTimeoutException, InterruptedException {
    assertEqualAsts(string, PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  public static void assertEqualAsts(TranslationUnit first, TranslationUnit second)
      throws IOException, ParseTimeoutException, InterruptedException {
    assertEqualAsts(PrettyPrinterVisitor.prettyPrintAsString(first),
          PrettyPrinterVisitor.prettyPrintAsString(second));
  }

  public static boolean isEqualAsts(String first, String second) throws IOException,
      ParseTimeoutException, InterruptedException {
    return PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(first))
        .equals(
            PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(second)));
  }

  public static boolean isEqualAsts(String first, TranslationUnit second) throws IOException,
      ParseTimeoutException, InterruptedException {
    return isEqualAsts(first, PrettyPrinterVisitor.prettyPrintAsString(second));
  }

}
