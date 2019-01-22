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

package com.graphicsfuzz.reducer.tool;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.reducer.util.Simplify;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Simplifier {

  private static final Logger LOGGER = LoggerFactory.getLogger(Simplifier.class);

  public static void main(String[] args) throws IOException, ParseTimeoutException,
      InterruptedException {
    if (args.length != 1) {
      System.err.println("Usage: Simplifier <file>.json");
      System.exit(1);
    }
    File inputShaderJobFile = new File(args[0]);
    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    ShaderJob shaderJob = fileOps.readShaderJobFile(inputShaderJobFile);

    // TODO: Warning: assumes only one shader fragment shader.
    LOGGER.warn("WARNING: assumes only fragment shaders.");

    shaderJob = new GlslShaderJob(
        shaderJob.getLicense(),
        shaderJob.getUniformsInfo(),
        Simplify.simplify(shaderJob.getShaders().get(0)));

    String[] firstTwoLines =
        fileOps.getFirstTwoLinesOfShader(inputShaderJobFile, ShaderKind.FRAGMENT);


    PrintStream ps = fileOps.getStdOut();
    new PrettyPrinterVisitor(ps, PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
        PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER, true,
        Optional.empty()).visit(shaderJob.getFragmentShader().get());
    ps.flush();
  }
}
