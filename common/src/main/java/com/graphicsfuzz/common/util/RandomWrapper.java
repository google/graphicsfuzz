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

package com.graphicsfuzz.common.util;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;

/**
 * Random generator to be used when genuine pseudo-random generation is required (as opposed to
 * mocking for testing).
 */
public class RandomWrapper implements IRandom {

  private final long seed;
  private final UniformRandomProvider provider;

  public RandomWrapper(long seed) {
    this.seed = seed;
    this.provider = RandomSource.create(RandomSource.ISAAC, seed);
  }

  @Override
  public int nextInt(int bound) {
    return provider.nextInt(bound);
  }

  @Override
  public Float nextFloat() {
    return provider.nextFloat();
  }

  @Override
  public long nextLong(long bound) {
    return provider.nextLong(bound);
  }

  @Override
  public boolean nextBoolean() {
    return provider.nextBoolean();
  }

  @Override
  public IRandom spawnChild() {
    return new RandomWrapper(provider.nextLong());
  }

  @Override
  public String getDescription() {
    return "RandomWrapper with seed: " + Long.toUnsignedString(seed);
  }

}
