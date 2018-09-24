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

import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.type.Type;
import java.util.Optional;

class StructifiedVariableInfo {

  private final String name;
  private final Type type;
  private final Optional<ScalarInitializer> initializer;

  StructifiedVariableInfo(String name, Type type, Optional<ScalarInitializer> initializer) {
    this.name = name;
    this.type = type;
    this.initializer = initializer;
  }

  public String getName() {
    return name;
  }

  public Type getType() {
    return type;
  }

  public Optional<ScalarInitializer> getInitializer() {
    return initializer;
  }
}
