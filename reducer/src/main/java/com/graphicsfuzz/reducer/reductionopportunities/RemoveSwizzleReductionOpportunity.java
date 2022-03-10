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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.typing.Typer;

/**
 * Remove a needless swizzle in a chain of swizzles. For instance, simplify v.xx.xy to v.xy. The
 * reduction opportunity is not semantics-preserving.
 */
public class RemoveSwizzleReductionOpportunity extends AbstractReductionOpportunity {

  private final IAstNode parent;
  private final Expr newChild;
  private final Expr originalChild;

  // This captures type information about the translation unit when the opportunity was created.
  private final Typer typer;

  RemoveSwizzleReductionOpportunity(IAstNode parent, Expr newChild, Expr originalChild,
                                    Typer typer,
                                    VisitationDepth depth) {
    super(depth);
    this.parent = parent;
    this.newChild = newChild;
    this.originalChild = originalChild;
    this.typer = typer;
  }

  @Override
  void applyReductionImpl() {
    parent.replaceChild(originalChild, newChild);
  }

  @Override
  public boolean preconditionHolds() {
    if (!parent.hasChild(originalChild)) {
      return false;
    }
    if (!(originalChild instanceof MemberLookupExpr)) {
      return false;
    }
    assert typer.lookupType(originalChild) instanceof BasicType;
    if (typer.lookupType(originalChild) == typer.lookupType(newChild)) {
      return true;
    }
    if (!isSwizzle(parent, typer)) {
      return false;
    }
    final Type newChildType = typer.lookupType(newChild).getWithoutQualifiers();
    if (!(newChildType instanceof BasicType)) {
      return false;
    }
    final BasicType newChildBasicType = (BasicType) newChildType;
    if (!BasicType.allVectorTypes().contains(newChildBasicType)
        && !BasicType.allScalarTypes().contains(newChildBasicType)) {
      return false;
    }
    final int largestSwizzleComponent = getLargestSwizzleComponent(parent);
    if (largestSwizzleComponent >= newChildBasicType.getNumElements()) {
      return false;
    }
    return true;
  }

  private static int swizzleCharacterToInt(char swizzleChar) {
    switch (swizzleChar) {
      case 'x':
      case 'r':
      case 's':
        return 0;
      case 'y':
      case 'g':
      case 't':
        return 1;
      case 'z':
      case 'b':
      case 'p':
        return 2;
      case 'w':
      case 'a':
      case 'q':
        return 3;
      default:
        throw new IllegalArgumentException("Unknown swizzle character " + swizzleChar);
    }
  }

  static int getLargestSwizzleComponent(IAstNode node) {
    assert node instanceof MemberLookupExpr;
    final String swizzleCharacters = ((MemberLookupExpr) node).getMember();
    int result = swizzleCharacterToInt(swizzleCharacters.charAt(0));
    for (int i = 1; i < swizzleCharacters.length(); i++) {
      int candidate = swizzleCharacterToInt(swizzleCharacters.charAt(i));
      if (candidate > result) {
        result = candidate;
      }
    }
    return result;
  }

  static boolean isSwizzle(IAstNode node, Typer typer) {
    if (!(node instanceof MemberLookupExpr)) {
      return false;
    }
    final MemberLookupExpr memberLookupExpr = (MemberLookupExpr) node;
    final Type structureType =
        typer.lookupType(memberLookupExpr.getStructure()).getWithoutQualifiers();
    if (!(structureType instanceof BasicType)) {
      return false;
    }
    final BasicType basicType = (BasicType) structureType;
    if (!BasicType.allVectorTypes().contains(basicType)
        && !BasicType.allScalarTypes().contains(basicType)) {
      return false;
    }
    // A member lookup on a vector or scalar type has to be a swizzle. We assume that the shader is
    // well-formed, so there is no need to go and check that the letters used in the swizzle are
    // valid.
    return true;
  }

}
