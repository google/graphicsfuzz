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

/**
 * An interface to enable mocking of java.util.Random during testing.
 * Methods of java.util.Random should be added on demand.
 */
public interface IRandom {

  int nextInt(int bound);

  default int nextInt(int origin, int bound) {
    return (int) (nextLong(((long)bound - (long) origin)) + (long) origin);
  }

  long nextLong(long bound);

  Float nextFloat();

  default int nextPositiveInt(int bound) {
    return nextInt(bound - 1) + 1;
  }


  boolean nextBoolean();

  /**
   * Spawn a new generator, seeded using the current generator.  This is useful if we wish to
   * separate the effects of generation between different stages of an overall generation
   * process.
   * @return A generator
   */
  IRandom spawnChild();


  /**
   * @return A description of the random number generator, ideally including the seed.
   */
  String getDescription();

}
