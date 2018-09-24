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

package com.graphicsfuzz.reducer.glslreducers;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.reducer.IReductionState;
import com.graphicsfuzz.reducer.IReductionStateFileWriter;
import java.io.FileNotFoundException;
import java.io.PrintStream;

public class GlslReductionStateFileWriter implements IReductionStateFileWriter {

  private ShadingLanguageVersion shadingLanguageVersion;

  public GlslReductionStateFileWriter(ShadingLanguageVersion shadingLanguageVersion) {
    this.shadingLanguageVersion = shadingLanguageVersion;
  }

  @Override
  public void writeFileFromState(IReductionState state, String outputFilesPrefix)
        throws FileNotFoundException {
    if (state.hasFragmentShader()) {
      writeFile(state.getFragmentShader(), ShaderKind.FRAGMENT, outputFilesPrefix);
    }
    if (state.hasVertexShader()) {
      writeFile(state.getVertexShader(), ShaderKind.VERTEX, outputFilesPrefix);
    }
  }

  private void writeFile(TranslationUnit shader, ShaderKind shaderKind, String outputFilesPrefix)
      throws FileNotFoundException {
    PrintStream ps = new PrintStream(outputFilesPrefix + shaderKind.getFileExtension());
    // TODO: should we pass a license through the reduction process?
    Helper.emitDefines(ps, shadingLanguageVersion, shaderKind, true);
    new PrettyPrinterVisitor(ps).visit(shader);
    ps.flush();
    ps.close();
  }
}
