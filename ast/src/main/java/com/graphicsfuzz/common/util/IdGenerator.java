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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class IdGenerator {

  private int nextFreeId;
  private final Set<Integer> initiallyUsedIds;

  /**
   * Creates an IdGenerator with no initially used ids.
   */
  public IdGenerator() {
    this(Collections.emptySet());
  }

  /**
   * Creates an IdGenerator, recording that a given set of ids has already been used.
   *
   * @param initiallyUsedIds A set of ids that have already been used and are thus unavailable.
   */
  public IdGenerator(Set<Integer> initiallyUsedIds) {
    this.initiallyUsedIds = new HashSet<>();
    this.initiallyUsedIds.addAll(initiallyUsedIds);
    this.nextFreeId = 0;
  }

  /**
   * Find the next id that (a) has not been returned by this IdGenerator, and (b) is not one of
   * the initially used ids that was specified when the IdGenerator was created.
   *
   * @return A fresh id.
   */
  public int freshId() {
    int result;
    do {
      result = nextFreeId;
      nextFreeId++;
    } while (initiallyUsedIds.contains(result));
    return result;
  }

}
