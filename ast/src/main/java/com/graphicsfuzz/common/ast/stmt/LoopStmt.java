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

package com.graphicsfuzz.common.ast.stmt;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;

public abstract class LoopStmt extends Stmt {

  private Expr condition;
  private Stmt body;

  LoopStmt(Expr condition, Stmt body) {
    if (body == null) {
      throw new RuntimeException("The body of a loop must be present.");
    }
    this.condition = condition;
    this.body = body;
  }

  public final Stmt getBody() {
    return body;
  }

  public final void setBody(Stmt body) {
    this.body = body;
  }

  /**
   * Reports whether a condition for the loop is present (it is not in e.g. "for(init; ; inc)"
   *
   * @return Whether condition is present.
   */
  public abstract boolean hasCondition();

  public final Expr getCondition() {
    return condition;
  }

  public final void setCondition(Expr condition) {
    this.condition = condition;
  }

  @Override
  public void replaceChild(IAstNode child, IAstNode newChild) {
    if (child == body) {
      setBody((Stmt) newChild);
    } else if (child == condition) {
      setCondition((Expr) newChild);
    } else {
      throw new IllegalArgumentException();
    }
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return candidateChild == body
          || candidateChild == condition;
  }

  /**
   * Determines whether the loop's body contains any break or continue statements
   * that are not nested in inner loops.
   */
  public boolean containsDirectBreakOrContinueStmt() {
    return new ContainsDirectBreakOrContinueStmt().check();
  }

  private class ContainsDirectBreakOrContinueStmt extends StandardVisitor {

    private class FoundBreakOrContinueStmtException extends RuntimeException {

    }

    private int nestingDepth = 0;

    public boolean check() {
      try {
        visit(body);
        return false;
      } catch (FoundBreakOrContinueStmtException exception) {
        return true;
      }
    }

    @Override
    public void visit(IAstNode node) {
      if (node instanceof LoopStmt) {
        nestingDepth++;
      }
      super.visit(node);
      if (node instanceof LoopStmt) {
        nestingDepth--;
      }
    }

    @Override
    public void visitBreakStmt(BreakStmt breakStmt) {
      if (nestingDepth == 0) {
        throw new FoundBreakOrContinueStmtException();
      }
    }

    @Override
    public void visitContinueStmt(ContinueStmt continueStmt) {
      if (nestingDepth == 0) {
        throw new FoundBreakOrContinueStmtException();
      }
    }

  }

}
