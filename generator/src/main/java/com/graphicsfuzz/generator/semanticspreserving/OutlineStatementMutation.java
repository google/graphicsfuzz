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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OutlineStatementMutation implements Mutation {
  
  private final ExprStmt toOutline;
  private final Scope scopeOfStmt;
  private final TranslationUnit tu; // Insert new stuff into the tu...
  private final FunctionDefinition enclosingFunction; // ...right before the enclosing function
  private final IdGenerator idGenerator;

  public OutlineStatementMutation(ExprStmt toOutline, Scope scopeOfStmt, TranslationUnit tu,
                                  FunctionDefinition enclosingFunction,
                                  IdGenerator idGenerator) {
    this.scopeOfStmt = scopeOfStmt;
    this.toOutline = toOutline;
    this.tu = tu;
    this.enclosingFunction = enclosingFunction;
    this.idGenerator = idGenerator;

    if (!isAssignment(toOutline)) {
      throw new IllegalArgumentException("Can only outline an assignment statement");
    }
    if (!assignsDirectlyToVariable((BinaryExpr) toOutline.getExpr())) {
      throw new IllegalArgumentException("At present, can only outline an assignment directly to "
          + "a variable");
    }
    if (referencesArray(((BinaryExpr) toOutline.getExpr()).getRhs())) {
      throw new IllegalArgumentException("At present, we cannot handle arrays on the RHS of an "
          + "outlined expression");
    }

  }

  private boolean referencesArray(Expr rhs) {
    return getReferencedVariables(rhs).stream()
      .anyMatch(item -> scopeOfStmt.lookupType(item) != null
          && scopeOfStmt.lookupType(item).getWithoutQualifiers() instanceof ArrayType);
  }

  static boolean assignsDirectlyToVariable(BinaryExpr expr) {
    return expr.getOp() == BinOp.ASSIGN && expr.getLhs() instanceof VariableIdentifierExpr;
  }

  static boolean isAssignment(Stmt stmt) {
    if (!(stmt instanceof ExprStmt)) {
      return false;
    }
    if (!(((ExprStmt)stmt).getExpr() instanceof BinaryExpr)) {
      return false;
    }
    return ((BinaryExpr)((ExprStmt)stmt).getExpr()).getOp() == BinOp.ASSIGN;
  }

  @Override
  public void apply() {
    BinaryExpr binaryExpr = (BinaryExpr) toOutline.getExpr();
    assert binaryExpr.getOp() == BinOp.ASSIGN;

    final List<String> referencedVariables = getReferencedVariables(binaryExpr.getRhs());
    final String newFunctionName = Constants.OUTLINED_FUNCTION_PREFIX + idGenerator.freshId();

    toOutline.setExpr(new BinaryExpr(
        binaryExpr.getLhs().clone(), new FunctionCallExpr(newFunctionName,
        referencedVariables.stream().map(item -> new VariableIdentifierExpr(item))
            .collect(Collectors.toList())), BinOp.ASSIGN));

    final List<ParameterDecl> params = new ArrayList<>();
    for (String v : referencedVariables) {
      final Type type = scopeOfStmt.lookupType(v);
      assert type != null;
      final Type varType = type.clone().getWithoutQualifiers();
      assert !(varType.getWithoutQualifiers() instanceof ArrayType);
      params.add(new ParameterDecl(v, varType, null));
    }
    Type returnType = scopeOfStmt.lookupType(
        ((VariableIdentifierExpr) binaryExpr.getLhs()).getName());
    if (returnType == null) {
      final Optional<Type> maybeTypeOfBuiltin =
          Typer.maybeGetTypeOfBuiltinVariable(((VariableIdentifierExpr) binaryExpr.getLhs())
              .getName());
      if (maybeTypeOfBuiltin.isPresent()) {
        returnType = maybeTypeOfBuiltin.get();
      } else {
        throw new RuntimeException("No type for "
              + ((VariableIdentifierExpr) binaryExpr.getLhs()).getName());
      }
    }
    returnType = returnType.getWithoutQualifiers();
    tu.addDeclarationBefore(new FunctionDefinition(new FunctionPrototype(
        newFunctionName,
        returnType,
        params),
        new BlockStmt(Arrays.asList(
            new ReturnStmt(binaryExpr.getRhs().clone())), false)),
        enclosingFunction);
  }

  private List<String> getReferencedVariables(Expr expr) {

    // Maintain an order but ignore duplicates.

    return new StandardVisitor() {

      private List<String> referencedVars = new ArrayList<String>();

      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        final String name = variableIdentifierExpr.getName();
        if (scopeOfStmt.lookupScopeEntry(name) != null && !referencedVars.contains(name)) {
          referencedVars.add(name);
        }
      }

      public List<String> getReferencedVars(IAstNode node) {
        visit(node);
        return referencedVars;
      }

    }.getReferencedVars(expr);

  }

}
