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
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.type.StructType;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import java.util.LinkedList;
import java.util.List;

public class InlineStructifiedFieldReductionOpportunities extends ScopeTreeBuilder {

  private final List<InlineStructifiedFieldReductionOpportunity> opportunities;
  private final TranslationUnit tu;

  private InlineStructifiedFieldReductionOpportunities(TranslationUnit tu) {
    this.opportunities = new LinkedList<>();
    this.tu = tu;
  }

  static List<InlineStructifiedFieldReductionOpportunity> findOpportunities(
        TranslationUnit tu,
        ReductionOpportunityContext context) {
    InlineStructifiedFieldReductionOpportunities finder =
          new InlineStructifiedFieldReductionOpportunities(tu);
    finder.visit(tu);
    return finder.opportunities;
  }

  @Override
  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    super.visitDeclarationStmt(declarationStmt);
    if (!Util.isStructifiedDeclaration(declarationStmt)) {
      return;
    }
    findInliningOpportunities(
          (StructType) declarationStmt.getVariablesDeclaration().getBaseType()
                .getWithoutQualifiers());

  }

  public void findInliningOpportunities(StructType struct) {
    assert struct.getName().startsWith(Constants.STRUCTIFICATION_STRUCT_PREFIX);
    for (String f : struct.getFieldNames()) {
      if (!f.startsWith(Constants.STRUCTIFICATION_FIELD_PREFIX)) {
        continue;
      }
      if (struct.getFieldType(f).getWithoutQualifiers() instanceof StructType) {
        opportunities.add(new InlineStructifiedFieldReductionOpportunity(
              struct, f, tu, getVistitationDepth()));
        findInliningOpportunities((StructType) struct.getFieldType(f).getWithoutQualifiers());
      }
    }
  }

}
