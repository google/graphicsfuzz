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

package com.graphicsfuzz.generator.transformation.injection;

import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.typing.Scope;

abstract class InjectionPoint implements IInjectionPoint {

  private final FunctionDefinition enclosingFunction;
  private final boolean inLoop;
  private final boolean inSwitch;
  private final Scope scope;

  InjectionPoint(FunctionDefinition enclosingFunction, boolean inLoop,
      boolean inSwitch,
      Scope scope) {
    this.enclosingFunction = enclosingFunction;
    this.inLoop = inLoop;
    this.inSwitch = inSwitch;
    this.scope = scope.shallowClone();
  }

  @Override
  public final FunctionDefinition getEnclosingFunction() {
    return enclosingFunction;
  }

  @Override
  public final boolean inLoop() {
    return inLoop;
  }

  @Override
  public final boolean inSwitch() {
    return inSwitch;
  }

  @Override
  public final Scope scopeAtInjectionPoint() {
    return scope;
  }

}
