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

package com.graphicsfuzz.generator.transformation.controlflow;

import com.graphicsfuzz.common.ast.expr.BinOp;

public class LoopSplitInfo {

  private final String loopCounter;
  private final int startValue;
  private final int endValue;
  private final boolean increasing;
  private final BinOp comparison;
  private final boolean variableBeforeLiteral;

  public LoopSplitInfo(String loopCounter, int startValue, int endValue,
      boolean increasing, BinOp comparison, boolean variableBeforeLiteral) {
    this.loopCounter = loopCounter;
    this.startValue = startValue;
    this.endValue = endValue;
    this.increasing = increasing;
    this.comparison = comparison;
    this.variableBeforeLiteral = variableBeforeLiteral;
  }

  public String getLoopCounter() {
    return loopCounter;
  }

  public int getStartValue() {
    return startValue;
  }

  public int getEndValue() {
    return endValue;
  }

  public boolean getIncreasing() {
    return increasing;
  }
}
