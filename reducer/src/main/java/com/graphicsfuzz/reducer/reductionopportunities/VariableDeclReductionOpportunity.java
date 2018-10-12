package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public class VariableDeclReductionOpportunity extends AbstractReductionOpportunity {

  final VariableDeclInfo variableDeclInfo;
  final VariablesDeclaration variablesDeclaration; // The parent of variableDeclInfo

  VariableDeclReductionOpportunity(VariableDeclInfo variableDeclInfo,
                                   VariablesDeclaration variablesDeclaration,
                                   VisitationDepth depth) {
    super(depth);
    this.variableDeclInfo = variableDeclInfo;
    this.variablesDeclaration = variablesDeclaration;
  }

  @Override
  void applyReductionImpl() {
    if (!variablesDeclaration.getDeclInfos().contains(variableDeclInfo)) {
      // The declaration must have been removed by another reduction opportunity
      return;
    }
    for (int i = 0; i < variablesDeclaration.getNumDecls(); i++) {
      if (variablesDeclaration.getDeclInfo(i) == variableDeclInfo) {
        variablesDeclaration.removeDeclInfo(i);
        return;
      }
    }
    throw new RuntimeException("Should be unreachable.");
  }

  @Override
  public boolean preconditionHolds() {
    return variablesDeclaration.getDeclInfos().contains(variableDeclInfo);
  }

}
