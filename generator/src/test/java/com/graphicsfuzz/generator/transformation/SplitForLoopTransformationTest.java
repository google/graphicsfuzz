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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.semanticspreserving.SplitForLoopMutation;
import com.graphicsfuzz.generator.transformation.SplitForLoopTransformation;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SplitForLoopTransformationTest {

  @Test
  public void suitableForSplittingNoNext()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method suitableForSplitting = SplitForLoopMutation.class.getDeclaredMethod("suitableForSplitting",
        IInjectionPoint.class);
    suitableForSplitting.setAccessible(true);
    Boolean result = (Boolean) suitableForSplitting.invoke(null,
        new IInjectionPoint() {
          @Override
          public void inject(Stmt stmt) {
            throw new RuntimeException();
          }

          @Override
          public Stmt getNextStmt() {
            throw new RuntimeException();
          }

          @Override
          public boolean hasNextStmt() {
            return false;
          }

          @Override
          public void replaceNext(Stmt stmt) {
            throw new RuntimeException();
          }

          @Override
          public boolean inLoop() {
            throw new RuntimeException();
          }

          @Override
          public FunctionDefinition getEnclosingFunction() {
            throw new RuntimeException();
          }

          @Override
          public Scope scopeAtInjectionPoint() {
            throw new RuntimeException();
          }

        });

    assertFalse(result);

  }


  @Test
  public void checkSimpleLoopSplit() {

    TranslationUnit tu = makeExampleTranslationUnit();

    List<IInjectionPoint> ips = new InjectionPoints(tu, new RandomWrapper(),
        SplitForLoopMutation::suitableForSplitting).getInjectionPoints(
        TransformationProbabilities.onlySplitLoops()::splitLoops);

    assertEquals(1, ips.size());

  }

  private static TranslationUnit makeExampleTranslationUnit() {
    return new TranslationUnit(Optional.empty(),
        Arrays.asList(new FunctionDefinition(
            new FunctionPrototype("foo", VoidType.VOID, new ArrayList<ParameterDecl>()),
            new BlockStmt(
                Arrays.asList(
                    new ForStmt(
                        new DeclarationStmt(
                            new VariablesDeclaration(
                                new QualifiedType(BasicType.INT,
                                    new ArrayList<TypeQualifier>()), new VariableDeclInfo("i", null, new ScalarInitializer(new IntConstantExpr("0"))))),
                        new BinaryExpr(
                            new VariableIdentifierExpr("i"),
                            new IntConstantExpr("10"),
                            BinOp.LT),
                        new UnaryExpr(new VariableIdentifierExpr("i"), UnOp.POST_INC),
                        new BlockStmt(new ArrayList<Stmt>(), false))
                ), false))));
  }

  @Test
  public void testSplitShouldBePossible() throws Exception {
    final String program = "#version 100\nvoid main() { for(int i = 0; i < 10; i++) { } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    assertEquals(1, countForLoops(tu));
    new SplitForLoopTransformation().apply(tu, TransformationProbabilities.onlySplitLoops(), new RandomWrapper(0), GenerationParams.normal(ShaderKind.FRAGMENT, true));
    assertEquals(2, countForLoops(tu));
  }

  @Test
  public void testSplitShouldNotBePossible() throws Exception {
    final String program = "#version 100\nvoid main() { for(int i = 0; i < 10; i++) { if(true) "
        + "break; } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    assertEquals(1, countForLoops(tu));
    new SplitForLoopTransformation().apply(tu, TransformationProbabilities.onlySplitLoops(), new RandomWrapper(0), GenerationParams.normal(ShaderKind.FRAGMENT, true));
    assertEquals(1, countForLoops(tu));
  }

  private int countForLoops(TranslationUnit tu) {
    return new StandardVisitor() {

      int getCount(TranslationUnit tu) {
        visit(tu);
        return count;
      }

      private int count = 0;
      @Override
      public void visitForStmt(ForStmt forStmt) {
        super.visitForStmt(forStmt);
        count++;
      }
    }.getCount(tu);
  }

}
