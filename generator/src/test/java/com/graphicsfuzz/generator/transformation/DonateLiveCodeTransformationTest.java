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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.donation.DonationContext;
import com.graphicsfuzz.generator.transformation.injection.BlockInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.server.thrift.ImageJob;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DonateLiveCodeTransformationTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  private DonateLiveCodeTransformation getDummyTransformationObject() {
    return new DonateLiveCodeTransformation(IRandom::nextBoolean, testFolder.getRoot(),
        GenerationParams.normal(ShaderKind.FRAGMENT, false, true),
        false);
  }

  @Test
  public void adaptTranslationUnitForSpecificDonationDiscardRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 320 es\n"
        + "void main() {\n"
        + "  discard;\n"
        + "}\n");
    final DonateLiveCodeTransformation dlc = getDummyTransformationObject();
    dlc.adaptTranslationUnitForSpecificDonation(tu, new RandomWrapper(0));
    assertFalse(new CheckPredicateVisitor() {
      @Override
      public void visitDiscardStmt(DiscardStmt discardStmt) {
        predicateHolds();
      }
    }.test(tu));
  }

  @Test
  public void prepareStatementToDonateTopLevelBreakRemoved() throws Exception {

    // Checks that a top-level 'break' gets removed, even when injecting into a loop or
    // switch.

    final DonateLiveCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  for (int i = 0; i < 10; i ++)\n"
        + "     if (i > 5) break;\n"
        + "}\n");

    final TranslationUnit reference = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  for(int i = 0; i < 100; i++) {\n"
        + "    switch (i) {\n"
        + "      case 0:\n"
        + "        i++;\n"
        + "      default:\n"
        + "        i++;\n"
        + "    }\n"
        + "  }\n"
        + "}\n");

    for (IInjectionPoint injectionPoint : new InjectionPoints(reference, new RandomWrapper(0),
        item -> true).getAllInjectionPoints()) {

      final Stmt toDonate = ((ForStmt) donor.getMainFunction().getBody().getStmt(0)).getBody()
          .clone();
      assert toDonate instanceof IfStmt;

      final DonationContext dc = new DonationContext(toDonate, new HashMap<>(),
          new ArrayList<>(), donor.getMainFunction());

      final Stmt donated = dlc.prepareStatementToDonate(injectionPoint, dc,
          TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
          ShadingLanguageVersion.ESSL_310);
      assertEquals("{\n"
          + " if(i > 5)\n"
          + "  1;\n"
          + "}\n", donated.getText());
    }

  }

  @Test
  public void prepareStatementToDonateTopLevelContinueRemoved() throws Exception {

    // Checks that a top-level 'continue' gets removed, even when injecting into a loop.

    final DonateLiveCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  for (int i = 0; i < 10; i ++)\n"
        + "     if (i > 5) continue;\n"
        + "}\n");

    final TranslationUnit reference = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  for(int i = 0; i < 100; i++) {\n"
        + "      1;\n"
        + "  }\n"
        + "}\n");

    for (IInjectionPoint injectionPoint : new InjectionPoints(reference, new RandomWrapper(0),
        item -> true).getAllInjectionPoints()) {
      final Stmt toDonate = ((ForStmt) donor.getMainFunction().getBody().getStmt(0)).getBody()
          .clone();
      assert toDonate instanceof IfStmt;

      final DonationContext dc = new DonationContext(toDonate, new HashMap<>(),
          new ArrayList<>(), donor.getMainFunction());

      final Stmt donated = dlc.prepareStatementToDonate(injectionPoint, dc,
          TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
          ShadingLanguageVersion.ESSL_100);
      assertEquals("{\n"
          + " if(i > 5)\n"
          + "  1;\n"
          + "}\n", donated.getText());
    }

  }

  @Test
  public void prepareStatementToDonateTopLevelCaseAndDefaultRemoved() throws Exception {
    // Checks that top-level 'case' and 'default' labels get removed, even when injecting into
    // a switch.

    final DonateLiveCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  int x = 3;\n"
        + "  switch (x) {\n"
        + "    case 0:\n"
        + "      x++;\n"
        + "    default:\n"
        + "      x++;\n"
        + "  }\n"
        + "}\n");

    final TranslationUnit reference = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  switch (0) {\n"
        + "    case 1:\n"
        + "      1;\n"
        + "    default:\n"
        + "      2;\n"
        + "  }\n"
        + "}\n");

    for (IInjectionPoint injectionPoint : new InjectionPoints(reference, new RandomWrapper(0),
        item -> true).getAllInjectionPoints()) {

      final Stmt toDonate = ((SwitchStmt) donor.getMainFunction().getBody().getStmt(1)).getBody()
          .clone();

      final DonationContext dc = new DonationContext(toDonate, new HashMap<>(),
          new ArrayList<>(), donor.getMainFunction());

      final Stmt donated = dlc.prepareStatementToDonate(injectionPoint, dc,
          TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
          ShadingLanguageVersion.ESSL_310);
      assertEquals("{\n"
          + " {\n"
          + "  1;\n"
          + "  x ++;\n"
          + "  1;\n"
          + "  x ++;\n"
          + " }\n"
          + "}\n", donated.getText());
    }
  }

  @Test
  public void prepareStatementToDonateBreakFromLoopKept() throws Exception {
    // Checks that a 'break' in a loop gets kept if the whole loop is donated.

    final DonateLiveCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  for (int i = 0; i < 10; i ++)\n"
        + "     if (i > 5) break;\n"
        + "}\n");

    final TranslationUnit reference = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  ;\n"
        + "  for(int i = 0; i < 100; i++) {\n"
        + "  }\n"
        + "}\n");

    for (IInjectionPoint injectionPoint : new InjectionPoints(reference, new RandomWrapper(0),
        item -> true).getAllInjectionPoints()) {

      final Stmt toDonate = donor.getMainFunction().getBody().getStmt(0)
          .clone();
      assert toDonate instanceof ForStmt;

      final DonationContext dc = new DonationContext(toDonate, new HashMap<>(),
          new ArrayList<>(), donor.getMainFunction());

      final Stmt donated = dlc.prepareStatementToDonate(injectionPoint, dc,
          TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
          ShadingLanguageVersion.ESSL_100);
      assertEquals("{\n"
          + " for(int i = 0; i < 10; i ++)\n"
          + "  if(i > 5)\n"
          + "   break;\n"
          + "}\n", donated.getText());
    }
  }

  @Test
  public void prepareStatementToDonateSwitchWithBreakAndDefaultKept() throws Exception {
    // Checks that 'case', 'default' and 'break' occurring in a switch are kept if the whole
    // switch statement is donated.

    final DonateLiveCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  switch (0) {\n"
        + "    case 0:\n"
        + "      1;\n"
        + "      break;\n"
        + "    default:\n"
        + "      2;\n"
        + "  }\n"
        + "}\n");

    final TranslationUnit reference = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  ;\n"
        + "  switch (0) {\n"
        + "    case 1:\n"
        + "      1;\n"
        + "    default:\n"
        + "      2;\n"
        + "  }\n"
        + "}\n");

    for (IInjectionPoint injectionPoint : new InjectionPoints(reference, new RandomWrapper(0),
        item -> true).getAllInjectionPoints()) {

      final Stmt toDonate = donor.getMainFunction().getBody().getStmt(0).clone();
      assert toDonate instanceof SwitchStmt;

      final DonationContext dc = new DonationContext(toDonate, new HashMap<>(),
          new ArrayList<>(), donor.getMainFunction());

      final Stmt donated = dlc.prepareStatementToDonate(injectionPoint, dc,
          TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
          ShadingLanguageVersion.ESSL_310);
      assertEquals("{\n"
          + " switch(0)\n"
          + "  {\n"
          + "   case 0:\n"
          + "   1;\n"
          + "   break;\n"
          + "   default:\n"
          + "   2;\n"
          + "  }\n"
          + "}\n", donated.getText());
    }
  }

  @Test
  public void prepareStatementToDonateContinueInLoopKept() throws Exception {
    // Checks that a 'continue' in a loop gets kept if the whole loop is donated.

    final DonateLiveCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  for (int i = 0; i < 10; i ++)\n"
        + "     if (i > 5) continue;\n"
        + "}\n");

    final TranslationUnit reference = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  ;\n"
        + "  for(int i = 0; i < 100; i++) {\n"
        + "  }\n"
        + "}\n");

    for (IInjectionPoint injectionPoint : new InjectionPoints(reference, new RandomWrapper(0),
        item -> true).getAllInjectionPoints()) {

      final Stmt toDonate = donor.getMainFunction().getBody().getStmt(0).clone();
      assert toDonate instanceof ForStmt;

      DonationContext dc = new DonationContext(toDonate, new HashMap<>(),
          new ArrayList<>(), donor.getMainFunction());

      final Stmt donated = dlc.prepareStatementToDonate(injectionPoint, dc,
          TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
          ShadingLanguageVersion.ESSL_100);
      assertEquals("{\n"
          + " for(int i = 0; i < 10; i ++)\n"
          + "  if(i > 5)\n"
          + "   continue;\n"
          + "}\n", donated.getText());
    }
  }

  @Test
  public void checkMutateSpecialCase() throws Exception {

    // This test aimed to expose an issue, but did not succeed.  It's been left
    // here in the spirit of "why delete a test?"

    final String reference = "#version 300 es\n"
        + "void main() {\n"
        + "  int t;\n"
        + "  {\n"
        + "  }\n"
        + "  gl_FragColor = vec4(float(t));\n"
        + "}\n";

    final IRandom generator = new RandomWrapper(0);

    for (int i = 0; i < 10; i++) {

      final DonateLiveCodeTransformation donateLiveCode =
          new DonateLiveCodeTransformation(item -> true, testFolder.getRoot(),
              GenerationParams.normal(ShaderKind.FRAGMENT, false, true),
              false);

      final TranslationUnit referenceTu = ParseHelper.parse(reference);

      BlockInjectionPoint blockInjectionPoint =

          new ScopeTrackingVisitor() {

            BlockInjectionPoint blockInjectionPoint;

            @Override
            public void visitBlockStmt(BlockStmt stmt) {
              super.visitBlockStmt(stmt);
              if (stmt.getNumStmts() == 0) {
                blockInjectionPoint = new BlockInjectionPoint(stmt, null, getEnclosingFunction(),
                    false, false, getCurrentScope());
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
                  new Initializer(
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
          GenerationParams.large(ShaderKind.FRAGMENT, false, true)
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
          + "void main() {\n"
          + "}\n";

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
            GenerationParams.normal(ShaderKind.FRAGMENT, false, true), false);

    assert referenceShaderJob.getFragmentShader().isPresent();

    boolean result = transformation.apply(
        referenceShaderJob.getFragmentShader().get(),
        TransformationProbabilities.onlyLiveCodeAlwaysSubstitute(),
        new RandomWrapper(0),
        GenerationParams.normal(ShaderKind.FRAGMENT, false, true)
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
          + "void main() {\n"
          + "}\n";

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
            GenerationParams.normal(ShaderKind.FRAGMENT, false, true), false);

    assert referenceShaderJob.getFragmentShader().isPresent();

    boolean result = transformation.apply(
        referenceShaderJob.getFragmentShader().get(),
        TransformationProbabilities.onlyLiveCodeAlwaysSubstitute(),
        new RandomWrapper(0),
        GenerationParams.normal(ShaderKind.FRAGMENT, false, true)
    );

    Assert.assertTrue(result);

    fileOps.writeShaderJobFile(referenceShaderJob, variantFile);
    fileOps.areShadersValid(variantFile, true);

    // Creating a parent map checks that there is no aliasing in the AST.
    IParentMap.createParentMap(referenceShaderJob.getFragmentShader().get());
  }

  @Test
  public void testArrayAccessesAreInBounds() throws Exception {
    // This checks that array accesses are correctly made in-bounds when injecting live code.

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final File donors = testFolder.newFolder("donors");
    final File referenceFile = testFolder.newFile("reference.json");

    {
      // This donor is designed to have a high chance of leading to an array access getting injected
      // such that the array indexing expression will be a free variable for which a fuzzed initial
      // value will be created.
      final String donorSource =
          "#version 300 es\n"
              + "void main() {\n"
              + " int x = 0;\n"
              + " {\n"
              + "  int A[1];\n"
              + "  A[x] = 42;\n"
              + "  {\n"
              + "   int B[1];\n"
              + "   B[x] = 42;\n"
              + "   {\n"
              + "    int C[1];\n"
              + "    C[x] = 42;\n"
              + "   }\n"
              + "  }\n"
              + " }\n"
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
          + "void main() {\n"
          + "}\n";

      fileOps.writeShaderJobFileFromImageJob(
          new ImageJob()
              .setFragmentSource(referenceSource)
              .setUniformsInfo("{}"),
          referenceFile

      );
    }

    int noCodeDonatedCount = 0;

    // Try the following a few times, so that there is a good chance of triggering the issue
    // this test was used to catch, should it return:
    for (int seed = 0; seed < 15; seed++) {

      final ShaderJob referenceShaderJob = fileOps.readShaderJobFile(referenceFile);

      // Do live code donation.
      DonateLiveCodeTransformation transformation =
          new DonateLiveCodeTransformation(IRandom::nextBoolean, donors,
              GenerationParams.normal(ShaderKind.FRAGMENT, false, true), false);

      assert referenceShaderJob.getFragmentShader().isPresent();

      boolean result = transformation.apply(
          referenceShaderJob.getFragmentShader().get(),
          TransformationProbabilities.onlyLiveCodeAlwaysSubstitute(),
          new RandomWrapper(seed),
          GenerationParams.normal(ShaderKind.FRAGMENT, false, true)
      );

      if (!result) {
        ++noCodeDonatedCount;
        continue;
      }

      // An array access injected into the shader must either be (1) already in bounds, or
      // (2) made in bounds.  Only in the former case can the array index be a variable identifier
      // expression, and in that case the expression cannot realistically be statically in bounds
      // if the initializer for that expression is under a _GLF_FUZZED macro.  (There is a tiny
      // chance that the fuzzed expression might statically evaluate to 0, but currently a
      // _GLF_FUZZED macro will be treated as not statically in bounds, so the access would be
      // made in bounds in that case.)
      //
      // The following thus checks that if an array is indexed directly by a variable reference,
      // the initializer for that variable is not a function call expression.
      new ScopeTrackingVisitor() {

        @Override
        public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
          super.visitArrayIndexExpr(arrayIndexExpr);
          if (arrayIndexExpr.getIndex() instanceof VariableIdentifierExpr) {
            final ScopeEntry scopeEntry = getCurrentScope().lookupScopeEntry(
                ((VariableIdentifierExpr) arrayIndexExpr.getIndex()).getName());
            assertTrue(scopeEntry.hasVariableDeclInfo());
            assertNotNull(scopeEntry.getVariableDeclInfo().getInitializer());
            assertFalse((scopeEntry.getVariableDeclInfo().getInitializer())
                .getExpr() instanceof FunctionCallExpr);
          }
        }

      }.visit(referenceShaderJob.getFragmentShader().get());

    }
    // The above code tests donation of live code, but there is still a chance that no code will
    // be donated. We assert that this happens < 10 times to ensure that we get some test
    // coverage, but this could fail due to bad luck.
    Assert.assertTrue(
        "Donation failure count should be < 10, " + noCodeDonatedCount,
        noCodeDonatedCount < 10
    );

  }

  private void checkLiveCodeDonationRepeatedly(String donorSource,
                                               String referenceSource,
                                               Predicate<TranslationUnit> check,
                                               int numIterations) throws Exception {
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final File donors = testFolder.newFolder("donors");
    final File referenceFile = testFolder.newFile("reference.json");
    fileOps.writeShaderJobFile(
        new GlslShaderJob(
            Optional.empty(),
            new PipelineInfo(),
            ParseHelper.parse(donorSource)),
        new File(donors, "donor.json")
    );
    fileOps.writeShaderJobFile(
        new GlslShaderJob(
            Optional.empty(),
            new PipelineInfo(),
            ParseHelper.parse(referenceSource)),
        referenceFile
    );

    int noCodeDonatedCount = 0;

    // Try the following a few times, so that there is a good chance of triggering the issue
    // this test was used to catch, should it return:
    for (int seed = 0; seed < numIterations; seed++) {

      final ShaderJob referenceShaderJob = fileOps.readShaderJobFile(referenceFile);

      // Do live code donation.
      final DonateLiveCodeTransformation transformation =
          new DonateLiveCodeTransformation(IRandom::nextBoolean, donors,
              GenerationParams.normal(ShaderKind.FRAGMENT, false, true), false);

      assert referenceShaderJob.getFragmentShader().isPresent();

      final boolean result = transformation.apply(
          referenceShaderJob.getFragmentShader().get(),
          TransformationProbabilities.onlyLiveCodeAlwaysSubstitute(),
          new RandomWrapper(seed),
          GenerationParams.normal(ShaderKind.FRAGMENT, false, true)
      );

      // Check that the resulting shader typechecks.
      new Typer(referenceShaderJob.getFragmentShader().get());

      // Check that the user-provided predicate holds.
      assertTrue(check.test(referenceShaderJob.getFragmentShader().get()));

      if (result) {
        final File tempFile = testFolder.newFile("shader" + seed + ".json");
        fileOps.writeShaderJobFile(referenceShaderJob, tempFile);
        // This will fail if the shader job turns out to be invalid.
        fileOps.areShadersValid(tempFile, true);
      } else {
        ++noCodeDonatedCount;
      }

    }

    // The above code tests donation of live code, but there is still a chance that no code will
    // be donated. We assert that this doesn't happen too often to ensure that we get some test
    // coverage, but this could fail due to bad luck.
    Assert.assertTrue(
        "Donation failure count is too high: " + noCodeDonatedCount + " out of "
            + numIterations,
        noCodeDonatedCount < numIterations * 0.6
    );
  }

  @Test
  public void testInAndOutParametersDonatedOk() throws Exception {
    // This checks that donation of code that uses 'in' and 'out' parameters of functions works.
    // This donor is designed to have a high chance of leading to an in, out or inout parameter
    // being used by a donated statement, making it a free variable for which a local variable
    // will need to be declared.
    final String donorSource =
        "#version 300 es\n"
            + "void foo(in int a, out int b, inout int c) {\n"
            + " {\n"
            + "  {\n"
            + "   {\n"
            + "     b = a;\n"
            + "     c = c + a;\n"
            + "   }\n"
            + "  }\n"
            + " }\n"
            + "}\n";
    final String referenceSource = "#version 300 es\n"
        + "void main() {\n"
        + "}\n";
    checkLiveCodeDonationRepeatedly(donorSource, referenceSource, item -> true, 15);
  }

  @Test
  public void testDonationOfArrayThatUsesSizeExpr() throws Exception {
    // This checks that donation of code that uses an array with a non-trivial constant expression
    // as its size.
    // This donor is designed to have a high chance of leading to the write to array A, but not
    // the declaration of A, being donated.  The array will then need to have explicit size 7,
    // not size M + N.
    final String donorSource =
        "#version 300 es\n"
            + "void foo(in int a, out int b, inout int c) {\n"
            + " {\n"
            + "  const int N = 3;\n"
            + "  const int M = 4;\n"
            + "  int A[M + N];\n"
            + "  {\n"
            + "   {\n"
            + "     A[0] = 12;\n"
            + "   }\n"
            + "  }\n"
            + " }\n"
            + "}\n";
    final String referenceSource = "#version 300 es\n"
        + "void main() {\n"
        + "}\n";
    checkLiveCodeDonationRepeatedly(donorSource, referenceSource, item -> true, 15);
  }

  @Test
  public void testDonationOfFreeTypeConstructors() throws Exception {
    // This checks that donation of code that uses type constructors leads to valid shaders.
    final String donorSource =
        "#version 300 es\n"
            + "precision highp float;\n"
            + "void foo(in int a, out int b, inout int c) {\n"
            + " {\n"
            + "  mat2x2 m = mat2x2(1.0);\n"
            + "  vec2 v = vec2(1.0);\n"
            + "  ivec3 iv = ivec3(3);\n"
            + " }\n"
            + "}\n";
    final String referenceSource = "#version 300 es\n"
        + "void main() {\n"
        + "}\n";
    checkLiveCodeDonationRepeatedly(donorSource, referenceSource, item -> true, 15);
  }

  @Test
  public void testDonationOfCodeThatUsesGlobalStruct() throws Exception {
    // This checks that donation of code that uses a global struct variable declared with its
    // struct declaration works properly.
    final String donorSource =
        "#version 300 es\n"
            + "struct S {\n"
            + "  int x;"
            + "} s;\n"
            + "void foo() {\n"
            + " {\n"
            + "  int a;\n"
            + "  a = s.x;\n"
            + " }\n"
            + "}\n";
    final String referenceSource = "#version 300 es\n"
        + "void main() {\n"
        + "}\n";
    checkLiveCodeDonationRepeatedly(donorSource, referenceSource, item -> true, 15);
  }

  @Test
  public void testNoDonationOfCodeThatUsesLocalStruct() throws Exception {
    // This checks that functions that declare local structs are not used for donation.
    final String donorSource =
        "#version 300 es\n"
            + "void foo(struct S { int a; } p) {\n"
            + " p.a = 2;\n"
            + "}\n"
            + "void bar(struct { int a; } p) {\n"
            + " p.a = 2;\n"
            + "}\n"
            + "void baz() {\n"
            + " struct T {\n"
            + "  int a; int b;\n"
            + " };\n"
            + " T myT;\n"
            + " myT.a = myT.b;\n"
            + "}\n";
    final String referenceSource = "#version 300 es\n"
        + "void main() {\n"
        + "}\n";
    checkLiveCodeDonationRepeatedly(donorSource, referenceSource, item -> true, 15);
  }

  @Test
  public void testDoNotUseConstParameterInConstExpression() throws Exception {
    // This checks that when fuzzing initializers, a const parameter is not used for a
    // const variable.
    // This donor makes heavy use of non-global structs.
    final String donorSource =
        "#version 300 es\n"
            + "void foo() {\n"
            + " const int a = 10;\n"
            + " {\n"
            + "  int b;\n"
            + "  b = a;\n"
            + " }\n"
            + "}\n";
    final String referenceSource = "#version 300 es\n"
        + "void foo(const int x) {\n"
        + "}\n";
    checkLiveCodeDonationRepeatedly(donorSource, referenceSource, item -> true, 15);
  }

}
