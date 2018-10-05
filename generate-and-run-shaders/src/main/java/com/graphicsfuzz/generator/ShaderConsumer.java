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

package com.graphicsfuzz.generator;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.RemoteShaderDispatcher;
import com.graphicsfuzz.shadersets.RunShaderFamily;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShaderConsumer implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShaderConsumer.class);

  private final int limit;
  private final BlockingQueue<Pair<ShaderJob, ShaderJob>> queue;
  private final File outputDir;
  private final String server;
  private final String token;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final Set<String> crashStringsToIgnore;
  private final Namespace ns;

  public ShaderConsumer(
      int limit,
      BlockingQueue<Pair<ShaderJob, ShaderJob>> queue,
      File outputDir,
      String server,
      String token,
      ShadingLanguageVersion shadingLanguageVersion,
      Set<String> crashStringsToIgnore,
      Namespace ns) {
    this.limit = limit;
    this.queue = queue;
    this.outputDir = outputDir;
    this.server = server;
    this.token = token;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.crashStringsToIgnore = crashStringsToIgnore;
    this.ns = ns;
  }

  @Override
  public void run() {

    final IShaderDispatcher imageGenerator = new RemoteShaderDispatcher(
        server + "/manageAPI",
        token);

    for (int received = 0; received < limit; received++) {
      LOGGER.info("Consuming shader job " + received);
      Pair<ShaderJob, ShaderJob> shaderPair;
      try {
        shaderPair = queue.take();
      } catch (InterruptedException exception) {
        LOGGER.error("Problem taking from queue.", exception);
        throw new RuntimeException(exception);
      }

      final String referencePrefix = "temp_reference";
      final String variantPrefix = "temp_variant";

      final File[] files = outputDir.listFiles((dir, name) -> name.startsWith(referencePrefix)
          || name.startsWith(variantPrefix));
      for (File toDelete :
          files) {
        if (!toDelete.delete()) {
          final String message = "Problem deleting file " + toDelete.getName();
          LOGGER.error(message);
          throw new RuntimeException(message);
        }
      }

      final String counterString = String.format("%04d", received);

      final ShaderJob variantShaderJob = shaderPair.getRight();
      final Optional<ImageJobResult> variantResult = runShaderJob(variantShaderJob,
          variantPrefix, imageGenerator, counterString);
      if (!variantResult.isPresent()) {
        continue;
      }
      maybeLogFailure(variantResult.get(), variantPrefix, counterString);

      if (!ns.getBoolean("only_variants")) {

        final ShaderJob referenceShaderJob = shaderPair.getLeft();
        final Optional<ImageJobResult> referenceResult = runShaderJob(referenceShaderJob,
            referencePrefix, imageGenerator, counterString);
        if (!referenceResult.isPresent()) {
          continue;
        }

        maybeLogFailure(referenceResult.get(), referencePrefix, counterString);
        maybeLogWrongImage(referenceResult.get(),
            variantResult.get(),
            referencePrefix,
            variantPrefix,
            counterString);
      }

    }
  }

  private void maybeLogFailure(ImageJobResult shaderJobResult, String prefix, String counter) {
    switch (shaderJobResult.getStatus()) {
      case CRASH:
      case COMPILE_ERROR:
      case LINK_ERROR:
      case TIMEOUT:
      case UNEXPECTED_ERROR:
      case SANITY_ERROR:
      case NONDET:
        try {
          File triageDirectory = new File(outputDir, shaderJobResult.getStatus().toString());
          makeDirectoryIfNeeded(triageDirectory);
          if (shaderJobResult.getStatus() == JobStatus.CRASH) {
            final String crashFileContents = FileUtils.readFileToString(
                new File(outputDir, prefix + ".txt"),
                StandardCharsets.UTF_8);
            if (crashStringsToIgnore.stream().anyMatch(crashFileContents::contains)) {
              triageDirectory = new File(triageDirectory, "IGNORE");
              makeDirectoryIfNeeded(triageDirectory);
            }
          }
          transferFilesToTriageDirectory(prefix, counter, triageDirectory);
          return;
        } catch (IOException exception) {
          LOGGER.error(
              "A problem occurred when logging failures; defeats the point of the exercise.",
              exception);
          throw new RuntimeException(exception);
        }
      default:
        return;
    }
  }

  private void maybeLogWrongImage(ImageJobResult referenceResult,
                                  ImageJobResult variantResult,
                                  String referencePrefix,
                                  String variantPrefix,
                                  String counter) {
    // TODO: implement.
  }

  private Optional<ImageJobResult> runShaderJob(ShaderJob shaderJob,
                                                String prefix,
                                                IShaderDispatcher imageGenerator,
                                                String counterString) {
    // Emit the shader job.
    try {
      Helper.emitShaderJob(shaderJob, shadingLanguageVersion,
          prefix,
          outputDir,
          null);
    } catch (IOException exception) {
      LOGGER.error("Could not emit " + prefix + " shader job.",
          exception);
      return Optional.empty();
    }

    // Validate the shader job.
    try {
      if (!isValid(shaderJob, prefix)) {
        final File triageDirectory = new File(outputDir, "INVALID");
        makeDirectoryIfNeeded(triageDirectory);
        transferFilesToTriageDirectory(prefix, counterString, triageDirectory);
        return Optional.empty();
      }
    } catch (InterruptedException | IOException exception) {
      LOGGER.error("Problem validating " + prefix + " shader job.", exception);
      return Optional.empty();
    }
    // Run the shader job.
    try {
      return Optional.of(RunShaderFamily.runShader(outputDir,
          prefix,
          imageGenerator,
          Optional.empty()));
    } catch (InterruptedException | IOException | ShaderDispatchException exception) {
      LOGGER.error("Problem running " + prefix + " shader job.", exception);
      return Optional.empty();
    }
  }

  private boolean isValid(ShaderJob shaderJob, String prefix) throws IOException,
      InterruptedException {
    if (shaderJob.hasVertexShader() && !validShader(prefix + ".vert")) {
      return false;
    }
    if (shaderJob.hasFragmentShader() && !validShader(prefix + ".frag")) {
      return false;
    }
    return true;
  }

  private boolean validShader(String filename) throws IOException, InterruptedException {
    final ExecResult validatorResult =
        ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER,
            new File(outputDir, filename));
    if (validatorResult.res != 0) {
      LOGGER.error("Shader job " + filename + " is not valid: "
          + validatorResult.stdout + validatorResult.stderr);
      return false;
    }
    return true;
  }

  private void makeDirectoryIfNeeded(File directory) {
    if (!directory.exists()) {
      directory.mkdir();
    } else {
      assert directory.isDirectory();
    }
  }

  private void transferFilesToTriageDirectory(String prefix, String counter, File triageDirectory)
      throws IOException {
    for (File file : outputDir.listFiles((dir, name) -> name.startsWith(prefix))) {
      FileUtils.copyFile(file, new File(triageDirectory, counter + file.getName()));
    }
  }


}
