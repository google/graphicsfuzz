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

import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ShaderKind;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

public class TranslationUnitTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testShaderKinds() throws Exception {
    final File frag = temporaryFolder.newFile("a.frag");
    final File vert = temporaryFolder.newFile("a.vert");
    final File comp = temporaryFolder.newFile("a.comp");
    final String emptyShader = "#version 310 es\nvoid main() { }\n";
    FileUtils.writeStringToFile(frag, emptyShader, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(vert, emptyShader, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(comp, emptyShader, StandardCharsets.UTF_8);
    final TranslationUnit fragTu = ParseHelper.parse(frag);
    final TranslationUnit vertTu = ParseHelper.parse(vert);
    final TranslationUnit compTu = ParseHelper.parse(comp);
    assertEquals(ShaderKind.FRAGMENT, fragTu.getShaderKind());
    assertEquals(ShaderKind.VERTEX, vertTu.getShaderKind());
    assertEquals(ShaderKind.COMPUTE, compTu.getShaderKind());

  }

  @Test
  public void testClone() throws Exception {
    final File vert = temporaryFolder.newFile("a.vert");
    FileUtils.writeStringToFile(vert, "void main() { }", StandardCharsets.UTF_8);
    final TranslationUnit vertTu = ParseHelper.parse(vert);
    assertEquals(ShaderKind.VERTEX, vertTu.getShaderKind());
    assertEquals(ShaderKind.VERTEX, vertTu.clone().getShaderKind());
  }

}
