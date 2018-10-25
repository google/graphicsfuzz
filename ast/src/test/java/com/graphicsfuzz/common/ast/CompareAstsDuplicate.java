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

package com.graphicsfuzz.common.ast;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import java.io.IOException;

/**
 * This class is deliberately a duplicate of another class, CompareAsts.
 * The ability to compare ASTs is really useful and is used in various projects.
 * It was deemed simplest to duplicate this code for the case when it is used in the
 * ast project itself.
 */
public class CompareAstsDuplicate {

  public static void assertEqualAsts(String first, String second)
        throws IOException, ParseTimeoutException {
    assertEquals(
          PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(first)),
          PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(second))
    );
  }

  public static void assertEqualAsts(String string, TranslationUnit tu)
        throws IOException, ParseTimeoutException {
    assertEqualAsts(string, PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  public static void assertEqualAsts(TranslationUnit first, TranslationUnit second)
        throws IOException, ParseTimeoutException {
    assertEqualAsts(PrettyPrinterVisitor.prettyPrintAsString(first),
          PrettyPrinterVisitor.prettyPrintAsString(second));
  }

}
