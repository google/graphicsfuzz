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
import com.graphicsfuzz.reducer.ReductionDriver;
import com.graphicsfuzz.reducer.reductionopportunities.Compatibility;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunityFinder;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimplePlan implements IReductionPlan {

  private static final Logger LOGGER = LoggerFactory.getLogger(SimplePlan.class);

  private final ReducerContext reducerContext;
  private final boolean verbose;
  private final IReductionOpportunityFinder<?> opportunitiesFinder;

  private int percentageToReduce;
  private int replenishCount;
  private final List<Integer> history;

  SimplePlan(
        ReducerContext reducerContext,
        boolean verbose,
        IReductionOpportunityFinder<?> opportunitiesFinder) {
    this.reducerContext = reducerContext;
    this.verbose = verbose;
    this.opportunitiesFinder = opportunitiesFinder;
    this.percentageToReduce = reducerContext.getMaxPercentageToReduce();
    this.replenishCount = 0;
    this.history = new ArrayList<>();
  }

  @Override
  public void update(boolean interesting) {
    if (interesting) {
      history.clear();
    } else {
      percentageToReduce = Math.max(0,
            percentageToReduce - reducerContext.getAggressionDecreaseStep());
    }
  }

  @Override
  public ShaderJob applyReduction(ShaderJob shaderJob)
      throws NoMoreToReduceException {
    final ShaderJob workingShaderJob = shaderJob.clone();
    int localPercentageToReduce = percentageToReduce;
    while (true) {
      if (attemptToTransform(workingShaderJob, localPercentageToReduce)) {
        return workingShaderJob;
      }
      localPercentageToReduce /= 2;
      if (localPercentageToReduce > 0) {
        history.clear();
      } else {
        throw new NoMoreToReduceException();
      }
    }
  }

  private boolean attemptToTransform(ShaderJob shaderJob, int localPercentageToReduce) {
    LOGGER.info("Looking for opportunities of kind: " + opportunitiesFinder.getName());
    if (verbose) {
      LOGGER.info("Applying " + localPercentageToReduce + "% reduction");
    }

    final List<? extends IReductionOpportunity> initialReductionOpportunities =
          getSortedReductionOpportunities(shaderJob);

    // We don't want to initially take any of the opportunities that were previously ineffective
    // when we took them first.  So find out the initial opportunities that do apply - get their
    // list indices.
    List<Integer> initialOptionIndices =
          getPossibleInitialOpportunities(initialReductionOpportunities);

    assert !history.stream().anyMatch(item -> initialOptionIndices.contains(item));

    if (initialOptionIndices.isEmpty()) {
      return false;
    }

    // Estimate how many opportunities we want to take all together
    final int maxOpportunitiesToTake = getNumOpportunitiesToTake(initialReductionOpportunities,
          localPercentageToReduce);

    int taken = 0;

    while (taken < maxOpportunitiesToTake && !initialOptionIndices.isEmpty()) {

      // Take an opportunity
      final int opportunityIndex =
            skewedRandomElement(initialOptionIndices);

      assert !history.contains(opportunityIndex);

      Class<? extends IReductionOpportunity> classOfTakenOpportunity =
            initialReductionOpportunities
                  .get(opportunityIndex).getClass();

      initialReductionOpportunities
            .get(opportunityIndex).applyReduction();

      taken++;

      history.add(opportunityIndex);

      initialOptionIndices.remove((Object) opportunityIndex);
      // TODO: I fear we are not respecting transitivity here!
      for (int i = initialOptionIndices.size() - 1; i >= 0; i--) {
        if (!Compatibility.compatible(classOfTakenOpportunity,
              initialReductionOpportunities.get(initialOptionIndices.get(i)).getClass())) {
          initialOptionIndices.remove(i);
        }
      }

    }

    for (; taken < maxOpportunitiesToTake; taken++) {

      final List<? extends IReductionOpportunity> currentReductionOpportunities =
            opportunitiesFinder.findOpportunities(
                  shaderJob, reducerContext);
      if (currentReductionOpportunities.isEmpty()) {
        break;
      }

      final IReductionOpportunity nextReductionOpportunity = currentReductionOpportunities
            .get(reducerContext.getRandom()
                  .nextInt(currentReductionOpportunities.size()));

      if (ReductionDriver.DEBUG_REDUCER) {
        LOGGER.info("Next reduction opportunity: " + nextReductionOpportunity);
      }
      nextReductionOpportunity
            .applyReduction();
      taken++;
    }

    LOGGER.info("Took " + taken + " reduction opportunities.");

    return true;
  }

  private List<? extends IReductionOpportunity> getSortedReductionOpportunities(
        ShaderJob shaderJob) {
    // Get the available reduction opportunities.
    final List<? extends IReductionOpportunity> initialReductionOpportunities =
          opportunitiesFinder.findOpportunities(shaderJob, reducerContext);

    initialReductionOpportunities.sort((first, second) -> first.depth().compareTo(second.depth()));
    return initialReductionOpportunities;
  }

  private int getNumOpportunitiesToTake(
        List<? extends IReductionOpportunity> initialReductionOpportunities,
        int localPercentageToReduce) {
    return localPercentageToReduce == 0 ? 1 :
          Math.max(1, (int) Math.ceil((double) localPercentageToReduce
                * ((double) initialReductionOpportunities.size() / 100.0)));
  }

  private int skewedRandomElement(List<Integer> options) {
    final int maximum = options.size();
    final int index =
          (maximum - 1) - (int) Math.sqrt(Math.pow((double) reducerContext
                .getRandom().nextInt(maximum), 2.0));
    assert index >= 0;
    assert index < maximum;
    return options.get(index);
  }

  private List<Integer> getPossibleInitialOpportunities(
        List<? extends IReductionOpportunity> initialReductionOpportunities) {
    List<Integer> result = new ArrayList<>();
    for (int i = 0; i < initialReductionOpportunities.size(); i++) {
      if (history.contains(i)) {
        continue;
      }
      result.add(i);
    }
    return result;
  }

  @Override
  public void replenish() {
    replenishCount++;
    percentageToReduce = reducerContext.getMaxPercentageToReduce();
    for (int i = 0; i < replenishCount; i++) {
      percentageToReduce /= 2;
    }
    percentageToReduce = Math.max(percentageToReduce, 1);
  }

}
