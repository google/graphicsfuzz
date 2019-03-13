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
import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    final IAstNode parentOfWrapper = parentMap.getParent(wrapper);
    if (!(parentOfWrapper instanceof Stmt)) {
      return false;
    }
    if (parentOfWrapper instanceof BlockStmt) {
      // We need to make sure it is still the case that applying the unwrap will not lead to name
      // clashes.  We only need to check the names declared directly by the parent block against
      // the names declared inside the block being unwrapped, because it is only these sets of names
      // that can have been affected by applying other reduction opportunities.
      if (!Collections.disjoint(getNamesDeclaredDirectlyByBlock((BlockStmt) parentOfWrapper),
          getNamesDeclaredByStmtList(wrapees))) {
        return false;
      }
    }

    return true;
  }

  static Set<String> getNamesDeclaredDirectlyByBlock(BlockStmt block) {
    final List<Stmt> stmts = block.getStmts();
    return getNamesDeclaredByStmtList(stmts);
  }

  private static Set<String> getNamesDeclaredByStmtList(List<Stmt> stmts) {
    return stmts
        .stream()
        .filter(item -> item instanceof DeclarationStmt)
        .map(item -> ((DeclarationStmt) item).getVariablesDeclaration().getDeclInfos())
        .reduce(Collections.emptyList(), ListConcat::concatenate)
        .stream()
        .map(item -> item.getName())
        .collect(Collectors.toSet());
  }

}
