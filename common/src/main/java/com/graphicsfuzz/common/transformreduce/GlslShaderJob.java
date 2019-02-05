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
import com.graphicsfuzz.common.ast.type.BindingLayoutQualifier;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.SetLayoutQualifier;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.ShaderKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GlslShaderJob implements ShaderJob {

  private final Optional<String> license;
  private final PipelineInfo pipelineInfo;
  private final List<TranslationUnit> shaders;

  public GlslShaderJob(Optional<String> license,
                       PipelineInfo pipelineInfo,
                       List<TranslationUnit> shaders) {
    this.license = license;
    this.pipelineInfo = pipelineInfo;
    this.shaders = new ArrayList<>();
    final Set<ShaderKind> stagesSoFar = new HashSet<>();
    for (TranslationUnit tu : shaders) {
      if (stagesSoFar.contains(tu.getShaderKind())) {
        throw new IllegalArgumentException("Attempt to create shader job with multiple shaders of"
            + " kind " + tu.getShaderKind());
      }
      stagesSoFar.add(tu.getShaderKind());
      this.shaders.add(tu);
    }
  }

  public GlslShaderJob(Optional<String> license,
                       PipelineInfo pipelineInfo,
                       TranslationUnit... shaders) {
    this(license, pipelineInfo, Arrays.asList(shaders));
  }

  @Override
  public PipelineInfo getPipelineInfo() {
    return pipelineInfo;
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
    for (String uniformName : getPipelineInfo().getUniformNames()) {
      assert !getPipelineInfo().hasBinding(uniformName);
    }

    final Set<Integer> usedBindings = findUsedBindings();

    // Find the first free binding.
    int nextBinding = 0;
    while (usedBindings.contains(nextBinding)) {
      nextBinding++;
    }

    for (TranslationUnit tu : shaders) {
      final List<Declaration> newTopLevelDeclarations = new ArrayList<>();
      for (Declaration decl : tu.getTopLevelDeclarations()) {
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
          assert pipelineInfo.hasUniform(uniformName);
          if (!pipelineInfo.hasBinding(uniformName)) {
            pipelineInfo.addUniformBinding(uniformName, nextBinding);
            do {
              nextBinding++;
            } while (usedBindings.contains(nextBinding));
          }
          final int binding = pipelineInfo.getBinding(uniformName);

          // Keep any qualifiers apart from "uniform".
          final QualifiedType memberType = new QualifiedType(qualifiedType.getWithoutQualifiers(),
              qualifiedType.getQualifiers()
                  .stream()
                  .filter(item -> item != TypeQualifier.UNIFORM)
                  .collect(Collectors.toList()));
          newTopLevelDeclarations.add(
              new InterfaceBlock(
                  Optional.of(new LayoutQualifierSequence(new SetLayoutQualifier(0),
                      new BindingLayoutQualifier(binding))),
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
      tu.setTopLevelDeclarations(newTopLevelDeclarations);
    }

    // Add bindings to any uniforms not referenced in the shaders, so that we don't end up in
    // a situation where some uniforms are unbound.
    for (String uniformName : getPipelineInfo().getUniformNames()) {
      if (!getPipelineInfo().hasBinding(uniformName)) {
        getPipelineInfo().addUniformBinding(uniformName, nextBinding++);
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
    for (String uniformName : getPipelineInfo().getUniformNames()) {
      assert getPipelineInfo().hasBinding(uniformName);
      getPipelineInfo().removeUniformBinding(uniformName);
    }
    for (TranslationUnit tu : shaders) {
      final List<Declaration> newTopLevelDeclarations = new ArrayList<>();
      for (Declaration decl : tu.getTopLevelDeclarations()) {
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
      tu.setTopLevelDeclarations(newTopLevelDeclarations);
    }
  }

  @Override
  public boolean hasUniformBindings() {
    for (String uniformName : getPipelineInfo().getUniformNames()) {
      // We maintain the invariant that either all or no uniforms have bindings, so it suffices
      // to check the first one we come across.
      return getPipelineInfo().hasBinding(uniformName);
    }
    return false;
  }

  @Override
  public List<TranslationUnit> getShaders() {
    return Collections.unmodifiableList(shaders);
  }

  private Optional<TranslationUnit> getShaderOfGivenKind(ShaderKind shaderKind) {
    return shaders.stream().filter(item -> item.getShaderKind().equals(shaderKind)).findAny();
  }

  @Override
  public Optional<TranslationUnit> getVertexShader() {
    return getShaderOfGivenKind(ShaderKind.VERTEX);
  }

  @Override
  public Optional<TranslationUnit> getFragmentShader() {
    return getShaderOfGivenKind(ShaderKind.FRAGMENT);
  }

  @Override
  public Optional<TranslationUnit> getComputeShader() {
    return getShaderOfGivenKind(ShaderKind.COMPUTE);
  }

  @Override
  public GlslShaderJob clone() {
    return new GlslShaderJob(
        getLicense(),
        getPipelineInfo().clone(),
        shaders.stream().map(TranslationUnit::clone).collect(Collectors.toList()));
  }

  /**
   * If "binding=x" occurs in any of the shaders, then "x" counts as a used binding.  The point of
   * this method is that it can be queried to ensure that we do not give out bindings that are
   * already used.
   * @return Set of all bindings used by at least one of the shaders.
   */
  private Set<Integer> findUsedBindings() {
    final Set<Integer> result = new HashSet<>();
    for (TranslationUnit shader : shaders) {
      new StandardVisitor() {

        @Override
        public void visitInterfaceBlock(InterfaceBlock interfaceBlock) {
          super.visitInterfaceBlock(interfaceBlock);
          if (interfaceBlock.hasLayoutQualifierSequence()) {
            gatherBindings(interfaceBlock.getLayoutQualifierSequence());
          }
        }

        @Override
        public void visitQualifiedType(QualifiedType qualifiedType) {
          super.visitQualifiedType(qualifiedType);
          qualifiedType.getQualifiers()
              .stream()
              .filter(item -> item instanceof LayoutQualifierSequence)
              .map(item -> (LayoutQualifierSequence) item)
              .forEach(this::gatherBindings);
        }

        private void gatherBindings(LayoutQualifierSequence layoutQualifierSequence) {
          for (BindingLayoutQualifier bindingLayoutQualifier : layoutQualifierSequence
              .getLayoutQualifiers()
              .stream()
              .filter(item -> item instanceof BindingLayoutQualifier)
              .map(item -> (BindingLayoutQualifier) item)
              .collect(Collectors.toList())) {
            result.add(bindingLayoutQualifier.getIndex());
          }
        }

      }.visit(shader);
    }
    return result;
  }

}
