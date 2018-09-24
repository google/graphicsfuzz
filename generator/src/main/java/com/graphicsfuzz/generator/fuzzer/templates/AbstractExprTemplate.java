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

package com.graphicsfuzz.generator.fuzzer.templates;

import com.graphicsfuzz.common.ast.type.Type;
import java.util.List;

abstract class AbstractExprTemplate implements IExprTemplate {

  protected abstract String getTemplateName();

  @Override
  public final String toString() {
    String result = getTemplateName() + ":(";
    boolean firstArg = true;
    for (List<? extends Type> ts : getArgumentTypes()) {
      if (!firstArg) {
        result += ",";
      }
      firstArg = false;
      result += "{";
      boolean firstType = true;
      for (Type t : ts) {
        if (!firstType) {
          result += ",";
        }
        firstType = false;
        result += t;
      }
      result += "}";
    }
    return result + ")->" + getResultType();
  }

}
