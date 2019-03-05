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
import com.graphicsfuzz.reducer.glslreducers.IReductionPass;
import com.graphicsfuzz.reducer.glslreducers.IReductionPassManager;
import com.graphicsfuzz.reducer.glslreducers.SystematicReductionPass;
import com.graphicsfuzz.reducer.glslreducers.SystematicReductionPassManager;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunityFinder;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import com.graphicsfuzz.reducer.util.Simplify;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReductionDriver {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReductionDriver.class);

  public static final boolean DEBUG_REDUCER = false;

  private static final int NUM_INITIAL_TRIES = 5;

  private final ReducerContext context;

  private final ShaderJobFileOperations fileOps;

  private final IFileJudge judge;

  private final File workDir;

  private int numSuccessfulReductions = 0;

  private final Set<String> failHashCache;
  private final Set<String> passHashCache;

  private int failHashCacheHits;

  private final IReductionPassManager passManager;

  public ReductionDriver(ReducerContext context,
                         boolean verbose,
                         ShaderJobFileOperations fileOps,
                         IFileJudge judge,
                         File workDir) {
    this.context = context;
    this.fileOps = fileOps;
    this.judge = judge;
    this.workDir = workDir;
    this.failHashCache = new HashSet<>();
    this.passHashCache = new HashSet<>();
    this.failHashCacheHits = 0;

    final List<IReductionPass> initialPasses = new ArrayList<>();
    initialPasses.add(new SystematicReductionPass(context, verbose,
        IReductionOpportunityFinder.largestStmtsFinder(10, 50), 1));
    initialPasses.add(new SystematicReductionPass(context, verbose,
        IReductionOpportunityFinder.largestFunctionsFinder(5), 1));

    final List<IReductionPass> cleanupPasses = new ArrayList<>();
    for (IReductionOpportunityFinder finder : new IReductionOpportunityFinder[]{
        IReductionOpportunityFinder.inlineUniformFinder(),
        IReductionOpportunityFinder.inlineInitializerFinder(),
        IReductionOpportunityFinder.inlineFunctionFinder(),
        IReductionOpportunityFinder.unusedParamFinder(),
        IReductionOpportunityFinder.foldConstantFinder(),
    }) {
      cleanupPasses.add(new SystematicReductionPass(context,
          verbose,
          finder));
    }

    final List<IReductionPass> corePasses = new ArrayList<>();
    for (IReductionOpportunityFinder finder : new IReductionOpportunityFinder[]{
        IReductionOpportunityFinder.vectorizationFinder(),
        IReductionOpportunityFinder.unswitchifyFinder(),
        IReductionOpportunityFinder.stmtFinder(),
        IReductionOpportunityFinder.functionFinder(),
        IReductionOpportunityFinder.exprToConstantFinder(),
        IReductionOpportunityFinder.functionFinder(),
        IReductionOpportunityFinder.compoundExprToSubExprFinder(),
        IReductionOpportunityFinder.functionFinder(),
        IReductionOpportunityFinder.mutationFinder(),
        IReductionOpportunityFinder.loopMergeFinder(),
        IReductionOpportunityFinder.compoundToBlockFinder(),
        IReductionOpportunityFinder.outlinedStatementFinder(),
        IReductionOpportunityFinder.unwrapFinder(),
        IReductionOpportunityFinder.removeStructFieldFinder(),
        IReductionOpportunityFinder.destructifyFinder(),
        IReductionOpportunityFinder.inlineStructFieldFinder(),
        IReductionOpportunityFinder.liveFragColorWriteFinder(),
        IReductionOpportunityFinder.functionFinder(),
        IReductionOpportunityFinder.variableDeclFinder(),
        IReductionOpportunityFinder.globalVariablesDeclarationFinder(),
    }) {
      final SystematicReductionPass pass = new SystematicReductionPass(context,
          verbose,
          finder);
      corePasses.add(pass);
      cleanupPasses.add(pass);
    }
    this.passManager = new SystematicReductionPassManager(initialPasses, corePasses, cleanupPasses);

  }

  public String doReduction(
        ShaderJob initialState,
        String shaderJobShortName,
        int fileCountOffset, // Used when continuing a reduction - added on to the number associated
        // with each reduction step during the current reduction.
        int stepLimit) throws IOException {

    // This is used for Vulkan compatibility.
    final boolean requiresUniformBindings = initialState.hasUniformBindings();
    if (initialState.hasUniformBindings()) {
      // We eliminate uniform bindings while applying reduction steps, and re-introduce them
      // each time we emit shaders.
      initialState.removeUniformBindings();
    }

    try {
      if (fileCountOffset > 0) {
        LOGGER.info("Continuing reduction for {}", shaderJobShortName);
      } else {
        LOGGER.info("Starting reduction for {}", shaderJobShortName);
        for (int i = 1; ; i++) {
          if (isInterestingNoCache(initialState, requiresUniformBindings, shaderJobShortName)) {
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

      ShaderJob currentState = initialState;

      int stepCount = 0;
      boolean stoppedEarly = false;

      while (true) {
        LOGGER.info("Trying reduction attempt " + stepCount + " (" + numSuccessfulReductions
            + " successful so far).");
        final Optional<ShaderJob> maybeNewState = passManager.applyReduction(currentState);
        if (!maybeNewState.isPresent()) {
          LOGGER.info("No more to reduce; stopping.");
          break;
        }
        final ShaderJob newState = maybeNewState.get();
        stepCount++;
        final int currentReductionAttempt = stepCount + fileCountOffset;
        String currentShaderJobShortName =
            getReductionStepShaderJobShortName(
                shaderJobShortName,
                currentReductionAttempt);
        final boolean interesting = isInterestingWithCache(newState,
            requiresUniformBindings,
            currentShaderJobShortName);
        passManager.notifyInteresting(interesting);
        final String currentStepShaderJobShortNameWithOutcome =
            getReductionStepShaderJobShortName(
                shaderJobShortName,
                currentReductionAttempt,
                Optional.of(interesting ? "success" : "fail"));
        fileOps.moveShaderJobFileTo(
            new File(workDir, currentShaderJobShortName + ".json"),
            new File(workDir, currentStepShaderJobShortNameWithOutcome + ".json"),
            true
        );
        if (interesting) {
          LOGGER.info("Successful reduction.");
          numSuccessfulReductions++;
          currentState = newState;
        } else {
          LOGGER.info("Failed reduction.");
        }

        if (stepLimit > -1 && stepCount >= stepLimit) {
          LOGGER.info("Stopping reduction due to hitting step limit {}.", stepLimit);
          stoppedEarly = true;
          break;
        }
      }

      ShaderJob finalState = finaliseReduction(currentState);

      String finalOutputFilePrefix = shaderJobShortName + "_reduced_final";

      if (!isInterestingNoCache(finalState, requiresUniformBindings, finalOutputFilePrefix)) {
        LOGGER.info(
            "Failed to simplify final reduction state! Reverting to the non-simplified state.");
        writeState(currentState, new File(workDir, finalOutputFilePrefix + ".json"),
            requiresUniformBindings);
      }

      if (stoppedEarly) {
        // Place a marker file to indicate that the reduction was not complete.
        fileOps.createFile(new File(workDir, Constants.REDUCTION_INCOMPLETE));
      }

      LOGGER.info("Total fail hash cache hits: " + failHashCacheHits);
      return finalOutputFilePrefix;
    } catch (FileNotFoundException | FileJudgeException exception) {
      throw new RuntimeException(exception);
    }
  }

  private boolean isInteresting(ShaderJob state,
                                boolean requiresUniformBindings,
                                String shaderJobShortName,
                                boolean useCache) throws IOException, FileJudgeException {
    final File shaderJobFile = new File(workDir, shaderJobShortName + ".json");
    final File resultFile = new File(workDir, shaderJobShortName + ".info.json");
    writeState(state, shaderJobFile, requiresUniformBindings);

    String hash = null;
    if (useCache) {
      hash = fileOps.getShaderJobFileHash(shaderJobFile);
      if (failHashCache.contains(hash)) {
        LOGGER.info(
            "Fail hash cache hit.");
        failHashCacheHits++;
        return false;
      }
      if (passHashCache.contains(hash)) {
        throw new RuntimeException("Reduction loop detected!");
      }
    }

    if (judge.isInteresting(
        shaderJobFile,
        resultFile)) {
      if (useCache) {
        passHashCache.add(hash);
      }
      return true;
    }
    if (useCache) {
      failHashCache.add(hash);
    }
    return false;
  }

  private boolean isInterestingWithCache(ShaderJob state,
                                boolean requiresUniformBindings,
                                String shaderJobShortName) throws IOException, FileJudgeException {

    return isInteresting(state, requiresUniformBindings, shaderJobShortName, true);
  }

  private boolean isInterestingNoCache(ShaderJob state,
                                boolean requiresUniformBindings,
                                String shaderJobShortName) throws IOException, FileJudgeException {
    return isInteresting(state, requiresUniformBindings, shaderJobShortName, false);
  }

  private void writeState(ShaderJob state, File shaderJobFileOutput,
                          boolean requiresUniformBindings) throws FileNotFoundException {
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

  public final ShaderJob finaliseReduction(ShaderJob state) {
    // Do final cleanup pass to get rid of macros
    return new GlslShaderJob(
        state.getLicense(),
        state.getPipelineInfo(),
        state.getShaders().stream().map(Simplify::simplify).collect(Collectors.toList()));
  }


}
