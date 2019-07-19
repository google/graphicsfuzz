package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;

public class VariableFact {
  VariablesDeclaration variablesDeclaration;
  VariableDeclInfo variableDeclInfo;
  Value value;


  public VariableFact(VariablesDeclaration variablesDeclaration,
                      VariableDeclInfo variableDeclInfo, Value value) {
    this.variablesDeclaration = variablesDeclaration;
    this.variableDeclInfo = variableDeclInfo;
    this.value = value;
  }
}
