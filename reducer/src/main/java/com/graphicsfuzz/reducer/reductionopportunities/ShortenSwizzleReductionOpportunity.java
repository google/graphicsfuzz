/*
 * Copyright 2022 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

/**
 * Shorten an intermediate swizzle in a chain of swizzle operations, e.g. simplify v.xyz.xx to
 * v.xy.xx. The reduction opportunity is not semantics-preserving.
 */
public class ShortenSwizzleReductionOpportunity extends AbstractReductionOpportunity {

  private final MemberLookupExpr parentSwizzle;
  private final MemberLookupExpr childSwizzle;
  private final int newChildLength;

  ShortenSwizzleReductionOpportunity(MemberLookupExpr parentSwizzle,
                                     MemberLookupExpr childSwizzle,
                                     int newChildLength,
                                     VisitationDepth depth) {
    super(depth);
    this.parentSwizzle = parentSwizzle;
    this.childSwizzle = childSwizzle;
    this.newChildLength = newChildLength;
  }

  @Override
  void applyReductionImpl() {
    childSwizzle.setMember(childSwizzle.getMember().substring(0, newChildLength));
  }

  @Override
  public boolean preconditionHolds() {
    if (!parentSwizzle.hasChild(childSwizzle)) {
      return false;
    }
    if (childSwizzle.getMember().length() <= newChildLength) {
      return false;
    }
    if (RemoveSwizzleReductionOpportunity.getLargestSwizzleComponent(parentSwizzle)
        >= newChildLength) {
      return false;
    }
    return true;
  }

}
