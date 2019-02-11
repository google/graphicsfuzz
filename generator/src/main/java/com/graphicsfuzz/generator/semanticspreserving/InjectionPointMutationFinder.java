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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.generator.mutateapi.MutationFinder;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

abstract class InjectionPointMutationFinder<MutationT extends Mutation>
    implements MutationFinder<MutationT> {

  private final TranslationUnit tu;
  private final IRandom random;
  private final Predicate<IInjectionPoint> isSuitableInjectionPoint;
  private final Function<IInjectionPoint, MutationT> mutateAtInjectionPoint;

  InjectionPointMutationFinder(
      TranslationUnit tu, IRandom random,
      Predicate<IInjectionPoint> isSuitableInjectionPoint,
      Function<IInjectionPoint, MutationT> mutateAtInjectionPoint) {
    this.tu = tu;
    this.random = random;
    this.isSuitableInjectionPoint = isSuitableInjectionPoint;
    this.mutateAtInjectionPoint = mutateAtInjectionPoint;
  }

  @Override
  public final List<MutationT> findMutations() {
    return new InjectionPoints(tu, random, isSuitableInjectionPoint)
        .getAllInjectionPoints()
        .stream()
        .map(mutateAtInjectionPoint)
        .collect(Collectors.toList());
  }

}
