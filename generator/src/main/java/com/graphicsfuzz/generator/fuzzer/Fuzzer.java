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

package com.graphicsfuzz.generator.fuzzer;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayConstructorExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.ContinueStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.fuzzer.templates.FunctionCallExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.IExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.VariableIdentifierExprTemplate;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {

  private static final int MAX_GLOBALS = 10;
  private static final int MAX_STRUCTS = 10;
  private static final int MAX_FIELDS_PER_STRUCT = 7;
  private static final int MAX_NON_MAIN_FuNCTIONS = 20;
  private static final int MAX_BLOCK_STMTS = 10;
  private static final int MAX_BLOCK_NESTING_DEPTH = 5;
  private static final int MAX_LOCALS_IN_DECL_GROUP = 4;
  private static final int MAX_ARRAY_SIZE = 100;
  private static final int MAX_FUNCTION_PARAMS = 10;

  private final ShadingLanguageVersion shadingLanguageVersion;
  private final IRandom generator;
  private int nextId;
  private FuzzingContext fuzzingContext;

  private Collection<IExprTemplate> builtinTemplates;

  private final GenerationParams generationParams;

  // This field is used to give each fuzzed declaration a prefix, to distinguish it from
  // declarations constructed by other fuzzing instances.
  // If not passed in on construction, yet required later, and exception will be thrown.
  private final String fuzzedDeclarationPrefix;

  public Fuzzer(FuzzingContext fuzzingContext,
        ShadingLanguageVersion shadingLanguageVersion,
        IRandom generator,
        GenerationParams generationParams,
        String fuzzedDeclarationPrefix) {
    this.fuzzingContext = fuzzingContext;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.generator = generator;
    this.generationParams = generationParams;
    this.builtinTemplates = Templates.get(shadingLanguageVersion);
    this.nextId = 0;
    this.fuzzedDeclarationPrefix = fuzzedDeclarationPrefix;
  }

  public Fuzzer(FuzzingContext fuzzingContext,
        ShadingLanguageVersion shadingLanguageVersion,
        IRandom generator,
        GenerationParams generationParams) {
    this(fuzzingContext, shadingLanguageVersion, generator, generationParams, null);
  }

  public Expr fuzzExpr(Type targetType, boolean isLValue, boolean constContext,
        final int depth) {
    int maxTries = 50;
    for (int i = 0; i < maxTries; i++) {
      try {
        return makeExpr(targetType, isLValue, constContext, depth);
      } catch (FuzzedIntoACornerException exception) {
        // Do nothing; try again;
        continue;
      }
    }
    throw new FuzzedIntoACornerException();
  }

  private Expr makeExpr(Type targetType, boolean isLValue, boolean constContext, final int depth) {

    if (targetType instanceof QualifiedType) {
      QualifiedType qualifiedType = (QualifiedType) targetType;
      if (qualifiedType.hasQualifier(TypeQualifier.CONST)) {
        assert constContext;
      }
      return makeExpr(qualifiedType.getTargetType(), isLValue, constContext, depth);
    }
    if (targetType instanceof BasicType) {

      List<IExprTemplate> applicableTemplates = availableTemplates()
            .filter(item -> item.getResultType().equals(targetType)).collect(Collectors.toList());
      if (isLValue) {
        applicableTemplates = applicableTemplates.stream().filter(item -> item.isLValue())
              .collect(Collectors.toList());
      }
      if (constContext) {
        applicableTemplates = applicableTemplates.stream().filter(item -> item.isConst())
              .collect(Collectors.toList());
      }
      if (isTooDeep(depth)) {
        applicableTemplates = applicableTemplates.stream()
              .filter(item -> item.getNumArguments() == 0)
              .collect(Collectors.toList());
      }

      if (applicableTemplates.size() == 0) {
        throw new FuzzedIntoACornerException();
      }

      IExprTemplate template = applicableTemplates
            .get(generator.nextInt(applicableTemplates.size()));

      List<Expr> args = new ArrayList<Expr>();
      for (int i = 0; i < template.getNumArguments(); i++) {
        List<? extends Type> possibleArgTypes = template.getArgumentTypes().get(i);
        Type argType = possibleArgTypes.stream().collect(Collectors.toList())
              .get(generator.nextInt(possibleArgTypes.size()));
        args.add(makeExpr(argType, template.requiresLValueForArgument(i), constContext,
              depth + 1));
      }
      return template.generateExpr(generator, args);
    }
    if (targetType instanceof ArrayType) {
      // TODO: we should use in-scope variables and functions to make arrays
      if (!shadingLanguageVersion.restrictedArrayIndexing()) {
        ArrayType arrayType = (ArrayType) targetType;
        List<Expr> args = new ArrayList<>();
        for (int i = 0; i < arrayType.getArrayInfo().getSize(); i++) {
          args.add(makeExpr(arrayType.getBaseType(), isLValue, constContext, depth + 1));
        }
        return new ArrayConstructorExpr((ArrayType) stripQualifiers(targetType), args);
      } else {
        throw new FuzzedIntoACornerException();
      }
    }
    if (targetType instanceof StructNameType) {
      final String structName = ((StructNameType) targetType).getName();
      final StructDefinitionType sdt =
          fuzzingContext.getCurrentScope().lookupStructName(structName);
      if (sdt == null) {
        throw new RuntimeException("Could not find a struct named " + structName + " in scope.");
      }
      assert sdt.getStructNameType().getName().equals(structName);
      return new TypeConstructorExpr(structName, sdt.getFieldTypes()
          .stream().map(item -> makeExpr(item, false, constContext, depth + 1))
          .collect(Collectors.toList()));
    }
    throw new RuntimeException("Do not yet know how to make expr of type " + targetType.getClass());

  }

  private boolean isTooDeep(int depth) {
    return isTooDeep(depth, generationParams, generator);
  }

  public static boolean isTooDeep(int depth, GenerationParams generationParams, IRandom generator) {
    if (depth >= generationParams.getMaxDepthForGeneratedExpr()) {
      return true;
    }
    // TODO: revisit to make more refined.  For now, this makes the chances of going deeper get
    // exponentially smaller as depth increases
    for (int i = 0; i <= depth; i++) {
      if (generator.nextInt(2) == 0) {
        return true;
      }
    }
    return false;
  }

  private Stream<IExprTemplate> availableTemplates() {
    return Stream.concat(builtinTemplates.stream(),
          availableTemplatesFromContext());
  }

  private Stream<IExprTemplate> availableTemplatesFromContext() {
    List<IExprTemplate> available = new LinkedList<>();
    for (String name : fuzzingContext.getCurrentScope().namesOfAllVariablesInScope()) {
      available.add(new VariableIdentifierExprTemplate(name,
            fuzzingContext.getCurrentScope().lookupType(name)));
    }
    for (FunctionPrototype proto : fuzzingContext.getFunctionPrototypes()) {
      available.add(new FunctionCallExprTemplate(proto));
    }
    return available.stream();
  }

  public static void main(String[] args) {
    try {
      //testFuzzExpr(args);
      showTemplates(args);

    } catch (Throwable throwable) {
      throwable.printStackTrace();
      System.exit(1);
    }

  }

  private static void showTemplates(String[] args) {
    // Call this from main to produce a list of templates
    List<IExprTemplate> templates = new ArrayList<>();
    templates.addAll(Templates.get(ShadingLanguageVersion.fromVersionString(args[0])));
    Collections.sort(templates, new Comparator<IExprTemplate>() {
      @Override
      public int compare(IExprTemplate t1, IExprTemplate t2) {
        return t1.toString().compareTo(t2.toString());
      }
    });

    for (IExprTemplate t : templates) {
      System.out.println(t);
    }
  }

  public TranslationUnit fuzzTranslationUnit() {

    List<Declaration> decls = new ArrayList<>();

    int numGlobalsLeftToGenerate = generator.nextInt(MAX_GLOBALS);
    int numNonMainFunctionsLeftToGenerate = generator.nextInt(MAX_NON_MAIN_FuNCTIONS);
    int numStructsLeftToGenerate = generator.nextInt(MAX_STRUCTS);

    while (numGlobalsLeftToGenerate > 0 || numNonMainFunctionsLeftToGenerate > 0
          || numStructsLeftToGenerate > 0) {
      switch (generator.nextInt(3)) {
        case 0:
          if (numNonMainFunctionsLeftToGenerate > 0) {
            numNonMainFunctionsLeftToGenerate--;
            FunctionDefinition functionDefinition = fuzzFunction();
            fuzzingContext.addFunction(functionDefinition.getPrototype());
            decls.add(functionDefinition);
          }
          continue;
        case 1:
          if (numGlobalsLeftToGenerate > 0) {
            numGlobalsLeftToGenerate--;
            VariablesDeclaration globals = fuzzGlobal();
            for (VariableDeclInfo declInfo : globals.getDeclInfos()) {
              fuzzingContext.addGlobal(declInfo.getName(),
                    getType(globals.getBaseType(), declInfo.getArrayInfo()));
            }
            decls.add(globals);
          }
          continue;
        case 2:
          if (numStructsLeftToGenerate > 0) {
            numStructsLeftToGenerate--;
            StructDefinitionType struct = fuzzStruct();
            fuzzingContext.addStruct(struct);
            decls.add(new VariablesDeclaration(struct));
          }
          continue;
        default:
          assert false;
      }
    }

    FunctionPrototype mainPrototype = new FunctionPrototype("main",
          VoidType.VOID,
          new ArrayList<>());
    fuzzingContext.enterFunction(mainPrototype);
    decls.add(new FunctionDefinition(
          mainPrototype, fuzzBlockStmt(false)));
    fuzzingContext.leaveFunction();

    return new TranslationUnit(Optional.of(shadingLanguageVersion), decls);
  }

  private StructDefinitionType fuzzStruct() {
    List<String> names = new ArrayList<>();
    List<Type> types = new ArrayList<>();
    int numFields = generator.nextPositiveInt(MAX_FIELDS_PER_STRUCT);

    for (int i = 0; i < numFields; i++) {
      names.add(createName("a"));
      types.add(fuzzType());
    }

    return new StructDefinitionType(
          new StructNameType(createName("S")),
                names,
                types);
  }

  private Type fuzzType() {
    List<Type> candidates = new ArrayList<>();
    candidates.addAll(BasicType.allBasicTypes());
    candidates.addAll(fuzzingContext.getStructDeclarations()
        .stream()
        .map(item -> item.getStructNameType())
        .collect(Collectors.toList()));
    return candidates.get(generator.nextInt(candidates.size()));
  }

  private Type getType(Type baseType, ArrayInfo arrayInfo) {
    if (arrayInfo == null) {
      return baseType;
    }
    return new ArrayType(baseType, arrayInfo);
  }

  private FunctionDefinition fuzzFunction() {

    fuzzingContext.enterScope();
    List<ParameterDecl> params = new ArrayList<>();
    for (int i = 0; i < generator.nextInt(MAX_FUNCTION_PARAMS); i++) {
      final String name = "p" + i;
      final Type type = fuzzType();
      // TODO: consider adding array parameters
      final ParameterDecl parameterDecl = new ParameterDecl(name, type, null);
      fuzzingContext.addParameter(parameterDecl);
      params.add(parameterDecl);

    }
    FunctionPrototype prototype = new FunctionPrototype(createName("f"),
          fuzzType(),
          params);
    fuzzingContext.enterFunction(prototype);
    final BlockStmt body = fuzzBlockStmt(false);
    if (!containsReturnStmt(body)) {
      body.addStmt(fuzzReturnStmt());
    }

    FunctionDefinition functionDefinition = new FunctionDefinition(
          prototype, body);
    fuzzingContext.leaveFunction();
    fuzzingContext.leaveScope();
    return functionDefinition;
  }

  private boolean containsReturnStmt(IAstNode node) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitReturnStmt(ReturnStmt returnStmt) {
        predicateHolds();
      }
    }.test(node);
  }

  private VariablesDeclaration fuzzGlobal() {
    // TODO: extend to allow generation of multiple globals per declaration group
    // TODO: extend to fuzz initializers and array globals
    return new VariablesDeclaration(fuzzType(),
          new VariableDeclInfo("g" + freshId(), null, null));
  }


  private BlockStmt fuzzBlockStmt(boolean introducesNewScope) {
    if (introducesNewScope) {
      fuzzingContext.enterScope();
    }
    List<Stmt> stmts = new ArrayList<>();
    // Populate with null statements for now
    final int numStmts = generator.nextInt(MAX_BLOCK_STMTS);
    for (int i = 0; i < numStmts; i++) {
      stmts.add(fuzzStmt());
    }
    if (introducesNewScope) {
      fuzzingContext.leaveScope();
    }
    return new BlockStmt(stmts, introducesNewScope);
  }

  public Stmt fuzzStmt() {
    while (true) {
      int num = generator.nextInt(120);
      if (inRange(num, 0, 5) && fuzzingContext.inLoop()) {
        return new ContinueStmt();
      }
      if (inRange(num, 6, 10) && fuzzingContext.inLoop()) {
        return new BreakStmt();
      }
      if (inRange(num, 10, 15)) {
        return new NullStmt();
      }
      if (inRange(num, 15, 17)
          && generationParams.getShaderKind() == ShaderKind.FRAGMENT) {
        return new DiscardStmt();
      }

      if (inRange(num, 17, 27)
            && belowStmtNestingDepth()) {
        return fuzzBlockStmt(true);
      }

      if (inRange(num, 27, 57)) {
        return fuzzExprStmt();
      }

      if (inRange(num, 57, 62) && fuzzingContext.hasEnclosingFunction()) {
        return fuzzReturnStmt();
      }

      if (inRange(num, 62, 82)) {
        return fuzzDeclarationStmt();
      }

      if (inRange(num, 82, 92) && belowStmtNestingDepth()) {
        return fuzzIfStmt();
      }

      if (inRange(num, 92, 102) && belowStmtNestingDepth()) {
        return fuzzForStmt();
      }

      if (inRange(num, 102, 110) && belowStmtNestingDepth()) {
        return fuzzWhileStmt();
      }

      if (inRange(num, 110, 112) && belowStmtNestingDepth()) {
        return fuzzDoStmt();
      }

    }
  }

  private Stmt fuzzReturnStmt() {
    return new ReturnStmt(
          fuzzExpr(fuzzingContext.getEnclosingFunction().getReturnType(),
                false, false, 0));
  }

  private DeclarationStmt fuzzDeclarationStmt() {
    final Type baseType = fuzzType();
    final int numDeclsInGroup = generator.nextPositiveInt(MAX_LOCALS_IN_DECL_GROUP);

    List<VariableDeclInfo> decls = new ArrayList<>();
    for (int i = 0; i < numDeclsInGroup; i++) {
      final String name = createName("v");
      ArrayInfo arrayInfo = null;
      if (generator.nextInt(10) < 3) { // TODO Hack for now, needs thought
        arrayInfo = new ArrayInfo(generator.nextPositiveInt(MAX_ARRAY_SIZE));
      }
      fuzzingContext.addLocal(name, arrayInfo == null ? baseType : getType(baseType, arrayInfo));
      decls.add(new VariableDeclInfo(name, arrayInfo, null)); // TODO: no initializer for now
    }
    return new DeclarationStmt(new VariablesDeclaration(baseType,
          decls));
  }

  private ExprStmt fuzzExprStmt() {
    return new ExprStmt(fuzzExpr(fuzzType(), false, false, 0));
  }

  private ForStmt fuzzForStmt() {
    fuzzingContext.enterScope();
    final Stmt init = generator.nextBoolean() ? fuzzDeclarationStmt() : fuzzExprStmt();
    final Expr condition = fuzzExpr(BasicType.BOOL, false, false, 0);
    final Expr increment = fuzzExpr(fuzzType(),
          false, false, 0);
    final Stmt body = fuzzBlockStmt(false);
    fuzzingContext.leaveScope();
    return new ForStmt(init, condition, increment, body);
  }

  private boolean belowStmtNestingDepth() {
    return fuzzingContext.belowBlockNestingDepth(MAX_BLOCK_NESTING_DEPTH);
  }

  private IfStmt fuzzIfStmt() {
    final Expr condition = fuzzExpr(BasicType.BOOL, false, false, 0);
    final Stmt thenStmt = fuzzBlockStmt(true);
    Stmt elseStmt = null;
    if (generator.nextBoolean()) {
      elseStmt = fuzzBlockStmt(true);
    }
    return new IfStmt(condition, thenStmt, elseStmt);
  }

  private WhileStmt fuzzWhileStmt() {
    final Expr condition = fuzzExpr(BasicType.BOOL, false, false, 0);
    final Stmt body = fuzzBlockStmt(true);
    return new WhileStmt(condition, body);
  }

  private DoStmt fuzzDoStmt() {
    final Expr condition = fuzzExpr(BasicType.BOOL, false, false, 0);
    final Stmt body = fuzzBlockStmt(true);
    return new DoStmt(body, condition);
  }

  private boolean inRange(int num, int lower, int upper) {
    assert lower < upper;
    return lower <= num && num < upper;
  }

  private String freshId() {
    return new Integer(nextId++).toString();
  }

  private String createName(String temp) {
    if (fuzzedDeclarationPrefix == null) {
      throw new RuntimeException("A fuzzer must be initialized with a fuzzed declaration prefix "
            + "in order to generate declarations.");
    }
    return temp + "_" + fuzzedDeclarationPrefix + "_" + freshId();
  }

  private Type stripQualifiers(Type type) {
    // TODO: when implementing this the only compound type I considered was ArrayType.
    if (type instanceof QualifiedType) {
      return stripQualifiers(type.getWithoutQualifiers());
    }
    if (type instanceof ArrayType) {
      return new ArrayType(stripQualifiers(((ArrayType) type).getBaseType()),
            ((ArrayType) type).getArrayInfo());
    }
    return type;
  }

}
