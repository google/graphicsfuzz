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
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MakeArrayAccessesInBoundsTest {

  @Test
  public void testBasic() throws Exception {
    final String shader = "void main() { int A[5]; int x = 17; A[x] = 2; }";
    final String expected = "void main() { int A[5]; int x = 17; A[(x) >= 0 && (x) < 5 ? x : 0] = 2; }";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final Typer typer = new Typer(tu, ShadingLanguageVersion.ESSL_300);
    MakeArrayAccessesInBounds.makeInBounds(tu, typer);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testMatrixVector() throws Exception {
    final String shader = "void main() { mat4x2 As[5]; int x = 17; int y = -22; int z = 100; As[x][y][z] = 2.0; }";
    final String expected = "void main() { mat4x2 As[5]; int x = 17; int y = -22; int z = 100;"
          + "As[(x) >= 0 && (x) < 5 ? x : 0]"
          + "  /* column */ [(y) >= 0 && (y) < 4 ? y : 0]"
          + "  /* row */ [(z) >= 0 && (z) < 2 ? z : 0] = 2.0; }";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final Typer typer = new Typer(tu, ShadingLanguageVersion.ESSL_300);
    MakeArrayAccessesInBounds.makeInBounds(tu, typer);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testMatrixVector2() throws Exception {
    final String shader = "void main() { mat3x4 As[5];"
          + "  int x = 17;"
          + "  int y = -22;"
          + "  int z = 100;"
          + "  mat3x4 A = As[x];"
          + "  vec4 v;"
          + "  v = A[y];"
          + "  float f;"
          + "  f = v[z];"
          + "}";
    final String expected = "void main() { mat3x4 As[5];"
          + "  int x = 17;"
          + "  int y = -22;"
          + "  int z = 100;"
          + "  mat3x4 A = As[(x) >= 0 && (x) < 5 ? x : 0];"
          + "  vec4 v;"
          + "  v = A[(y) >= 0 && (y) < 3 ? y : 0];"
          + "  float f;"
          + "  f = v[(z) >= 0 && (z) < 4 ? z : 0];"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final Typer typer = new Typer(tu, ShadingLanguageVersion.ESSL_300);
    MakeArrayAccessesInBounds.makeInBounds(tu, typer);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}