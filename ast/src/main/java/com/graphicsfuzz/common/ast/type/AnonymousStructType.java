package com.graphicsfuzz.common.ast.type;

import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public final class AnonymousStructType extends StructType {

  private final int counter; // We count anonymous structs and give each one a distinct number.

  public AnonymousStructType(int counter) {
    this.counter = counter;
  }

  @Override
  public String getName() {
    return "_anonStruct_" + counter;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitAnonymousStructNameType(this);
  }

  @Override
  public AnonymousStructType clone() {
    return new AnonymousStructType(counter);
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof AnonymousStructType)) {
      return false;
    }
    return counter == ((AnonymousStructType) that).counter;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(counter);
  }

}
