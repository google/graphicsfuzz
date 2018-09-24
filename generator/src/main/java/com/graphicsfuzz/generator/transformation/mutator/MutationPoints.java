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

package com.graphicsfuzz.generator.transformation.mutator;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.decl.ArrayInitializer;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class MutationPoints extends ScopeTreeBuilder {

  private Typer typer;
  private List<IMutationPoint> mutationPoints;
  private boolean inInitializer;
  private boolean atGlobalScope;
  private int insideLValueCount;
  private Type enclosingVariablesDeclarationType;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final IRandom generator;
  private final GenerationParams generationParams;
  private final Deque<Set<String>> forLoopIterators;


  public MutationPoints(Typer typer, IAstNode node, ShadingLanguageVersion shadingLanguageVersion,
      IRandom generator,
        GenerationParams generationParams) {
    this.typer = typer;
    this.mutationPoints = new ArrayList<>();
    this.inInitializer = false;
    this.atGlobalScope = true;
    this.insideLValueCount = 0;
    this.enclosingVariablesDeclarationType = null;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.generator = generator;
    this.generationParams = generationParams;
    this.forLoopIterators = new LinkedList<>();
    visit(node);
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    if (!shadingLanguageVersion.restrictedForLoops()) {
      super.visitForStmt(forStmt);
    } else {
      // GLSL 1.00 has very strict rules about the structure of a for loop.
      // They mainly apply to the loop initialiser, condition and increment,
      // so we simply do not visit these, and go to the body directly.
      //
      // We need to note the loop iteration variable, to make sure its name is
      // unavailable at each expression in this loop.  This is because there may be a
      // variable in an enclosing scope already declared with this name.
      forLoopIterators.addLast(
            new StandardVisitor() {
              private Set<String> names = new HashSet<>();

              Set<String> getIteratorVariableNames(ForStmt forStmt) {
                visit(forStmt.getIncrement());
                return names;
              }

              @Override
              public void visitVariableIdentifierExpr(
                    VariableIdentifierExpr variableIdentifierExpr) {
                names.add(variableIdentifierExpr.getName());
              }
            }.getIteratorVariableNames(forStmt)
      );
      visitForStmtBodyOnly(forStmt);
      forLoopIterators.removeLast();
    }
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    assert atGlobalScope;
    atGlobalScope = false;
    super.visitFunctionDefinition(functionDefinition);
    atGlobalScope = true;
  }

  @Override
  public void visitBinaryExpr(BinaryExpr binaryExpr) {
    if (isSideEffectingBinOp(binaryExpr.getOp())) {
      insideLValueCount++;
    }
    visit(binaryExpr.getLhs());
    if (isSideEffectingBinOp(binaryExpr.getOp())) {
      assert insideLValueCount > 0;
      insideLValueCount--;
    }
    visit(binaryExpr.getRhs());

    // Skip the LHS if it is side-effecting
    identifyMutationPoints(binaryExpr,
          isSideEffectingBinOp(binaryExpr.getOp()) ? new HashSet<>(Arrays.asList(0))
                : new HashSet<>());
  }

  @Override
  public void visitUnaryExpr(UnaryExpr unaryExpr) {
    if (isSideEffectingUnOp(unaryExpr.getOp())) {
      insideLValueCount++;
    }
    super.visitUnaryExpr(unaryExpr);
    if (isSideEffectingUnOp(unaryExpr.getOp())) {
      assert insideLValueCount > 0;
      insideLValueCount--;
    }
    if (!isSideEffectingUnOp(unaryExpr.getOp())) {
      identifyMutationPoints(unaryExpr);
    }
  }

  @Override
  public void visitParenExpr(ParenExpr parenExpr) {
    super.visitParenExpr(parenExpr);
    identifyMutationPoints(parenExpr);
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    FunctionPrototype bestMatch = null;
    for (FunctionPrototype candidate : typer.getPrototypes(functionCallExpr.getCallee())) {
      if (candidate.getNumParameters() == functionCallExpr.getNumArgs()) {
        if (bestMatch == null || callMatchesPrototype(functionCallExpr, candidate)) {
          // We are assuming the input program is well-typed.  So, if there is just
          // one possible prototype and it has the right number of arguments, we grab it.
          bestMatch = candidate;
        }
      }
    }
    Set<Integer> indicesOfLValueArguments = new HashSet<>();
    for (int i = 0; i < functionCallExpr.getNumArgs(); i++) {
      boolean isLValue = false;
      if (bestMatch != null) {
        Type argType = bestMatch.getParameters().get(i).getType();
        if (argType instanceof QualifiedType) {
          QualifiedType qt = (QualifiedType) argType;
          if (qt.hasQualifier(TypeQualifier.INOUT_PARAM)
                || qt.hasQualifier(TypeQualifier.OUT_PARAM)) {
            isLValue = true;
            indicesOfLValueArguments.add(i);
          }
        }
      }
      if (isLValue) {
        insideLValueCount++;
      }
      visit(functionCallExpr.getArg(i));
      if (isLValue) {
        insideLValueCount--;
      }
    }

    identifyMutationPoints(functionCallExpr, indicesOfLValueArguments);
  }

  private boolean callMatchesPrototype(FunctionCallExpr call, FunctionPrototype prototype) {
    assert call.getNumArgs() == prototype.getNumParameters();
    for (int i = 0; i < call.getNumArgs(); i++) {
      Type argType = typer.lookupType(call.getArg(i));
      if (argType == null) {
        // With incomplete information we say there is a match
        continue;
      }
      if (!typesMatchWithoutQualifiers(argType, prototype.getParameters().get(i).getType())) {
        return false;
      }
    }
    return true;
  }

  private boolean typesMatchWithoutQualifiers(Type t1, Type t2) {
    return t1.getWithoutQualifiers().equals(t2.getWithoutQualifiers());
  }

  @Override
  public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
    super.visitTypeConstructorExpr(typeConstructorExpr);
    identifyMutationPoints(typeConstructorExpr);
  }

  @Override
  public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
    // WebGL has restrictions on the form of array indices, so
    // we shall not mutate anything that lies under them.
    // TODO: this may be more restrictive than necessary, since
    // if A[f(x)] is a legal array lookup then it would be fine
    // to mutate x.  However, the strictness of WebGL indexing
    // rules means that A[f(x)] is likely not legal in the first
    // place.
    if (!shadingLanguageVersion.isWebGl()) {
      super.visitArrayIndexExpr(arrayIndexExpr);
      identifyMutationPoints(arrayIndexExpr);
    }
  }

  @Override
  public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
    super.visitMemberLookupExpr(memberLookupExpr);
    identifyMutationPoints(memberLookupExpr);
  }

  @Override
  public void visitTernaryExpr(TernaryExpr ternaryExpr) {
    super.visitTernaryExpr(ternaryExpr);
    identifyMutationPoints(ternaryExpr);
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    assert enclosingVariablesDeclarationType == null;
    enclosingVariablesDeclarationType = variablesDeclaration.getBaseType();
    super.visitVariablesDeclaration(variablesDeclaration);
    enclosingVariablesDeclarationType = null;
  }

  @Override
  public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
    assert !inInitializer;
    inInitializer = true;
    super.visitScalarInitializer(scalarInitializer);
    inInitializer = false;
  }

  @Override
  public void visitArrayInitializer(ArrayInitializer arrayInitializer) {
    assert !inInitializer;
    inInitializer = true;
    super.visitArrayInitializer(arrayInitializer);
    inInitializer = false;
  }

  private boolean isSideEffectingUnOp(UnOp op) {
    switch (op) {
      case POST_DEC:
      case POST_INC:
      case PRE_DEC:
      case PRE_INC:
        return true;
      default:
        return false;
    }
  }

  private boolean isSideEffectingBinOp(BinOp op) {
    switch (op) {
      case ADD_ASSIGN:
      case ASSIGN:
      case BAND_ASSIGN:
      case BOR_ASSIGN:
      case BXOR_ASSIGN:
      case DIV_ASSIGN:
      case MOD_ASSIGN:
      case MUL_ASSIGN:
      case SHL_ASSIGN:
      case SHR_ASSIGN:
      case SUB_ASSIGN:
        return true;
      default:
        return false;
    }
  }

  private void identifyMutationPoints(Expr expr, Set<Integer> indicesToSkip) {
    if (insideLValueCount == 0) {
      for (int i = 0; i < expr.getNumChildren(); i++) {
        if (indicesToSkip.contains(i)) {
          continue;
        }
        if (typer.hasType(expr.getChild(i))) {
          Scope clonedScope = currentScope.shallowClone();
          if (shadingLanguageVersion.restrictedForLoops()) {
            for (Set<String> iterators : forLoopIterators) {
              iterators.forEach(clonedScope::remove);
            }
          }
          mutationPoints.add(new MutationPoint(expr, i, typer.lookupType(expr.getChild(i)),
                clonedScope, isConstContext(), shadingLanguageVersion, generator,
                generationParams));
        }
      }
    }
  }

  private void identifyMutationPoints(Expr expr) {
    identifyMutationPoints(expr, new HashSet<>());
  }

  public List<IMutationPoint> getMutationPoints(TransformationProbabilities probabilities) {
    List<IMutationPoint> result = new ArrayList<>();
    for (IMutationPoint mutationPoint : mutationPoints) {
      if (probabilities.mutatePoint(generator)) {
        result.add(mutationPoint);
      }
    }
    return result;
  }

  private boolean isConstContext() {
    if (inInitializer) {
      assert enclosingVariablesDeclarationType != null;
      if (enclosingVariablesDeclarationType instanceof QualifiedType
            && ((QualifiedType) enclosingVariablesDeclarationType).hasQualifier(
            TypeQualifier.CONST)) {
        return true;
      }
      if (atGlobalScope) {
        return true;
      }
    }
    return false;
  }

  List<IMutationPoint> getAllMutationPoints() {
    return Collections.unmodifiableList(mutationPoints);
  }
}
