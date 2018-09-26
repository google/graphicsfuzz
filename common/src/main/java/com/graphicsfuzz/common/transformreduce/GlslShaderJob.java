package com.graphicsfuzz.common.transformreduce;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.util.Optional;

public class GlslShaderJob implements ShaderJob {

  private final Optional<TranslationUnit> vertexShader;
  private final Optional<TranslationUnit> fragmentShader;
  private final UniformsInfo uniformsInfo;

  public GlslShaderJob(Optional<TranslationUnit> vertexShader,
                            Optional<TranslationUnit> fragmentShader,
                            UniformsInfo uniformsInfo) {
    this.vertexShader = vertexShader;
    this.fragmentShader = fragmentShader;
    this.uniformsInfo = uniformsInfo;
  }

  @Override
  public boolean hasVertexShader() {
    return vertexShader.isPresent();
  }

  @Override
  public boolean hasFragmentShader() {
    return fragmentShader.isPresent();
  }

  @Override
  public TranslationUnit getVertexShader() {
    assert hasVertexShader();
    return vertexShader.get();
  }

  @Override
  public TranslationUnit getFragmentShader() {
    assert hasFragmentShader();
    return fragmentShader.get();
  }

  @Override
  public UniformsInfo getUniformsInfo() {
    return uniformsInfo;
  }

}
