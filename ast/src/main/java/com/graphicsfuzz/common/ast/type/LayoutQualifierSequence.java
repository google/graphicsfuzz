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

package com.graphicsfuzz.common.ast.type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LayoutQualifierSequence extends TypeQualifier {

  private final List<LayoutQualifier> contents;

  public LayoutQualifierSequence(List<LayoutQualifier> contents) {
    super("layout");
    this.contents = new ArrayList<>();
    this.contents.addAll(contents);
  }

  public LayoutQualifierSequence(LayoutQualifier... contents) {
    this(Arrays.asList(contents));
  }

  public List<LayoutQualifier> getLayoutQualifiers() {
    return Collections.unmodifiableList(contents);
  }

  @Override
  public String toString() {
    return super.toString() + "("
        + contents
        .stream()
        .map(LayoutQualifier::toString)
        .reduce((item1, item2) -> (item1 + ", " + item2))
        .orElse("") + ")";
  }

}
