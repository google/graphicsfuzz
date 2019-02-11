/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.semanticschanging.astfuzzer.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class MainFuzzer {

  private static Namespace parse(String[] args) {

    ArgumentParser parser = ArgumentParsers.newArgumentParser("AstFuzzer")
          .defaultHelp(true)
          .description("Takes a shader as input, returns a list of variant shaders, "
                + "i.e shaders which have a different AST.");

    // Required arguments
    parser.addArgument("fragment_shader")
          .help("Path of fragment shader to be fuzzed.")
          .type(File.class);

    // Optional arguments
    parser.addArgument("--number_of_variants")
          .help("How many variant shaders you want to generate.")
          .setDefault(25)
          .type(Integer.class);
    parser.addArgument("--keep_bad_shaders")
          .help("Keep the generated shaders which do not compile.")
          .setDefault(false)
          .type(Boolean.class);

    parser.addArgument("--output_prefix")
          .help("Prefix of target file name, e.g. \"foo\" if shader is to be \"foo.frag\".")
          .setDefault("")
          .type(String.class);

    try {
      return parser.parseArgs(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
      return null;
    }

  }

  /**
   * Calls AstFuzzer and writes the generated shaders in files.
   *
   * @param args program arguments list.
   */
  public static void main(String[] args) {
    Namespace ns = parse(args);

    try {
      final File fragmentShader = ns.get("fragment_shader");
      final Integer numberOfVariants = ns.get("number_of_variants");
      final String outputPrefix = ns.get("output_prefix");
      final Boolean keepBadShader = ns.get("keep_bad_shaders");

      generateVariants(
          fragmentShader, new File("."), numberOfVariants, outputPrefix, keepBadShader);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  public static void generateVariants(
      File fragmentShader,
      File workDir,
      Integer numberOfVariants,
      String outputPrefix, Boolean keepBadShader)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {

    File outputFolder = new File(workDir, "output");

    try {
      outputFolder.mkdir();
    } catch (Exception exception) {
      System.err.print(exception.getMessage());
    }

    TranslationUnit initialTu = ParseHelper.parse(fragmentShader);
    AstFuzzer fuzzer = new AstFuzzerChangeAnythingMatching(initialTu.getShadingLanguageVersion(),
          new RandomWrapper());
    List<TranslationUnit> variants = fuzzer.generateShaderVariations(initialTu, numberOfVariants);

    for (int i = 0; i < numberOfVariants; i++) {

      File outputFile = new File(outputFolder, outputPrefix
          + fragmentShader.toString().split("\\.frag")[0] + "_" + i + ".frag");
      if (!outputFile.exists()) {
        new File(outputFile.getParent()).mkdirs();
        outputFile.createNewFile();
      }
      writeFile(variants.get(i), initialTu.getShadingLanguageVersion(),
          outputFile);

      File json = new File(outputFolder, outputPrefix
            + fragmentShader.toString().split("\\.frag")[0] + "_" + i + ".json");
      json.createNewFile();
      InputStream is = MainFuzzer.class.getClassLoader().getResourceAsStream("default.json");
      java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
      PrintWriter pw = new PrintWriter(json);
      while (scanner.hasNext()) {
        pw.append(scanner.next());
      }
      pw.close();
      checkShaderValidity(outputFile, json, keepBadShader);
    }
  }

  private static void checkShaderValidity(File outputFile, File jsonFile, Boolean keepBadShader)
        throws IOException, InterruptedException {

    ExecResult execResult = ToolHelper
          .runValidatorOnShader(ExecHelper.RedirectType.TO_BUFFER, outputFile);

    //If the file does not compile
    if (execResult.res != 0) {
      jsonFile.delete();

      if (keepBadShader) {
        try {
          outputFile.renameTo(new File(outputFile.toString().split("\\.frag")[0]
                + "_bad.frag"));
        } catch (Exception exeception) {
          System.err.print(exeception.getMessage());
        }
      } else {
        try {
          outputFile.delete();
        } catch (Exception exeception) {
          System.err.print(exeception.getMessage());
        }
      }
    }

  }


  private static void writeFile(TranslationUnit tu,
      ShadingLanguageVersion shadingLanguageVersion, File outputFile)
        throws FileNotFoundException {
    PrintStream ps = new PrintStream(outputFile);
    assert false; // TODO: need to decide how to handle this
    //Helper.emitDefines(ps, shadingLanguageVersion, true);

    new PrettyPrinterVisitor(ps).visit(tu);
    ps.flush();
    ps.close();
  }
}
