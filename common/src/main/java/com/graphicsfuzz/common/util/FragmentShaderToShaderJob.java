/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.ArgsUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public final class FragmentShaderToShaderJob {

  private FragmentShaderToShaderJob() {
    // Utility class
  }

  /**
   * Creates a shader job with the given translation unit as its fragment shader, a pipeline state
   * that provides a random value for every uniform declared in the shader, and if needed a vertex
   * shader that provides outputs for the fragment shader's inputs.
   * @param tu A fragment shader.
   * @return A shader job that includes the fragment shader.
   */
  public static ShaderJob createShaderJob(TranslationUnit tu, IRandom generator) {
    assert (tu.getShaderKind() == ShaderKind.FRAGMENT);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(tu);

    // TODO(https://github.com/google/graphicsfuzz/issues/837): Iterate through all uniforms in
    //  'tu'.  For each, add an entry to 'pipelineInfo' with a randomized value (using 'generator'
    //  as the source of randomness).

    // TODO(https://github.com/google/graphicsfuzz/issues/837): If the translation unit uses 'in'
    //  global variables, declare a vertex shader TranslationUnit with corresponding 'out' variables
    //  and add it to 'shaders'.

    return new GlslShaderJob(Optional.empty(), pipelineInfo, shaders);
  }

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("FragmentShaderToShaderJob")
        .defaultHelp(true)
        .description("Turns a fragment shader into a shader job with randomized uniforms and "
            + "(if needed) a suitable vertex shader.");

    // Required arguments
    parser.addArgument("shader")
        .help("Path of .frag shader to be turned into a shader job.")
        .type(File.class);

    parser.addArgument("output")
        .help("Target shader job .json file.")
        .type(String.class);

    parser.addArgument("--seed")
        .help("Seed (unsigned 64 bit long integer) for the random number generator.")
        .type(String.class);

    return parser.parseArgs(args);
  }

  public static void main(String[] args) throws IOException, InterruptedException {

    try {
      Namespace ns = parse(args);
      long startTime = System.currentTimeMillis();
      TranslationUnit tu = ParseHelper.parse(new File(ns.getString("shader")));
      long endTime = System.currentTimeMillis();
      System.err.println("Time for parsing: " + (endTime - startTime));

      startTime = System.currentTimeMillis();
      final ShaderJob result = createShaderJob(tu, new RandomWrapper(ArgsUtil.getSeedArgument(ns)));
      endTime = System.currentTimeMillis();
      System.err.println("Time for creating shader job: " + (endTime - startTime));

      final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
      fileOperations.writeShaderJobFile(result, new File(ns.getString("output")));
    } catch (Throwable exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }

}
