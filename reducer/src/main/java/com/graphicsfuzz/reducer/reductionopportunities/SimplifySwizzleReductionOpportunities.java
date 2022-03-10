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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * <p>This class finds opportunities to make swizzles (and vector lookups) simpler.</p>
 */
public class SimplifySwizzleReductionOpportunities
    extends ReductionOpportunitiesBase<SimplifySwizzleReductionOpportunity> {

  private final Typer typer;

  static List<SimplifySwizzleReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Collections.emptyList(), ListConcat::concatenate);
  }

  private static List<SimplifySwizzleReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    SimplifySwizzleReductionOpportunities finder =
        new SimplifySwizzleReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  private SimplifySwizzleReductionOpportunities(TranslationUnit tu,
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
    final Set<Character> existingComponents = new HashSet<>();
    for (int i = 0; i < memberLookupExpr.getMember().length(); i++) {
      existingComponents.add(memberLookupExpr.getMember().charAt(i));
    }
    for (int component = 0; component < memberLookupExpr.getMember().length(); component++) {
      char currentComponent = memberLookupExpr.getMember().charAt(component);
      // We push replacements on to a queue such that the most aggressive replacements are pushed
      // last, then pop them in LIFO order. This ensures that e.g. changing 'w' to 'x' is added as
      // an opportunity before changing 'w' to 'z'.
      final Deque<Character> replacements = new LinkedList<>();
      switch (currentComponent) {
        case 'w':
          replacements.push('z');
          // fall through
        case 'z':
          replacements.push('y');
          // fall through
        case 'y':
          replacements.push('x');
          break;
        case 'a':
          replacements.push('b');
          // fall through
        case 'b':
          replacements.push('g');
          // fall through
        case 'g':
          replacements.push('r');
          break;
        case 'q':
          replacements.push('p');
          // fall through
        case 'p':
          replacements.push('t');
          // fall through
        case 't':
          replacements.push('s');
          break;
        default:
          break;
      }
      if (inLValueContext()) {
        // An l-value swizzle cannot have repeated components, so we remove any existing components
        // from the set of replacements.
        replacements.removeAll(existingComponents);
      }
      while (!replacements.isEmpty()) {
        addOpportunity(new SimplifySwizzleReductionOpportunity(memberLookupExpr, component,
            replacements.pop(), inLValueContext(), getVistitationDepth()));
      }
    }
  }
}
