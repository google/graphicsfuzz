package com.graphicsfuzz.common.transformreduce;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.LayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

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
    int nextBinding = 0;

    for (Optional<TranslationUnit> maybeTu : Arrays.asList(vertexShader, fragmentShader)) {
      if (!maybeTu.isPresent()) {
        continue;
      }
      for (VariablesDeclaration variablesDeclaration : maybeTu.get().getTopLevelDeclarations()
          .stream()
          .filter(item -> item instanceof VariablesDeclaration)
          .map(item -> (VariablesDeclaration) item)
          .filter(item -> item.getBaseType().hasQualifier(TypeQualifier.UNIFORM))
          .collect(Collectors.toList())) {
        // Check that there are no existing bindings.
        final QualifiedType qualifiedType = (QualifiedType) variablesDeclaration.getBaseType();
        assert !qualifiedType.getQualifiers()
            .stream()
            .anyMatch(item -> item instanceof LayoutQualifier);
        // For now we are conservative and do not handle multiple declarations per uniform;
        // this should be trivial to handle in due course but we need to write proper tests for it.
        assert variablesDeclaration.getNumDecls() == 1;
        final String uniformName = variablesDeclaration.getDeclInfo(0).getName();
        assert uniformsInfo.containsKey(uniformName);
        if (!uniformsInfo.hasBinding(uniformName)) {
          uniformsInfo.addBindingToUniform(uniformName, nextBinding);
          nextBinding++;
        }
        qualifiedType.addQualifier(new LayoutQualifier("set = 0, binding = "
            + uniformsInfo.getBinding(uniformName)));
      }

    }


  }

}
