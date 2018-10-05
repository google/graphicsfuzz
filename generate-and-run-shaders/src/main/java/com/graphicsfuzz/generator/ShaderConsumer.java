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
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.generator.tool.PrepareReference;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.ImageData;
import com.graphicsfuzz.shadersets.RemoteShaderDispatcher;
import com.graphicsfuzz.shadersets.RunShaderFamily;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
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

  public ShaderConsumer(
      int limit,
      BlockingQueue<Pair<ShaderJob, ShaderJob>> queue,
      File outputDir,
      String server,
      String token,
      ShadingLanguageVersion shadingLanguageVersion) {
    this.limit = limit;
    this.queue = queue;
    this.outputDir = outputDir;
    this.server = server;
    this.token = token;
    this.shadingLanguageVersion = shadingLanguageVersion;
  }

  @Override
  public void run() {

    final IShaderDispatcher imageGenerator = new RemoteShaderDispatcher(
        server + "/manageAPI",
        token);

    for (int received = 0; received < limit; received++) {
      Pair<ShaderJob, ShaderJob> shaderPair;
      try {
        shaderPair = queue.take();
      } catch (InterruptedException exception) {
        LOGGER.error("Problem taking from queue.", exception);
        throw new RuntimeException(exception);
      }

      final ShaderJob referenceShaderJob = shaderPair.getLeft();
      final Optional<ImageJobResult> referenceResult = runShaderJob(referenceShaderJob,
          "reference", imageGenerator);
      if (!referenceResult.isPresent()) {
        continue;
      }
      final ShaderJob variantShaderJob = shaderPair.getRight();
      final Optional<ImageJobResult> variantResult = runShaderJob(variantShaderJob,
          "variant", imageGenerator);
      if (!variantResult.isPresent()) {
        continue;
      }

      maybeLogCrash(referenceResult, referenceShaderJob);
      maybeLogCrash(variantResult, variantShaderJob);
      maybeLogWrongImage(referenceResult, variantResult, referenceShaderJob, variantShaderJob);

      throw new RuntimeException("Decide what to do with the results!");

    }
  }

  private void maybeLogCrash(Optional<ImageJobResult> referenceResult, ShaderJob referenceShaderJob) {
    // TODO: implement.
  }

  private void maybeLogWrongImage(Optional<ImageJobResult> referenceResult, Optional<ImageJobResult> variantResult, ShaderJob referenceShaderJob, ShaderJob variantShaderJob) {
    // TODO: implement.
  }

  private Optional<ImageJobResult> runShaderJob(ShaderJob shaderJob, String referenceOrVariant,
                               IShaderDispatcher imageGenerator) {
    final String tempPrefix = "temp_" + referenceOrVariant;
    try {
      Helper.emitShaderJob(shaderJob, shadingLanguageVersion,
          tempPrefix,
          outputDir,
          null);
    } catch (IOException exception) {
      LOGGER.error("Could not emit " + referenceOrVariant + " shader job.",
          exception);
      return Optional.empty();
    }

    try {
      return Optional.of(RunShaderFamily.runShader(outputDir,
          tempPrefix,
          imageGenerator,
          Optional.empty()));
    } catch (InterruptedException | IOException | ShaderDispatchException exception) {
      LOGGER.error("Problem running " + referenceOrVariant + " shader job.",
          exception);
      return Optional.empty();
    }
  }

}
