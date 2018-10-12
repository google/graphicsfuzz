package com.graphicsfuzz.common.ast.type;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public final class StructNameType extends UnqualifiedType {

  private String name;

  public StructNameType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitStructNameType(this);
  }

  @Override
  public StructNameType clone() {
    return new StructNameType(name);
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof StructNameType)) {
      return false;
    }
    return name.equals(((StructNameType) that).name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public final boolean hasCanonicalConstant() {
    return false;
  }

  @Override
  public final Expr getCanonicalConstant() {
    throw new UnsupportedOperationException("Canonical constants not yet supported for structs.");
  }

}
