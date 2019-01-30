package com.graphicsfuzz.generator.fuzzer.templates;

import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VariableIdentifierExprTemplateTest {

  @Test
  public void testConstIsNotLValue() throws Exception {
    final String program = "#version 100\n"
        + "const int x;";
    assertFalse(getTemplateFromSingleDeclarationProgram(program).isLValue());
  }

  @Test
  public void testUniformIsNotLValue() throws Exception {
    final String program = "#version 100\n"
        + "uniform int x;";
    assertFalse(getTemplateFromSingleDeclarationProgram(program).isLValue());
  }

  @Test
  public void testAttributeIsNotLValue() throws Exception {
    final String program = "#version 100\n"
        + "attribute int x;";
    assertFalse(getTemplateFromSingleDeclarationProgram(program).isLValue());
  }

  @Test
  public void testShaderInputIsNotLValue() throws Exception {
    final String program = "#version 310 es\n"
        + "in int x;"
        + "void main() {"
        + "}";
    assertFalse(getTemplateFromSingleDeclarationProgram(program).isLValue());
  }

  @Test
  public void testPlainIsLValue() throws Exception {
    final String program = "#version 100\n"
        + "int x;";
    assertTrue(getTemplateFromSingleDeclarationProgram(program).isLValue());
  }

  @Test
  public void testShaderOutputIsLValue() throws Exception {
    final String program = "#version 100\n"
        + "out int x;";
    assertTrue(getTemplateFromSingleDeclarationProgram(program).isLValue());
  }

  public VariableIdentifierExprTemplate getTemplateFromSingleDeclarationProgram(String program) throws IOException, ParseTimeoutException, InterruptedException {
    final VariablesDeclaration variablesDeclaration =
        (VariablesDeclaration) ParseHelper.parse(program).getTopLevelDeclarations().get(0);
    return new VariableIdentifierExprTemplate(variablesDeclaration.getDeclInfo(0).getName(),
        variablesDeclaration.getBaseType());
  }

}
