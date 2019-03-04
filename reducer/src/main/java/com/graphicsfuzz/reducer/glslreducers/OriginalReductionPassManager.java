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
import com.graphicsfuzz.reducer.reductionopportunities.FailedReductionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OriginalReductionPassManager implements IReductionPassManager {

  private static final int MAX_STEPS_PER_PASS = 200;

  private final List<IReductionPass> passes;
  private int passIndex;
  private int currentPassSteps;
  private boolean somePassMadeProgress;

  public OriginalReductionPassManager(List<IReductionPass> passes) {
    this.passes = new ArrayList<>();
    this.passes.addAll(passes);
    this.passIndex = 0;
    this.currentPassSteps = 0;
    this.somePassMadeProgress = false;
  }

  @Override
  public Optional<ShaderJob> applyReduction(ShaderJob state) {
    final int maxAttempts = 3;
    int attempts = 0;
    while (true) {
      try {
        return tryApplyReduction(state);
      } catch (FailedReductionException exception) {
        attempts++;
        if (attempts == maxAttempts) {
          throw exception;
        }
      }
    }
  }

  private Optional<ShaderJob> tryApplyReduction(ShaderJob state) {
    while (true) {
      if (currentPassSteps < MAX_STEPS_PER_PASS) {
        // Try the current pass.
        final Optional<ShaderJob> maybeResult =
            getCurrentPass().tryApplyReduction(state);
        if (maybeResult.isPresent()) {
          currentPassSteps++;
          return maybeResult;
        }
        // The current pass failed.  Replenish it, in case it is needed again later, and
        // move on to the next pass.
        getCurrentPass().replenish();
      }

      passIndex++;
      currentPassSteps = 0;

      if (passIndex == passes.size()) {
        // We've done all the passes.
        if (!somePassMadeProgress) {
          // No pass made progress; we have reached a fixed-point for this shader kind.
          return Optional.empty();
        } else {
          passIndex = 0;
          somePassMadeProgress = false;
        }
      }
      // Having moved on to the next reduction pass, try to transform
      // again.
    }
  }

  @Override
  public void notifyInteresting(boolean isInteresting) {
    if (isInteresting) {
      somePassMadeProgress = true;
    }
    getCurrentPass().notifyInteresting(isInteresting);
  }

  private IReductionPass getCurrentPass() {
    return passes.get(passIndex);
  }

}
