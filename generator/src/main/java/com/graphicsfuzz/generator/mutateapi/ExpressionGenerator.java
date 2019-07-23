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

package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.generator.semanticschanging.LiteralFuzzer;
import com.graphicsfuzz.util.Constants;
import com.sun.org.apache.xpath.internal.operations.Bool;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.util.Pair;

public class ExpressionGenerator {

  private static final int MAX_FUNCTION_PARAMS = 5;
  private static final int INT_MIN = -(1 << 17);
  private static final int INT_MAX = 1 << 17;

  private final TranslationUnit translationUnit;
  private final PipelineInfo pipelineInfo;
  private final IdGenerator idGenerator;

  public ExpressionGenerator(TranslationUnit translationUnit, PipelineInfo pipelineInfo) {
    this.translationUnit = translationUnit;
    this.pipelineInfo = pipelineInfo;
    idGenerator = new IdGenerator();
  }

  private Expr generateLiteralNumber(Value value) {
    assert BasicType.allScalarTypes().contains(value.getType());

    if (value.getType() == BasicType.FLOAT) {
      return new FloatConstantExpr(nameFromNumber(value.getData().get(0).get()));
    }
    if (value.getType() == BasicType.INT) {
      return new IntConstantExpr(nameFromNumber(value.getData().get(0).get()));
    }
    if (value.getType() == BasicType.UINT) {
      return new UIntConstantExpr(nameFromNumber(value.getData().get(0).get()) + "u");
    }
    List<Expr> args = new ArrayList<>();
    for (int i = 0; i < value.getType().getNumElements(); i++) {
      assert value.getType().getElementType() != value.getType();
      args.add(generateLiteralNumber(
          new PrimitiveValue(value.getType().getElementType(),
              Arrays.asList(Optional.of(value.getData().get(i).get()))
          )));
    }
    return new TypeConstructorExpr(value.getType().toString(), args);
  }

  public Expr generateExpr(FactManager factManager,
                           FunctionDefinition functionDefinition,
                           Stmt stmt,
                           Value value,
                           IRandom generator) {
    while (true) {
      switch (generator.nextInt(2)) {
        case 0:
          return generateVariableFact(factManager,
              functionDefinition,
              stmt,
              value,
              generator);
        case 1:
          return generateFunction(factManager,
              functionDefinition,
              stmt,
              value,
              generator);
        case 2:
          final Optional<Expr> expr = factManager.getFact(value);
          if (expr.isPresent()) {
            return expr.get();
          } else {
            continue;
          }
        case 3:
          return generateLiteralNumber(value);
        default:
          return null;
      }
    }
  }

  private String genVarName(Value value) {
    return Constants.GLF_PRIMITIVE_VAR + "_"
        + value.getType().toString() + "_"
        + value.getData().get(0).get().toString().replace(".", "_");
  }

  private String nameFromNumber(Number number) {
    return number.toString();
  }

  private String getFunctionName(Value value) {
    return Constants.GLF_COMPUTE + "_"
        + value.getType().toString().toUpperCase() + "_"
        + nameFromNumber(value.getData().get(0).get()).replace(".", "_")
        + "_ID_" + idGenerator.freshId();

  }

  private Optional<Number> numberFromName(String string) {
    try {
      return Optional.of(NumberFormat.getInstance().parse(string));
    } catch (ParseException parseException) {
      return Optional.empty();
    }
  }


  private Expr generateVariableFact(FactManager factManager,
                                    FunctionDefinition functionDefinition,
                                    Stmt stmt,
                                    Value value,
                                    IRandom generator) {
    String variableName = genVarName(value);
    VariableDeclInfo declInfo =
        new VariableDeclInfo(variableName, null,
            new Initializer(new FloatConstantExpr(value.getData().get(0).get().toString())));
    VariablesDeclaration variablesDecl = new VariablesDeclaration(value.getType(), declInfo);
    functionDefinition.getBody().addStmt(new DeclarationStmt(variablesDecl));

    // Store variable fact in FactManager
    factManager.addVariableFact(declInfo, new VariableFact(variablesDecl, declInfo, value));
    return new VariableIdentifierExpr(variableName);
  }


  private Expr generateFunction(FactManager factManager,
                                FunctionDefinition functionDefinition,
                                Stmt stmt,
                                Value targetValue,
                                IRandom generator) {

    final String functionName = getFunctionName(targetValue);
    final FactManager childFactManager = factManager.clone();
    final List<ParameterDecl> params = generateParams(generator, functionDefinition,
        childFactManager);
    final FunctionPrototype prototype = new FunctionPrototype(functionName,
        targetValue.getType(),
        params);
    final FunctionDefinition newFunctionDef = new FunctionDefinition(prototype,
        new BlockStmt(new ArrayList<>(), false)
    );
    newFunctionDef.getBody().addStmt(new ReturnStmt(
        generateExpr(childFactManager, newFunctionDef, stmt, targetValue, generator)));
    translationUnit.addDeclaration(newFunctionDef);
    return new FunctionCallExpr(functionName,
        params
            .stream()
            .map(ParameterDecl::getName)
            .map(VariableIdentifierExpr::new)
            .collect(Collectors.toList())
    );
  }

  private List<ParameterDecl> generateParams(IRandom generator, FunctionDefinition fd,
                                             FactManager factManager) {
    List<ParameterDecl> params = new ArrayList<>();
    for (int i = 0; i < generator.nextInt(MAX_FUNCTION_PARAMS); i++) {
      final BasicType type =
          BasicType.allBasicTypes().get(generator.nextInt(BasicType.allScalarTypes().size()));
      final String name = Constants.GLF_PARAM + "_" + idGenerator.freshId();
      final ParameterDecl parameterDecl = new ParameterDecl(name, type, null);
      final Optional<Pair<Expr, Value>> fuzzedExpr = generateFuzzedExpr(type, generator);

      final VariableDeclInfo variableDeclInfo = new VariableDeclInfo(name, null,
          new Initializer(fuzzedExpr.get().getKey()));
      final VariablesDeclaration variablesDeclaration = new VariablesDeclaration(type,
          variableDeclInfo);

      factManager.addVariableFact(variableDeclInfo, new VariableFact(
          variablesDeclaration,
          variableDeclInfo,
          fuzzedExpr.get().getValue()
      ));
      fd.getBody().addStmt(new DeclarationStmt(variablesDeclaration));
      params.add(parameterDecl);
    }
    return params;
  }

  private String randomFloatString(IRandom generator) {
    final int maxDigitsEitherSide = 2;
    StringBuilder sb = new StringBuilder();
    int digitsBefore = Math.max(1, generator.nextInt(maxDigitsEitherSide));
    for (int i = 0; i < digitsBefore; i++) {
      int candidate;
      while (true) {
        candidate = generator.nextInt(10);
        if (candidate == 0 && i == 0 && digitsBefore > 1) {
          continue;
        }
        break;
      }
      sb.append(String.valueOf(candidate));
    }
    sb.append(".");
    for (int i = 0; i < digitsBefore; i++) {
      sb.append(String.valueOf(generator.nextInt(10)));
    }
    return sb.toString();
  }

  // Pair object is used here since we are going to store the newly generated Expression
  // as well as its associated Value.
  public Optional<Pair<Expr, Value>> generateFuzzedExpr(BasicType type, IRandom generator) {
    if (type == BasicType.INT) {
      final String randomInt = String.valueOf(generator.nextInt(INT_MAX - INT_MIN) + INT_MIN);
      return Optional.of(new Pair<>(
          new IntConstantExpr(randomInt),
          new PrimitiveValue(type, Arrays.asList(numberFromName(randomInt)))
      ));
    }
    if (type == BasicType.FLOAT) {
      final String randomFloat = randomFloatString(generator);
      return Optional.of(new Pair<>(
          new FloatConstantExpr(randomFloat),
          new PrimitiveValue(type, Arrays.asList(numberFromName(randomFloat)))
      ));
    }

    if (type == BasicType.VEC2 || type == BasicType.VEC3 || type == BasicType.VEC4) {
      final List<Expr> args = new ArrayList<>();
      final List<Pair<Expr, Value>> pairs = new ArrayList<>();
      for (int i = 0; i < type.getNumElements(); i++) {
        //Recursively call fuzz method to get the fuzzed expr for the arguments.
        Pair pair = generateFuzzedExpr(type.getElementType(), generator).get();
        args.add((Expr) pair.getKey());
        pairs.add(pair);
      }
      final List<Optional<Number>> data = pairs
          .stream()
          .map(item ->
              Optional.of(item.getValue().getData().get(0).get())
          ).collect(Collectors.toList());

      return Optional.of(new Pair<>(
              new TypeConstructorExpr(type.toString(), args),
              new PrimitiveValue(type, data)
          )
      );
    }
    return Optional.empty();
  }
}
