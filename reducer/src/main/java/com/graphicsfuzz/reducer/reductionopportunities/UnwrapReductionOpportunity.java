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

import com.graphicsfuzz.common.ast.ChildDoesNotExistException;
import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class UnwrapReductionOpportunity extends AbstractReductionOpportunity {

  private final Stmt wrapper;
  private final List<Stmt> wrapees;
  private final IParentMap parentMap;

  public UnwrapReductionOpportunity(Stmt wrapper, List<Stmt> wrapees, IParentMap parentMap,
        VisitationDepth depth) {
    super(depth);
    this.wrapper = wrapper;
    assert wrapees != null;
    this.wrapees = new ArrayList<>();
    this.wrapees.addAll(wrapees);
    this.parentMap = parentMap;
    if (wrapees.size() > 1) {
      if (!(parentMap.getParent(wrapper) instanceof BlockStmt)) {
        throw new RuntimeException("We can only unwrap a block containing multiple statements if "
              + "its direct parent is a block.");
      }
    }

  }

  public UnwrapReductionOpportunity(Stmt wrapper, Stmt wrapee, IParentMap parentMap,
        VisitationDepth depth) {
    this(wrapper, Arrays.asList(wrapee), parentMap, depth);
    assert wrapee != null;
  }

  @Override
  public void applyReductionImpl() {
    try {
      Stmt parent = (Stmt) parentMap.getParent(wrapper);
      if (parent instanceof BlockStmt) {
        final BlockStmt parentBlock = (BlockStmt) parent;
        if (!parentBlock.getStmts().contains(wrapper)) {
          throw new ChildDoesNotExistException(wrapper, parentBlock);
        }
        for (Stmt stmt : wrapees) {
          parentBlock.insertBefore(wrapper, stmt);
        }
        parentBlock.removeStmt(wrapper);
      } else {
        parent.replaceChild(wrapper,
              wrapees.size() == 1 ? wrapees.get(0) : new BlockStmt(wrapees, true));
      }
    } catch (ChildDoesNotExistException exception) {
      // TODO: it would be cleaner to capture this in the precondition.
      // The wrapper has already been eliminated
      // by some other reduction opportunity
    }
  }

  @Override
  public boolean preconditionHolds() {
    if (!(parentMap.getParent(wrapper) instanceof Stmt)) {
      return false;
    }
    return true;
  }

}
