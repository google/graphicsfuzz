package com.graphicsfuzz.generator.transformation.injection;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AvailableStructsCollector extends ScopeTreeBuilder {

  private final List<StructDefinitionType> structDefinitionTypes;
  private final IAstNode donorFragment;

  public AvailableStructsCollector(TranslationUnit donor, IAstNode donorFragment) {
    this.structDefinitionTypes = new ArrayList<>();
    this.donorFragment = donorFragment;
    visit(donor);
  }

  @Override
  public void visit(IAstNode node) {
    if (node == donorFragment) {
      assert structDefinitionTypes.isEmpty();
      for (String structName : currentScope.namesOfAllStructDefinitionsInScope()) {
        structDefinitionTypes.add(currentScope.lookupStructName(structName));
      }
    }
    super.visit(node);
  }

  public List<StructDefinitionType> getStructDefinitionTypes() {
    return Collections.unmodifiableList(structDefinitionTypes);
  }

}
