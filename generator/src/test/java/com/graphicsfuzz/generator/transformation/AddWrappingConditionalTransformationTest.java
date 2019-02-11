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

package com.graphicsfuzz.generator.transformation;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.CannedRandom;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.AddWrappingConditionalTransformation;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AddWrappingConditionalTransformationTest {

  @Test
  public void testIfProblems() throws Exception {
    final String prog = "#version 130\nint x; void main() { if(true) x++; }";
    TranslationUnit tu = ParseHelper.parse(prog);

    new AddWrappingConditionalTransformation().apply(tu, TransformationProbabilities.onlyWrap(),
        new CannedRandom(0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0),
        GenerationParams.normal(ShaderKind.FRAGMENT, true));
    checkStructuralProperties(tu);
    TranslationUnit tu2 = ParseHelper.parse(PrettyPrinterVisitor.prettyPrintAsString(tu));
    checkStructuralProperties(tu2);
  }

  private void checkStructuralProperties(TranslationUnit tu) {
    FunctionDefinition fd = (FunctionDefinition) tu.getTopLevelDeclarations().get(1);
    assertEquals(1, fd.getBody().getNumStmts());
    assertTrue(fd.getBody().getStmt(0) instanceof IfStmt);
    IfStmt ifStmt = ((IfStmt) fd.getBody().getStmt(0));
    assertTrue(ifStmt.hasElseStmt());
    assertTrue(ifStmt.getElseStmt() instanceof BlockStmt);
    assertEquals(0, ((BlockStmt) ifStmt.getElseStmt()).getNumStmts());

    assertTrue(ifStmt.getThenStmt() instanceof BlockStmt);
    assertEquals(1, ((BlockStmt) ifStmt.getThenStmt()).getNumStmts());
    IfStmt innerIf = (IfStmt) ((BlockStmt) ifStmt.getThenStmt()).getStmt(0);
    assertFalse(innerIf.hasElseStmt());
  }

}
