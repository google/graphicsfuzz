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

package com.graphicsfuzz.generator.transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.ContinueStmt;
import com.graphicsfuzz.common.ast.stmt.DefaultCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.donation.DonationContext;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DonateDeadCodeTransformationTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  private DonateDeadCodeTransformation getDummyTransformationObject() {
    return new DonateDeadCodeTransformation(IRandom::nextBoolean, testFolder.getRoot(),
        GenerationParams.normal(ShaderKind.FRAGMENT, false, true));
  }

  @Test
  public void prepareStatementToDonateTopLevelBreakRemovedWhenNecessary() throws Exception {

    // Checks that a top-level 'break' gets removed, unless injecting into a loop or switch.

    final DonateDeadCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  for (int i = 0; i < 10; i ++)\n"
        + "     if (i > 5) break;\n"
        + "}\n");

    final TranslationUnit reference = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  ;\n"
        + "  for(int i = 0; i < 100; i++) {\n"
        + "    switch (i) {\n"
        + "      case 0:\n"
        + "        i++;\n"
        + "      default:\n"
        + "        i++;\n"
        + "    }\n"
        + "  }"
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
      final boolean containsBreak = new CheckPredicateVisitor() {
        @Override
        public void visitBreakStmt(BreakStmt breakStmt) {
          predicateHolds();
        }
      }.test(donated);
      assertEquals(containsBreak, injectionPoint.inLoop() || injectionPoint.inSwitch());
    }
  }

  @Test
  public void prepareStatementToDonateTopLevelContinueRemovedWhenNecessary() throws Exception {

    // Checks that a top-level 'continue' gets removed, unless injecting into a loop.

    final DonateDeadCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  for (int i = 0; i < 10; i ++)\n"
        + "     if (i > 5) continue;\n"
        + "\n"
        + "}\n");

    final TranslationUnit reference = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  ;\n"
        + "  switch(0) {\n"
        + "    case 1:\n"
        + "      break;\n"
        + "    default:\n"
        + "      1;\n"
        + "  }\n"
        + "  for(int i = 0; i < 100; i++) {\n"
        + "    ;\n"
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

      final boolean containsContinue = new CheckPredicateVisitor() {
        @Override
        public void visitContinueStmt(ContinueStmt continueStmt) {
          predicateHolds();
        }
      }.test(donated);
      assertEquals(containsContinue, injectionPoint.inLoop());
    }

  }

  @Test
  public void prepareStatementToDonateTopLevelCaseAndDefaultRemoved() throws Exception {
    // Checks that top-level 'case' and 'default' labels get removed, even when injecting into
    // a switch.

    final DonateDeadCodeTransformation dlc = getDummyTransformationObject();
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

      DonationContext dc = new DonationContext(toDonate, new HashMap<>(),
          new ArrayList<>(), donor.getMainFunction());

      final Stmt donated = dlc.prepareStatementToDonate(injectionPoint, dc,
          TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
          ShadingLanguageVersion.ESSL_310);

      new StandardVisitor() {
        @Override
        public void visitDefaultCaseLabel(DefaultCaseLabel defaultCaseLabel) {
          // 'default' labels should have been removed.
          fail();
        }

        @Override
        public void visitExprCaseLabel(ExprCaseLabel exprCaseLabel) {
          // 'case' labels should have been removed.
          fail();
        }
      }.visit(donated);
    }
  }

  @Test
  public void prepareStatementToDonateBreakFromLoopKept() throws Exception {
    // Checks that a 'break' in a loop gets kept if the whole loop is donated.

    final DonateDeadCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  for (int i = 0; i < 10; i ++)\n"
        + "     if (i > 5) break;\n"
        + "\n"
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

      final DonationContext dc = new DonationContext(toDonate, new HashMap<>(),
          new ArrayList<>(), donor.getMainFunction());

      final Stmt donated = dlc.prepareStatementToDonate(injectionPoint, dc,
          TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
          ShadingLanguageVersion.ESSL_100);
      assertTrue(new CheckPredicateVisitor() {
        @Override
        public void visitBreakStmt(BreakStmt breakStmt) {
          predicateHolds();
        }
      }.test(donated));
    }
  }

  @Test
  public void prepareStatementToDonateSwitchWithBreakAndDefaultKept() throws Exception {
    // Checks that 'case', 'default' and 'break' occurring in a switch are kept if the whole
    // switch statement is donated.

    final DonateDeadCodeTransformation dlc = getDummyTransformationObject();
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

      // Check that the donated statement contains exactly one each of 'break', 'case' and
      // 'default'.
      new StandardVisitor() {

        private boolean foundBreak = false;
        private boolean foundCase = false;
        private boolean foundDefault = false;

        @Override
        public void visitBreakStmt(BreakStmt breakStmt) {
          assertFalse(foundBreak);
          foundBreak = true;
        }

        @Override
        public void visitExprCaseLabel(ExprCaseLabel exprCaseLabel) {
          assertFalse(foundCase);
          foundCase = true;
        }

        @Override
        public void visitDefaultCaseLabel(DefaultCaseLabel defaultCaseLabel) {
          assertFalse(foundDefault);
          foundDefault = true;
        }

        private void check(Stmt stmt) {
          visit(stmt);
          assertTrue(foundBreak);
          assertTrue(foundCase);
          assertTrue(foundDefault);
        }
      };
    }
  }

  @Test
  public void prepareStatementToDonateContinueInLoopKept() throws Exception {
    // Checks that a 'continue' in a loop gets kept if the whole loop is donated.

    final DonateDeadCodeTransformation dlc = getDummyTransformationObject();
    final TranslationUnit donor = ParseHelper.parse("#version 100\n"
        + "void main() {\n"
        + "  for (int i = 0; i < 10; i ++)\n"
        + "     if (i > 5) continue;\n"
        + "\n"
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

      final DonationContext dc = new DonationContext(toDonate, new HashMap<>(),
          new ArrayList<>(), donor.getMainFunction());

      final Stmt donated = dlc.prepareStatementToDonate(injectionPoint, dc,
          TransformationProbabilities.DEFAULT_PROBABILITIES, new RandomWrapper(0),
          ShadingLanguageVersion.ESSL_100);

      // Check that the 'continue' statement is retained.
      assertTrue(new CheckPredicateVisitor() {
        @Override
        public void visitContinueStmt(ContinueStmt continueStmt) {
          predicateHolds();
        }
      }.test(donated));
    }
  }

  @Test
  public void testInAndOutParametersDonatedOk() throws Exception {
    // This checks that donation of code that uses 'in' and 'out' parameters of functions works.

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final File donors = testFolder.newFolder("donors");
    final File referenceFile = testFolder.newFile("reference.json");

    {
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

      fileOps.writeShaderJobFile(
          new GlslShaderJob(
              Optional.empty(),
              new PipelineInfo(),
              ParseHelper.parse(donorSource)),
          new File(donors, "donor.json")
      );
    }

    {
      final String referenceSource = "#version 300 es\n"
          + "void main() {\n"
          + "}\n";
      fileOps.writeShaderJobFile(
          new GlslShaderJob(
              Optional.empty(),
              new PipelineInfo(),
              ParseHelper.parse(referenceSource)),
          referenceFile
      );
    }

    int noCodeDonatedCount = 0;

    // Try the following a few times, so that there is a good chance of triggering the issue
    // this test was used to catch, should it return:
    for (int seed = 0; seed < 15; seed++) {

      final ShaderJob referenceShaderJob = fileOps.readShaderJobFile(referenceFile);

      // Do live code donation.
      final DonateDeadCodeTransformation transformation =
          new DonateDeadCodeTransformation(IRandom::nextBoolean, donors,
              GenerationParams.normal(ShaderKind.FRAGMENT, false, false));

      assert referenceShaderJob.getFragmentShader().isPresent();

      final boolean result = transformation.apply(
          referenceShaderJob.getFragmentShader().get(),
          TransformationProbabilities.onlyLiveCodeAlwaysSubstitute(),
          new RandomWrapper(seed),
          GenerationParams.normal(ShaderKind.FRAGMENT, false, false)
      );

      if (result) {
        final File tempFile = testFolder.newFile("shader" + seed + ".json");
        fileOps.writeShaderJobFile(referenceShaderJob, tempFile);
        // This will fail if the shader job turns out to be invalid.
        fileOps.areShadersValid(tempFile, true);
      } else {
        ++noCodeDonatedCount;
      }

    }

    // The above code tests donation of dead code, but there is still a chance that no code will
    // be donated. We assert that this happens < 10 times to ensure that we get some test
    // coverage, but this could fail due to bad luck.
    Assert.assertTrue(
        "Donation failure count should be < 10, " + noCodeDonatedCount,
        noCodeDonatedCount < 10
    );

  }

}
