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

package com.graphicsfuzz.common.ast.visitors;

/**
 * Class to represent visitation depth, which is right now just an integer.  Using a class to
 * (a) make sure we do pass what is specifically a visitation depth, not some other integer;
 * (b) future-proof so that we can enrich the notion of depth (e.g., to include details of
 * parents in the AST) if needed.
 */
public class VisitationDepth implements Comparable<VisitationDepth> {

  private final Integer depth;

  public VisitationDepth(int depth) {
    this.depth = depth;
  }

  public VisitationDepth deeper() {
    return new VisitationDepth(depth + 1);
  }

  @Override
  public int compareTo(VisitationDepth other) {
    return depth.compareTo(other.depth);
  }
}
