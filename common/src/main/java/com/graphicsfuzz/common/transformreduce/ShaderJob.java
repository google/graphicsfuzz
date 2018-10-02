package com.graphicsfuzz.common.transformreduce;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.util.List;
import java.util.Optional;

public interface ShaderJob {

  boolean hasFragmentShader();

  boolean hasVertexShader();

  TranslationUnit getFragmentShader();

  TranslationUnit getVertexShader();

  UniformsInfo getUniformsInfo();

  void makeUniformBindings();

  void removeUniformBindings();

  boolean hasUniformBindings();

  List<TranslationUnit> getShaders();

  ShaderJob clone();

}
