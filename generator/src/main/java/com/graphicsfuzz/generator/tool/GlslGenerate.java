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

package com.graphicsfuzz.generator.tool;

import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.util.ArgsUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlslGenerate {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlslGenerate.class);

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("GlslGenerate")
        .defaultHelp(true)
        .description("Generate a set of shader families.");

    parser.addArgument("references")
        .help("Path to directory of reference shaders.")
        .type(File.class);

    parser.addArgument("donors")
        .help("Path to directory of donor shaders.")
        .type(File.class);

    parser.addArgument("num-variants")
        .help("Number of variants to be produced for each generated shader family.")
        .type(Integer.class);

    parser.addArgument("glsl-version")
        .help("Version of GLSL to target.")
        .type(String.class);

    parser.addArgument("prefix")
        .help("String with which to prefix shader family names.")
        .type(String.class);

    parser.addArgument("output-dir")
        .help("Output directory for shader families.")
        .type(File.class);

    Generate.addGeneratorCommonArguments(parser);

    GenerateShaderFamily.addFamilyGenerationArguments(parser);

    return parser.parseArgs(args);

  }

  public static void mainHelper(String[] args) throws ArgumentParserException,
      InterruptedException, IOException, ParseTimeoutException {
    final Namespace ns = parse(args);

    final File referencesDir = ns.get("references");
    final File donorsDir = ns.get("donors");
    final File outputDir = ns.get("output_dir");
    final String prefix = ns.get("prefix");
    final int numVariants = ns.getInt("num_variants");
    final boolean verbose = ns.getBoolean("verbose");
    final int seed = ArgsUtil.getSeedArgument(ns);

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    fileOps.forceMkdir(outputDir);

    final File[] referenceShaderJobFiles =
        fileOps.listShaderJobFiles(referencesDir, (dir, name) -> true);
    if (referenceShaderJobFiles.length == 0) {
      LOGGER.warn("Warning: no reference shader jobs found in " + referencesDir.getAbsolutePath()
          + ".");
    } else {
      LOGGER.info("About to generate " + referenceShaderJobFiles.length + " shader famil"
          + (referenceShaderJobFiles.length == 1 ? "y" : "ies") + ", each with " + numVariants
          + " variant" + (numVariants == 1 ? "" : "s") + ".");
    }

    final IRandom generator = new RandomWrapper(seed);
    int referenceCount = 0;
    for (File shaderJobFile : referenceShaderJobFiles) {
      LOGGER.info("Generating family " + referenceCount + " from reference "
          + shaderJobFile.getName() + ".");
      referenceCount++;

      final List<String> generateShaderFamilyArgs = getGenerateShaderFamilyArgs(ns,
          outputDir, shaderJobFile, prefix, generator.nextInt(Integer.MAX_VALUE));
      if (verbose) {
        LOGGER.info("Generating a shader family: " + generateShaderFamilyArgs.stream()
            .reduce((String item1, String item2) -> item1 + " " + item2).orElse(""));
      }
      GenerateShaderFamily.mainHelper(generateShaderFamilyArgs.toArray(new String[0]));
    }
    LOGGER.info("Generation complete.");
  }

  private static List<String> getGenerateShaderFamilyArgs(Namespace ns,
                                                          File overallOutputDir,
                                                          File shaderJobFile,
                                                          String prefix,
                                                          int innerSeed) {
    List<String> result = new ArrayList<>();
    result.add(shaderJobFile.getAbsolutePath());
    result.add(ns.get("donors").toString());
    result.add(ns.getString("glsl_version"));
    result.add(new File(overallOutputDir,
        prefix + "_" + FilenameUtils.removeExtension(shaderJobFile.getName())).getAbsolutePath());
    result.add("--seed");
    result.add(String.valueOf(innerSeed));

    for (String arg : ns.getAttrs().keySet()) {
      switch (arg) {
        // These arguments are either dealt with above, or are irrelevant.
        case "donors":
        case "glsl_version":
        case "output_dir":
        case "prefix":
        case "references":
        case "seed":
          continue;
        default:
          break;
      }
      if (ns.get(arg) == null) {
        continue;
      }
      final String replacementArg = arg.replace("_", "-");
      if (ns.get(arg) instanceof Boolean) {
        if (ns.getBoolean(arg)) {
          result.add("--" + replacementArg);
        }
      } else {
        result.add("--" + replacementArg);
        result.add(ns.get(arg).toString());
      }
    }
    return result;
  }

  public static void main(String[] args) {
    try {
      mainHelper(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (Throwable ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

}
