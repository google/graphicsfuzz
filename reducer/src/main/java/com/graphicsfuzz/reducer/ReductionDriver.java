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

import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.reducer.glslreducers.IReductionPlan;
import com.graphicsfuzz.reducer.glslreducers.MasterPlan;
import com.graphicsfuzz.reducer.glslreducers.NoMoreToReduceException;
import com.graphicsfuzz.reducer.reductionopportunities.FailedReductionException;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import com.graphicsfuzz.reducer.util.Simplify;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReductionDriver {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReductionDriver.class);

  public static final boolean DEBUG_REDUCER = false;

  private static final int NUM_INITIAL_TRIES = 5;

  private final boolean verbose;

  private final ReducerContext context;

  private final IReductionPlan plan;

  private final ShaderJobFileOperations fileOps;

  private ShaderJob newState;

  // This is used for Vulkan compatibility.
  private final boolean requiresUniformBindings;

  private ShaderJob state;
  private int numReductionAttempts;
  private int numSuccessfulReductions = -1;

  private final Set<String> failHashes;
  private final Set<String> passHashes;

  public ReductionDriver(ReducerContext context,
                         boolean verbose,
                         ShaderJobFileOperations fileOps,
                         ShaderJob initialState) {
    this.verbose = verbose;
    this.context = context;
    this.fileOps = fileOps;
    this.plan = new MasterPlan(context, verbose);
    this.state = null;
    this.newState = initialState;
    this.requiresUniformBindings = this.newState.hasUniformBindings();
    this.failHashes = new HashSet<>();
    this.passHashes = new HashSet<>();

    if (this.newState.hasUniformBindings()) {
      // We eliminate uniform bindings while applying reduction steps, and re-introduce them
      // each time we emit shaders.
      this.newState.removeUniformBindings();
    }

  }

  public String doReduction(
        String shaderJobShortName,
        int fileCountOffset, // Used when continuing a reduction - added on to the number associated
        // with each reduction step during the current reduction.
        IFileJudge judge,
        File workDir,
        int stepLimit) throws IOException {

    try {

      if (fileCountOffset > 0) {
        LOGGER.info("Continuing reduction for {}", shaderJobShortName);
      } else {
        LOGGER.info("Starting reduction for {}", shaderJobShortName);
        for (int i = 1; ; i++) {
          if (judge.isInteresting(
              new File(workDir, shaderJobShortName + ".json"),
              new File(workDir, shaderJobShortName + ".info.json")
          )) {
            break;
          }
          LOGGER.info("Result from initial state is not interesting (attempt " + i + ")");
          if (i >= NUM_INITIAL_TRIES) {
            LOGGER.info("Tried " + NUM_INITIAL_TRIES + " times; stopping.");
            fileOps.createFile(new File(workDir, "NOT_INTERESTING"));
            return null;
          }
        }
        LOGGER.info("Result from initial state is interesting - proceeding with reduction.");
      }

      boolean isInteresting = true;
      int stepCount = 0;
      boolean stoppedEarly = false;
      while (true) {
        notifyNewStateInteresting(isInteresting);
        ShaderJob newState = doReductionStep();
        if (newState == null) {
          break;
        }
        ++stepCount;
        final int currentReductionAttempt = numReductionAttempts + fileCountOffset;
        String currentShaderJobShortName =
            getReductionStepShaderJobShortName(
                shaderJobShortName,
                currentReductionAttempt);
        writeState(newState, new File(workDir, currentShaderJobShortName + ".json"));

        isInteresting = isInterestingWithCache(
            judge,
            new File(workDir, currentShaderJobShortName + ".json"),
            new File(workDir, currentShaderJobShortName + ".info.json"));

        // Includes "_success" or "_fail".
        String currentStepShaderJobShortNameWithOutcome =
            getReductionStepShaderJobShortName(
              shaderJobShortName,
              currentReductionAttempt,
              Optional.of(isInteresting ? "success" : "fail"));

        fileOps.moveShaderJobFileTo(
            new File(workDir, currentShaderJobShortName + ".json"),
            new File(workDir, currentStepShaderJobShortNameWithOutcome + ".json"),
            true
        );

        if (stepLimit > -1 && stepCount >= stepLimit) {
          LOGGER.info("Stopping reduction due to hitting step limit {}.", stepLimit);
          giveUp();
          stoppedEarly = true;
          break;
        }
      }

      ShaderJob finalState = getSimplifiedState();

      String finalOutputFilePrefix = shaderJobShortName + "_reduced_final";
      writeState(finalState, new File(workDir, finalOutputFilePrefix + ".json"));

      if (!judge.isInteresting(
          new File(workDir, finalOutputFilePrefix + ".json"),
          new File(workDir, finalOutputFilePrefix + ".info.json")
      )) {
        LOGGER.info(
            "Failed to simplify final reduction state! Reverting to the non-simplified state.");
        writeState(state, new File(workDir, finalOutputFilePrefix + ".json"));
      }

      if (stoppedEarly) {
        // Place a marker file to indicate that the reduction was not complete.
        fileOps.createFile(new File(workDir, Constants.REDUCTION_INCOMPLETE));
      }

      return finalOutputFilePrefix;
    } catch (FileNotFoundException | FileJudgeException exception) {
      throw new RuntimeException(exception);
    }
  }

  private void writeState(ShaderJob state, File shaderJobFileOutput) throws FileNotFoundException {
    if (requiresUniformBindings) {
      assert !state.hasUniformBindings();
      state.makeUniformBindings();
    }
    fileOps.writeShaderJobFile(
        state,
        shaderJobFileOutput,
        context.getEmitGraphicsFuzzDefines()
    );
    if (requiresUniformBindings) {
      assert state.hasUniformBindings();
      state.removeUniformBindings();
    }
  }

  private boolean isInterestingWithCache(
      IFileJudge judge,
      File shaderJobFile,
      File shaderResultFileOutput)
        throws FileJudgeException, IOException {

    final String hash = fileOps.getShaderJobFileHash(shaderJobFile);
    if (failHashes.contains(hash)) {
      return false;
    }
    if (passHashes.contains(hash)) {
      throw new RuntimeException("Reduction loop detected!");
    }
    boolean result = judge.isInteresting(
        shaderJobFile,
        shaderResultFileOutput
    );
    if (result) {
      passHashes.add(hash);
    } else {
      failHashes.add(hash);
    }
    return result;
  }

  public static String getReductionStepShaderJobShortName(String variantPrefix,
                                                          int currentReductionAttempt,
                                                          Optional<String> successIndicator) {
    return variantPrefix + "_reduced_" + String.format("%04d", currentReductionAttempt)
          + successIndicator
          .flatMap(item -> Optional.of("_" + item))
          .orElse("");
  }

  private String getReductionStepShaderJobShortName(String variantPrefix,
                                                    int currentReductionAttempt) {
    return getReductionStepShaderJobShortName(variantPrefix, currentReductionAttempt,
        Optional.empty());
  }



  public ShaderJob doReductionStep() {
    LOGGER.info("Trying reduction attempt " + numReductionAttempts + " (" + numSuccessfulReductions
          + " successful so far).");
    if (newState != null) {
      throw new IllegalStateException("Called doReductionStep yet a newState is already set.");
    }
    try {
      final ShaderJob reductionStepResult = applyReduction(state);
      numReductionAttempts++;
      newState = reductionStepResult;
      return newState;
    } catch (NoMoreToReduceException exception) {
      LOGGER.info("No more to reduce; stopping.");
      return null;
    }
  }

  private ShaderJob applyReduction(ShaderJob state) throws NoMoreToReduceException {
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

  private ShaderJob getSimplifiedState() {
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

  public final ShaderJob finaliseReduction() {
    // Do final cleanup pass to get rid of macros
    return new GlslShaderJob(
        state.getLicense(),
        state.getPipelineInfo(),
        state.getShaders().stream().map(Simplify::simplify).collect(Collectors.toList()));
  }


}
