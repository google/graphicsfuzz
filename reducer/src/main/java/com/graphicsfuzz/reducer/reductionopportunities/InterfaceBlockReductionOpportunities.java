/*
 * Copyright 2021 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds opportunities to remove unused interface blocks from a shader. That is, interface blocks
 * that are not referenced from anywhere inside the shader. In the following example, the buffer
 * interface block could be removed:
 *
 * {@code
 *   layout(binding = 1) buffer SomeName {
 *     int x;
 *     int y;
 *   }
 *
 *   void main() {
 *     // Code that neither references 'x' nor 'y'
 *   }
 * }
 */
public class InterfaceBlockReductionOpportunities
    extends ReductionOpportunitiesBase<InterfaceBlockReductionOpportunity> {

  // Used to find all interface blocks that are referenced from somewhere in the shader. Blocks that
  // are never referenced can then be removed.
  private final Set<InterfaceBlock> referencedInterfaceBlocks;

  private InterfaceBlockReductionOpportunities(TranslationUnit tu,
                                               ReducerContext context) {
    super(tu, context);
    this.referencedInterfaceBlocks = new HashSet<>();
  }

  @Override
  public void visitTranslationUnit(TranslationUnit translationUnit) {
    // Finding reduction opportunities follows a "mark and sweep" approach. First, all referenced
    // interface blocks are found. This is achieved by visiting the shader.
    super.visitTranslationUnit(translationUnit);

    // Now that all referenced interface blocks are known, the un-referenced blocks can be
    // identified and recorded as candidates for removal.
    for (Declaration declaration : translationUnit.getTopLevelDeclarations()) {
      if (!(declaration instanceof InterfaceBlock)) {
        continue;
      }
      if (!referencedInterfaceBlocks.contains(declaration)) {
        addOpportunity(new InterfaceBlockReductionOpportunity(translationUnit,
            (InterfaceBlock) declaration,
            getVistitationDepth()));
      }
    }
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    // Check whether this variable identifier expression refers to a field of an interface block.
    // If it does, that interface blok cannot be removed.
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    final ScopeEntry scopeEntry =
        getCurrentScope().lookupScopeEntry(variableIdentifierExpr.getName());
    if (scopeEntry != null && scopeEntry.hasInterfaceBlock()) {
      referencedInterfaceBlocks.add(scopeEntry.getInterfaceBlock());
    }
  }

  private static List<InterfaceBlockReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    InterfaceBlockReductionOpportunities finder =
        new InterfaceBlockReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  static List<InterfaceBlockReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Collections.emptyList(), ListConcat::concatenate);
  }

}
