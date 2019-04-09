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

package com.graphicsfuzz.generator.transformation;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.donation.DonationContext;
import com.graphicsfuzz.generator.transformation.injection.BlockInjectionPoint;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.server.thrift.ImageJob;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;

public class DonateLiveCodeTransformationTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void prepareStatementToDonate() throws Exception {

    final DonateLiveCodeTransformation dlc =
        new DonateLiveCodeTransformation(IRandom::nextBoolean, testFolder.getRoot(), GenerationParams.normal(ShaderKind.FRAGMENT, true),
            false);

    DonationContext dc = new DonationContext(new DiscardStmt(), new HashMap<>(),
        new ArrayList<>(), null);
    Stmt donated = dlc.prepareStatementToDonate(null, dc, TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
        ShadingLanguageVersion.ESSL_100);

    assertFalse(new CheckPredicateVisitor() {
      @Override
      public void visitDiscardStmt(DiscardStmt discardStmt) {
        predicateHolds();
      }
    }.test(donated));

  }

  @Test
  public void checkMutateSpecialCase() throws Exception {

    // This test aimed to expose an issue, but did not succeed.  It's been left
    // here in the spirit of "why delete a test?"

    final String reference = "#version 300 es\n"
          + "void main() {"
          + "  int t;"
          + "  {"
          + "  }"
          + "  gl_FragColor = vec4(float(t));"
          + "}";

    final IRandom generator = new RandomWrapper(0);

    for (int i = 0; i < 10; i++) {

      final DonateLiveCodeTransformation donateLiveCode =
            new DonateLiveCodeTransformation(item -> true, testFolder.getRoot(), GenerationParams.normal(ShaderKind.FRAGMENT, true),
                  false);

      final TranslationUnit referenceTu = ParseHelper.parse(reference);

      BlockInjectionPoint blockInjectionPoint =

            new ScopeTreeBuilder() {

              BlockInjectionPoint blockInjectionPoint;

              @Override
              public void visitBlockStmt(BlockStmt stmt) {
                super.visitBlockStmt(stmt);
                if (stmt.getNumStmts() == 0) {
                  blockInjectionPoint = new BlockInjectionPoint(stmt, null, enclosingFunction,
                        false, currentScope);
                }
              }

              BlockInjectionPoint getBlockInjectionPoint(TranslationUnit tu) {
                visit(tu);
                return blockInjectionPoint;
              }
            }.getBlockInjectionPoint(referenceTu);

      final DeclarationStmt declarationStmt = new DeclarationStmt(
            new VariablesDeclaration(
                  BasicType.INT,
                  new VariableDeclInfo("a", null,
                        new ScalarInitializer(
                              new BinaryExpr(
                                    new BinaryExpr(
                                          new BinaryExpr(
                                                new BinaryExpr(
                                                      new VariableIdentifierExpr("x1"),
                                                      new VariableIdentifierExpr("x2"),
                                                      BinOp.ADD
                                                ),
                                                new VariableIdentifierExpr("x3"),
                                                BinOp.ADD
                                          ),
                                          new VariableIdentifierExpr("x4"),
                                          BinOp.ADD
                                    ),
                                    new VariableIdentifierExpr("x5"),
                                    BinOp.ADD
                              )
                        )
                  )));
      final Map<String, Type> freeVariables = new HashMap<>();
      freeVariables.put("x1", BasicType.INT);
      freeVariables.put("x2", BasicType.INT);
      freeVariables.put("x3", BasicType.INT);
      freeVariables.put("x4", BasicType.INT);
      freeVariables.put("x5", BasicType.INT);
      Stmt toDonate = donateLiveCode.prepareStatementToDonate(blockInjectionPoint,
            new DonationContext(
                  declarationStmt,
                  freeVariables,
                  new ArrayList<>(),
                  new FunctionDefinition(new FunctionPrototype("foo", VoidType.VOID,
                        new ArrayList<>()), null)),
            TransformationProbabilities.onlyLiveCodeAlwaysSubstitute(),
            generator,
          referenceTu.getShadingLanguageVersion());

      blockInjectionPoint.inject(toDonate);

      final IdentityTransformation identityTransformation =
            new IdentityTransformation();

      identityTransformation.apply(referenceTu, TransformationProbabilities.onlyMutateExpressions(),
            generator,
            GenerationParams.large(ShaderKind.FRAGMENT, true)
      );
    }
  }

  @Test
  public void checkFunctionDeclsUnique() throws Exception {
    // This test injects live code from a donor into a reference, such that the reference contains
    // an injected function prototype followed by the injected function definition. We then create a
    // parent map for the modified reference to ensure that there is no aliasing. I.e. the
    // function prototype object is not reused in the function definition.

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final File donors = testFolder.newFolder("donors");
    final File referenceFile = testFolder.newFile("reference.json");
    final File variantFile = testFolder.newFile("variant.json");

    {
      // A call to "bar", which calls "foo" via its prototype (i.e. "foo" is defined after "bar").
      final String donorSource =
          "#version 300 es\n"
          + "\n"
          + "int foo(int f);\n"
          + "\n"
          + "int bar(int b) {\n"
          + "    return foo(b);\n"
          + "}\n"
          + "\n"
          + "int foo(int f) {\n"
          + "    return f + 1;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "    bar(1);\n"
          + "}\n";

      fileOps.writeShaderJobFileFromImageJob(
          new ImageJob()
              .setFragmentSource(donorSource)
              .setUniformsInfo("{}"),
          new File(donors, "donor.json")
      );
    }

    {
      final String referenceSource = "#version 300 es\n"
          + "void main() {"
          + "  "
          + "}";

      fileOps.writeShaderJobFileFromImageJob(
          new ImageJob()
              .setFragmentSource(referenceSource)
              .setUniformsInfo("{}"),
          referenceFile

      );
    }

    final ShaderJob referenceShaderJob = fileOps.readShaderJobFile(referenceFile);

    DonateLiveCodeTransformation transformation =
        new DonateLiveCodeTransformation(IRandom::nextBoolean, donors,
        GenerationParams.normal(ShaderKind.FRAGMENT, true), false);

    assert referenceShaderJob.getFragmentShader().isPresent();

    boolean result = transformation.apply(
        referenceShaderJob.getFragmentShader().get(),
        TransformationProbabilities.onlyLiveCodeAlwaysSubstitute(),
        new RandomWrapper(0),
        GenerationParams.normal(ShaderKind.FRAGMENT, true)
    );

    Assert.assertTrue(result);

    fileOps.writeShaderJobFile(referenceShaderJob, variantFile);
    fileOps.areShadersValid(variantFile, true);

    // Creating a parent map checks that there is no aliasing in the AST.
    IParentMap.createParentMap(referenceShaderJob.getFragmentShader().get());
  }

  @Test
  public void verySimpleDonorAndSourceNoPrecision() throws Exception {
    // This test injects live code from a donor into a reference. The donor and reference contain
    // almost no code. The modified reference is output and validated. This caught an issue where
    // shaders without precision qualifiers became invalid because the injected color and coord
    // variables did not have precision qualifiers.

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final File donors = testFolder.newFolder("donors");
    final File referenceFile = testFolder.newFile("reference.json");
    final File variantFile = testFolder.newFile("variant.json");

    {
      final String donorSource =
          "#version 300 es\n"
              + "void main() {\n"
              + "    1;\n"
              + "}\n";

      fileOps.writeShaderJobFileFromImageJob(
          new ImageJob()
              .setFragmentSource(donorSource)
              .setUniformsInfo("{}"),
          new File(donors, "donor.json")
      );
    }

    {
      final String referenceSource = "#version 300 es\n"
          + "void main() {"
          + "  "
          + "}";

      fileOps.writeShaderJobFileFromImageJob(
          new ImageJob()
              .setFragmentSource(referenceSource)
              .setUniformsInfo("{}"),
          referenceFile

      );
    }

    final ShaderJob referenceShaderJob = fileOps.readShaderJobFile(referenceFile);

    DonateLiveCodeTransformation transformation =
        new DonateLiveCodeTransformation(IRandom::nextBoolean, donors,
            GenerationParams.normal(ShaderKind.FRAGMENT, true), false);

    assert referenceShaderJob.getFragmentShader().isPresent();

    boolean result = transformation.apply(
        referenceShaderJob.getFragmentShader().get(),
        TransformationProbabilities.onlyLiveCodeAlwaysSubstitute(),
        new RandomWrapper(0),
        GenerationParams.normal(ShaderKind.FRAGMENT, true)
    );

    Assert.assertTrue(result);

    fileOps.writeShaderJobFile(referenceShaderJob, variantFile);
    fileOps.areShadersValid(variantFile, true);

    // Creating a parent map checks that there is no aliasing in the AST.
    IParentMap.createParentMap(referenceShaderJob.getFragmentShader().get());
  }

}
