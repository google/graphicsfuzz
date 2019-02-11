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

package com.graphicsfuzz.generator.transformation;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.semanticspreserving.IdentityMutation;
import com.graphicsfuzz.generator.semanticspreserving.IdentityMutationFinder;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.List;

public class IdentityTransformation implements ITransformation {

  public static final String NAME = "identity";

  @Override
  public boolean apply(TranslationUnit tu,
                       TransformationProbabilities probabilities,
                       IRandom generator,
                       GenerationParams generationParams) {
    List<IdentityMutation> mutationPoints = new IdentityMutationFinder(
        tu, generator, generationParams).findMutations(probabilities::mutatePoint, generator);
    mutationPoints.forEach(IdentityMutation::apply);
    return !mutationPoints.isEmpty();
  }

  @Override
  public String getName() {
    return NAME;
  }

}
