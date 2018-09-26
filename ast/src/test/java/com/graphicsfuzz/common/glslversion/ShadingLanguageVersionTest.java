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

package com.graphicsfuzz.common.glslversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ShadingLanguageVersionTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testGlobalVariableInitializersMustBeConst() throws Exception {
    for (ShadingLanguageVersion shadingLanguageVersion : ShadingLanguageVersion.allShadingLanguageVersions()) {
      checkGlobalVariableInitializersMustBeConst(shadingLanguageVersion);
    }
  }

  @Test
  public void testInitializersOfConstMustBeConst() throws Exception {
    for (ShadingLanguageVersion shadingLanguageVersion : ShadingLanguageVersion.allShadingLanguageVersions()) {
      checkInitializersOfConstMustBeConst(shadingLanguageVersion);
    }
  }

  @Test
  public void testSupportedDoStmt() throws Exception {
    for (ShadingLanguageVersion shadingLanguageVersion : ShadingLanguageVersion.allShadingLanguageVersions()) {
      checkDoStmtSupport(shadingLanguageVersion);
    }
  }

  @Test
  public void testSupportedSwitchStmt() throws Exception {
    for (ShadingLanguageVersion shadingLanguageVersion : ShadingLanguageVersion.allShadingLanguageVersions()) {
      checkSwitchStmtSupport(shadingLanguageVersion);
    }
  }

  @Test
  public void testSupportedUnsigned() throws Exception {
    for (ShadingLanguageVersion shadingLanguageVersion : ShadingLanguageVersion.allShadingLanguageVersions()) {
      checkUnsignedSupport(shadingLanguageVersion);
    }
  }

  @Test
  public void testGetShaderVersionFromFile() throws Exception {
    final Map<String, ShadingLanguageVersion> expected = new HashMap<>();
    expected.put("#version 110\n"
        + "void main() { }", ShadingLanguageVersion.GLSL_110);
    expected.put("#version 330\n"
        + "void main() { }", ShadingLanguageVersion.GLSL_330);
    expected.put("#version 450\n"
        + "void main() { }", ShadingLanguageVersion.GLSL_450);
    expected.put("#version 100\n"
        + "void main() { }", ShadingLanguageVersion.ESSL_100);
    expected.put("#version 300 es\n"
        + "void main() { }", ShadingLanguageVersion.ESSL_300);
    expected.put("#version 310 es\n"
        + "void main() { }", ShadingLanguageVersion.ESSL_310);
    expected.put("#version 100\n"
        + "//WebGL\n"
        + "void main() { }", ShadingLanguageVersion.WEBGL_SL);
    expected.put("#version 300 es\n"
        + "//WebGL\n"
        + "void main() { }", ShadingLanguageVersion.WEBGL2_SL);
    for (String prog : expected.keySet()) {
      final File temp = temporaryFolder.newFile();
      FileUtils.writeStringToFile(temp, prog, StandardCharsets.UTF_8);
      assertEquals(expected.get(prog), ShadingLanguageVersion.getGlslVersionFromShader(temp));
      temp.delete();
    }
  }


  private void checkDoStmtSupport(ShadingLanguageVersion shadingLanguageVersion)
      throws IOException, InterruptedException {
    final boolean expectedInvalid = !shadingLanguageVersion.supportedDoStmt();
    final String program = programWithDoStmt(shadingLanguageVersion).toString();
    checkValidity(expectedInvalid, program);
  }

  private void checkSwitchStmtSupport(ShadingLanguageVersion shadingLanguageVersion)
      throws IOException, InterruptedException {
    final boolean expectedInvalid = !shadingLanguageVersion.supportedSwitchStmt();
    final String program = programWithSwitchStmt(shadingLanguageVersion).toString();
    checkValidity(expectedInvalid, program);
  }

  private void checkUnsignedSupport(ShadingLanguageVersion shadingLanguageVersion)
      throws IOException, InterruptedException {
    final boolean expectedInvalid = !shadingLanguageVersion.supportedUnsigned();
    final String program = programWithUnsigned(shadingLanguageVersion).toString();
    checkValidity(expectedInvalid, program);
  }

  private void checkInitializersOfConstMustBeConst(ShadingLanguageVersion shadingLanguageVersion)
      throws IOException, InterruptedException {
    final boolean expectedInvalid = shadingLanguageVersion.initializersOfConstMustBeConst();
    final String program = constInitializedWithNonConst(shadingLanguageVersion).toString();
    checkValidity(expectedInvalid, program);
  }

  private void checkGlobalVariableInitializersMustBeConst(ShadingLanguageVersion shadingLanguageVersion)
      throws IOException, InterruptedException {
    final boolean expectedInvalid = shadingLanguageVersion.globalVariableInitializersMustBeConst();
    final String program = globalWithNonConstInitializer(shadingLanguageVersion).toString();
    checkValidity(expectedInvalid, program);
  }

  private void checkValidity(boolean expectedInvalid, String program)
      throws IOException, InterruptedException {
    final File shader = temporaryFolder.newFile("temp.frag");
    FileUtils.writeStringToFile(shader,
        program,
        StandardCharsets.UTF_8);
    final ExecResult result = ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER, shader);
    if (expectedInvalid) {
      assertNotEquals(0, result.res);
    } else {
      assertEquals(0, result.res);
    }
    shader.delete();
  }


  private StringBuilder globalWithNonConstInitializer(ShadingLanguageVersion shadingLanguageVersion) {
    final StringBuilder result = new StringBuilder();
    writeHeader(shadingLanguageVersion, result);
    result.append("float x = 1.5;\n"
        + "float y = x;\n"
        + "\n"
        + "void main() { }\n");
    return result;
  }

  private void writeHeader(ShadingLanguageVersion shadingLanguageVersion, StringBuilder result) {
    result.append("#version " + shadingLanguageVersion.getVersionString() + "\n"
        + "#ifdef GL_ES\n"
        + "precision mediump float;\n"
        + "#endif\n"
        + "\n");
  }

  private StringBuilder constInitializedWithNonConst(ShadingLanguageVersion shadingLanguageVersion) {
    final StringBuilder result = new StringBuilder();
    writeHeader(shadingLanguageVersion, result);
    result.append("void main() {\n"
        + " int x = 2;\n"
        + " const int y = x;\n"
        + "}\n");
    return result;
  }

  private StringBuilder programWithDoStmt(ShadingLanguageVersion shadingLanguageVersion) {
    final StringBuilder result = new StringBuilder();
    writeHeader(shadingLanguageVersion, result);
    result.append("void main() {\n"
        + "  do { } while (false);\n"
        + "}\n");
    return result;
  }

  private StringBuilder programWithSwitchStmt(ShadingLanguageVersion shadingLanguageVersion) {
    final StringBuilder result = new StringBuilder();
    writeHeader(shadingLanguageVersion, result);
    result.append("void main() {\n"
        + "  switch(0) {\n"
        + "    default:\n"
        + "      1;\n"
        + "  }\n"
        + "}\n");
    return result;
  }

  private StringBuilder programWithUnsigned(ShadingLanguageVersion shadingLanguageVersion) {
    final StringBuilder result = new StringBuilder();
    writeHeader(shadingLanguageVersion, result);
    result.append("void main() {\n"
        + "  uint x;\n"
        + "  uvec2 x2;\n"
        + "  uvec3 x3;\n"
        + "  uvec4 x4;\n"
        + "}\n");
    return result;
  }

}