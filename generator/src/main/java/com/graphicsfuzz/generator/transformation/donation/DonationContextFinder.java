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
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.util.AvailableStructsCollector;
import com.graphicsfuzz.generator.util.FreeVariablesCollector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DonationContextFinder extends StandardVisitor {

  private final TranslationUnit donor;
  private final List<Stmt> donorFragments;
  private final Map<Stmt, FunctionDefinition> enclosingFunction;
  private FunctionDefinition currentFunction;
  private final IRandom generator;

  public DonationContextFinder(TranslationUnit donor, IRandom generator) {
    this.donor = donor;
    this.donorFragments = new ArrayList<>();
    this.enclosingFunction = new HashMap<>();
    this.currentFunction = null;
    this.generator = generator;
    visit(donor);
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    currentFunction = functionDefinition;
    super.visitFunctionDefinition(functionDefinition);
    currentFunction = null;
  }

  @Override
  public void visitBlockStmt(BlockStmt stmt) {
    addDonorFragment(stmt);
    super.visitBlockStmt(stmt);
  }

  private void addDonorFragment(Stmt stmt) {
    donorFragments.add(stmt);
    enclosingFunction.put(stmt, currentFunction);
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    addDonorFragment(ifStmt);
    super.visitIfStmt(ifStmt);
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    addDonorFragment(whileStmt);
    super.visitWhileStmt(whileStmt);
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    addDonorFragment(doStmt);
    super.visitDoStmt(doStmt);
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    addDonorFragment(forStmt);
    super.visitForStmt(forStmt);
  }

  public DonationContext getDonationContext() {
    final Stmt donorFragment = donorFragments.get(generator.nextInt(donorFragments.size()));
    final FreeVariablesCollector fvCollector = new FreeVariablesCollector(donor, donorFragment);
    final AvailableStructsCollector asCollector =
        new AvailableStructsCollector(donor, donorFragment);


    final Stmt clonedDonorFragment = donorFragment.clone();
    if (clonedDonorFragment instanceof BlockStmt) {
      // If we got the donor fragment from a function body, it may not introduce a new scope.
      // We ensure that the donor fragment to be used in the
      // com.graphicsfuzz.generator.transformation.donation context does.
      ((BlockStmt) clonedDonorFragment).setIntroducesNewScope(true);
    }
    return new DonationContext(clonedDonorFragment, fvCollector.getFreeVariables(),
          asCollector.getStructDefinitionTypes(),
          enclosingFunction.get(donorFragment));
  }

}
