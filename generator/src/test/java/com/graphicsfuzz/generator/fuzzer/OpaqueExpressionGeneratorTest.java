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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
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
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.SupportedTypes;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.fuzzer.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

public class OpaqueExpressionGeneratorTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testGeneratesValidExpressions() throws Exception {
    final IRandom generator = new RandomWrapper(0);
    for (ShadingLanguageVersion shadingLanguageVersion : Arrays.asList(
          ShadingLanguageVersion.ESSL_300,
          ShadingLanguageVersion.ESSL_100)) {
      for (BasicType t : BasicType.allBasicTypes()) {
        if (!SupportedTypes.supported(t, shadingLanguageVersion)) {
          continue;
        }
        final GenerationParams generationParams = GenerationParams.large(ShaderKind.FRAGMENT, true);
        final Expr expr = new OpaqueExpressionGenerator(generator, generationParams,
            shadingLanguageVersion)
              .applyIdentityFunction(t.getCanonicalConstant(), t, false, 0,
                    new Fuzzer(new FuzzingContext(new Scope(null)), shadingLanguageVersion, generator,
                          generationParams));

        final TranslationUnit tu = new TranslationUnit(Optional.of(ShadingLanguageVersion.ESSL_310),
              Arrays.asList(
                    new PrecisionDeclaration("precision mediump float;"),
                    new FunctionDefinition(
                          new FunctionPrototype("main", VoidType.VOID, new ArrayList<>()),
                          new BlockStmt(
                                Arrays.asList(new DeclarationStmt(new VariablesDeclaration(t,
                                            new VariableDeclInfo("x", null, null))),
                                      new ExprStmt(new BinaryExpr(
                                            new VariableIdentifierExpr("x"),
                                            expr, BinOp.ASSIGN))), false))));
        Generate.addInjectionSwitchIfNotPresent(tu);
        final File file = temporaryFolder.newFile("ex.frag");
        try (PrintStream stream = new PrintStream(new FileOutputStream(file))) {
          PrettyPrinterVisitor.emitShader(
              tu,
              Optional.empty(),
              stream,
              PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
              PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER,
              true
          );
        }
        ExecResult execResult = ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER, file);
        assertEquals(0, execResult.res);
        file.delete();
      }
    }
  }

}
