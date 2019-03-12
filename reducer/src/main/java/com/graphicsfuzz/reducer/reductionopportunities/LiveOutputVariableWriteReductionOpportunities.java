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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.util.Constants;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class LiveOutputVariableWriteReductionOpportunities
    extends ReductionOpportunitiesBase<LiveOutputVariableWriteReductionOpportunity> {

  static List<LiveOutputVariableWriteReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<LiveOutputVariableWriteReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    LiveOutputVariableWriteReductionOpportunities finder =
          new LiveOutputVariableWriteReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  private LiveOutputVariableWriteReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
  }

  @Override
  protected void visitChildOfBlock(BlockStmt block, int index) {

    final Stmt child = block.getStmt(index);

    if (!(child instanceof BlockStmt)) {
      return;
    }
    final Optional<String> backupName = containsOutVariableBackup((BlockStmt) child);
    if (backupName.isPresent()) {
      addOpportunity(new LiveOutputVariableWriteReductionOpportunity((BlockStmt) child,
            backupName.get(),
            getVistitationDepth()));
    }
  }

  private static Optional<String> containsOutVariableBackup(BlockStmt block) {
    return block.getStmts().stream()
          .filter(item -> item instanceof DeclarationStmt)
          .map(item -> (DeclarationStmt) item)
          .filter(LiveOutputVariableWriteReductionOpportunities::isOutVariableBackup)
          .findFirst()
          .map(vdi -> vdi.getVariablesDeclaration().getDeclInfos()
            .stream().map(item -> item.getName()).findFirst().get());
  }

  private static boolean isOutVariableBackup(DeclarationStmt declarationStmt) {
    return declarationStmt.getVariablesDeclaration().getDeclInfos()
        .stream().map(VariableDeclInfo::getName)
        .anyMatch(LiveOutputVariableWriteReductionOpportunities::isOutVariableBackup);
  }

  private static boolean isOutVariableBackup(String name) {
    return name.startsWith(Constants.GLF_OUT_VAR_BACKUP_PREFIX);
  }

}
