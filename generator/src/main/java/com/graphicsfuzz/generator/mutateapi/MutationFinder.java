/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.util.IRandom;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface MutationFinder<MutationT extends Mutation> {

  /**
   * Yields all mutations that were found.
   * @return All mutations.
   */
  List<MutationT> findMutations();

  /**
   * Yields a randomly-filtered list of available mutations.
   * @param probabilities Controls the probability with which a mutation is filtered.
   * @param generator Random number generator.
   * @return Randomly filtered list of mutations.
   */
  default List<MutationT> findMutations(Function<IRandom, Boolean> probabilities,
                                       IRandom generator) {
    return findMutations().stream().filter(item -> probabilities.apply(generator))
        .collect(Collectors.toList());
  }

}
