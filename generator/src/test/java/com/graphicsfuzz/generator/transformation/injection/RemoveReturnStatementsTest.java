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

package com.graphicsfuzz.generator.transformation.injection;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.generator.util.RemoveReturnStatements;
import org.junit.Test;

public class RemoveReturnStatementsTest {

  @Test
  public void testDo() throws Exception {
    final String prog = "void main() { do return; while(true); }";
    final String expectedProg = "void main() { do 1; while(true); }";
    TranslationUnit tu = ParseHelper.parse(prog);
    new RemoveReturnStatements(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProg)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testWhile() throws Exception {
    final String prog = "void main() { while(true) return; }";
    final String expectedProg = "void main() { while(true) 1; }";
    TranslationUnit tu = ParseHelper.parse(prog);
    new RemoveReturnStatements(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProg)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testFor() throws Exception {
    final String prog = "void main() { for(int i = 0; i < 100; i++) return; }";
    final String expectedProg = "void main() { for(int i = 0; i < 100; i++) 1; }";
    TranslationUnit tu = ParseHelper.parse(prog);
    new RemoveReturnStatements(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProg)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testIf() throws Exception {
    final String prog = "void main() { if(true) return; else return; }";
    final String expectedProg = "void main() { if(true) 1; else 1; }";
    TranslationUnit tu = ParseHelper.parse(prog);
    new RemoveReturnStatements(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProg)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testSwitch() throws Exception {
    final String prog = "void main() {\n"
        + "  switch(0) {\n"
        + "    case 0:\n"
        + "      return;\n"
        + "  }\n"
        + "}\n";
    final String expectedProg = "void main() {\n"
        + "  switch(0) {\n"
        + "    case 0:\n"
        + "      1;\n"
        + "  }\n"
        + "}\n";
    TranslationUnit tu = ParseHelper.parse(prog);
    new RemoveReturnStatements(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProg)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}
