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

package com.graphicsfuzz.reducer;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

public class CheckAstFeaturesFileJudge implements IFileJudge {

  private final List<Supplier<CheckAstFeatureVisitor>> visitorSuppliers;
  private final ShaderKind shaderKind;
  private final ShaderJobFileOperations fileOps;

  public CheckAstFeaturesFileJudge(
      List<Supplier<CheckAstFeatureVisitor>> visitorSuppliers,
      ShaderKind shaderKind,
      ShaderJobFileOperations fileOps) {
    this.visitorSuppliers = visitorSuppliers;
    this.shaderKind = shaderKind;
    this.fileOps = fileOps;
  }

  @Override
  public boolean isInteresting(
      File shaderJobFile,
      File shaderResultFileOutput) {
    try {
      ShaderJob shaderJob = fileOps.readShaderJobFile(shaderJobFile);
      // This judge harks from fragment shader-centric days and is only intended for use
      // on single-shader shader jobs.
      assert shaderJob.getShaders().size() == 1;
      final TranslationUnit tu = shaderJob.getShaders().get(0);
      return visitorSuppliers.stream().allMatch(item -> item.get().check(tu));
    } catch (IOException | ParseTimeoutException | InterruptedException
        | GlslParserException exception) {
      throw new RuntimeException(exception);
    }
  }

}
