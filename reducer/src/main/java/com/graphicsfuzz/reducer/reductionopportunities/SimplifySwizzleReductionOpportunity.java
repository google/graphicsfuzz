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
 * Change a swizzle operation so that it uses "lower" components, e.g. simplifying v.xy to v.xx. The
 * reduction opportunity is not semantics-preserving.
 */
public class SimplifySwizzleReductionOpportunity extends AbstractReductionOpportunity {

  final MemberLookupExpr swizzle;
  final int component;
  final char newComponentValue;
  final boolean swizzleIsLvalue;

  SimplifySwizzleReductionOpportunity(MemberLookupExpr swizzle,
                                      int component, char newComponentValue,
                                      boolean swizzleIsLvalue,
                                      VisitationDepth depth) {
    super(depth);
    this.swizzle = swizzle;
    this.component = component;
    this.newComponentValue = newComponentValue;
    this.swizzleIsLvalue = swizzleIsLvalue;
  }

  @Override
  void applyReductionImpl() {
    final StringBuilder newSwizzleComponents = new StringBuilder();
    for (int i = 0; i < swizzle.getMember().length(); i++) {
      if (i == component) {
        newSwizzleComponents.append(newComponentValue);
      } else {
        newSwizzleComponents.append(swizzle.getMember().charAt(i));
      }
    }
    swizzle.setMember(newSwizzleComponents.toString());
  }

  @Override
  public boolean preconditionHolds() {
    if (swizzle.getMember().length() <= component) {
      return false;
    }

    if (swizzleIsLvalue) {
      for (int i = 0; i < swizzle.getMember().length(); i++) {
        if (i == component) {
          continue;
        }
        if (newComponentValue == swizzle.getMember().charAt(i)) {
          // Changing the component to the new value would lead to duplicated components, which is
          // not allowed in an l-value swizzle.
          return false;
        }
      }
    }

    final char existingComponentValue = swizzle.getMember().charAt(component);
    switch (newComponentValue) {
      case 'x':
        return existingComponentValue == 'y' || existingComponentValue == 'z'
            || existingComponentValue == 'w';
      case 'y':
        return existingComponentValue == 'z' || existingComponentValue == 'w';
      case 'z':
        return existingComponentValue == 'w';
      case 'r':
        return existingComponentValue == 'g' || existingComponentValue == 'b'
            || existingComponentValue == 'a';
      case 'g':
        return existingComponentValue == 'b' || existingComponentValue == 'a';
      case 'b':
        return existingComponentValue == 'a';
      case 's':
        return existingComponentValue == 't' || existingComponentValue == 'p'
            || existingComponentValue == 'q';
      case 't':
        return existingComponentValue == 'p' || existingComponentValue == 'q';
      case 'p':
        return existingComponentValue == 'q';
      default:
        throw new IllegalArgumentException("Badd swizzle simplification.");
    }
  }

}
