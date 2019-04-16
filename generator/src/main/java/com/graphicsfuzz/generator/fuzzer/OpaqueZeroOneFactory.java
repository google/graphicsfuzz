package com.graphicsfuzz.generator.fuzzer;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.type.BasicType;
import java.util.Optional;

@FunctionalInterface
public interface OpaqueZeroOneFactory {
  Optional<Expr> tryMakeOpaque(BasicType type, boolean constContext, final int depth,
                               Fuzzer fuzzer, boolean isZero);
}
