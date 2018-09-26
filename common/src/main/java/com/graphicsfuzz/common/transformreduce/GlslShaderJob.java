package com.graphicsfuzz.common.transformreduce;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.LayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    int nextBinding = 0;

    for (Optional<TranslationUnit> maybeTu : Arrays.asList(vertexShader, fragmentShader)) {
      if (!maybeTu.isPresent()) {
        continue;
      }
      final List<Declaration> newTopLevelDeclarations = new ArrayList<>();
      for (Declaration decl : maybeTu.get().getTopLevelDeclarations()) {
        // For now we conservatively assume that there are no interface blocks, covering our
        // assumption that there are no existing bindings.
        assert !(decl instanceof InterfaceBlock);
        if (decl instanceof VariablesDeclaration &&
            ((VariablesDeclaration) decl).getBaseType().hasQualifier(TypeQualifier.UNIFORM)) {
          final VariablesDeclaration variablesDeclaration = (VariablesDeclaration) decl;
          final QualifiedType qualifiedType = (QualifiedType) variablesDeclaration.getBaseType();
          // Conservatively assume that uniform is the only qualifier.
          assert qualifiedType.getQualifiers().size() == 1;
          // For now we are conservative and do not handle multiple declarations per uniform;
          // this should be trivial to handle in due course but we need to write proper tests for it.
          assert variablesDeclaration.getNumDecls() == 1;
          final String uniformName = variablesDeclaration.getDeclInfo(0).getName();
          assert uniformsInfo.containsKey(uniformName);
          if (!uniformsInfo.hasBinding(uniformName)) {
            uniformsInfo.addBindingToUniform(uniformName, nextBinding);
            nextBinding++;
          }
          final int binding = uniformsInfo.getBinding(uniformName);
          newTopLevelDeclarations.add(
            new InterfaceBlock(
                Optional.of(new LayoutQualifier("set = 0, binding = " + binding)),
                TypeQualifier.UNIFORM,
                "buf" + binding,
                Arrays.asList(uniformName),
                Arrays.asList(qualifiedType.getWithoutQualifiers()),
                Optional.empty())
          );
        } else {
          newTopLevelDeclarations.add(decl);
        }
      }
      maybeTu.get().setTopLevelDeclarations(newTopLevelDeclarations);
    }
  }

}
