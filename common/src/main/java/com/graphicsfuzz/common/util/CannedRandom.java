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

import java.util.Arrays;
import java.util.Iterator;

/**
 * Mock implemenation of IRandom that uses given sequences of values.
 */
public class CannedRandom implements IRandom {

  private final Iterator<Object> items;

  /**
   * Constructs a canned random instance that will iterate over the given lists of integers
   * and booleans.
   *
   * @param intsAndBools Ordered sequence of integers, long and booleans
   */
  public CannedRandom(Object... intsAndBools) {
    if (Arrays.stream(intsAndBools).anyMatch(item -> !(item instanceof Integer
        || item instanceof Boolean || item instanceof Long))) {
      throw new IllegalArgumentException("Only Integer, Long and Boolean items allowed.");
    }
    this.items = Arrays.asList(intsAndBools).iterator();
  }

  @Override
  public int nextInt(int bound) {
    Object next = items.next();
    if (!(next instanceof Integer)) {
      throw new UnsupportedOperationException("nextInt failed because next item was a "
          + next.getClass() + "(" + next + ")");
    }
    return Math.abs((Integer)next) % bound;
  }

  @Override
  public int nextInt(int origin, int bound) {
    return nextInt(bound);
  }

  @Override
  public long nextLong(long bound) {
    Object next = items.next();
    if (!(next instanceof Long)) {
      throw new UnsupportedOperationException("nextLong failed because next item was a "
         + next.getClass() + "(" + next + ")");
    }
    return (Long) next;
  }

  @Override
  public Float nextFloat() {
    throw new RuntimeException("Not yet supported.");
  }

  @Override
  public boolean nextBoolean() {
    Object next = items.next();
    if (!(next instanceof Boolean)) {
      throw new UnsupportedOperationException("nextBool failed because next item was a "
          + next.getClass() + "(" + next + ")");
    }
    return (Boolean) next;
  }

  @Override
  public IRandom spawnChild() {
    return this;
  }

  @Override
  public String getDescription() {
    return "CannedRandom";
  }

  public boolean isExhausted() {
    return !items.hasNext();
  }

}
