/*
 * Copyright 2018 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.transformreduce;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.LayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
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
  private final Optional<String> license;

  public GlslShaderJob(Optional<TranslationUnit> vertexShader,
                       Optional<TranslationUnit> fragmentShader,
                       UniformsInfo uniformsInfo,
                       Optional<String> license) {
    this.vertexShader = vertexShader;
    this.fragmentShader = fragmentShader;
    this.uniformsInfo = uniformsInfo;
    this.license = license;
  }

  @Override
  public Optional<TranslationUnit> getVertexShader() {
    return vertexShader;
  }

  @Override
  public Optional<TranslationUnit> getFragmentShader() {
    return fragmentShader;
  }

  @Override
  public UniformsInfo getUniformsInfo() {
    return uniformsInfo;
  }

  @Override
  public Optional<String> getLicense() {
    return license;
  }

  /**
   * <p>This method works under the assumption that uniforms in the vertex and fragment
   * shader are "plain".  That is, they have no associated bindings and are not part of uniform
   * blocks.</p>
   *
   * <p>The method then puts every uniform in its own uniform block and assigns bindings.
   * If a uniform is declared in both shaders, the binding it is given is common to them.</p>
   */
  @Override
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
        if (decl instanceof VariablesDeclaration
            && ((VariablesDeclaration) decl).getBaseType().hasQualifier(TypeQualifier.UNIFORM)) {
          final VariablesDeclaration variablesDeclaration = (VariablesDeclaration) decl;
          if (variablesDeclaration.getNumDecls() == 0) {
            // No uniforms are actually declared; move on.
            continue;
          }
          final QualifiedType qualifiedType = (QualifiedType) variablesDeclaration.getBaseType();
          // Conservatively assume that uniform is the only qualifier.
          assert qualifiedType.getQualifiers().size() == 1;
          // For now we are conservative and do not handle multiple declarations per uniform; this
          // should be trivial to handle in due course but we need to write proper tests for it.
          assert variablesDeclaration.getNumDecls() == 1;
          // We cannot yet deal with adding bindings for uniform arrays.
          assert !variablesDeclaration.getDeclInfo(0).hasArrayInfo();
          final String uniformName = variablesDeclaration.getDeclInfo(0).getName();
          assert uniformsInfo.containsKey(uniformName);
          if (!uniformsInfo.hasBinding(uniformName)) {
            uniformsInfo.addBinding(uniformName, nextBinding);
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

    // Add bindings to any uniforms not referenced in the shaders, so that we don't end up in
    // a situation where some uniforms are unbound.
    for (String uniformName : getUniformsInfo().getUniformNames()) {
      if (!getUniformsInfo().hasBinding(uniformName)) {
        getUniformsInfo().addBinding(uniformName, nextBinding++);
      }
    }
  }

  /**
   * <p>This method works under the assumption that uniforms in the vertex and fragment
   * shader are enclosed in uniform blocks, with associated bindings.  That is, there are no
   * "plain" uniform declarations.</p>
   *
   * <p>The method removes all bindings and demotes the uniform blocks to plain uniforms.</p>
   */
  @Override
  public void removeUniformBindings() {
    for (String uniformName : getUniformsInfo().getUniformNames()) {
      assert getUniformsInfo().hasBinding(uniformName);
      getUniformsInfo().removeBinding(uniformName);
    }
    for (Optional<TranslationUnit> maybeTu : Arrays.asList(vertexShader, fragmentShader)) {
      if (!maybeTu.isPresent()) {
        continue;
      }
      final List<Declaration> newTopLevelDeclarations = new ArrayList<>();
      for (Declaration decl : maybeTu.get().getTopLevelDeclarations()) {
        if (decl instanceof VariablesDeclaration) {
          // We are assuming that there are no plain uniforms - all uniforms should be in
          // interface blocks, which we shall remove.
          assert !((VariablesDeclaration) decl).getBaseType().hasQualifier(TypeQualifier.UNIFORM);
        }
        if (decl instanceof InterfaceBlock && ((InterfaceBlock) decl).getInterfaceQualifier()
            .equals(TypeQualifier.UNIFORM)) {
          final InterfaceBlock interfaceBlock = (InterfaceBlock) decl;
          // We are assuming that each uniform block wraps precisely one uniform.
          assert interfaceBlock.getMemberNames().size() == 1;
          final String uniformName = interfaceBlock.getMemberNames().get(0);
          newTopLevelDeclarations.add(new VariablesDeclaration(
              new QualifiedType(interfaceBlock.getMemberTypes().get(0).getWithoutQualifiers(),
                  Arrays.asList(TypeQualifier.UNIFORM)),
              new VariableDeclInfo(uniformName, null, null)));
        } else {
          newTopLevelDeclarations.add(decl);
        }
      }
      maybeTu.get().setTopLevelDeclarations(newTopLevelDeclarations);
    }
  }

  @Override
  public boolean hasUniformBindings() {
    for (String uniformName : getUniformsInfo().getUniformNames()) {
      // We maintain the invariant that either all or no uniforms have bindings, so it suffices
      // to check the first one we come across.
      return getUniformsInfo().hasBinding(uniformName);
    }
    return false;
  }

  @Override
  public List<TranslationUnit> getShaders() {
    final List<TranslationUnit> result = new ArrayList<>();
    if (getVertexShader().isPresent()) {
      result.add(getVertexShader().get());
    }
    if (getFragmentShader().isPresent()) {
      result.add(getFragmentShader().get());
    }
    return result;
  }

  @Override
  public GlslShaderJob clone() {
    return new GlslShaderJob(
        getVertexShader().map(TranslationUnit::clone),
        getFragmentShader().map(TranslationUnit::clone),
        new UniformsInfo(getUniformsInfo().toString()),
        getLicense());
  }

}
