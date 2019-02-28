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

package com.graphicsfuzz.common.ast.inliner;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReturnRemover {

  private final FunctionDefinition fd;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final IParentMap parentMap;
  private boolean removedAReturn;

  private ReturnRemover(FunctionDefinition fd, ShadingLanguageVersion shadingLanguageVersion) {
    this.fd = fd;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.parentMap = IParentMap.createParentMap(fd);
    this.removedAReturn = false;
  }

  public static void removeReturns(FunctionDefinition fd, ShadingLanguageVersion
      shadingLanguageVersion)
        throws CannotRemoveReturnsException {
    new ReturnRemover(fd, shadingLanguageVersion).doRemoveReturns();
  }

  private void doRemoveReturns() throws CannotRemoveReturnsException {
    if (!containsReturn(fd.getBody())) {
      // No return to remove
      return;
    }
    if (numReturnStmts(fd.getBody()) == 1 && fd.getBody()
          .getStmt(fd.getBody().getNumStmts() - 1) instanceof ReturnStmt) {
      // Only one return at end -- removing it doesn't simplify things
      return;
    }
    if (containsSwitch(fd)) {
      throw new CannotRemoveReturnsException("Switch statements not yet supported.");
    }
    addReturnInstrumentation();
    replaceReturnStatements();
    addSpecialDeclarations();
  }

  private void replaceReturnStatements() {
    final IParentMap parentMap = IParentMap.createParentMap(fd);
    new StandardVisitor() {
      @Override
      public void visitReturnStmt(ReturnStmt returnStmt) {
        if (returnStmt.hasExpr()) {
          parentMap.getParent(returnStmt).replaceChild(returnStmt,
                new BlockStmt(Arrays.asList(
                      new ExprStmt(new BinaryExpr(makeReturnValue(), returnStmt.getExpr(),
                            BinOp.ASSIGN)),
                      setHasReturned()), true));
        } else {
          parentMap.getParent(returnStmt).replaceChild(returnStmt,
                setHasReturned());

        }
      }

      @Override
      public void visitForStmt(ForStmt forStmt) {
        if (shadingLanguageVersion.restrictedForLoops()) {
          handleRestrictedForLoop(forStmt);
        } else {
          handleLoop(forStmt);
        }
        super.visitForStmt(forStmt);
      }

      @Override
      public void visitWhileStmt(WhileStmt whileStmt) {
        handleLoop(whileStmt);
        super.visitWhileStmt(whileStmt);
      }

      @Override
      public void visitDoStmt(DoStmt doStmt) {
        handleLoop(doStmt);
        super.visitDoStmt(doStmt);
      }

      private void handleLoop(LoopStmt loopStmt) {
        if (containsReturn(loopStmt)) {
          loopStmt.setCondition(
                new BinaryExpr(
                      new ParenExpr(new UnaryExpr(makeHasReturned(), UnOp.LNOT)),
                      loopStmt.hasCondition() ? loopStmt.getCondition() :
                          new BoolConstantExpr(true),
                    BinOp.LAND));
        }
      }

      private void handleRestrictedForLoop(ForStmt forStmt) {
        if (containsReturn(forStmt)) {
          forStmt.setBody(new BlockStmt(
                Arrays.asList(
                      new IfStmt(
                        makeHasReturned(),
                        new BlockStmt(Arrays.asList(new BreakStmt()), true),
                        null),
                      forStmt.getBody()),
                false));
        }
      }

      private ExprStmt setHasReturned() {
        return new ExprStmt(new BinaryExpr(makeHasReturned(), new BoolConstantExpr(true),
            BinOp.ASSIGN));
      }
    }.visit(fd);
  }

  private void addSpecialDeclarations() {
    if (fd.getPrototype().getReturnType().getWithoutQualifiers() != VoidType.VOID) {
      fd.getBody().insertStmt(0, new DeclarationStmt(
            new VariablesDeclaration(fd.getPrototype().getReturnType().getWithoutQualifiers(),
                  new VariableDeclInfo(makeReturnValueName(), null, null))));
      fd.getBody().addStmt(new ReturnStmt(makeReturnValue()));
    }
    fd.getBody().insertStmt(0, new DeclarationStmt(
          new VariablesDeclaration(BasicType.BOOL,
                new VariableDeclInfo(makeHasReturnedName(), null,
                      new ScalarInitializer(new BoolConstantExpr(false))))));
  }

  private void addReturnInstrumentation() {
    new StandardVisitor() {
      @Override
      public void visitBlockStmt(BlockStmt blockStmt) {
        super.visitBlockStmt(blockStmt);
        final List<List<Stmt>> regionStack = new ArrayList<>();
        regionStack.add(new ArrayList<>());
        for (Stmt stmt : blockStmt.getStmts()) {
          addToCurrentRegion(regionStack, stmt);
          if (containsReturn(stmt)) {
            regionStack.add(new ArrayList<>());
          }
        }
        blockStmt.setStmts(regionStackToStmts(regionStack));
      }

      private List<Stmt> regionStackToStmts(List<List<Stmt>> regionStack) {
        if (regionStack.size() == 1) {
          return regionStack.get(0);
        }
        final List<Stmt> result = new ArrayList<>();
        result.addAll(regionStack.get(0));

        // Check that the remainder actually contains some statements
        if (regionStack.subList(1, regionStack.size()).stream().anyMatch(item -> !item.isEmpty())) {
          result.add(new IfStmt(
                new UnaryExpr(makeHasReturned(), UnOp.LNOT),
                new BlockStmt(regionStackToStmts(
                      regionStack.subList(1, regionStack.size())),
                      true),
                null));
        }
        return result;
      }

      private void addToCurrentRegion(List<List<Stmt>> regionStack, Stmt stmt) {
        regionStack.get(regionStack.size() - 1).add(stmt);
      }

    }.visit(fd);
  }

  private Expr makeHasReturned() {
    return new VariableIdentifierExpr(
          makeHasReturnedName());
  }

  private String makeHasReturnedName() {
    return functionName() + "_has_returned";
  }

  private Expr makeReturnValue() {
    return new VariableIdentifierExpr(
          makeReturnValueName());
  }

  private String makeReturnValueName() {
    return functionName() + "_return_value";
  }

  private String functionName() {
    return fd.getPrototype().getName();
  }

  private boolean containsSwitch(FunctionDefinition fd) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitSwitchStmt(SwitchStmt switchStmt) {
        predicateHolds();
      }
    }.test(fd);
  }

  private boolean containsReturn(Stmt stmt) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitReturnStmt(ReturnStmt returnStmt) {
        predicateHolds();
      }
    }.test(stmt);
  }

  private int numReturnStmts(Stmt stmt) {
    return new StandardVisitor() {
      private int returnCount = 0;

      @Override
      public void visitReturnStmt(ReturnStmt returnStmt) {
        super.visitReturnStmt(returnStmt);
        returnCount++;
      }

      int countReturns(IAstNode node) {
        visit(node);
        return returnCount;
      }
    }.countReturns(stmt);
  }

}
