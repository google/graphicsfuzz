package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public class GlobalVariablesDeclarationReductionOpportunity extends AbstractReductionOpportunity {

  final TranslationUnit translationUnit;
  final VariablesDeclaration variablesDeclaration;

  public GlobalVariablesDeclarationReductionOpportunity(TranslationUnit translationUnit,
                                                        VariablesDeclaration variablesDeclaration,
                                                        VisitationDepth depth) {
    super(depth);
    this.translationUnit = translationUnit;
    this.variablesDeclaration = variablesDeclaration;
  }

  @Override
  void applyReductionImpl() {
    translationUnit.removeTopLevelDeclaration(variablesDeclaration);
  }

  @Override
  public boolean preconditionHolds() {
    return translationUnit.getTopLevelDeclarations().contains(variablesDeclaration);
  }

}
