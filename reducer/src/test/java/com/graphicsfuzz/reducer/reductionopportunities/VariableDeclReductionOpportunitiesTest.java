package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class VariableDeclReductionOpportunitiesTest {

  @Test
  public void testRemoveUnusedLiveCodeDecl() throws Exception {
    final String program = "void main() {\n"
        + "  int GLF_live0c = 3;\n"
        + "}\n";
    final String reducedProgram = "void main() {\n"
        + "  int ;\n"
        + "}\n";
    final TranslationUnit tu = Helper.parse(program, false);
    List<VariableDeclReductionOpportunity> ops = VariableDeclReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(reducedProgram, false)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}