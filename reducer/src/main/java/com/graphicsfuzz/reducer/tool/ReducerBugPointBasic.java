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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.reducer.reductionopportunities.Compatibility;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunities;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunityContext;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;

public class ReducerBugPointBasic {

  private static Namespace parse(String[] args) {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("ReducerBugPoint")
          .defaultHelp(true)
          .description("Find bugs in the reducer.");

    // Required arguments
    parser.addArgument("shader")
          .help("Path of shader to be analysed.")
          .type(File.class);

    // Optional arguments
    parser.addArgument("--max_iterations")
          .help("Maximum number of times to iterate before giving up.")
          .setDefault(30)
          .type(Integer.class);

    parser.addArgument("--seed")
          .help("Seed to initialize random number generator with.")
          .setDefault(new Random().nextInt())
          .type(Integer.class);

    parser.addArgument("--reduce_everywhere")
          .help("Reduce arbitrary parts of the shader.")
          .action(Arguments.storeTrue());

    parser.addArgument("--ignore_invalid")
          .help("Do not log or fix on cases where the shader is invalid - "
                + "ignore them and backtrack.")
          .action(Arguments.storeTrue());

    parser.addArgument("--expected_string")
          .help("A string to look for in the exception message.")
          .type(String.class);

    try {
      return parser.parseArgs(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
      return null;
    }

  }


  public static void main(String[] args)
        throws IOException, ParseTimeoutException, InterruptedException {

    final Namespace ns = parse(args);

    final File shader = ns.get("shader");

    final ShadingLanguageVersion shadingLanguageVersion =
        ShadingLanguageVersion.getGlslVersionFromShader(shader);

    final IRandom generator = new RandomWrapper(ns.get("seed"));

    final TranslationUnit originalShader = Helper.parse(shader, true);

    final int maxIterations = ns.get("max_iterations");

    final boolean reduceEverywhere = ns.get("reduce_everywhere");

    final boolean ignoreInvalid = ns.get("ignore_invalid");

    final String expectedString = ns.getString("expected_string") == null
          ? ""
          : ns.getString("expected_string");

    final IdGenerator idGenerator = new IdGenerator();

    TranslationUnit lastGoodButLeadingToBad = originalShader;

    int exceptionCount = 0;
    int invalidCount = 0;

    int getOpsCounter = 0;

    for (int i = 0; i < maxIterations; i++) {

      TranslationUnit current = lastGoodButLeadingToBad.cloneAndPatchUp();

      while (true) {
        final TranslationUnit prev = current.cloneAndPatchUp();
        List<IReductionOpportunity> ops;
        try {
          ops = ReductionOpportunities.getReductionOpportunities(current,
                new ReductionOpportunityContext(reduceEverywhere,
                    shadingLanguageVersion, generator, idGenerator));
        } catch (Exception exception) {
          recordThrowsExceptionWhenGettingReductionOpportunities(current, exception,
              shadingLanguageVersion);
          break;
        }
        System.out.println(ops.size());
        if (ops.isEmpty()) {
          break;
        }
        final List<IReductionOpportunity> opsToApply = getOpsToApply(ops, generator);
        //System.out.println("About to try applying " + op);
        try {
          for (IReductionOpportunity op : opsToApply) {
            op.applyReduction();
          }
        } catch (Exception exception) {
          System.err.println("Exception occurred while applying a reduction opportunity.");
          if (exception.toString().contains(expectedString)) {
            lastGoodButLeadingToBad = prev;
            current = lastGoodButLeadingToBad.cloneAndPatchUp();
            emitShader(lastGoodButLeadingToBad, "leads_to_exception_" + exceptionCount
                  + ".frag", shadingLanguageVersion);
            emitException(exception, "leads_to_exception_" + exceptionCount + ".txt");
            exceptionCount++;
          } else {
            System.err.println("The exception was not interesting as it did not contain \""
                  + expectedString + "\"");
            System.out.println(exception);
          }
          continue;
        }
        if (invalid(current, shadingLanguageVersion)) {
          System.err.println("Invalid shader after reduction step.");
          if (ignoreInvalid) {
            System.err.println("Ignoring it and backtracking.");
            current = prev;
          } else {
            emitShader(prev, "leads_to_invalid_" + invalidCount + "_before.frag",
                shadingLanguageVersion);
            emitShader(current, "leads_to_invalid_" + invalidCount + "_after.frag",
                shadingLanguageVersion);
            invalidCount++;
            lastGoodButLeadingToBad = prev;
            current = lastGoodButLeadingToBad.cloneAndPatchUp();
          }
        }
      }
    }
  }

  private static List<IReductionOpportunity> getOpsToApply(List<IReductionOpportunity> ops,
        IRandom generator) {
    List<IReductionOpportunity> result = new ArrayList<>();
    final IReductionOpportunity initialOp = ops.remove(generator.nextInt(ops.size()));
    result.add(initialOp);
    for (int i = 0; i < 10; i++) {
      if (generator.nextInt(10) < 10 - i) {
        break;
      }
      while (!ops.isEmpty()) {
        final IReductionOpportunity op = ops.remove(generator.nextInt(ops.size()));
        if (!Compatibility.compatible(initialOp.getClass(), op.getClass())) {
          continue;
        }
        result.add(op);
        break;
      }
      if (ops.isEmpty()) {
        break;
      }
    }
    return result;
  }

  private static boolean invalid(TranslationUnit translationUnit,
      ShadingLanguageVersion shadingLanguageVersion)
        throws IOException, InterruptedException {
    final String filename = "temp_to_validate.frag";
    emitShader(translationUnit, filename, shadingLanguageVersion);
    ExecResult execResult = ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER,
          new File(filename));
    return execResult.res != 0;
  }

  private static void emitException(Exception exception, String filename)
        throws FileNotFoundException {
    exception.printStackTrace(new PrintStream(new FileOutputStream(filename)));
  }

  private static void emitShader(TranslationUnit translationUnit, String filename,
        ShadingLanguageVersion shadingLanguageVersion)
        throws FileNotFoundException {
    Helper.emitShader(shadingLanguageVersion, ShaderKind.FRAGMENT, translationUnit,
          new PrintStream(new FileOutputStream(filename)));
  }

  private static void recordThrowsExceptionWhenGettingReductionOpportunities(
        TranslationUnit translationUnit, Exception exception,
        ShadingLanguageVersion shadingLanguageVersion) throws IOException, ParseTimeoutException {
    emitShader(translationUnit, "temp.frag", shadingLanguageVersion);
    TranslationUnit reparsed = Helper.parse(new File("temp.frag"),
          true);
    new ReportAstDifferences(translationUnit, reparsed);
    final File maybeExistingFile = new File("problem_getting_reduction_opportunities.frag");
    if (!maybeExistingFile.exists()
          || maybeExistingFile.length() > new File("temp.frag").length()) {
      if (maybeExistingFile.exists()) {
        maybeExistingFile.delete();
      }
      FileUtils.moveFile(new File("temp.frag"), maybeExistingFile);
      final File maybeExistingExceptionFile =
            new File("problem_getting_reduction_opportunities.txt");
      if (maybeExistingExceptionFile.exists()) {
        maybeExistingExceptionFile.delete();
      }
      exception.printStackTrace(new PrintStream(new FileOutputStream(maybeExistingExceptionFile)));
    }
  }


}
