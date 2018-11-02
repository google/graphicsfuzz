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

package com.graphicsfuzz.common.ast.inliner;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.StatsVisitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Inliner {

  private final FunctionCallExpr call;
  private final TranslationUnit tu;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final Typer typer;
  private final IParentMap parentMap;

  public static void inline(FunctionCallExpr functionCallExpr, TranslationUnit tu,
        ShadingLanguageVersion shadingLanguageVersion, IdGenerator idGenerator)
      throws CannotInlineCallException {
    new Inliner(functionCallExpr, tu, shadingLanguageVersion).doInline(idGenerator);
  }

  public static boolean canInline(FunctionCallExpr functionCallExpr,
                                  TranslationUnit tu,
                                  ShadingLanguageVersion shadingLanguageVersion,
                                  int nodeLimit) {
    return new Inliner(functionCallExpr, tu, shadingLanguageVersion).canInline(nodeLimit);
  }

  private Inliner(FunctionCallExpr call, TranslationUnit tu,
      ShadingLanguageVersion shadingLanguageVersion) {
    this.call = call;
    this.tu = tu;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.typer = new Typer(tu, shadingLanguageVersion);
    this.parentMap = IParentMap.createParentMap(tu);
  }

  private boolean canInline(int nodeLimit) {
    try {
      final FunctionDefinition inlinable =
          getCloneWithReturnsRemoved(findMatchingFunctionForCall());
      return nodeLimit == 0 || new StatsVisitor(inlinable).getNumNodes() <= nodeLimit;
    } catch (CannotInlineCallException exception) {
      return false;
    }
  }

  private void doInline(IdGenerator idGenerator) throws CannotInlineCallException {
    final FunctionDefinition clonedFunctionDefinition =
          getCloneWithReturnsRemoved(findMatchingFunctionForCall());

    final Optional<String> returnVariableName
          = clonedFunctionDefinition.getPrototype().getReturnType().getWithoutQualifiers()
            == VoidType.VOID ? Optional.empty() : Optional.of(call.getCallee()
          + "_inline_return_value_"
          + idGenerator.freshId());

    final List<Stmt> inlinedStmts = getInlinedStmts(clonedFunctionDefinition, returnVariableName);

    new StandardVisitor() {
      private BlockStmt currentBlockStmt = null;
      private int currentIndex;

      @Override
      public void visitBlockStmt(BlockStmt block) {
        final BlockStmt prevBlock = currentBlockStmt;
        final int prevIndex = currentIndex;
        currentBlockStmt = block;
        for (currentIndex = 0; currentIndex < block.getNumStmts(); currentIndex++) {
          visit(block.getStmt(currentIndex));
        }
        currentBlockStmt = prevBlock;
        currentIndex = prevIndex;
      }

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        if (functionCallExpr != call) {
          return;
        }
        if (currentBlockStmt == null) {
          // Cannot inline e.g. at global scope
          return;
        }
        currentBlockStmt.insertStmt(currentIndex,
              new BlockStmt(inlinedStmts, true));
        if (returnVariableName.isPresent()) {
          currentBlockStmt.insertStmt(currentIndex, new DeclarationStmt(
                new VariablesDeclaration(
                      clonedFunctionDefinition.getPrototype().getReturnType()
                            .getWithoutQualifiers(),
                      new VariableDeclInfo(returnVariableName.get(), null, null))));
          parentMap.getParent(functionCallExpr).replaceChild(functionCallExpr,
                new VariableIdentifierExpr(returnVariableName.get()));
        } else {
          assert clonedFunctionDefinition.getPrototype().getReturnType().getWithoutQualifiers()
                == VoidType.VOID;
          assert parentMap.getParent(functionCallExpr) instanceof ExprStmt;
          currentBlockStmt.removeStmt((Stmt) parentMap.getParent(functionCallExpr));
        }
      }
    }.visit(tu);
  }

  private FunctionDefinition getCloneWithReturnsRemoved(FunctionDefinition fd)
        throws CannotInlineCallException {
    try {
      final FunctionDefinition clonedFunctionDefinition = fd.clone();
      ReturnRemover.removeReturns(clonedFunctionDefinition, shadingLanguageVersion);
      return clonedFunctionDefinition;
    } catch (CannotRemoveReturnsException exception) {
      throw new CannotInlineCallException("Could not remove returns from callee");
    }
  }

  private FunctionDefinition findMatchingFunctionForCall() throws CannotInlineCallException {
    final List<FunctionDefinition> matches =
        tu.getTopLevelDeclarations().stream()
              .filter(item -> item instanceof FunctionDefinition)
              .map(item -> (FunctionDefinition) item)
              .filter(this::functionMatches)
              .collect(Collectors.toList());
    if (matches.size() == 0) {
      throw new CannotInlineCallException("No matching call");
    }
    if (matches.size() != 1) {
      throw new CannotInlineCallException("More than one matching call");
    }
    final FunctionDefinition result = matches.get(0);
    if (hasOutQualifier(result)) {
      throw new CannotInlineCallException("Cannot yet deal with 'out' parameters");
    }
    if (hasArrayParameter(result)) {
      throw new CannotInlineCallException("Cannot yet deal with array parameters");
    }
    return result;
  }

  private List<Stmt> getInlinedStmts(FunctionDefinition functionDefinition,
        Optional<String> returnVariableName) {
    final List<Stmt> inlinedStmts = new ArrayList<>();
    for (int i = 0; i < functionDefinition.getPrototype().getNumParameters(); i++) {
      ParameterDecl pd = functionDefinition.getPrototype().getParameter(i);
      inlinedStmts.add(new DeclarationStmt(
            new VariablesDeclaration(
                  pd.getType().getWithoutQualifiers(),
                  new VariableDeclInfo(pd.getName(), null,
                        new ScalarInitializer(call.getArg(i).clone())))));
    }
    for (Stmt stmt : functionDefinition.getBody().getStmts()) {
      if (stmt instanceof ReturnStmt) {
        if (((ReturnStmt) stmt).hasExpr()) {
          inlinedStmts.add(new ExprStmt(
                new BinaryExpr(
                      new VariableIdentifierExpr(returnVariableName.get()),
                      (((ReturnStmt) stmt).getExpr()),
                      BinOp.ASSIGN)));
        }
      } else {
        inlinedStmts.add(stmt);
      }
    }
    return inlinedStmts;
  }

  private boolean hasArrayParameter(FunctionDefinition functionDefinition) {
    return functionDefinition.getPrototype().getParameters().stream()
          .anyMatch(item -> item.getArrayInfo() != null);
  }

  private boolean hasOutQualifier(FunctionDefinition functionDefinition) {
    return functionDefinition.getPrototype().getParameters().stream()
          .map(item -> item.getType())
          .anyMatch(item -> item.hasQualifier(TypeQualifier.OUT_PARAM)
                || item.hasQualifier(TypeQualifier.INOUT_PARAM));
  }

  private boolean functionMatches(FunctionDefinition declaration) {
    final FunctionPrototype prototype = declaration.getPrototype();
    if (!prototype.getName().equals(call.getCallee())) {
      return false;
    }
    if (prototype.getNumParameters() != call.getNumArgs()) {
      return false;
    }
    for (int i = 0; i < prototype.getNumParameters(); i++) {
      if (typer.lookupType(call.getArg(i)) == null) {
        continue;
      }
      if (!typer.lookupType(call.getArg(i)).getWithoutQualifiers()
            .equals(prototype.getParameter(i).getType().getWithoutQualifiers())) {
        return false;
      }
    }
    return true;
  }


}
