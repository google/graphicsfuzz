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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;

public class SplitForLoopMutationFinder extends InjectionPointMutationFinder<SplitForLoopMutation> {

  public SplitForLoopMutationFinder(TranslationUnit tu, IRandom random, IdGenerator idGenerator) {
    super(tu, random, SplitForLoopMutation::suitableForSplitting,
        item -> new SplitForLoopMutation(item, random, idGenerator));
  }

}
