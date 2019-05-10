package com.graphicsfuzz.reducer.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Ignore;
import org.junit.Test;

public class SimplifyTest {

  // TODO(110) - this test fails due to issue 110, and should be enabled once that issue is fixed.
  @Ignore
  @Test
  public void testParenthesesRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {"
        + "  if (_GLF_DEAD(_GLF_FALSE(false, false))) {"
        + "    _GLF_FUZZED(1);"
        + "  }"
        + "}");
    final String expected = "void main() {"
        + "  if(false) {"
        + "    1;"
        + "  }"
        + "}";
    final TranslationUnit simplifiedTu = Simplify.simplify(tu);
    CompareAsts.assertEqualAsts(expected, simplifiedTu);
  }

}
