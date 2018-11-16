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

package com.graphicsfuzz.common.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.ParseHelper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

class PrettyPrint {

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("PrettyPrint")
        .defaultHelp(true)
        .description("Pretty print a shader.");

    // Required arguments
    parser.addArgument("shader")
        .help("Path of shader to be pretty-printed.")
        .type(File.class);

    parser.addArgument("output")
        .help("Target file name.")
        .type(String.class);

    // Optional arguments
    parser.addArgument("--glsl_version")
        .help("Version of GLSL to target.")
        .type(String.class);

    return parser.parseArgs(args);

  }

  public static void main(String[] args) throws IOException, InterruptedException {

    try {
      Namespace ns = parse(args);
      long startTime = System.currentTimeMillis();
      TranslationUnit tu = ParseHelper.parse(new File(ns.getString("shader")));
      long endTime = System.currentTimeMillis();

      prettyPrintShader(ns, tu);

      System.err.println("Time for parsing: " + (endTime - startTime));
    } catch (Throwable exception) {
      exception.printStackTrace();
      System.exit(1);
    }

  }

  private static void prettyPrintShader(Namespace ns, TranslationUnit tu)
      throws FileNotFoundException {
    try (PrintStream stream =
             new PrintStream(new FileOutputStream(new File(ns.getString("output"))))) {
      if (getGlslVersion(ns) != null) {
        throw new RuntimeException();
        //Helper.emitDefines(stream, new ShadingLanguageVersion(getGlslVersion(ns), false),
        //    false);
      }
      PrettyPrinterVisitor ppv = new PrettyPrinterVisitor(stream);
      ppv.visit(tu);
    }
  }

  private static String getGlslVersion(Namespace ns) {
    return ns.getString("glsl_version");
  }

}
