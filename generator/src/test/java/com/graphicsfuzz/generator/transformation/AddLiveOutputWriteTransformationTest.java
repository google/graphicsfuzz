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
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.AddLiveOutputWriteTransformation;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AddLiveOutputWriteTransformationTest {

  private static final String trivialProgramEssl100 = "#version 100\nvoid main() { }";
  private static final String trivialProgramEssl300 = "#version 300 es\nvoid main() { }";
  private static final String trivialProgramEssl310 = "#version 310 es\nvoid main() { }";

  @Test
  public void testGlFragColorWritesFragment100() throws Exception {
    final TranslationUnit trivialTu = ParseHelper.parse(trivialProgramEssl100);
    final ShaderKind shaderKind = ShaderKind.FRAGMENT;
    applyTransformation(trivialTu, shaderKind, 0);
    assertEquals(OpenGlConstants.GL_FRAG_COLOR, getBackedUpVariableName(trivialTu));
  }

  @Test
  public void testNoGlFragColorWritesVertex100() throws Exception {
    final TranslationUnit trivialTu = ParseHelper.parse(trivialProgramEssl100);
    final ShaderKind shaderKind = ShaderKind.VERTEX;
    applyTransformation(trivialTu, shaderKind, 0);
    assertNotEquals(OpenGlConstants.GL_FRAG_COLOR, getBackedUpVariableName(trivialTu));
  }

  @Test
  public void testNoGlFragColorWritesFragment310() throws Exception {
    final TranslationUnit trivialTu = ParseHelper.parse(trivialProgramEssl300);
    final ShaderKind shaderKind = ShaderKind.FRAGMENT;
    applyTransformation(trivialTu, shaderKind, 0);
    assertEquals(0,
        ((FunctionDefinition) trivialTu.getTopLevelDeclarations().get(0)).getBody()
        .getNumStmts());
  }

  @Test
  public void testGlPositionOrGlPointSizeWritesVertex310() throws Exception {
    final ShaderKind shaderKind = ShaderKind.VERTEX;
    for (int i = 0; i < 5; i++) {
      final TranslationUnit trivialTu = ParseHelper.parse(trivialProgramEssl310);
      applyTransformation(trivialTu, shaderKind, i);
      assertTrue(Arrays.asList(OpenGlConstants.GL_POSITION, OpenGlConstants.GL_POINT_SIZE)
          .contains(getBackedUpVariableName(trivialTu)));
    }
  }

  @Test
  public void testOutVariableWritesFragment310() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 310 es\nlayout(location = 0) out vec2 "
        + "someoutvar;\n"
        + "void main() { }");
    final ShaderKind shaderKind = ShaderKind.FRAGMENT;
    applyTransformation(tu, shaderKind, 0);
    assertEquals("someoutvar", getBackedUpVariableName(tu));
  }

  private void applyTransformation(TranslationUnit tu, ShaderKind shaderKind, int seed) {
    new AddLiveOutputWriteTransformation().apply(tu, TransformationProbabilities.onlyAddLiveFragColorWrites(),
        new RandomWrapper(seed),
        GenerationParams.normal(shaderKind, true));
  }

  private String getBackedUpVariableName(TranslationUnit tu) {
    return ((VariableIdentifierExpr) ((BinaryExpr) ((ExprStmt)
        ((BlockStmt) tu.getMainFunction().getBody().getStmt(0))
            .getStmt(1)).getExpr()).getRhs()).getName();
  }

}
