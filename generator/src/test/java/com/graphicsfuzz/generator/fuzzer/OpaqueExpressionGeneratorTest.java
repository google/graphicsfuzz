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

import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.SupportedTypes;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OpaqueExpressionGeneratorTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testGeneratesValidExpressions() throws Exception {
    // We make a random number generator here and pass it into functions below in order to maximise
    // diversity - even though there are differences between shading languages, there is still a
    // good chance that until such a difference is hit, generating expressions for different
    // shading languages but from the same seed will lead to identical results.
    final IRandom generator = new RandomWrapper(0);

    // These are the shading language versions we support best, so test them thoroughly.
    for (ShadingLanguageVersion shadingLanguageVersion : Arrays.asList(
        ShadingLanguageVersion.ESSL_100,
        ShadingLanguageVersion.ESSL_300,
        ShadingLanguageVersion.ESSL_310,
        ShadingLanguageVersion.ESSL_320)) {
      for (BasicType t : BasicType.allBasicTypes()) {
        if (!SupportedTypes.supported(t, shadingLanguageVersion)) {
          continue;
        }
        final TranslationUnit tu = new TranslationUnit(Optional.of(shadingLanguageVersion),
            Arrays.asList(
                new PrecisionDeclaration("precision mediump float;"),
                new FunctionDefinition(
                    new FunctionPrototype("main", VoidType.VOID, Collections.emptyList()),
                    new BlockStmt(makeMutatedExpressionAssignments(t,
                        shadingLanguageVersion, 1000, generator),
                        false))));
        Generate.addInjectionSwitchIfNotPresent(tu);
        final File shaderJobFile = temporaryFolder.newFile("ex.json");
        final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
        fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(), new PipelineInfo(),
            tu), shaderJobFile);
        assertTrue(fileOps.areShadersValid(shaderJobFile, false));
        fileOps.deleteShaderJobFile(shaderJobFile);
      }
    }
  }

  private List<Stmt> makeMutatedExpressionAssignments(BasicType basicType,
                                                      ShadingLanguageVersion shadingLanguageVersion,
                                                      int numberOfAssignments,
                                                      IRandom generator) {
    // We declare only one variable and generate a number of assignments to this variable,
    // instead of making multiple declarations and assignments.
    // numberOfAssignments should be large enough to get high coverage, e.g. 1000.
    final GenerationParams generationParams = GenerationParams.large(ShaderKind.FRAGMENT, false,
        true);
    final List<Stmt> newStmts = new ArrayList<>();
    newStmts.add(new DeclarationStmt(new VariablesDeclaration(basicType,
        new VariableDeclInfo("x", null,
            null))));
    for (int i = 0; i < numberOfAssignments; i++) {
      final OpaqueExpressionGenerator opaqueExpressionGenerator =
          new OpaqueExpressionGenerator(generator,
              generationParams, shadingLanguageVersion);
      final Fuzzer fuzzer = new Fuzzer(new FuzzingContext(new Scope()), shadingLanguageVersion,
          generator, generationParams);
      final Expr expr = opaqueExpressionGenerator.applyIdentityFunction(basicType
              .getCanonicalConstant(new Scope()),
              basicType, false, 0, fuzzer);

      newStmts.add(new ExprStmt(new BinaryExpr(new VariableIdentifierExpr("x"), expr,
          BinOp.ASSIGN)));
    }
    return newStmts;
  }

  @Test
  public void testWaysToMakeZeroAndOne() throws Exception {

    final IRandom generator = new RandomWrapper(0);
    final GenerationParams generationParams = GenerationParams.large(ShaderKind.FRAGMENT, false,
        true);

    // These are the shading language versions we support best, so test them thoroughly.
    for (ShadingLanguageVersion shadingLanguageVersion : Arrays.asList(
        ShadingLanguageVersion.ESSL_100,
        ShadingLanguageVersion.ESSL_300,
        ShadingLanguageVersion.ESSL_310,
        ShadingLanguageVersion.ESSL_320)) {
      final IdGenerator idGenerator = new IdGenerator();
      final List<Stmt> stmts = new ArrayList<>();
      final OpaqueExpressionGenerator opaqueExpressionGenerator =
          new OpaqueExpressionGenerator(generator,
              generationParams, shadingLanguageVersion);
      for (BasicType basicType : BasicType.allNumericTypes()) {
        if (!SupportedTypes.supported(basicType, shadingLanguageVersion)) {
          continue;
        }
        for (boolean constContext : Arrays.asList(true, false)) {
          stmts.addAll(makeStatementsFromFactories(generator, generationParams,
              shadingLanguageVersion,
              idGenerator, basicType, constContext, opaqueExpressionGenerator.waysToMakeZero(),
              true));
          // We do not allow making one for non-square matrices.
          if (!BasicType.allNonSquareMatrixTypes().contains(basicType)) {
            stmts.addAll(makeStatementsFromFactories(generator, generationParams,
                shadingLanguageVersion,
                idGenerator, basicType, constContext, opaqueExpressionGenerator.waysToMakeOne(),
                false));
          }
        }
      }
      final TranslationUnit tu = new TranslationUnit(Optional.of(shadingLanguageVersion),
          Arrays.asList(
              new PrecisionDeclaration("precision mediump float;"),
              new FunctionDefinition(
                  new FunctionPrototype("main", VoidType.VOID, Collections.emptyList()),
                  new BlockStmt(stmts,
                      false))));
      Generate.addInjectionSwitchIfNotPresent(tu);
      final File shaderJobFile = temporaryFolder.newFile("ex.json");
      final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
      fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(), new PipelineInfo(),
          tu), shaderJobFile);
      assertTrue(fileOps.areShadersValid(shaderJobFile, false));
      fileOps.deleteShaderJobFile(shaderJobFile);
    }
  }

  private List<Stmt> makeStatementsFromFactories(IRandom generator,
                                                 GenerationParams generationParams,
                                                 ShadingLanguageVersion shadingLanguageVersion,
                                                 IdGenerator idGenerator,
                                                 BasicType typeToGenerate, boolean constContext,
                                                 List<OpaqueZeroOneFactory> factories,
                                                 boolean makingZero) {
    List<Stmt> result = new ArrayList<>();
    for (OpaqueZeroOneFactory factory : factories) {
      final Optional<Expr> expr = factory.tryMakeOpaque(typeToGenerate, constContext, 0,
          new Fuzzer(new FuzzingContext(new Scope()), shadingLanguageVersion,
              generator, generationParams), makingZero);
      if (expr.isPresent()) {
        final Type baseType = constContext ? new QualifiedType(typeToGenerate,
            Collections.singletonList(TypeQualifier.CONST)) : typeToGenerate;
        result.add(new DeclarationStmt(
            new VariablesDeclaration(
                baseType,
                new VariableDeclInfo(
                    "v" + idGenerator.freshId(),
                    null,
                    new Initializer(expr.get())
                )
            )
        ));
      }
    }
    return result;
  }

}
