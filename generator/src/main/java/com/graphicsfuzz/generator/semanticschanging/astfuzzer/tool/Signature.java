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

package com.graphicsfuzz.generator.semanticschanging.astfuzzer.tool;

import com.graphicsfuzz.common.ast.type.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Signature {

  private final List<Type> parameterTypes;
  private final Type returnType;

  /**
   * Constructor.
   *
   * @param returnType ReturnType of the function.
   * @param args List containing the argument types.
   */
  public Signature(Type returnType, List<Type> args) {
    this.returnType = returnType;
    this.parameterTypes = new ArrayList<>();
    this.parameterTypes.addAll(args);
  }

  public Signature(Type returnType, Type... args) {
    this.returnType = returnType;
    this.parameterTypes = new ArrayList<>();
    this.parameterTypes.addAll(Arrays.asList(args));
  }

  /**
   * Similar to equals, but in this case null is considered to be equal to anything.
   *
   * @param thatSignature Signature to compare with.
   * @return True if the functionSignatures match, false otherwise.
   */
  boolean matches(Signature thatSignature) {

    if (!equalOrNull(returnType, thatSignature.returnType)) {
      return false;
    }

    if (parameterTypes.size() != thatSignature.parameterTypes.size()) {
      return false;
    }

    for (int i = 0; i < parameterTypes.size(); i++) {
      if (!equalOrNull(parameterTypes.get(i),
          thatSignature.parameterTypes.get(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean equalOrNull(Type thisType, Type thatType) {

    if (thisType == null || thatType == null
        || thisType.getWithoutQualifiers().equals(thatType.getWithoutQualifiers())) {
      return true;
    }
    return false;
  }

}
