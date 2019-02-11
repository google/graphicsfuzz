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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.AbortVisitationException;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.typing.TyperHelper;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.fuzzer.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RestrictFragmentShaderColors {

  // The following pairs need to be kept in sync.
  public static final String HI_COLOR = "0.75";
  public static final int HI_COLOR_COMPONENT_VALUE = 191;
  public static final String LO_COLOR = "0.25";
  public static final int LO_COLOR_COMPONENT_VALUE = 64;

  private final ShaderJob shaderJob;
  private final IRandom generator;
  private final String outputVariableName;
  private final GenerationParams generationParams;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final float probabilityOfAddingNewWrite;

  private RestrictFragmentShaderColors(ShaderJob shaderJob, IRandom generator,
                                       String outputVariableName,
                                       GenerationParams generationParams,
                                       float probabilityOfAddingNewWrite) {
    this.shaderJob = shaderJob;
    this.generator = generator;
    this.outputVariableName = outputVariableName;
    this.generationParams = generationParams;
    this.shadingLanguageVersion = shaderJob.getFragmentShader().get().getShadingLanguageVersion();
    this.probabilityOfAddingNewWrite = probabilityOfAddingNewWrite;
  }

  /**
   * Provides functionality to take a shader job and fragment shader output variable, and hijack
   * the fragment shader to force any writes to this variable to be taken from the restricted
   * palette of 16 colours that have each of R, G, B and A to LOW_COLOR or HI_COLOR.
   * This is achieved by:
   *  - Adapting all existing writes to write one of these colors
   *  - Randomly adding some additional writes of these colors
   *  - Adding an initial statement to the fragment shader that writes one of these colors
   * Opaque expressions are used to make the writes look non-explicit to the shader compiler.
   * The transformation conservatively reports failure if a case that cannot yet be handled is
   * encountered.  In this case the given shader job may be in an inconsistent state and should
   * be discarded.
   *
   * @param shaderJob The shader job to be transformed.
   * @param generator A source of random numbers.
   * @param outputVariableName Name of the output variable in the fragment shader used for color
   *                           writes.
   * @return true if and only if application of the restriction succeeded.
   */
  public static boolean restrictFragmentShaderColors(ShaderJob shaderJob,
                                                     IRandom generator,
                                                     String outputVariableName,
                                                     GenerationParams generationParams,
                                                     float probabilityOfAddingNewWrite) {
    return new RestrictFragmentShaderColors(shaderJob, generator, outputVariableName,
        generationParams, probabilityOfAddingNewWrite).apply();
  }

  private boolean apply() {
    if (!shaderJob.getFragmentShader().isPresent()) {
      // No fragment shader; nothing to do.
      return true;
    }

    // Adapt existing writes
    if (!adaptExistingWrites()) {
      return false;
    }

    addNewWrites();

    addInitialWrite();

    return true;

  }

  private void addInitialWrite() {
    final Optional<FunctionDefinition> maybeMain = shaderJob.getFragmentShader().get()
        .getTopLevelDeclarations()
        .stream()
        .filter(item -> item instanceof FunctionDefinition)
        .map(item -> (FunctionDefinition) item)
        .filter(item -> item.getPrototype().getName().equals("main"))
        .findAny();
    if (!maybeMain.isPresent()) {
      throw new RuntimeException("Fragment shader must have a main function.");
    }
    final Scope emptyScope = new Scope(null);
    maybeMain.get().getBody().insertStmt(0,
        makeOutputVariableWrite(makeColorVector(BasicType.VEC4, emptyScope)));
  }

  private Stmt makeOutputVariableWrite(Expr values) {
    return new ExprStmt(
        new BinaryExpr(
            new VariableIdentifierExpr(outputVariableName),
            values, BinOp.ASSIGN));
  }

  private void addNewWrites() {
    for (IInjectionPoint injectionPoint : new InjectionPoints(
        shaderJob.getFragmentShader().get(), generator, item -> true)
        .getInjectionPoints(item -> item.nextFloat() < probabilityOfAddingNewWrite)) {
      final Scope scope = injectionPoint.scopeAtInjectionPoint();
      injectionPoint.inject(makeOutputVariableWrite(
          makeColorVector(BasicType.VEC4, scope)));
    }
  }

  private boolean adaptExistingWrites() {

    final Typer typer = new Typer(shaderJob.getFragmentShader().get(), shadingLanguageVersion);

    return new ScopeTreeBuilder() {

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        Set<String> acceptableFunctionNames = new HashSet<>();
        acceptableFunctionNames.addAll(TyperHelper.getBuiltins(shadingLanguageVersion).keySet());
        acceptableFunctionNames.add(Constants.GLF_FUZZED);
        acceptableFunctionNames.add(Constants.GLF_IDENTITY);
        if (acceptableFunctionNames.contains(functionCallExpr.getCallee())
            || functionCallExpr.getCallee().startsWith(Constants.OUTLINED_FUNCTION_PREFIX)) {
          return;
        }
        for (Expr expr : functionCallExpr.getArgs()) {
          if (isPartOfOutputVariable(expr, currentScope)) {
            throw new AbortVisitationException("We do not yet handle components of the output "
                + "variable being passed to functions, in case they are out parameters.");
          }
        }
      }

      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        if (!binaryExpr.getOp().isSideEffecting()) {
          // Do nothing: the binary operator is not side-effecting.
          return;
        }
        if (!isPartOfOutputVariable(binaryExpr.getLhs(), currentScope)) {
          return;
        }
        final BasicType basicType = getBasicType(binaryExpr.getLhs());
        switch (binaryExpr.getOp()) {
          case MUL_ASSIGN:
          case DIV_ASSIGN:
            // Replace RHS by
            //   (1.0 + 0.0 * (is_nan_or_inf(original_rhs) ? 0.0 : original_rhs))
            binaryExpr.setRhs(
                new ParenExpr(new BinaryExpr(opaqueOne(currentScope, basicType),
                 new BinaryExpr(opaqueZero(currentScope, basicType),
                     ifNanOrInfThenZeroElse(basicType, binaryExpr.getRhs(), currentScope),
                      BinOp.MUL),
                BinOp.ADD))
            );
            break;
          case ADD_ASSIGN:
          case SUB_ASSIGN:
            // Replace RHS by (0.0 * is_nan_or_inf(original_rhs ? 0.0 : original_rhs))
            binaryExpr.setRhs(
                new ParenExpr(
                    new BinaryExpr(
                        opaqueZero(currentScope, basicType),
                        ifNanOrInfThenZeroElse(basicType, binaryExpr.getRhs(), currentScope),
                        BinOp.MUL)
                )
            );
            break;
          case ASSIGN:
            // Change "outVar = e" to "outVar = (new_color + zero * is_nan_or_inf(e ? 0.0 : e))"
            binaryExpr.setRhs(
                new ParenExpr(
                    new BinaryExpr(
                        makeColorVector(basicType, currentScope),
                        new BinaryExpr(opaqueZero(currentScope, basicType),
                            ifNanOrInfThenZeroElse(basicType, binaryExpr.getRhs(), currentScope),
                            BinOp.MUL),
                        BinOp.ADD
                    )
                )
            );
            break;
          default:
            throw new AbortVisitationException("Cannot yet handle output variable being updated "
                + "via the " + binaryExpr.getOp() + " operator.");
        }
      }

      public BasicType getBasicType(Expr expr) {
        final Type type = typer.lookupType(expr);
        if (type == null) {
          throw new AbortVisitationException("Cannot work out type of assignment to part of "
              + "the output variable.");
        }
        assert type.getWithoutQualifiers() instanceof BasicType;
        assert BasicType.allGenTypes().contains(type.getWithoutQualifiers());
        return (BasicType) type.getWithoutQualifiers();
      }

      private boolean tryAdapt() {
        try {
          visit(shaderJob.getFragmentShader().get());
          return true;
        } catch (AbortVisitationException exception) {
          return false;
        }
      }

    }.tryAdapt();
  }

  public Expr ifNanOrInfThenZeroElse(BasicType basicType, Expr expr, Scope scope) {
    final FunctionCallExpr isNan = new FunctionCallExpr("isnan", expr.clone());
    final FunctionCallExpr isInf = new FunctionCallExpr("isinf", expr.clone());
    final FunctionCallExpr zeroIfNan = new FunctionCallExpr("mix", expr,
        opaqueZero(scope, basicType), isNan);
    final FunctionCallExpr zeroIfInf = new FunctionCallExpr("mix", zeroIfNan,
        opaqueZero(scope, basicType),
        isInf);
    return zeroIfInf;
  }

  private boolean isPartOfOutputVariable(Expr expr, Scope scope) {
    if (isOutputVariable(expr, scope)) {
      return true;
    }
    if (expr instanceof MemberLookupExpr) {
      return isPartOfOutputVariable(((MemberLookupExpr) expr).getStructure(), scope);
    }
    if (expr instanceof ArrayIndexExpr) {
      return isPartOfOutputVariable(((ArrayIndexExpr) expr).getArray(), scope);
    }
    return false;
  }

  private boolean isOutputVariable(Expr expr, Scope scope) {
    if (!(expr instanceof VariableIdentifierExpr)) {
      // It's not a variable, so cannot be the output variable.
      return false;
    }
    final VariableIdentifierExpr variableIdentifierExpr = (VariableIdentifierExpr) expr;
    if (!variableIdentifierExpr.getName().equals(outputVariableName)) {
      // It has the wrong name.
      return false;
    }
    final Type type = scope.lookupType(outputVariableName);
    if (type == null) {
      // We don't have a type for it, so it cannot be the output variable.
      return false;
    }
    if (!type.hasQualifier(TypeQualifier.SHADER_OUTPUT)) {
      // It does not have the "out" qualifier, so cannot be the output variable.
      return false;
    }
    return true;
  }

  private Expr opaqueOne(Scope scope, BasicType basicType) {
    return new OpaqueExpressionGenerator(generator, generationParams, shadingLanguageVersion)
        .makeOpaqueOne(basicType, false, 0,
            new Fuzzer(new FuzzingContext(scope), shadingLanguageVersion, generator,
                generationParams));
  }

  private Expr opaqueZero(Scope scope, BasicType basicType) {
    return new OpaqueExpressionGenerator(generator, generationParams, shadingLanguageVersion)
        .makeOpaqueZero(basicType, false, 0,
            new Fuzzer(new FuzzingContext(scope), shadingLanguageVersion, generator,
                generationParams));
  }

  private Expr applyIdentity(Expr expr, Scope scope) {
    return new OpaqueExpressionGenerator(generator, generationParams, shadingLanguageVersion)
        .applyIdentityFunction(expr, BasicType.FLOAT, false, 0,
            new Fuzzer(new FuzzingContext(scope), shadingLanguageVersion, generator,
                generationParams));
  }

  private Expr makeColorVector(BasicType basicType, Scope scope) {
    assert BasicType.allGenTypes().contains(basicType);
    final List<Expr> args = new ArrayList<>();
    for (int i = 0; i < basicType.getNumElements(); i++) {
      args.add(applyIdentity(new FloatConstantExpr(generator.nextBoolean() ? LO_COLOR : HI_COLOR),
          scope));
    }
    return new TypeConstructorExpr(basicType.toString(), args);
  }

}
