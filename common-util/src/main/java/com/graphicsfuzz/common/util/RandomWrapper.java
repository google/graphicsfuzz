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

import java.util.Random;

/**
 * Random generator that uses java.util.Random, to be used when genuine pseudo-random generation
 * is required (as opposed to mocking for testing).
 */
public class RandomWrapper implements IRandom {

  private final Random generator;

  public RandomWrapper(int seed) {
    this.generator = new Random(seed);
  }

  public RandomWrapper() {
    this.generator = new Random();
  }

  @Override
  public int nextInt(int bound) {
    return generator.nextInt(bound);
  }

  @Override
  public Float nextFloat() {
    return generator.nextFloat();
  }

  @Override
  public boolean nextBoolean() {
    return generator.nextBoolean();
  }

  @Override
  public IRandom spawnChild() {
    return new RandomWrapper(generator.nextInt());
  }

  public void setSeed(int seed) {
    generator.setSeed(seed);
  }

}
