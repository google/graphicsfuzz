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

package com.graphicsfuzz.generator.transformation.vectorizer;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.MergeSet;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VectorizationOpportunities extends ScopeTreeBuilder {

  private final IParentMap parentMap;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final IRandom generator;
  private final List<VectorizationOpportunity> opportunities;
  private BlockStmt lastExitedBlock;

  /**
   * Constructs an instance and gathers vectorization opportunities for the given node.
   *
   * @param node The node to be analysed for vectorization opportunities
   * @param shadingLanguageVersion The GLSL version of interest
   * @param generator For random number generation
   */
  public VectorizationOpportunities(IAstNode node, ShadingLanguageVersion shadingLanguageVersion,
      IRandom generator) {
    this.parentMap = IParentMap.createParentMap(node);
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.generator = generator;
    this.opportunities = new ArrayList<>();
    this.lastExitedBlock = null;
    visit(node);
  }

  @Override
  protected void popScope() {
    assert lastExitedBlock != null;
    if (!(parentMap.getParent(lastExitedBlock) instanceof SwitchStmt)) {
      List<MergeSet> mergeSetsForThisScope = new ArrayList<>();
      for (String v : currentScope.keys()) {
        ScopeEntry entry = currentScope.lookupScopeEntry(v);
        if (!isCandidateForMerging(entry)) {
          continue;
        }
        List<MergeSet> mergeSetsWithSpace = mergeSetsForThisScope.stream()
              .filter(mergeSet -> mergeSet
                    .canAccept(entry)).collect(Collectors.toList());
        int index = generator.nextInt(mergeSetsWithSpace.size() + 1);
        if (index == mergeSetsWithSpace.size()) {
          MergeSet newMergeSet = new MergeSet(entry);
          mergeSetsForThisScope.add(newMergeSet);
        } else {
          mergeSetsWithSpace.get(index).add(entry);
        }
      }
      opportunities.addAll(
            mergeSetsForThisScope.stream().filter(mergeSet -> mergeSet.getNumVars() > 1)
                  .map(mergeSet -> new VectorizationOpportunity(lastExitedBlock, mergeSet,
                        parentMap))
                  .collect(Collectors.toList()));
    }
    super.popScope();
  }

  private boolean isCandidateForMerging(ScopeEntry entry) {
    if (!entry.hasVariableDeclInfo()) {
      return false;
    }
    if (shadingLanguageVersion.initializersOfConstMustBeConst()) {
      if (entry.getType() instanceof QualifiedType
            && ((QualifiedType) entry.getType()).hasQualifier(TypeQualifier.CONST)) {
        // Do not merge const variables if the shading language requires the initializers of
        // const variables to be const.  The issue is that if v is const and used in
        // some future initialiser, and we merge v into m, as m.x say, then m.x will appear in the
        // future initializer, which is illegal if m is not const itself.
        // With some additional analysis we could make this restriction only apply to consts
        // that are actually used in future initialisers.
        return false;
      }
    }
    Type unqualifiedType = entry.getType().getWithoutQualifiers();
    if (!(unqualifiedType instanceof BasicType)) {
      return false;
    }
    BasicType basicType = (BasicType) unqualifiedType;
    if (!(basicType.isScalar() || basicType.isVector())) {
      return false;
    }
    if (basicType.isBoolean()) {
      return false;
    }
    return true;
  }

  public List<VectorizationOpportunity> getOpportunities(
        TransformationProbabilities probabilities) {
    return opportunities.stream().filter(opportunity -> probabilities.vectorizeStmts(generator))
          .collect(Collectors.toList());
  }

  public List<VectorizationOpportunity> getAllOpportunities() {
    return Collections.unmodifiableList(opportunities);
  }

  @Override
  protected void leaveBlockStmt(BlockStmt stmt) {
    lastExitedBlock = stmt;
    super.leaveBlockStmt(stmt);
  }

}
