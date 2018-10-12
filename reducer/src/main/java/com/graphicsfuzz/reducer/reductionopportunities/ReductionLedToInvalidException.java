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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.transformreduce.ShaderJob;

public class ReductionLedToInvalidException extends RuntimeException {

  private final ShaderJob before;
  private final ShaderJob after;
  private final IReductionOpportunity reductionOpportunity;

  public ReductionLedToInvalidException(
      ShaderJob before,
      ShaderJob after,
      IReductionOpportunity reductionOpportunity) {
    this.before = before;
    this.after = after;
    this.reductionOpportunity = reductionOpportunity;
  }

  public ShaderJob getShaderJobBefore() {
    return before;
  }

  public ShaderJob getShaderJobAfter() {
    return after;
  }

  public IReductionOpportunity getReductionOpportunity() {
    return reductionOpportunity;
  }

}
