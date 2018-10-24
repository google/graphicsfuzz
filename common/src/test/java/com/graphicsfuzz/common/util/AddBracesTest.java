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
import static org.junit.Assert.assertNotEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AddBracesTest {

  @Test
  public void danglingElse() throws IOException, ParseTimeoutException {
    String program = "void main() { if (a) if (b) s1; else s2; }";
    TranslationUnit tu = ParseHelper.parse(program);
    TranslationUnit transformed = AddBraces.transform(tu);
    assertNotEquals(tu, transformed);

    String programAfter = "void main() { if (a) { if (b) { s1; } } else { s2; } }";

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(
        ParseHelper.parse(programAfter)),
        PrettyPrinterVisitor.prettyPrintAsString(transformed));
  }

  @Test
  public void loops() throws IOException, ParseTimeoutException {
    String program =      "void main() { for (a; b; c) while (d) do e; while (f); }";
    String programAfter = "void main() { for (a; b; c) { while (d) { do { e; } while (f); } } }";

    TranslationUnit tu = ParseHelper.parse(program);
    TranslationUnit transformed = AddBraces.transform(tu);
    assertNotEquals(tu, transformed);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(
        ParseHelper.parse(programAfter)),
        PrettyPrinterVisitor.prettyPrintAsString(transformed));
  }

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testMain() throws IOException, ParseTimeoutException {
    File input = testFolder.newFile("input.frag");
    String program = "void main() { for (a; b; c) while (d) do e; while (f); }";
    BufferedWriter bw = new BufferedWriter(new FileWriter(input));
    bw.write(program);
    bw.flush();
    bw.close();
    AddBraces.main(new String[] { input.getAbsolutePath() });
  }

  @Test
  public void testIsUtilityClass() throws Exception {
    CheckUtilityClass.assertUtilityClassWellDefined(AddBraces.class);
  }

}