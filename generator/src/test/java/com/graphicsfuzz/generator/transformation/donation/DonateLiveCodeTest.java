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

package com.graphicsfuzz.generator.transformation.donation;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
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
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.injection.BlockInjectionPoint;
import com.graphicsfuzz.generator.transformation.mutator.MutateExpressions;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;

public class DonateLiveCodeTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void prepareStatementToDonate() throws Exception {

    final DonateLiveCode dlc =
        new DonateLiveCode(IRandom::nextBoolean, testFolder.getRoot(), GenerationParams.normal(ShaderKind.FRAGMENT, true),
            false);

    DonationContext dc = new DonationContext(DiscardStmt.INSTANCE, new HashMap<>(),
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

    final String reference = "void main() {"
          + "  int t;"
          + "  {"
          + "  }"
          + "  gl_FragColor = vec4(float(t));"
          + "}";

    final IRandom generator = new RandomWrapper(0);

    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_300;

    for (int i = 0; i < 10; i++) {

      final DonateLiveCode donateLiveCode =
            new DonateLiveCode(item -> true, testFolder.getRoot(), GenerationParams.normal(ShaderKind.FRAGMENT, true),
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
                        new ArrayList<ParameterDecl>()), null)),
            TransformationProbabilities.onlyLiveCodeAlwaysSubstitute(),
            generator,
          shadingLanguageVersion);

      blockInjectionPoint.inject(toDonate);

      final MutateExpressions mutateExpressions =
            new MutateExpressions();

      mutateExpressions.apply(referenceTu, TransformationProbabilities.onlyMutateExpressions(),
            ShadingLanguageVersion.ESSL_300,
            generator,
            GenerationParams.large(ShaderKind.FRAGMENT, true)
      );
    }
  }

}