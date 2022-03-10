/*
 * Copyright 2022 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Collections;
import java.util.List;

/**
 * <p>This class finds opportunities to remove redundant swizzles in swizzle chains.</p>
 */
public class RemoveSwizzleReductionOpportunities
    extends ReductionOpportunitiesBase<RemoveSwizzleReductionOpportunity> {

  private final Typer typer;

  static List<RemoveSwizzleReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Collections.emptyList(), ListConcat::concatenate);
  }

  private static List<RemoveSwizzleReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    RemoveSwizzleReductionOpportunities finder =
        new RemoveSwizzleReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  private RemoveSwizzleReductionOpportunities(TranslationUnit tu,
                                                      ReducerContext context) {
    super(tu, context);
    this.typer = new Typer(tu);
  }

  @Override
  public void visitTranslationUnit(TranslationUnit translationUnit) {

    if (!context.reduceEverywhere()) {
      // This class finds semantics-changing reduction opportunities, so we cannot use it unless we
      // are reducing everywhere.
      return;
    }

    super.visitTranslationUnit(translationUnit);
  }

  @Override
  public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
    super.visitMemberLookupExpr(memberLookupExpr);
    if (!RemoveSwizzleReductionOpportunity.isSwizzle(memberLookupExpr, typer)) {
      return;
    }
    final IAstNode parent = parentMap.getParent(memberLookupExpr);
    if (typer.lookupType(memberLookupExpr) == typer.lookupType(memberLookupExpr.getStructure())
        .getWithoutQualifiers()) {
      addOpportunity(new RemoveSwizzleReductionOpportunity(parent,
          memberLookupExpr.getStructure(), memberLookupExpr, typer, getVistitationDepth()));
      return;
    }
    if (!RemoveSwizzleReductionOpportunity.isSwizzle(parent, typer)) {
      return;
    }
    final BasicType basicType =
        (BasicType) typer.lookupType(memberLookupExpr.getStructure()).getWithoutQualifiers();
    if (basicType.getNumElements() > RemoveSwizzleReductionOpportunity
        .getLargestSwizzleComponent(parent)) {
      addOpportunity(new RemoveSwizzleReductionOpportunity(parent,
          memberLookupExpr.getStructure(), memberLookupExpr, typer, getVistitationDepth()));
    }
  }
}
