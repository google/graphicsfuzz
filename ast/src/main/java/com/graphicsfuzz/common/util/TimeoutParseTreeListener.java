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

package com.graphicsfuzz.common.util;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

public class TimeoutParseTreeListener implements ParseTreeListener {

  private final long timeToStop;

  public TimeoutParseTreeListener(long timeToStop) {
    this.timeToStop = timeToStop;
  }

  private void checkTime() {
    if (System.currentTimeMillis() > timeToStop) {
      throw new ParseTimeoutRuntimeException();
    }
  }

  @Override
  public void visitTerminal(TerminalNode terminalNode) {
    checkTime();
  }

  @Override
  public void visitErrorNode(ErrorNode errorNode) {
    checkTime();
  }

  @Override
  public void enterEveryRule(ParserRuleContext parserRuleContext) {
    checkTime();
  }

  @Override
  public void exitEveryRule(ParserRuleContext parserRuleContext) {
    checkTime();
  }
}
