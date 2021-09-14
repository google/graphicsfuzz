/*
 * Copyright 2021 The GraphicsFuzz Project Authors
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

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.util.List;
import org.junit.Test;

public class InterfaceBlockReductionOpportunitiesTest {

  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Test
  public void testUsedInterfaceBlockIsNotRemoved() throws Exception {
    final String original = "layout(binding = 0) buffer SomeName {"
        + "  int x;"
        + "};"
        + "void main() { x; }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<InterfaceBlockReductionOpportunity> ops = InterfaceBlockReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testUnusedInterfaceBlockIsRemoved() throws Exception {
    final String original = "layout(binding = 0) buffer SomeName {"
        + "  int x;"
        + "};"
        + "void main() { }";
    final String expected = "void main() { }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<InterfaceBlockReductionOpportunity> ops = InterfaceBlockReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
    final List<InterfaceBlockReductionOpportunity> moreOps =
        InterfaceBlockReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                    new RandomWrapper(0), new IdGenerator()));
    assertEquals(0, moreOps.size());
  }

}
