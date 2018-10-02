package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.util.Optional;

/**
 * We have a lot of tests written with just fragment shaders in mind.
 * This class provides a helper method to make a shader job from a fragment shader.
 */
public class MakeShaderJobFromFragmentShader {

  private MakeShaderJobFromFragmentShader() {
    // Utility class
  }

  public static ShaderJob make(TranslationUnit tu) {
    return new GlslShaderJob(Optional.empty(), Optional.of(tu), new UniformsInfo());
  }

}
