/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.tool.UniformValueSupplier;
import java.util.List;
import java.util.Optional;

public class PipelineUniformValueSupplier implements UniformValueSupplier {

  private final PipelineInfo pipelineInfo;

  public PipelineUniformValueSupplier(PipelineInfo pipelineInfo) {
    this.pipelineInfo = pipelineInfo;
  }

  @Override
  public Optional<List<String>> getValues(String name) {
    if (pipelineInfo.hasUniform(name)) {
      return Optional.of(this.pipelineInfo.getArgs(name));
    }
    return Optional.empty();
  }
}
