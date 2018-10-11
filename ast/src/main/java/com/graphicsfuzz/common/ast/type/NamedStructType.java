package com.graphicsfuzz.common.ast.type;

import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public final class NamedStructType extends StructType {

  private String name;

  public NamedStructType(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitConcreteStructNameType(this);
  }

  @Override
  public NamedStructType clone() {
    return new NamedStructType(name);
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof NamedStructType)) {
      return false;
    }
    return name.equals(((NamedStructType) that).name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

}
