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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.util.PipelineInfo;

public class RemoveRedundantUniformMetadataReductionOpportunity
    extends AbstractReductionOpportunity {

  private final PipelineInfo pipelineInfo;
  private final String uniformName;

  RemoveRedundantUniformMetadataReductionOpportunity(String uniformName,
                                                     PipelineInfo pipelineInfo,
                                                     VisitationDepth depth) {
    super(depth);
    this.pipelineInfo = pipelineInfo;
    this.uniformName = uniformName;
  }

  @Override
  void applyReductionImpl() {
    pipelineInfo.removeUniform(uniformName);
  }

  @Override
  public boolean preconditionHolds() {
    return pipelineInfo.getUniformNames().contains(uniformName);
  }

}
