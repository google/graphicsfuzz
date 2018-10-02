package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;

public class InlineUniformReductionOpportunitiesTest {

  @Test
  public void inlineUniforms() throws Exception {
    final String prog =
            "uniform float f;" +
            "uniform int i;" +
            "uniform vec2 v;" +
            "uniform uint u;" +
            "void main() {" +
            "  if (f > float(i)) {" +
            "    f + v.x + v.y + float(i) + float(u);" +
            "  }" +
            "}";
    final TranslationUnit tu = Helper.parse(prog, false);
    final UniformsInfo uniformsInfo = new UniformsInfo();
    uniformsInfo.addUniform("f", BasicType.FLOAT, Optional.empty(), Arrays.asList(3.2));
    uniformsInfo.addUniform("i", BasicType.INT, Optional.empty(), Arrays.asList(10));
    uniformsInfo.addUniform("v", BasicType.VEC2, Optional.empty(), Arrays.asList(2.2, 2.3));
    uniformsInfo.addUniform("u", BasicType.UINT, Optional.empty(), Arrays.asList(17));

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), Optional.of(tu), uniformsInfo);




  }

}