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

  /**
   * <p>This method works under the assumption that uniforms in the vertex and fragment
   * shader are "plain".  That is, they have no associated bindings and are not part of uniform
   * blocks.</p>
   *
   * <p>The method then puts every uniform in its own uniform block and assigns bindings.
   * If a uniform is declared in both shaders, the binding it is given is common to them.</p>
   */
  public void makeUniformBindings() {
    for (String uniformName : getUniformsInfo().getUniformNames()) {
      assert !getUniformsInfo().hasBinding(uniformName);
    }
  }

}
