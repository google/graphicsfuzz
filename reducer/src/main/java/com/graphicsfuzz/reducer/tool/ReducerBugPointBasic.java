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
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.reducer.reductionopportunities.Compatibility;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunities;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class ReducerBugPointBasic {

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("ReducerBugPoint")
          .defaultHelp(true)
          .description("Find bugs in the reducer.");

    // Required arguments
    parser.addArgument("shader")
          .help("Path of shader job file (.json) to be analysed.")
          .type(File.class);

    // Optional arguments
    parser.addArgument("--max-iterations")
          .help("Maximum number of times to iterate before giving up.")
          .setDefault(30)
          .type(Integer.class);

    parser.addArgument("--seed")
          .help("Seed to initialize random number generator with.")
          .setDefault(new Random().nextInt())
          .type(Integer.class);

    parser.addArgument("--preserve-semantics")
          .help("Only perform semantics-preserving reductions.")
          .action(Arguments.storeTrue());

    parser.addArgument("--ignore-invalid")
          .help("Do not log or fix on cases where the shader is invalid - "
                + "ignore them and backtrack.")
          .action(Arguments.storeTrue());

    parser.addArgument("--expected-string")
          .help("A string to look for in the exception message.")
          .type(String.class);

    return parser.parseArgs(args);
  }


  public static void main(String[] args)
      throws IOException, ParseTimeoutException, InterruptedException, ArgumentParserException {

    final Namespace ns = parse(args);

    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final File shaderJobFile = ns.get("shader");

    final ShadingLanguageVersion shadingLanguageVersion =
        ShadingLanguageVersion
            .getGlslVersionFromFirstTwoLines(
                fileOps.getFirstTwoLinesOfShader(shaderJobFile, ShaderKind.FRAGMENT));

    final IRandom generator = new RandomWrapper(ns.get("seed"));

    final ShaderJob originalShaderJob = fileOps.readShaderJobFile(shaderJobFile);

    final int maxIterations = ns.get("max_iterations");

    final boolean reduceEverywhere = !ns.getBoolean("reduce_everywhere");

    final boolean ignoreInvalid = ns.get("ignore_invalid");

    final String expectedString = ns.getString("expected_string") == null
          ? ""
          : ns.getString("expected_string");

    final IdGenerator idGenerator = new IdGenerator();

    ShaderJob lastGoodButLeadingToBadShaderJob = originalShaderJob;

    int exceptionCount = 0;
    int invalidCount = 0;

    int getOpsCounter = 0;

    for (int i = 0; i < maxIterations; i++) {

      ShaderJob current = lastGoodButLeadingToBadShaderJob.clone();

      // TODO: this code was written pre vertex shader support, and does not take
      // account of uniforms.
      // If it still proves useful as a debugging tool, it could do with updating.
      // Paul: partially updated during refactor, but not tested and still makes assumptions
      // about frag files.

      while (true) {
        final ShaderJob prev = current.clone();
        List<IReductionOpportunity> ops;
        try {
          ops = ReductionOpportunities.getReductionOpportunities(
              current,
              new ReducerContext(
                  reduceEverywhere,
                  shadingLanguageVersion,
                  generator,
                  idGenerator, true),
              fileOps);

        } catch (Exception exception) {
          recordThrowsExceptionWhenGettingReductionOpportunities(
              current,
              exception,
              fileOps);
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
            lastGoodButLeadingToBadShaderJob = prev;
            current = lastGoodButLeadingToBadShaderJob.clone();

            fileOps.writeShaderJobFile(
                lastGoodButLeadingToBadShaderJob,
                new File("leads_to_exception_" + exceptionCount + ".json")
            );

            emitException(exception, "leads_to_exception_" + exceptionCount + ".txt", fileOps);
            exceptionCount++;
          } else {
            System.err.println("The exception was not interesting as it did not contain \""
                  + expectedString + "\"");
            System.out.println(exception.toString());
          }
          continue;
        }
        if (invalid(current, shadingLanguageVersion, fileOps)) {
          System.err.println("Invalid shader after reduction step.");
          if (ignoreInvalid) {
            System.err.println("Ignoring it and backtracking.");
            current = prev;
          } else {
            fileOps.writeShaderJobFile(
                prev,
                new File("leads_to_invalid_" + invalidCount + "_before.json"));
            fileOps.writeShaderJobFile(
                current,
                new File("leads_to_invalid_" + invalidCount + "_after.json"));
            invalidCount++;
            lastGoodButLeadingToBadShaderJob = prev;
            current = lastGoodButLeadingToBadShaderJob.clone();
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

  private static boolean invalid(
      ShaderJob shaderJob,
      ShadingLanguageVersion shadingLanguageVersion,
      ShaderJobFileOperations fileOps)
        throws IOException, InterruptedException {
    File tempShaderJobFile = new File("temp_to_validate.json");
    fileOps.writeShaderJobFile(
        shaderJob,
        tempShaderJobFile);
    return fileOps.areShadersValid(tempShaderJobFile, false);
  }

  private static void emitException(
      Exception exception,
      String filename,
      ShaderJobFileOperations fileOps) throws IOException {
    String stacktrace = ExceptionUtils.getStackTrace(exception);
    fileOps.writeStringToFile(new File(filename), stacktrace);
  }

  private static void recordThrowsExceptionWhenGettingReductionOpportunities(
      ShaderJob shaderJob,
      Exception exception,
      ShaderJobFileOperations fileOps) throws IOException, ParseTimeoutException {
    File tempShaderJobFile = new File("temp.json");

    fileOps.writeShaderJobFile(
        shaderJob,
        tempShaderJobFile
    );

    ShaderJob reparsedShaderJob = fileOps.readShaderJobFile(
        tempShaderJobFile
    );

    new ReportAstDifferences(
        shaderJob.getShaders().get(0),
        reparsedShaderJob.getShaders().get(0));

    final File maybeExistingFile = new File("problem_getting_reduction_opportunities.json");

    if (!fileOps.doesShaderJobExist(maybeExistingFile)
          || (fileOps.getShaderLength(maybeExistingFile, ShaderKind.FRAGMENT)
              > fileOps.getShaderLength(tempShaderJobFile, ShaderKind.FRAGMENT))) {

      if (fileOps.doesShaderJobExist(maybeExistingFile)) {
        fileOps.deleteShaderJobFile(maybeExistingFile);
      }
      fileOps.moveShaderJobFileTo(tempShaderJobFile, maybeExistingFile, true);
      final File maybeExistingExceptionFile =
            new File("problem_getting_reduction_opportunities.txt");
      if (fileOps.isFile(maybeExistingExceptionFile)) {
        fileOps.deleteFile(maybeExistingExceptionFile);
      }
      String stacktrace = ExceptionUtils.getStackTrace(exception);
      fileOps.writeStringToFile(maybeExistingExceptionFile, stacktrace);
    }
  }


}
