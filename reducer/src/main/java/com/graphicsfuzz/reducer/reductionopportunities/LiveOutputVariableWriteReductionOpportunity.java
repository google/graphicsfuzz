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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LiveOutputVariableWriteReductionOpportunity extends AbstractReductionOpportunity {

  private final BlockStmt block;
  private final String backupName;

  LiveOutputVariableWriteReductionOpportunity(BlockStmt block,
        String backupName,
        VisitationDepth depth) {
    super(depth);
    this.block = block;
    this.backupName = backupName;
  }

  @Override
  public boolean preconditionHolds() {

    final Optional<Integer> declarationIndex = indexOfBackupDeclaration();
    if (!declarationIndex.isPresent()) {
      // Backup is declared
      return false;
    }

    final Optional<Integer> backupIndex = indexOfAssignmentToBackup();
    if (!backupIndex.isPresent()) {
      // Backup is written to
      return false;
    }

    final Optional<Integer> restoreIndex = indexOfRestorationFromBackup();
    if (!restoreIndex.isPresent()) {
      // Backup is restored from
      return false;
    }

    if (!(declarationIndex.get() < backupIndex.get() && backupIndex.get() < restoreIndex.get())) {
      return false;
    }

    // Nothing before or after refers to backup
    for (int i = declarationIndex.get() + 1; i < backupIndex.get(); i++) {
      if (referencesBackup(block.getStmt(i))) {
        return false;
      }
    }

    for (int i = restoreIndex.get() + 1; i < block.getNumStmts(); i++) {
      if (referencesBackup(block.getStmt(i))) {
        return false;
      }
    }

    return true;
  }

  private boolean referencesBackup(Stmt stmt) {
    final VariableDeclInfo backupVdi =
          ((DeclarationStmt) block.getStmt(indexOfBackupDeclaration().get()))
          .getVariablesDeclaration().getDeclInfo(0);
    return new ScopeTreeBuilder() {
      private boolean found = false;
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        final ScopeEntry se = currentScope.lookupScopeEntry(variableIdentifierExpr.getName());
        if (se != null && se.hasVariableDeclInfo() && se.getVariableDeclInfo() == backupVdi) {
          found = true;
        }
      }

      private boolean check(Stmt stmt) {
        visit(stmt);
        return found;
      }
    }.check(stmt);
  }

  private Optional<Integer> indexOfBackupDeclaration() {
    for (int i = 0; i < block.getNumStmts(); i++) {
      if (!(block.getStmt(i) instanceof DeclarationStmt)) {
        continue;
      }
      if (isOutVariableBackup(
            (DeclarationStmt) block.getStmt(i))) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  private boolean isOutVariableBackup(DeclarationStmt declarationStmt) {
    return declarationStmt.getVariablesDeclaration().getDeclInfos()
        .stream().anyMatch(vdi -> vdi.getName().equals(backupName));
  }

  private Optional<Integer> indexOfAssignmentToBackup() {
    return indexOfAssignment(backupName, getOriginalVariableName());
  }

  private Optional<Integer> indexOfRestorationFromBackup() {
    final Optional<Integer> maybeAssignmentIndex = indexOfAssignment(getOriginalVariableName(),
        backupName);
    if (maybeAssignmentIndex.isPresent()) {
      return maybeAssignmentIndex;
    }
    for (int i = 0; i < block.getNumStmts(); i++) {
      final Stmt stmt = block.getStmt(i);
      if (!(stmt instanceof IfStmt)) {
        continue;
      }
      final IfStmt ifStmt = (IfStmt) stmt;
      if (!MacroNames.isIfWrapperTrue(ifStmt.getCondition())) {
        continue;
      }
      if (isAssignment(ifStmt.getThenStmt(), getOriginalVariableName(), backupName)) {
        return Optional.of(i);
      }
      if (ifStmt.getThenStmt() instanceof BlockStmt
          && ((BlockStmt) ifStmt.getThenStmt()).getNumStmts() == 1
          && isAssignment(((BlockStmt) ifStmt.getThenStmt()).getStmt(0),
            getOriginalVariableName(), backupName)) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  private Optional<Integer> indexOfAssignment(String lhsName, String rhsName) {
    for (int i = 0; i < block.getNumStmts(); i++) {
      if (isAssignment(block.getStmt(i), lhsName, rhsName)) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  private boolean isAssignment(Stmt stmt, String lhsName, String rhsName) {
    if (!(stmt instanceof ExprStmt)) {
      return false;
    }
    final ExprStmt exprStmt = (ExprStmt) stmt;
    if (!(exprStmt.getExpr() instanceof BinaryExpr)) {
      return false;
    }
    final BinaryExpr binaryExpr = (BinaryExpr) exprStmt.getExpr();
    if (binaryExpr.getOp() != BinOp.ASSIGN) {
      return false;
    }
    if (!isVariable(binaryExpr.getLhs(), lhsName)) {
      return false;
    }
    if (!isVariable(binaryExpr.getRhs(), rhsName)) {
      return false;
    }
    return true;
  }

  private boolean isVariable(Expr expr, String name) {
    return expr instanceof VariableIdentifierExpr
          && ((VariableIdentifierExpr) expr).getName().equals(name);
  }

  private String getOriginalVariableName() {
    return backupName.substring(Constants.GLF_OUT_VAR_BACKUP_PREFIX.length());
  }

  @Override
  void applyReductionImpl() {
    List<Stmt> newStmts = new ArrayList<>();
    for (int i = 0; i < block.getNumStmts(); i++) {
      if (i == indexOfBackupDeclaration().get()) {
        continue;
      }
      if (i >= indexOfAssignmentToBackup().get() && i <= indexOfRestorationFromBackup().get()) {
        continue;
      }
      newStmts.add(block.getStmt(i));
    }
    block.setStmts(newStmts);
  }

}
