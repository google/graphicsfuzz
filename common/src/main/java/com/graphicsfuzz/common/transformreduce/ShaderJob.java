package com.graphicsfuzz.common.transformreduce;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.UniformsInfo;

public interface ShaderJob {

  boolean hasFragmentShader();

  boolean hasVertexShader();

  TranslationUnit getFragmentShader();

  TranslationUnit getVertexShader();

  UniformsInfo getUniformsInfo();

}
