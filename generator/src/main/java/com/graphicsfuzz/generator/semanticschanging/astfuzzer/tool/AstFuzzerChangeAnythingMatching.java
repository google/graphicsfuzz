/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.semanticschanging.astfuzzer.tool;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.semanticschanging.astfuzzer.util.ExprInterchanger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AstFuzzerChangeAnythingMatching extends AstFuzzer {

  private Map<Expr, Expr> mapOfReplacements;

  public AstFuzzerChangeAnythingMatching(ShadingLanguageVersion shadingLanguageVersion,
      IRandom random) {
    super(shadingLanguageVersion, random);
  }

  @Override
  public TranslationUnit generateNewShader(TranslationUnit tu) {

    TranslationUnit result = tu.clone();
    traverseTreeAndMakeMappings(result);

    new StandardVisitor() {

      @Override
      protected <T extends IAstNode> void visitChildFromParent(Consumer<T> visitorMethod,
            T child,
            IAstNode parent) {
        super.visitChildFromParent(visitorMethod, child, parent);
        if (mapOfReplacements.containsKey(child)) {
          //some IAstNodes don't allow replaceChild
          try {
            parent.replaceChild(child, mapOfReplacements.get(child));
          } catch (Exception exception) {
            System.err.println(exception.getMessage());
          }
        }
        ;
      }

    }.visit(result);
    return result;

  }

  private void traverseTreeAndMakeMappings(TranslationUnit tu) {

    Typer typer = new Typer(tu, getShadingLanguageVersion());
    mapOfReplacements = new HashMap<>();

    new StandardVisitor() {

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        fuzzExpr(functionCallExpr, typer);

      }

      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        if (!binaryExpr.getOp().isSideEffecting()) {
          fuzzExpr(binaryExpr, typer);
        }
      }

      @Override
      public void visitTernaryExpr(TernaryExpr ternaryExpr) {
        super.visitTernaryExpr(ternaryExpr);
        fuzzExpr(ternaryExpr, typer);
      }

      @Override
      public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
        super.visitTypeConstructorExpr(typeConstructorExpr);
        fuzzExpr(typeConstructorExpr, typer);
      }
    }.visit(tu);

  }

  private void fuzzExpr(Expr expr, Typer typer) {

    if (getRandom().nextBoolean()) {
      Expr replacement = findReplacement(expr, typer);
      mapOfReplacements.put(expr, replacement);
    }
  }

  private Expr findReplacement(Expr expr, Typer typer) {

    Type returnType = typer.lookupType(expr);
    List<Expr> args = new ArrayList<>();
    for (int i = 0; i < expr.getNumChildren(); i++) {
      args.add(expr.getChild(i));

    }
    Signature signature = new Signature(returnType,
          args.stream().map(x -> typer.lookupType(x)).collect(Collectors.toList()));

    List<ExprInterchanger> matches = getFunctionLists()
          .getInterchangeableForSignature(signature);
    if (!matches.isEmpty()) {
      int randomIndex = getRandom().nextInt(matches.size());
      return matches.get(randomIndex).interchangeExpr(args);
    }
    return expr;
  }
}
