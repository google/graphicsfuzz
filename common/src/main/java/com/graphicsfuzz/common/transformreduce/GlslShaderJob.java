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

import static java.util.Optional.of;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BindingLayoutQualifier;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.PushConstantLayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.SamplerType;
import com.graphicsfuzz.common.ast.type.SetLayoutQualifier;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.typing.Typer;
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
import org.apache.commons.lang3.tuple.ImmutablePair;

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
   *
   * <p>Since samplers cannot be placed in uniform blocks, they get binding assignment but are
   * left outside uniform blocks.</p>
   *
   * <p>The pushConstant parameter can name one uniform as a candidate to be turned into a push
   * contant.</p>
   */
  @Override
  public void makeUniformBindings(Optional<String> pushConstant) {
    for (String uniformName : getPipelineInfo().getUniformNames()) {
      assert !getPipelineInfo().hasBindingOrIsPushConstant(uniformName);
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
          assert !(variablesDeclaration.getBaseType().getWithoutQualifiers() instanceof ArrayType)
              : "We cannot yet deal with uniforms with array base types, such as "
              + "'uniform float[5] A;'.";
          for (VariableDeclInfo declInfo : variablesDeclaration.getDeclInfos()) {
            final String uniformName = declInfo.getName();
            assert pipelineInfo.hasUniform(uniformName);
            if (pushConstant.isPresent() && pushConstant.get().equals(uniformName)) {
              // In case of push constants, the binding number (0 here) is ignored.
              pipelineInfo.addUniformBinding(uniformName, true, 0);
            } else if (!pipelineInfo.hasBinding(uniformName)) {
              pipelineInfo.addUniformBinding(uniformName, false, nextBinding);
              do {
                nextBinding++;
              } while (usedBindings.contains(nextBinding));
            }

            final Type baseType = variablesDeclaration.getBaseType();
            final Type targetType = baseType.getWithoutQualifiers();
            if (targetType instanceof SamplerType) {
              assert !declInfo.hasArrayInfo() : "We cannot yet deal with arrays of samplers.";
              assert !pipelineInfo.isPushConstant(uniformName) : "A sampler cannot be a push "
                  + "constant";

              // Samplers may not exist as part of uniform blocks.
              final int binding = pipelineInfo.getBinding(uniformName);
              newTopLevelDeclarations.add(
                  new VariablesDeclaration(
                      new QualifiedType(targetType,
                      Arrays.asList(
                          new LayoutQualifierSequence(
                              new SetLayoutQualifier(0),
                              new BindingLayoutQualifier(binding)),
                          TypeQualifier.UNIFORM
                          )), declInfo));
            } else {
              // The member's type is the combination of the base type and array info for the decl.
              // We clone this type, having created it, as we will use the same base type for other
              // decls in this variables declaration and we do not want to have aliasing.
              final Type memberType =
                  Typer.combineBaseTypeAndArrayInfo(baseType,
                      declInfo.getArrayInfo()).clone();

              ((QualifiedType) memberType).removeQualifier(TypeQualifier.UNIFORM);

              if (pipelineInfo.isPushConstant(uniformName)) {
                // Handle push constants separately
                newTopLevelDeclarations.add(
                    new InterfaceBlock(
                        of(new LayoutQualifierSequence(new PushConstantLayoutQualifier())),
                        Collections.singletonList(TypeQualifier.UNIFORM),
                        "buf_push",
                        Collections.singletonList(uniformName),
                        Collections.singletonList(memberType),
                        Optional.empty())
                );
              } else {
                // Not a push constant, so it's a normal uniform
                final int binding = pipelineInfo.getBinding(uniformName);
                newTopLevelDeclarations.add(
                    new InterfaceBlock(
                        of(new LayoutQualifierSequence(new SetLayoutQualifier(0),
                            new BindingLayoutQualifier(binding))),
                        Collections.singletonList(TypeQualifier.UNIFORM),
                        "buf" + binding,
                        Collections.singletonList(uniformName),
                        Collections.singletonList(memberType),
                        Optional.empty())
                );
              }
            }
          }
        } else {
          newTopLevelDeclarations.add(decl);
        }
      }
      tu.setTopLevelDeclarations(newTopLevelDeclarations);
    }

    // Add bindings to any uniforms not referenced in the shaders, so that we don't end up in
    // a situation where some uniforms are unbound.
    for (String uniformName : getPipelineInfo().getUniformNames()) {
      if (!getPipelineInfo().hasBindingOrIsPushConstant(uniformName)) {
        if (pushConstant.isPresent() && pushConstant.get().equals(uniformName)) {
          getPipelineInfo().addUniformBinding(uniformName, true, 0);
        } else {
          getPipelineInfo().addUniformBinding(uniformName, false, nextBinding++);
        }
      }
    }
  }

  /**
   * <p>This method works under the assumption that uniforms in the vertex and fragment
   * shader are enclosed in uniform blocks, with associated bindings.  That is, there are no
   * "plain" uniform declarations.</p>
   *
   * <p>Since samplers are not allowed to be inside uniform blocks, they are allowed to exist
   * outside the uniform blocks in the input.</p>
   *
   * <p>The method removes all bindings and demotes the uniform blocks to plain uniforms.</p>
   */
  @Override
  public void removeUniformBindings() {
    for (String uniformName : getPipelineInfo().getUniformNames()) {
      assert getPipelineInfo().hasBindingOrIsPushConstant(uniformName);
      getPipelineInfo().removeUniformBinding(uniformName);
    }
    for (TranslationUnit tu : shaders) {
      final List<Declaration> newTopLevelDeclarations = new ArrayList<>();
      for (Declaration decl : tu.getTopLevelDeclarations()) {

        if (decl instanceof VariablesDeclaration && ((VariablesDeclaration) decl).getBaseType()
            .hasQualifier(TypeQualifier.UNIFORM)) {
          final VariablesDeclaration variablesDeclaration = (VariablesDeclaration) decl;
          final Type targetType = variablesDeclaration.getBaseType().getWithoutQualifiers();
          assert targetType instanceof SamplerType : "Only samplers may exist as top-level "
              + "uniforms with bindings.";
          assert variablesDeclaration.getNumDecls() == 1 : "A bound sampler must appear on its "
              + "own.";
          final VariableDeclInfo declInfo = variablesDeclaration.getDeclInfos().get(0);
          newTopLevelDeclarations.add(
              new VariablesDeclaration(
                  new QualifiedType(targetType,
                      Collections.singletonList(
                          TypeQualifier.UNIFORM
                      )), declInfo));
        } else if (decl instanceof InterfaceBlock && ((InterfaceBlock) decl).isUniformBlock()) {
          final InterfaceBlock interfaceBlock = (InterfaceBlock) decl;
          assert interfaceBlock.getMemberNames().size() == 1 : "We assume that each uniform block"
              + " wraps precisely one uniform.";

          // Split the block member's type into a base type and array info.
          final ImmutablePair<Type, ArrayInfo> baseTypeAndArrayInfo =
              Typer.getBaseTypeArrayInfo(interfaceBlock.getMemberTypes().get(0));

          // We will use the block member's type plus the uniform qualifier as the base type for
          // a new variable.
          final List<TypeQualifier> qualifiers = new ArrayList<>();
          qualifiers.add(TypeQualifier.UNIFORM);
          // If the block member had qualifiers, use these in addition to uniform.
          if (baseTypeAndArrayInfo.left instanceof QualifiedType) {
            qualifiers.addAll(((QualifiedType) baseTypeAndArrayInfo.left).getQualifiers());
          }
          // Add a singleton variables declaration.
          newTopLevelDeclarations.add(new VariablesDeclaration(
              new QualifiedType(baseTypeAndArrayInfo.left.getWithoutQualifiers(),
                  qualifiers),
              new VariableDeclInfo(interfaceBlock.getMemberNames().get(0),
                  baseTypeAndArrayInfo.right, null)));
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
      return getPipelineInfo().hasBindingOrIsPushConstant(uniformName);
    }
    return false;
  }

  @Override
  public Optional<String> getPushConstant() {
    for (String uniformName : getPipelineInfo().getUniformNames()) {
      if (getPipelineInfo().isPushConstant(uniformName)) {
        return Optional.of(uniformName);
      }
    }
    return Optional.empty();
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
