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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;

public class ReducerContext {

  static final int DEFAULT_MAX_PERCENTAGE_TO_REDUCE = 50;
  static final int DEFAULT_AGGRESSION_DECREASE_STEP = 5;

  private final boolean reduceEverywhere;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final IRandom random;
  private final IdGenerator idGenerator;
  private final int maxPercentageToReduce;
  private final int aggressionDecreaseStep;
  private final boolean emitGraphicsFuzzDefines;

  public ReducerContext(boolean reduceEverywhere,
                        ShadingLanguageVersion shadingLanguageVersion,
                        IRandom random, IdGenerator idGenerator, int maxPercentageToReduce,
                        int aggressionDecreaseStep, boolean emitGraphicsFuzzDefines) {
    this.reduceEverywhere = reduceEverywhere;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.random = random;
    this.idGenerator = idGenerator;
    this.maxPercentageToReduce = maxPercentageToReduce;
    this.aggressionDecreaseStep = aggressionDecreaseStep;
    this.emitGraphicsFuzzDefines = emitGraphicsFuzzDefines;
  }

  public ReducerContext(boolean reduceEverywhere,
                        ShadingLanguageVersion shadingLanguageVersion,
                        IRandom random, IdGenerator idGenerator, boolean emitGraphicsFuzzDefines) {
    this(reduceEverywhere, shadingLanguageVersion, random, idGenerator,
        DEFAULT_MAX_PERCENTAGE_TO_REDUCE, DEFAULT_AGGRESSION_DECREASE_STEP,
        emitGraphicsFuzzDefines);
  }

  public boolean reduceEverywhere() {
    return reduceEverywhere;
  }

  public ShadingLanguageVersion getShadingLanguageVersion() {
    assert shadingLanguageVersion != null;
    return shadingLanguageVersion;
  }

  public IRandom getRandom() {
    assert random != null;
    return random;
  }

  public IdGenerator getIdGenerator() {
    assert idGenerator != null;
    return idGenerator;
  }

  public int getMaxPercentageToReduce() {
    return maxPercentageToReduce;
  }

  public int getAggressionDecreaseStep() {
    return aggressionDecreaseStep;
  }

  public boolean getEmitGraphicsFuzzDefines() {
    return emitGraphicsFuzzDefines;
  }

}
