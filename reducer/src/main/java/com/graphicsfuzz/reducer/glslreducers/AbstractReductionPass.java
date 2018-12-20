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

package com.graphicsfuzz.reducer.glslreducers;

import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunityFinder;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;

public abstract class AbstractReductionPass implements IReductionPass {

  private final ReducerContext reducerContext;
  private final IReductionOpportunityFinder<? extends IReductionOpportunity> finder;

  AbstractReductionPass(ReducerContext reducerContext,
                        IReductionOpportunityFinder<? extends IReductionOpportunity> finder) {
    this.reducerContext = reducerContext;
    this.finder = finder;
  }

  @Override
  public final String getName() {
    return finder.getName();
  }

  protected final IReductionOpportunityFinder<? extends IReductionOpportunity> getFinder() {
    return finder;
  }

  protected final ReducerContext getReducerContext() {
    return reducerContext;
  }

}
