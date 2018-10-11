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

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class QualifiedType extends Type {

  private Type targetType;
  private List<TypeQualifier> qualifiers;

  public QualifiedType(Type targetType, List<TypeQualifier> qualifiers) {
    assert !(targetType instanceof QualifiedType);
    assert noDuplicateQualifiers(qualifiers);

    this.targetType = targetType;
    this.qualifiers = new ArrayList<>();
    this.qualifiers.addAll(qualifiers);
  }

  private boolean noDuplicateQualifiers(List<TypeQualifier> qualifiers) {
    Set<TypeQualifier> qualifierSet = new HashSet<>();
    qualifierSet.addAll(qualifiers);
    return qualifiers.size() == qualifierSet.size();
  }

  public List<TypeQualifier> getQualifiers() {
    return Collections.unmodifiableList(qualifiers);
  }

  public void removeQualifier(TypeQualifier qualifier) {
    if (!hasQualifier(qualifier)) {
      throw new UnsupportedOperationException("Attempt to remove absent qualifier " + qualifier);
    }
    qualifiers.remove(qualifier);
  }

  @Override
  public boolean hasQualifier(TypeQualifier qualifier) {
    return qualifiers.contains(qualifier);
  }

  public boolean hasQualifiers() {
    return !qualifiers.isEmpty();
  }

  public Type getTargetType() {
    return targetType;
  }

  public void setTargetType(Type targetType) {
    this.targetType = targetType;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitQualifiedType(this);
  }

  @Override
  public String toString() {
    String result = "";
    for (TypeQualifier q : getQualifiers()) {
      result += q + " ";
    }
    result += targetType;
    return result;
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof QualifiedType)) {
      return false;
    }
    QualifiedType thatQualifiedType = (QualifiedType) that;
    Set<TypeQualifier> thisQualifiers = this.qualifiers.stream().collect(Collectors.toSet());
    Set<TypeQualifier> thatQualifiers = thatQualifiedType.qualifiers.stream()
        .collect(Collectors.toSet());
    return thisQualifiers.equals(thatQualifiers) && this.targetType
        .equals(thatQualifiedType.targetType);
  }

  @Override
  public int hashCode() {
    // TODO revisit if we end up storing large sets of types
    Set<TypeQualifier> qualifiersSet = qualifiers.stream().collect(Collectors.toSet());
    return qualifiersSet.hashCode() + targetType.hashCode();
  }

  @Override
  public QualifiedType clone() {
    List<TypeQualifier> newQualifiers = new ArrayList<>();
    for (TypeQualifier q : qualifiers) {
      newQualifiers.add(q);
    }
    return new QualifiedType(targetType.clone(), newQualifiers);
  }

  @Override
  public boolean hasCanonicalConstant() {
    return targetType.hasCanonicalConstant();
  }

  @Override
  public Expr getCanonicalConstant() {
    return targetType.getCanonicalConstant();
  }

  @Override
  public Type getWithoutQualifiers() {
    return targetType.getWithoutQualifiers();
  }

}
