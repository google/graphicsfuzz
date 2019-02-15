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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.type.BasicType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SsboFieldData {

  private final BasicType baseType;
  private final List<Number> data;

  public SsboFieldData(BasicType baseType, List<? extends Number> data) {
    assert !data.isEmpty();
    assert (baseType.getElementType() == baseType.FLOAT && data.get(0) instanceof Float)
        || (baseType.getElementType() == baseType.INT && data.get(0) instanceof Integer);
    this.baseType = baseType;
    this.data = new ArrayList<>();
    this.data.addAll(data);
  }

  public BasicType getBaseType() {
    return baseType;
  }

  public List<Number> getData() {
    return Collections.unmodifiableList(data);
  }

}
