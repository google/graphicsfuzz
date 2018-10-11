package com.graphicsfuzz.common.ast.type;

import com.graphicsfuzz.common.ast.expr.Expr;

public abstract class StructType extends UnqualifiedType {

  @Override
  public final boolean hasCanonicalConstant() {
    return false;
  }

  @Override
  public final Expr getCanonicalConstant() {
    throw new UnsupportedOperationException("Canonical constants not yet supported for structs.");
  }

  @Override
  public abstract StructType clone();

  public abstract String getName();

}
