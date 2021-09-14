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

package com.graphicsfuzz.common.typing;

import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * This class extends StandardVisitor to track details of what is in scope at each point of
 * visitation.
 */
public abstract class ScopeTrackingVisitor extends StandardVisitor {

  // Tracks the scope at the current point of visitation.
  private Scope currentScope;

  // Tracks the function, if any, enclosing the current point of visitation.  If this field is null,
  // this indicates that visitation is at global scope.
  private FunctionDefinition enclosingFunction;

  // A stack of the blocks that enclose the current point of visitation.
  private final Deque<BlockStmt> enclosingBlocks;

  // All of the function prototypes that have been encountered during visitation so far.
  private final List<FunctionPrototype> encounteredFunctionPrototypes;

  // When we visit a function prototype, we want to add its parameters to the current scope if and
  // only if it is the prototype component of a function definition.  If it is a stand-alone
  // function prototype then we do not want to add its parameters to the current scope.  This field
  // is used to determine whether or not we should record encountered parameters to the current
  // scope.
  private boolean addEncounteredParametersToScope;

  protected ScopeTrackingVisitor() {
    this.currentScope = new Scope();
    this.enclosingFunction = null;
    this.enclosingBlocks = new LinkedList<>();
    this.encounteredFunctionPrototypes = new ArrayList<>();
    this.addEncounteredParametersToScope = false;
  }

  // Over-ridden 'visit' methods; keep in alphabetical order:

  @Override
  public void visitBlockStmt(BlockStmt stmt) {
    enterBlockStmt(stmt);
    super.visitBlockStmt(stmt);
    leaveBlockStmt(stmt);
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    // A 'for' statement is special in that it, rather than its open-curly, starts a new scope.
    // For example, this is illegal:
    //
    // for (int i = 0; i < 10; i++) {
    //   int i;
    // }
    //
    // because it declares 'i' twice in the same scope.
    //
    // Furthermore, a 'for' statement introduces a new scope regardless of whether its body is
    // a block or a single statement.
    //
    // We thus have to push a new scope before traversing a 'for' statement and pop it afterwards,
    // and we do this regardless of whether the body is a block.
    pushScope();
    super.visitForStmt(forStmt);
    popScope();
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    // Record the fact that we are in a function, and push a scope for that function.
    assert enclosingFunction == null;
    enclosingFunction = functionDefinition;
    pushScope();

    // As this is a function definition, we *do* want to add the function's parameters to the
    // current scope when we visit the function prototpye.
    addEncounteredParametersToScope = true;
    visitFunctionPrototype(functionDefinition.getPrototype());
    addEncounteredParametersToScope = false;

    visit(functionDefinition.getBody());

    // Get rid of the function's scope, and record that we are no longer in a function.
    popScope();
    enclosingFunction = null;
  }

  @Override
  public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
    encounteredFunctionPrototypes.add(functionPrototype);
    for (ParameterDecl p : functionPrototype.getParameters()) {
      visitParameterDecl(p);

      // Only add the parameter to the current scope if we have decided to do so, and then only
      // if it actually has a name.
      if (!addEncounteredParametersToScope || p.getName() == null) {
        continue;
      }
      currentScope.add(p.getName(),
          Typer.combineBaseTypeAndArrayInfo(p.getType(), p.getArrayInfo()),
          p);
    }
  }

  @Override
  public void visitInterfaceBlock(InterfaceBlock interfaceBlock) {
    assert atGlobalScope();
    super.visitInterfaceBlock(interfaceBlock);
    for (String member : interfaceBlock.getMemberNames()) {
      currentScope.add(member, interfaceBlock.getMemberType(member), interfaceBlock);
    }
  }

  @Override
  public void visitStructDefinitionType(StructDefinitionType structDefinitionType) {
    super.visitStructDefinitionType(structDefinitionType);
    if (structDefinitionType.hasStructNameType()) {
      // We add named structs to the current scope.
      currentScope.addStructDefinition(structDefinitionType);
    }
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    visit(variablesDeclaration.getBaseType());
    for (VariableDeclInfo declInfo : variablesDeclaration.getDeclInfos()) {
      // Visit the declInfo both before and after adding it to the current scope, to give
      // subclasses the flexibility to examine both cases.
      visitVariableDeclInfo(declInfo);

      // We record a type for declInfo.name, taking account of the fact that it might be an array.
      currentScope.add(declInfo.getName(),
          Typer.combineBaseTypeAndArrayInfo(variablesDeclaration.getBaseType(),
              declInfo.getArrayInfo()),
          declInfo, variablesDeclaration);
      visitVariableDeclInfoAfterAddedToScope(declInfo);
    }
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    // A 'while' statement is special in that it, rather than its open-curly, starts a new scope.
    // For example, this is illegal:
    //
    // while (bool b = true) {
    //   bool b;
    // }
    //
    // because it declares 'b' twice in the same scope.
    //
    // Furthermore, a 'while' statement introduces a new scope regardless of whether its body is
    // a block or a single statement.
    //
    // We thus have to push a new scope before traversing a 'while' statement and pop it
    // afterwards, and we do this regardless of whether the body is a block.
    pushScope();
    super.visitWhileStmt(whileStmt);
    popScope();
  }

  // Additional methods for use by subclasses:

  /**
   * Tracks entry to a block, updating the current scope and the enclosing blocks.
   * Subclasses can override this method to perform additional actions on block entry, but *must*
   * invoke this superclass method, otherwise scopes will not be properly tracked.
   * @param blockStmt A block statement that is about to be traversed
   */
  protected void enterBlockStmt(BlockStmt blockStmt) {
    enclosingBlocks.addFirst(blockStmt);
    if (blockStmt.introducesNewScope()) {
      pushScope();
    }
  }

  /**
   * Tracks exit from a block, updating the current scope and the enclosing blocks.
   * Subclasses can override this method to perform additional actions on block exit, but *must*
   * invoke this superclass method, otherwise scopes will not be properly tracked.
   * @param blockStmt A block statement that has just been traversed
   */
  protected void leaveBlockStmt(BlockStmt blockStmt) {
    if (blockStmt.introducesNewScope()) {
      popScope();
    }
    enclosingBlocks.removeFirst();
  }

  /**
   * Returns the closest block enclosing the current point of visitation.
   *
   * @return The closest block
   */
  protected BlockStmt currentBlock() {
    return enclosingBlocks.peekFirst();
  }

  /**
   * Returns true if and only if visitation is in some block.
   *
   * @return Whether visitation is in a block
   */
  protected boolean inSomeBlock() {
    return !enclosingBlocks.isEmpty();
  }

  /**
   * Because WebGL places limits on for loops it can be convenient to only visit the body of a
   * for loop, skipping the header.  Subclasses can call this method to invoke this behaviour
   * during visitation, instead of calling super.visitForStmt(...).
   *
   * @param forStmt For statement whose body should be visited
   */
  protected void visitForStmtBodyOnly(ForStmt forStmt) {
    pushScope();
    visit(forStmt.getBody());
    popScope();
  }

  /**
   * This is a hook for subclasses that need to perform an action after a declaration has been added
   * to the current scope.
   *
   * @param declInfo The declaration info that was just added to the current scope
   */
  protected void visitVariableDeclInfoAfterAddedToScope(VariableDeclInfo declInfo) {
    // Deliberately empty - to be optionally overridden by subclasses.
  }

  /**
   * Replace the current scope with its parent.
   */
  protected void popScope() {
    currentScope = currentScope.getParent();
  }

  /**
   * Replace the current scope with a new scope whose parent is the old current scope.
   */
  protected void pushScope() {
    Scope newScope = new Scope(currentScope);
    currentScope = newScope;
  }

  /**
   * Yield the function prototypes that have been encountered during visitation so far.
   * @return The function prototypes that have been encountered during visitation so far
   */
  protected List<FunctionPrototype> getEncounteredFunctionPrototypes() {
    return Collections.unmodifiableList(encounteredFunctionPrototypes);
  }

  /**
   * Determines whether visitation is at global scope, i.e. not in any function.
   * @return true if and only if visitation is at global scope
   */
  protected boolean atGlobalScope() {
    return !currentScope.hasParent();
  }

  /**
   * Yields the current scope.
   * @return the current scope
   */
  protected Scope getCurrentScope() {
    return currentScope;
  }

  /**
   * Replaces the current scope with the given scope, returning the old current scope.  This can
   * be useful if one needs to temporarily change the visitor's view of what is in scope,
   * @param newScope A scope to replace the current scope
   * @return the previous current scope
   */
  protected Scope swapCurrentScope(Scope newScope) {
    final Scope result = currentScope;
    currentScope = newScope;
    return result;
  }

  /**
   * Yields the function enclosing the current point of visitation.
   * @return the function enclosing the current point of visitation
   */
  protected FunctionDefinition getEnclosingFunction() {
    return enclosingFunction;
  }

}
