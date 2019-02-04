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

package com.graphicsfuzz.shadersets;

import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunShaderFamily {

  private static final Logger LOGGER = LoggerFactory.getLogger(RunShaderFamily.class);

  public static void main(String[] args) {
    try {
      mainHelper(args, null);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

  public static void mainHelper(
      String[] args,
      FuzzerServiceManager.Iface managerOverride)
      throws ShaderDispatchException, InterruptedException, IOException, ArgumentParserException {

    ArgumentParser parser = ArgumentParsers.newArgumentParser("RunShaderFamily")
        .defaultHelp(true)
        .description("Get images for all shaders in a shader family.");

    parser.addArgument("--verbose")
        .action(Arguments.storeTrue())
        .help("Verbose output.");

    parser.addArgument("--server")
        .help(
            "URL of server to use for sending get image requests.")
        .type(String.class);

    parser.addArgument("--worker")
        .help("The worker used for get image requests. Used with --server.")
        .type(String.class);

    parser.addArgument("--output")
        .help("Output directory.")
        .setDefault(new File("."))
        .type(File.class);

    parser.addArgument("shader_family")
        .help("Shader family directory, or a single shader job .json file")
        .type(File.class);


    Namespace ns = parser.parseArgs(args);

    final boolean verbose = ns.get("verbose");
    final File shaderFamily = ns.get("shader_family");
    final String server = ns.get("server");
    final String worker = ns.get("worker");
    final File outputDir = ns.get("output");

    if (managerOverride != null && (server == null || worker == null)) {
      throw new ArgumentParserException(
          "Must supply server (dummy string) and worker name when executing in server process.",
          parser);
    }

    if (server != null) {
      if (worker == null) {
        throw new ArgumentParserException("Must supply worker name with server.", parser);
      }
    }

    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    IShaderDispatcher imageGenerator =
        server == null
            ? new LocalShaderDispatcher(false, fileOps, new File(outputDir, "temp"))
            : new RemoteShaderDispatcher(
                server + "/manageAPI",
                worker,
                managerOverride,
                new AtomicLong());

    fileOps.mkdir(outputDir);

    if (!fileOps.isDirectory(shaderFamily)) {
      if (!fileOps.doesShaderJobExist(shaderFamily)) {
        throw new ArgumentParserException(
            "Shader family must be a directory or the prefix of a single shader job.", parser);
      }
      // Special case: run get image on a single shader.
      String shaderName = FilenameUtils.removeExtension(shaderFamily.getName());
      runShader(
          new File(outputDir, shaderName + ".info.json"),
          shaderFamily,
          imageGenerator,
          Optional.empty(),
          fileOps);
      return;
    }

    int numRun = runShaderFamily(shaderFamily, outputDir, imageGenerator, fileOps);

    LOGGER.info("Ran {} shaders.", numRun);
  }

  public static int runShaderFamily(
      File shaderFamilyDir,
      File outputDir,
      IShaderDispatcher imageGenerator,
      ShaderJobFileOperations fileOps)
      throws ShaderDispatchException, InterruptedException, IOException {

    int numShadersRun = 0;

    File referenceResult = new File(outputDir, "reference.info.json");
    File referenceJob = new File(shaderFamilyDir, "reference.json");

    if (!fileOps.doesShaderJobResultFileExist(referenceResult)) {
      runShader(
          referenceResult,
          referenceJob,
          imageGenerator,
          Optional.empty(),
          fileOps);
      ++numShadersRun;
    }

    if (!fileOps.isComputeShaderJob(referenceJob)) {
      if (!fileOps.doesShaderJobResultFileHaveImage(referenceResult)) {
        LOGGER.info("Reference failed to render, so skipping variants.");
        return numShadersRun;
      }
    }

    final File[] variants =
        fileOps.listShaderJobFiles(shaderFamilyDir, (dir, name) -> name.startsWith("variant"));

    for (File variant : variants) {

      final String variantName = FilenameUtils.removeExtension(variant.getName());
      final File resultFile = new File(outputDir, variantName + ".info.json");

      if (fileOps.doesShaderJobResultFileExist(resultFile)) {
        LOGGER.info("Skipping {} because we already have a result.", variant);
      } else {
        try {
          runShader(
              resultFile,
              variant,
              imageGenerator,
              Optional.of(referenceResult),
              fileOps);
        } catch (Exception err) {
          LOGGER.error("runShader() raise exception on {}", variant);
          err.printStackTrace();
        }
        ++numShadersRun;
      }
    }
    return numShadersRun;
  }

  /**
   * This method runs shaderJobFile using the imageGenerator (IShaderDispatcher),
   * writes the ImageJobResult to shaderJobResultFile, and returns the ImageJobResult.
   * If you don't need an output file, you can just use an IShaderDispatcher directly.
   *
   * @param shaderJobResultFile E.g. "variant_blah.info.json"
   * @param shaderJobFile E.g. "variant_blah.json"
   * @param imageGenerator to generate an ImageJobResult
   * @param referenceShaderResult E.g. "reference.info.json"
   * @param fileOps File operations.
   * @return ImageJobResult
   * @throws ShaderDispatchException On imageGenerator failure.
   * @throws InterruptedException On imageGenerator failure.
   * @throws IOException On IO failure.
   */
  public static ImageJobResult runShader(
      File shaderJobResultFile,
      File shaderJobFile,
      IShaderDispatcher imageGenerator,
      Optional<File> referenceShaderResult,
      ShaderJobFileOperations fileOps)
      throws ShaderDispatchException, InterruptedException, IOException {

    LOGGER.info("Running shader job: {} ", shaderJobFile);

    // shaderJobFile -> imageJob
    ImageJob imageJob = new ImageJob();
    fileOps.readShaderJobFileToImageJob(shaderJobFile, imageJob);

    // imageJob -> imageJobResult
    ImageJobResult imageJobResult = imageGenerator.getImage(imageJob);

    // imageJobResult -> shaderJobResultFile
    fileOps.writeShaderResultToFile(
        imageJobResult,
        shaderJobResultFile,
        referenceShaderResult);

    return imageJobResult;
  }


}
