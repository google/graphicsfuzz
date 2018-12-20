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

package com.graphicsfuzz.reducer.glslreducers;

import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystematicReductionPassManager implements IReductionPassManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      SystematicReductionPassManager.class);

  // Initial passes that aim to make big gains.
  private final List<IReductionPass> initialPasses;

  // The core passes to be iterated over frequently.
  private final List<IReductionPass> corePasses;

  // Passes to be used at the end of a reduction so that a minimum is reached.
  private final List<IReductionPass> exhaustivePasses;

  // Starts by referring to core, then switches to cleanup.
  private List<IReductionPass> currentPasses;

  // Determines whether, on completing one round of reduction passes, it is
  // worthwhile trying a further round.
  private boolean anotherRoundWorthwhile;

  // The index of the pass currently being applied.
  private int passIndex;

  public SystematicReductionPassManager(List<IReductionPass> initialPasses,
                                        List<IReductionPass> corePasses,
                                        List<IReductionPass> exhaustivePasses) {
    this.initialPasses = new ArrayList<>();
    this.initialPasses.addAll(initialPasses);
    this.corePasses = new ArrayList<>();
    this.corePasses.addAll(corePasses);
    this.exhaustivePasses = new ArrayList<>();
    this.exhaustivePasses.addAll(exhaustivePasses);
    this.anotherRoundWorthwhile = false;
    this.currentPasses = this.initialPasses;
    this.passIndex = 0;
  }

  @Override
  public Optional<ShaderJob> applyReduction(ShaderJob shaderJob) {
    while (true) {
      LOGGER.info("About to apply pass " + getCurrentPass().getName() + ": " + getCurrentPass());
      Optional<ShaderJob> maybeResult =
          getCurrentPass().tryApplyReduction(shaderJob);
      if (maybeResult.isPresent()) {
        LOGGER.info("Pass " + getCurrentPass().getName() + " made a reduction step.");
        return maybeResult;
      }
      // This pass did not have any impact, so move on to the next pass.
      LOGGER.info("Pass " + getCurrentPass().getName() + " did not make a reduction step.");
      passIndex++;
      if (passIndex < currentPasses.size()) {
        anotherRoundWorthwhile |= !getCurrentPass().reachedMinimumGranularity();
      } else if (anotherRoundWorthwhile) {
        startNewRound(currentPasses);
      } else if (currentPasses == initialPasses) {
        startNewRound(corePasses);
      } else if (currentPasses == corePasses) {
        startNewRound(exhaustivePasses);
      } else {
        return Optional.empty();
      }
    }
  }

  public void startNewRound(List<IReductionPass> passes) {
    passIndex = 0;
    anotherRoundWorthwhile = false;
    currentPasses = passes;
  }

  @Override
  public void notifyInteresting(boolean isInteresting) {
    getCurrentPass().notifyInteresting(isInteresting);
    if (isInteresting) {
      anotherRoundWorthwhile = true;
    }
  }

  private IReductionPass getCurrentPass() {
    return currentPasses.get(passIndex);
  }

}
