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

package com.graphicsfuzz.reducer;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.reducer.glslreducers.GlslReductionState;
import com.graphicsfuzz.reducer.glslreducers.IReductionPlan;
import com.graphicsfuzz.reducer.glslreducers.MasterPlan;
import com.graphicsfuzz.reducer.glslreducers.NoMoreToReduceException;
import com.graphicsfuzz.reducer.reductionopportunities.FailedReductionException;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunityContext;
import com.graphicsfuzz.reducer.util.Simplify;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReductionDriver {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReductionDriver.class);

  public static final boolean DEBUG_REDUCER = false;

  private static final int NUM_INITIAL_TRIES = 5;

  private final boolean verbose;
  private final IReductionPlan plan;

  private IReductionState newState;

  private IReductionState state;
  private int numReductionAttempts;
  private int numSuccessfulReductions = -1;

  private final Set<String> failHashes;
  private final Set<String> passHashes;

  public ReductionDriver(ReductionOpportunityContext reductionOpportunityContext,
        boolean verbose,
        IReductionState initialState) {
    this.verbose = verbose;
    this.plan = new MasterPlan(
          reductionOpportunityContext, verbose);
    this.newState = initialState;
    this.failHashes = new HashSet<>();
    this.passHashes = new HashSet<>();
  }

  public String doReduction(
        String initialFilePrefix,
        int fileCountOffset, // Used when continuing a reduction - added on to the number associated
        // with each reduction step during the current reduction.
        IReductionStateFileWriter fileWriter,
        IFileJudge judge,
        File workDir,
        int stepLimit) throws IOException {

    try {

      if (fileCountOffset > 0) {
        LOGGER.info("Continuing reduction for {}", initialFilePrefix);
      } else {
        LOGGER.info("Starting reduction for {}", initialFilePrefix);
        for (int i = 1; ; i++) {
          if (judge.isInteresting(initialFilePrefix)) {
            break;
          }
          LOGGER.info("Result from initial state is not interesting (attempt " + i + ")");
          if (i >= NUM_INITIAL_TRIES) {
            LOGGER.info("Tried " + NUM_INITIAL_TRIES + " times; stopping.");
            new File(workDir, "NOT_INTERESTING").createNewFile();
            return null;
          }
        }
        LOGGER.info("Result from initial state is interesting - proceeding with reduction.");
      }

      final String variantName = FilenameUtils.getBaseName(initialFilePrefix);

      final File initialFileJson = new File(initialFilePrefix + ".json");

      boolean isInteresting = true;
      int stepCount = 0;
      boolean stoppedEarly = false;
      while (true) {
        notifyNewStateInteresting(isInteresting);
        IReductionState newState = doReductionStep();
        if (newState == null) {
          break;
        }
        ++stepCount;
        final int currentReductionAttempt = numReductionAttempts + fileCountOffset;
        String outputFilesPrefix = Paths.get(workDir.getAbsolutePath(),
            getReductionStepFilenamePrefix(variantName, currentReductionAttempt))
            .toString();
        fileWriter.writeFileFromState(newState, outputFilesPrefix);
        Helper.emitUniformsInfo(newState.computeRemainingUniforms(initialFileJson),
              new PrintStream(new FileOutputStream(outputFilesPrefix + ".json")));
        isInteresting = isInterestingWithCache(judge, outputFilesPrefix);
        renameReductionStepFiles(isInteresting, variantName, currentReductionAttempt, workDir);

        if (stepLimit > -1 && stepCount >= stepLimit) {
          LOGGER.info("Stopping reduction due to hitting step limit {}.", stepLimit);
          giveUp();
          stoppedEarly = true;
          break;
        }
      }

      IReductionState finalState = getSimplifiedState();

      String finalOutputFilePrefix = Paths.get(workDir.getAbsolutePath(),
          variantName + "_reduced_final").toString();
      fileWriter.writeFileFromState(finalState, finalOutputFilePrefix);
      Helper.emitUniformsInfo(finalState.computeRemainingUniforms(initialFileJson),
            new PrintStream(new FileOutputStream(
                  finalOutputFilePrefix + ".json")));

      if (!judge.isInteresting(finalOutputFilePrefix)) {
        LOGGER.info(
              "Failed to simplify final reduction state! Reverting to the non-simplified state.");
        fileWriter.writeFileFromState(finalState, finalOutputFilePrefix);
      }

      if (stoppedEarly) {
        // Place a marker file to indicate that the reduction was not complete.
        new File(workDir, Constants.REDUCTION_INCOMPLETE).createNewFile();
      }

      return finalOutputFilePrefix;
    } catch (FileNotFoundException | FileJudgeException exception) {
      throw new RuntimeException(exception);
    }
  }

  private boolean isInterestingWithCache(IFileJudge judge, String outputFilesPrefix)
        throws FileJudgeException, IOException {
    final String hash = getMD5(outputFilesPrefix);
    if (failHashes.contains(hash)) {
      return false;
    }
    if (passHashes.contains(hash)) {
      throw new RuntimeException("Reduction loop detected!");
    }
    boolean result = judge.isInteresting(outputFilesPrefix);
    if (result) {
      passHashes.add(hash);
    } else {
      failHashes.add(hash);
    }
    return result;
  }

  public static String getReductionStepFilenamePrefix(String variantName,
        int currentReductionAttempt,
        Optional<String> successIndicator) {
    return variantName + "_reduced_" + String.format("%04d", currentReductionAttempt)
          + successIndicator
          .flatMap(item -> Optional.of("_" + item))
          .orElse("");
  }

  private String getReductionStepFilenamePrefix(String variantName, int currentReductionAttempt) {
    return getReductionStepFilenamePrefix(variantName, currentReductionAttempt, Optional.empty());
  }

  private void renameReductionStepFiles(boolean isInteresting, String variantName,
        int currentReductionAttempt,
        File workDir) throws IOException {
    for (String fileName : workDir.list((dir, name) -> FilenameUtils.removeExtension(name)
          .equals(getReductionStepFilenamePrefix(variantName, currentReductionAttempt)))) {
      FileUtils.moveFile(
            new File(workDir, fileName),
            new File(workDir, getReductionStepFilenamePrefix(variantName, currentReductionAttempt,
                  Optional.of(isInteresting ? "success" : "fail"))
                  + "." + FilenameUtils.getExtension(fileName)));
    }
  }

  private String getMD5(String filesPrefix) throws IOException {
    final File vertexShaderFile = new File(filesPrefix + ".vert");
    final File fragmentShaderFile = new File(filesPrefix + ".frag");
    byte[] vertexData = vertexShaderFile.exists()
        ? FileUtils.readFileToByteArray(vertexShaderFile)
        : new byte[0];
    byte[] fragmentData = fragmentShaderFile.exists()
        ? FileUtils.readFileToByteArray(fragmentShaderFile)
        : new byte[0];
    byte[] combinedData = new byte[vertexData.length + fragmentData.length];
    System.arraycopy(vertexData, 0, combinedData, 0, vertexData.length);
    System.arraycopy(fragmentData, 0, combinedData, vertexData.length, fragmentData.length);
    return DigestUtils.md5Hex(combinedData);
  }

  public IReductionState doReductionStep() {
    LOGGER.info("Trying reduction attempt " + numReductionAttempts + " (" + numSuccessfulReductions
          + " successful so far).");
    if (newState != null) {
      throw new IllegalStateException("Called doReductionStep yet a newState is already set.");
    }
    while (true) {
      try {
        final IReductionState reductionStepResult = applyReduction(state);
        numReductionAttempts++;
        newState = reductionStepResult;
        return newState;
      } catch (NoMoreToReduceException exception) {
        LOGGER.info("No more to reduce; stopping.");
        return null;
      }
    }
  }

  private IReductionState applyReduction(IReductionState state) throws NoMoreToReduceException {
    int attempts = 0;
    final int maxAttempts = 3;
    while (true) {
      try {
        return plan.applyReduction(state);
      } catch (FailedReductionException exception) {
        attempts++;
        if (attempts == maxAttempts) {
          throw exception;
        }
      }
    }
  }

  private void notifyNewStateInteresting(boolean isInteresting) {
    if (newState == null) {
      throw new IllegalStateException(
            "Called notifyNewStateInteresting when there was no newState.");
    }
    if (isInteresting) {
      // If the reduction attempt is interesting, the new state becomes
      // the current reduction state
      LOGGER.info("Successful reduction.");
      numSuccessfulReductions++;
      state = newState;
    } else {
      LOGGER.info("Failed reduction.");
    }
    plan.update(isInteresting);
    newState = null;

    if (state == null) {
      throw new IllegalStateException(
            "Called notifyNewStateInteresting(" + isInteresting + ") and "
                  + "there was no interesting state. Perhaps you forgot to call "
                  + "notifyNewStateInteresting(true) initially?");
    }
  }

  private IReductionState getSimplifiedState() {
    if (newState != null) {
      throw new IllegalStateException("Called getSimplifiedState yet a newState is set.");
    }
    if (state == null) {
      throw new IllegalStateException("Called getSimplifiedState yet state is null.");
    }
    return finaliseReduction();
  }

  private void giveUp() {
    if (state == null) {
      throw new IllegalStateException(
            "Called giveUp yet state is null. "
                  + "Perhaps you forgot to call notifyNewStateInteresting(true) initially?");
    }
    newState = null;
  }

  public final GlslReductionState finaliseReduction() {
    // Do final cleanup pass to get rid of macros
    // TODO: need to make this work for vertex shaders too.
    final Optional<TranslationUnit> simplifiedFragmentShader =
        Optional.of(Simplify.simplify(state.getFragmentShader()));
    Optional<TranslationUnit> vertexShader = state.hasVertexShader()
        ? Optional.of(state.getVertexShader())
        : Optional.empty();
    return new GlslReductionState(simplifiedFragmentShader, vertexShader);
  }


}
