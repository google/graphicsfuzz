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
import com.graphicsfuzz.common.util.AddInitializers;
import com.graphicsfuzz.common.util.GloballyTruncateLoops;
import com.graphicsfuzz.common.util.MakeArrayAccessesInBounds;
import com.graphicsfuzz.common.util.PipelineUniformValueSupplier;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.reducer.glslreducers.IReductionPass;
import com.graphicsfuzz.reducer.glslreducers.IReductionPassManager;
import com.graphicsfuzz.reducer.glslreducers.SystematicReductionPass;
import com.graphicsfuzz.reducer.glslreducers.SystematicReductionPassManager;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunityFinder;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import com.graphicsfuzz.reducer.util.Simplify;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    this(context,
        verbose,
        fileOps,
        judge,
        workDir,
        false);
  }

  public ReductionDriver(ReducerContext context,
                         boolean verbose,
                         ShaderJobFileOperations fileOps,
                         IFileJudge judge,
                         File workDir,
                         boolean literalsToUniforms) {
    this.context = context;
    this.fileOps = fileOps;
    this.judge = judge;
    this.workDir = workDir;
    this.failHashCache = new HashSet<>();
    this.passHashCache = new HashSet<>();
    this.failHashCacheHits = 0;

    if (literalsToUniforms) {
      this.passManager = ReductionDriver.getLiteralsToUniformsPassManager(context, verbose);
    } else {
      this.passManager = ReductionDriver.getDefaultPassManager(context, verbose);
    }

  }

  private static IReductionPassManager getDefaultPassManager(
      ReducerContext context,
      boolean verbose) {

    final List<IReductionPass> initialPasses = new ArrayList<>();
    initialPasses.add(new SystematicReductionPass(context, verbose,
        IReductionOpportunityFinder.largestStmtsFinder(10, 50), 1));
    initialPasses.add(new SystematicReductionPass(context, verbose,
        IReductionOpportunityFinder.largestFunctionsFinder(5), 1));

    final List<IReductionPass> cleanupPasses = new ArrayList<>();
    for (IReductionOpportunityFinder<? extends IReductionOpportunity> finder : Arrays.asList(
        IReductionOpportunityFinder.inlineUniformFinder(),
        IReductionOpportunityFinder.inlineInitializerFinder(),
        IReductionOpportunityFinder.inlineFunctionFinder(),
        IReductionOpportunityFinder.unusedParamFinder(),
        IReductionOpportunityFinder.foldConstantFinder(),
        IReductionOpportunityFinder.redundantUniformMetadataFinder(),
        IReductionOpportunityFinder.variableDeclToExprFinder(),
        IReductionOpportunityFinder.globalVariableDeclToExprFinder(),
        IReductionOpportunityFinder.globalPrecisionDeclarationFinder())) {
      cleanupPasses.add(new SystematicReductionPass(context,
          verbose,
          finder));
    }

    final List<IReductionPass> corePasses = new ArrayList<>();
    for (IReductionOpportunityFinder<? extends IReductionOpportunity> finder : Arrays.asList(
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
        IReductionOpportunityFinder.flattenControlFlowFinder(),
        IReductionOpportunityFinder.switchToLoopFinder(),
        IReductionOpportunityFinder.outlinedStatementFinder(),
        IReductionOpportunityFinder.unwrapFinder(),
        IReductionOpportunityFinder.removeStructFieldFinder(),
        IReductionOpportunityFinder.destructifyFinder(),
        IReductionOpportunityFinder.inlineStructFieldFinder(),
        IReductionOpportunityFinder.liveFragColorWriteFinder(),
        IReductionOpportunityFinder.functionFinder(),
        IReductionOpportunityFinder.variableDeclFinder(),
        IReductionOpportunityFinder.globalVariablesDeclarationFinder(),
        IReductionOpportunityFinder.interfaceBlockFinder())) {
      final SystematicReductionPass pass = new SystematicReductionPass(context,
          verbose,
          finder);
      corePasses.add(pass);
      cleanupPasses.add(pass);
    }
    return new SystematicReductionPassManager(initialPasses, corePasses, cleanupPasses);
  }

  private static IReductionPassManager getLiteralsToUniformsPassManager(
      ReducerContext context,
      boolean verbose) {

    return new SystematicReductionPassManager(
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.singletonList(
            new SystematicReductionPass(
                context,
                verbose,
                IReductionOpportunityFinder.literalToUniformFinder())));
  }

  public String doReduction(
        ShaderJob initialState,
        String shaderJobShortName,
        int fileCountOffset, // Used when continuing a reduction - added on to the number associated
        // with each reduction step during the current reduction.
        int stepLimit) throws IOException {

    // This is used for Vulkan compatibility.
    // TODO(https://github.com/google/graphicsfuzz/issues/1046): The check for zero uniforms is a
    //  workaround for the fact that we don't have a way to infer what the right thing to do is
    //  when there are no uniforms.  As per the issue, we should really have a --vulkan option that
    //  instructs the reducer as to whether we want Vulkan-style uniform blocks.
    final boolean requiresUniformBindings =
        initialState.getPipelineInfo().getNumUniforms() == 0 || initialState.hasUniformBindings();
    final Optional<String> pushConstant = initialState.getPushConstant();
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
          // Initially, check that things are interesting even if we do nothing to make the shader
          // job more likely to be free from undefined behaviour, such as adding loop limiters.
          if (isInterestingNoCache(initialState, requiresUniformBindings, pushConstant,
              false,
              false,
              false,
              shaderJobShortName)) {
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

      // Flags to control whether attempts are made to make the shader job less prone to undefined
      // behaviour when writing it out to a file.  These flags are not enabled if we are preserving
      // semantics (because we do not expect undefined behaviour in that case, and because the
      // measures to limit undefined behaviour are semantics-changing.)
      boolean addGlobalLoopLimiters = false;
      boolean makeArrayAccessesInBounds = false;
      boolean addInitializers = false;

      if (context.addUbGuards() && context.reduceEverywhere()) {
        LOGGER.info("We are not preserving semantics, so see whether adding guards against "
            + "undefined behaviour preserves interestingness.");

        if (isInterestingNoCache(initialState, requiresUniformBindings, pushConstant, true,
            false, false, shaderJobShortName)) {
          LOGGER.info("The shader is still interesting when global loop limiters are added; these"
              + " will be used during reduction, so the final reduced shader should be free from "
              + "long-running or infinite loops.");
          addGlobalLoopLimiters = true;
        } else {
          LOGGER.info("The shader is not interesting when global loop limiters are added; these"
              + " will not be used during reduction, so the final reduced shader could contain "
              + "long-running or infinite loops.");
        }

        if (isInterestingNoCache(initialState, requiresUniformBindings,  pushConstant,
            addGlobalLoopLimiters, true, false, shaderJobShortName)) {
          LOGGER.info("The shader is still interesting when array/vector/matrix accesses "
              + "are made in-bounds.  Bounds clamping will be applied during reduction, so "
              + "the final reduced shader should be free from out-of-bounds accesses.");
          makeArrayAccessesInBounds = true;
        } else {
          LOGGER.info("The shader is not interesting when array/vector/matrix accesses "
              + "are made in-bounds.  Bounds clamping will be not be applied during reduction, "
              + "so the final reduced shader might exhibit out-of-bounds accesses.");
        }

        if (isInterestingNoCache(initialState, requiresUniformBindings, pushConstant,
            addGlobalLoopLimiters, makeArrayAccessesInBounds, true, shaderJobShortName)) {
          LOGGER.info("The shader is still interesting when initializers are added to all regular "
              + "variables.  Adding of initializers will be applied during reduction, so the final "
              + "reduced shader should be free from reads from uninitialized regular variables.");
          addInitializers = true;
        } else {
          LOGGER.info("The shader is not interesting when initializers are added to all regular "
              + "variables.  Adding of initializers will not be applied during reduction, so the "
              + "final reduced shader should be free from reads from uninitialized regular "
              + "variables.");
        }

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
            pushConstant,
            addGlobalLoopLimiters,
            makeArrayAccessesInBounds,
            addInitializers,
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

      if (!isInterestingNoCache(finalState, requiresUniformBindings, pushConstant,
          addGlobalLoopLimiters, makeArrayAccessesInBounds, addInitializers,
          finalOutputFilePrefix)) {
        LOGGER.info(
            "Failed to simplify final reduction state! Reverting to the non-simplified state.");
        writeState(currentState, new File(workDir, finalOutputFilePrefix + ".json"),
            requiresUniformBindings, pushConstant, addGlobalLoopLimiters, makeArrayAccessesInBounds,
            addInitializers);
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
                                Optional<String> pushConstant,
                                boolean addGlobalLoopLimiters,
                                boolean makeArrayAccessesInBounds,
                                boolean addInitializers,
                                String shaderJobShortName,
                                boolean useCache) throws IOException, FileJudgeException {

    final File shaderJobFile = new File(workDir, shaderJobShortName + ".json");
    String hash = null;
    if (useCache) {
      // The cache is enabled, so first check for a cache hit.

      // Write the state out to a shader job file, without doing any post-processing
      // transformations.  This is because two different shader jobs might get post-processed to the
      // same thing, and we want to avoid treating this as a reduction loop (and we *do* want to
      // guard against reduction loops).
      writeState(state, shaderJobFile, false, Optional.empty(), false, false, false);

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

    // If we used the cache, we will have already written the shader job out, but without any post-
    // processing; we overwrite it now with any relevant post-processing.
    writeState(state, shaderJobFile, requiresUniformBindings, pushConstant, addGlobalLoopLimiters,
        makeArrayAccessesInBounds, addInitializers);

    if (judge.isInteresting(
        shaderJobFile,
        new File(workDir, shaderJobShortName + ".info.json"))) {
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
                                Optional<String> pushConstant,
                                boolean addGlobalLoopLimiters,
                                boolean makeArrayAccessesInBounds,
                                boolean addInitializers,
                                String shaderJobShortName) throws IOException, FileJudgeException {

    return isInteresting(state, requiresUniformBindings, pushConstant, addGlobalLoopLimiters,
        makeArrayAccessesInBounds, addInitializers, shaderJobShortName, true);
  }

  private boolean isInterestingNoCache(ShaderJob state,
                                boolean requiresUniformBindings,
                                Optional<String> pushConstant,
                                boolean addGlobalLoopLimiters,
                                boolean makeArrayAccessesInBounds,
                                boolean addInitializers,
                                String shaderJobShortName) throws IOException, FileJudgeException {
    return isInteresting(state, requiresUniformBindings, pushConstant, addGlobalLoopLimiters,
        makeArrayAccessesInBounds, addInitializers, shaderJobShortName, false);
  }

  private void writeState(ShaderJob state, File shaderJobFileOutput,
                          boolean requiresUniformBindings,
                          Optional<String> pushConstant,
                          boolean addGlobalLoopLimiters,
                          boolean makeArrayAccessesInBounds,
                          boolean addInitializers) throws FileNotFoundException {
    ShaderJob stateToWrite = state.clone();
    if (requiresUniformBindings) {
      assert !stateToWrite.hasUniformBindings();
      stateToWrite.makeUniformBindings(pushConstant);
    }
    if (addGlobalLoopLimiters) {
      GloballyTruncateLoops.truncate(stateToWrite,
          Constants.GLF_GLOBAL_LOOP_BOUND_VALUE,
          Constants.GLF_GLOBAL_LOOP_COUNT_NAME,
          Constants.GLF_GLOBAL_LOOP_BOUND_NAME);
    }
    if (makeArrayAccessesInBounds) {
      MakeArrayAccessesInBounds.makeInBounds(stateToWrite);
    }
    if (addInitializers) {
      AddInitializers.addInitializers(stateToWrite);
    }
    fileOps.writeShaderJobFile(
        stateToWrite,
        shaderJobFileOutput,
        Optional.of(new PipelineUniformValueSupplier(stateToWrite.getPipelineInfo()))
    );
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

  private ShaderJob finaliseReduction(ShaderJob state) {
    // Do final cleanup pass to get rid of macros
    return new GlslShaderJob(
        state.getLicense(),
        state.getPipelineInfo(),
        state.getShaders().stream().map(Simplify::simplify).collect(Collectors.toList()));
  }

}
