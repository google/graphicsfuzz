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

package com.graphicsfuzz.generator.transformation.controlflow;

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
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AddLiveOutputVariableWritesTest {

  private static final String trivialProgram = "void main() { }";

  @Test
  public void testGlFragColorWritesFragment100() throws Exception {
    final TranslationUnit trivialTu = ParseHelper.parse(trivialProgram);
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_100;
    final ShaderKind shaderKind = ShaderKind.FRAGMENT;
    applyTransformation(trivialTu, shadingLanguageVersion, shaderKind, 0);
    assertEquals(OpenGlConstants.GL_FRAG_COLOR, getBackedUpVariableName(trivialTu));
  }

  @Test
  public void testNoGlFragColorWritesVertex100() throws Exception {
    final TranslationUnit trivialTu = ParseHelper.parse(trivialProgram);
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_100;
    final ShaderKind shaderKind = ShaderKind.VERTEX;
    applyTransformation(trivialTu, shadingLanguageVersion, shaderKind, 0);
    assertNotEquals(OpenGlConstants.GL_FRAG_COLOR, getBackedUpVariableName(trivialTu));
  }

  @Test
  public void testNoGlFragColorWritesFragment310() throws Exception {
    final TranslationUnit trivialTu = ParseHelper.parse(trivialProgram);
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_300;
    final ShaderKind shaderKind = ShaderKind.FRAGMENT;
    applyTransformation(trivialTu, shadingLanguageVersion, shaderKind, 0);
    assertEquals(0,
        ((FunctionDefinition) trivialTu.getTopLevelDeclarations().get(0)).getBody()
        .getNumStmts());
  }

  @Test
  public void testGlPositionOrGlPointSizeWritesVertex310() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_300;
    final ShaderKind shaderKind = ShaderKind.VERTEX;
    for (int i = 0; i < 5; i++) {
      final TranslationUnit trivialTu = ParseHelper.parse(trivialProgram);
      applyTransformation(trivialTu, shadingLanguageVersion, shaderKind, i);
      assertTrue(Arrays.asList(OpenGlConstants.GL_POSITION, OpenGlConstants.GL_POINT_SIZE)
          .contains(getBackedUpVariableName(trivialTu)));
    }
  }

  @Test
  public void testOutVariableWritesFragment310() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("layout(location = 0) out vec2 someoutvar;\n"
        + "void main() { }");
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_300;
    final ShaderKind shaderKind = ShaderKind.FRAGMENT;
    applyTransformation(tu, shadingLanguageVersion, shaderKind, 0);
    assertEquals("someoutvar", getBackedUpVariableName(tu));
  }

  private void applyTransformation(TranslationUnit tu, ShadingLanguageVersion shadingLanguageVersion, ShaderKind shaderKind, int seed) {
    new AddLiveOutputVariableWrites().apply(tu, TransformationProbabilities.onlyAddLiveFragColorWrites(),
        shadingLanguageVersion, new RandomWrapper(seed),
        GenerationParams.normal(shaderKind, true));
  }

  private String getBackedUpVariableName(TranslationUnit tu) {
    final FunctionDefinition main = tu.getTopLevelDeclarations()
        .stream()
        .filter(item -> item instanceof FunctionDefinition)
        .map(item -> (FunctionDefinition) item)
        .filter(item -> item.getPrototype().getName().equals("main"))
        .findAny()
        .get();
    return ((VariableIdentifierExpr) ((BinaryExpr) ((ExprStmt)
        ((BlockStmt) main.getBody().getStmt(0))
            .getStmt(1)).getExpr()).getRhs()).getName();
  }

}