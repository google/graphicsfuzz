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

package com.graphicsfuzz.common.ast.expr;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import java.util.Collections;

public class MemberLookupExpr extends Expr {

  private Expr structure;
  private String member;

  public MemberLookupExpr(Expr structure, String member) {
    setStructure(structure);
    this.member = member;
  }

  public Expr getStructure() {
    return structure;
  }

  public void setStructure(Expr structure) {
    checkNoTopLevelCommaExpression(Collections.singletonList(structure));
    if (structure == null) {
      throw new IllegalArgumentException("Member lookup expression cannot have null structure");
    }
    this.structure = structure;
  }

  public String getMember() {
    return member;
  }

  public void setMember(String member) {
    this.member = member;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitMemberLookupExpr(this);
  }

  @Override
  public MemberLookupExpr clone() {
    return new MemberLookupExpr(structure.clone(), member);
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return structure == candidateChild;
  }

  @Override
  public Expr getChild(int index) {
    if (index == 0) {
      return structure;
    }
    throw new IndexOutOfBoundsException("Index for MemberLookupExpr must be 0");
  }

  @Override
  public void setChild(int index, Expr expr) {
    if (index == 0) {
      setStructure(expr);
      return;
    }
    throw new IndexOutOfBoundsException("Index for MemberLookupExpr must be 0");
  }

  @Override
  public int getNumChildren() {
    return 1;
  }

}
