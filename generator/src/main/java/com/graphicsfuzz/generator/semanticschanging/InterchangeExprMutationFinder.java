/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.semanticschanging;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.templates.IExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.ParenExprTemplate;
import com.graphicsfuzz.generator.mutateapi.Expr2ExprMutation;
import com.graphicsfuzz.generator.mutateapi.Expr2ExprMutationFinder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InterchangeExprMutationFinder extends Expr2ExprMutationFinder {

  private final IRandom generator;

  public InterchangeExprMutationFinder(TranslationUnit tu,
                                       IRandom generator) {
    super(tu);
    this.generator = generator;
  }

  @Override
  protected void visitExpr(Expr expr) {
    if (!typer.hasType(expr)) {
      return;
    }
    if (expr.getNumChildren() == 0) {
      return;
    }
    final List<Type> childTypes = new ArrayList<>();
    for (int i = 0; i < expr.getNumChildren(); i++) {
      if (!typer.hasType(expr.getChild(i))) {
        return;
      }
      childTypes.add(typer.lookupType(expr.getChild(i)).getWithoutQualifiers());
    }
    addMutation(new Expr2ExprMutation(parentMap.getParent(expr), expr,
        () -> applyInterchange(expr, childTypes)));
  }

  private Expr applyInterchange(Expr expr, List<Type> argumentTypes) {
    final Type resultType = typer.lookupType(expr).getWithoutQualifiers();
    List<IExprTemplate> templates = Fuzzer.availableTemplatesFromScope(
        getTranslationUnit().getShadingLanguageVersion(), currentScope)
        // Ignore templates that require l-values, so that invalidity is not too likely
        .filter(InterchangeExprMutationFinder::doesNotRequireLvalue)
        // Ignore the possibility of replacing a one-argument expression with parentheses, as it
        // is not very interesting.
        .filter(item -> !(item instanceof ParenExprTemplate))
        // Restrict to templates with the right result type.
        .filter(item -> item.getResultType().equals(resultType))
        // Restrict to templates with the same numbers of arguments of the same types.
        .filter(item -> compatibleArguments(item, argumentTypes))
        .collect(Collectors.toList());
    if (templates.isEmpty()) {
      // If no templates remain, don't change the expression.
      return expr;
    }
    // Choose a template at random.
    final IExprTemplate template = templates.get(generator.nextInt(templates.size()));
    // Generate an expression from the template, randomly assigning the expression's
    // sub-expressions to arguments of the template with matching types.
    return template.generateExpr(generator, selectExpressionsForTemplate(expr,
        argumentTypes,
        template));
  }

  /**
   * <p>The given expr and the given template accept and require, respectively, expressions of
   * the same types but possibly in a different order.  This randomly selects an ordering of
   * the expression's subexpressions that match the requirements of the template.
   * </p>
   * <p>
   * For example, if the expression were foo(1, 2.0, 3) and the template required expressions
   * of type [int, int, float], this method could return [1, 3, 2.0] or [3, 1, 2.0].
   * </p>
   */
  private List<Expr> selectExpressionsForTemplate(Expr exprToBeInterchanged,
                                                  List<Type> typesForSubexpressions,
                                                  IExprTemplate template) {
    assert exprToBeInterchanged.getNumChildren() == typesForSubexpressions.size();
    assert exprToBeInterchanged.getNumChildren() == template.getNumArguments();
    final List<Integer> remainingArgumentsIndices = new ArrayList<>();
    for (int i = 0; i < exprToBeInterchanged.getNumChildren(); i++) {
      remainingArgumentsIndices.add(i);
    }
    final List<Expr> shuffledArguments = new ArrayList<>();
    assert remainingArgumentsIndices.size() == template.getNumArguments();
    for (int i = 0; i < template.getNumArguments(); i++) {
      assert remainingArgumentsIndices.size() + shuffledArguments.size()
          == typesForSubexpressions.size();
      final Type argumentType = template.getArgumentTypes().get(i).get(0);
      final List<Integer> choices =
          remainingArgumentsIndices.stream().filter(item -> typesForSubexpressions.get(item)
          .equals(argumentType)).collect(Collectors.toList());
      assert !choices.isEmpty();
      final Integer choice = choices.get(generator.nextInt(choices.size()));
      shuffledArguments.add(exprToBeInterchanged.getChild(choice));
      remainingArgumentsIndices.remove(choice);
    }
    return shuffledArguments;
  }

  /**
   * Returns true if and only if the template types and argument types are permutations
   * of one another.
   */
  private boolean compatibleArguments(IExprTemplate template, List<Type> argumentTypes) {
    if (template.getNumArguments() != argumentTypes.size()) {
      return false;
    }
    final Map<Type, Integer> typeCountsForTemplate = new HashMap<>();
    final Map<Type, Integer> typeCountsForExpr = new HashMap<>();
    for (int i = 0; i < template.getNumArguments(); i++) {
      countType(typeCountsForTemplate, template.getArgumentTypes().get(i).get(0));
      countType(typeCountsForExpr, argumentTypes.get(i));
    }
    return typeCountsForExpr.equals(typeCountsForTemplate);
  }

  /**
   * Increments 'typeCounts' at 'type', or sets it to 1 if not present.
   */
  private void countType(Map<Type, Integer> typeCounts, Type type) {
    if (typeCounts.containsKey(type)) {
      typeCounts.put(type,
          typeCounts.get(type) + 1);
    } else {
      typeCounts.put(type, 1);
    }
  }

  private static boolean doesNotRequireLvalue(IExprTemplate template) {
    for (int i = 0; i < template.getNumArguments(); i++) {
      if (template.requiresLValueForArgument(i)) {
        return false;
      }
    }
    return true;
  }

}
