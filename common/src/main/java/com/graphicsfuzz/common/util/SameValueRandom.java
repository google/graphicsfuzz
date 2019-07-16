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

public class SameValueRandom implements IRandom {

  private final boolean boolValue;
  private final int intValue;

  public SameValueRandom(boolean boolValue, int intValue) {
    this.boolValue = boolValue;
    this.intValue = intValue;
  }

  @Override
  public int nextInt(int bound) {
    assert intValue < bound;
    return intValue;
  }

  @Override
  public Float nextFloat() {
    throw new RuntimeException("Not yet supported.");
  }

  @Override
  public boolean nextBoolean() {
    return boolValue;
  }

  @Override
  public IRandom spawnChild() {
    throw new UnsupportedOperationException("Child spawning not available");
  }

  @Override
  public String getDescription() {
    return "SameValueRandom: " + intValue + " " + boolValue;
  }
}
