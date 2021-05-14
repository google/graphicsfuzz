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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayConstructorExpr;
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.SamplerType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class PruneUniforms {

  private PruneUniforms() {
    // Utility class
  }

  /**
   * Prunes uniforms from the shader job, by turning them into global variables initialized to the
   * value the uniform would take, until there are no more than a given number of uniforms in the
   * shader job.
   * @param shaderJob A shader job whose uniforms are to be pruned.
   * @param limit The maximum number of uniforms that should remain.
   */
  public static void pruneIfNeeded(ShaderJob shaderJob,
                                   int limit) {

    // Work out how many uniforms need to be pruned to meet the limit.
    final int numToPrune = shaderJob.getPipelineInfo().getNumUniforms() - limit;
    if (numToPrune <= 0) {
      // No pruning is required.
      return;
    }

    // Determine those uniforms we would prefer to prune (donated uniforms) and those uniforms we
    // will only prune if necessary (uniforms from the original shader).
    final List<String> pruneFirst = new ArrayList<>();
    final List<String> pruneIfNecessary = new ArrayList<>();


    // Decide on a single sampler - if any are present in the shader job - with which uses of
    // donated samplers will be replaced if they are pruned.  Prefer a non-donated sampler as this
    // canonical sampler if possible.
    Optional<String> canonicalSampler = Optional.empty();
    for (String uniformName : shaderJob.getPipelineInfo().getUniformNames()) {
      if (!shaderJob.getPipelineInfo().isSampler(uniformName)) {
        continue;
      }
      if (!isDonated(uniformName)) {
        // Prefer a reference sampler even if we already found a donated sampler.
        assert !canonicalSampler.isPresent() || isDonated(canonicalSampler.get()) : "We should "
            + "take the first reference sampler that we find.";
        canonicalSampler = Optional.of(uniformName);
        break;
      }
      if (!canonicalSampler.isPresent()) {
        canonicalSampler = Optional.of(uniformName);
      }
    }

    for (String uniformName : shaderJob.getPipelineInfo().getUniformNames()) {
      // We prioritise pruning uniforms arising from live and dead code injection, including
      // sampler uniforms, except we don't allow pruning of the canonical sampler.
      //
      // We allow pruning of non-sampler uniforms that are not donated, i.e. uniforms from the
      // original shader, but we de-prioritise such pruning.
      if (shaderJob.getPipelineInfo().isSampler(uniformName)) {
        // Skip this sampler uniform if it is not donated, or if it's the canonical sampler.
        if (!isDonated(uniformName)
            || canonicalSampler.isPresent() && canonicalSampler.get().equals(uniformName)) {
          continue;
        }
      }
      if (isDonated(uniformName)) {
        pruneFirst.add(uniformName);
      } else if (!shaderJob.getPipelineInfo().isSampler(uniformName)) {
        pruneIfNecessary.add(uniformName);
      }
    }

    // Order the uniforms so that the ones we prefer to prune come first.
    final List<String> orderedForPruning = new ArrayList<>();
    orderedForPruning.addAll(pruneFirst);
    orderedForPruning.addAll(pruneIfNecessary);

    // Prune as many uniforms as needed.
    for (int i = 0; i < numToPrune; i++) {
      final String uniformName = orderedForPruning.get(i);
      for (TranslationUnit tu : shaderJob.getShaders()) {
        if (shaderJob.getPipelineInfo().isSampler(uniformName)) {
          assert canonicalSampler.isPresent();
          replaceWithCanonicalSampler(tu, uniformName, canonicalSampler.get());
        } else {
          inlineUniform(tu, shaderJob.getPipelineInfo(), uniformName);
        }
      }
      shaderJob.getPipelineInfo().removeUniform(uniformName);
    }

  }

  private static void inlineUniform(TranslationUnit tu, PipelineInfo pipelineInfo,
                                    String uniformName) {
    boolean found = false; // For coherence-checking

    final List<Declaration> newTopLevelDeclarations = new ArrayList<>();
    for (Declaration decl : tu.getTopLevelDeclarations()) {
      if (decl instanceof VariablesDeclaration
          && ((VariablesDeclaration) decl).getBaseType().hasQualifier(TypeQualifier.UNIFORM)) {
        final VariablesDeclaration variablesDeclaration = (VariablesDeclaration) decl;
        for (int i = 0; i < variablesDeclaration.getNumDecls(); i++) {
          final VariableDeclInfo declInfo = variablesDeclaration.getDeclInfo(i);
          if (!declInfo.getName().equals(uniformName)) {
            continue;
          }
          assert !found;
          found = true;
          assert !declInfo.hasInitializer();
          final BasicType withoutQualifiers = (BasicType) variablesDeclaration.getBaseType()
              .getWithoutQualifiers();
          declInfo.setInitializer(makeInitializer(
              withoutQualifiers,
              declInfo.getArrayInfo(), pipelineInfo.getArgs(uniformName)));
          final VariablesDeclaration newVariablesDeclaration = new VariablesDeclaration(
              withoutQualifiers, declInfo);
          variablesDeclaration.removeDeclInfo(i);

          newTopLevelDeclarations.add(newVariablesDeclaration);
          break;
        }
        if (variablesDeclaration.getNumDecls() > 0) {
          // Keep the original variables declaration as it still has other uniforms.
          newTopLevelDeclarations.add(variablesDeclaration);
        }
      } else {
        newTopLevelDeclarations.add(decl);
      }
    }
    tu.setTopLevelDeclarations(newTopLevelDeclarations);
  }

  private static Initializer makeInitializer(BasicType baseType,
                                             ArrayInfo arrayInfo,
                                             List<String> args) {
    if (arrayInfo != null) {
      if (arrayInfo.getDimensionality() != 1) {
        throw new RuntimeException("Multi-dimensional array uniforms are not supported.");
      }
      assert arrayInfo.getConstantSize(0) * baseType.getNumElements() == args.size();
      List<Expr> argExprs = new ArrayList<>();
      for (int index = 0; index < arrayInfo.getConstantSize(0); index++) {
        argExprs.add(getBasicTypeLiteralExpr(baseType,
            args.subList(index * baseType.getNumElements(),
                (index + 1) * baseType.getNumElements())));
      }
      return new Initializer(new ArrayConstructorExpr(
          new ArrayType(baseType.getWithoutQualifiers(), arrayInfo.clone()),
          argExprs));
    }
    return new Initializer(getBasicTypeLiteralExpr(baseType, args));
  }

  public static Expr getBasicTypeLiteralExpr(BasicType baseType, List<String> args) {
    List<Expr> argExprs;
    if (baseType.getElementType() == BasicType.BOOL) {
      // If the type is bool then each element of "args" is required to have an integer value, thus
      // "parseInt" should not fail.
      argExprs = args.stream().map(item -> new BoolConstantExpr(Integer.parseInt(item) == 1))
          .collect(Collectors.toList());
    } else if (baseType.getElementType() == BasicType.FLOAT) {
      argExprs = args.stream().map(item -> new FloatConstantExpr(item.toString()))
          .collect(Collectors.toList());
    } else if (baseType.getElementType() == BasicType.UINT) {
      argExprs = args.stream().map(item -> new UIntConstantExpr(item.toString() + "u"))
          .collect(Collectors.toList());
    } else {
      argExprs = args.stream().map(item -> new IntConstantExpr(item.toString()))
          .collect(Collectors.toList());
    }
    if (argExprs.size() == 1) {
      return argExprs.get(0);
    }
    return new TypeConstructorExpr(baseType.toString(),
        argExprs);
  }

  private static void replaceWithCanonicalSampler(TranslationUnit tu, String samplerName,
                                                  String canonicalSamplerName) {
    assert !samplerName.equals(canonicalSamplerName) : "We must not prune the canonical sampler.";
    assert isDonated(samplerName) : "We do not prune non-donated sampler uniforms.";

    // This visitor replaces any uses of |samplerName| with |canonicalSamplerName|.  True is
    // returned if at least one replacement was made - i.e., if an additional use of the canonical
    // sampler was added.
    final boolean useOfCanonicalSamplerWasAdded = new ScopeTrackingVisitor() {
      private boolean madeReplacement = false;

      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        // Replace all references to the original sampler with references to the canonical sampler.
        if (variableIdentifierExpr.getName().equals(samplerName)) {
          variableIdentifierExpr.setName(canonicalSamplerName);
          madeReplacement = true;
        }
      }

      private boolean replaceSamplerUses(TranslationUnit tu) {
        visit(tu);
        return madeReplacement;
      }
    }.replaceSamplerUses(tu);

    // We need to get rid of the pruned sampler, if it indeed exists in this translation unit.
    // If an extra use of the canonical sampler was introduced then we need to move the declaration
    // of the canonical sampler to the top of the translation unit, in case the new use occurs
    // above its current declaration.

    // First, we find the variables declarations in which these samplers are declared (if they are
    // declared at all).
    Optional<VariablesDeclaration> prunedSamplerDeclaration = Optional.empty();
    Optional<VariablesDeclaration> canonicalSamplerDeclaration = Optional.empty();
    for (Declaration declaration : tu.getTopLevelDeclarations()) {
      if (!(declaration instanceof VariablesDeclaration)) {
        continue;
      }
      final VariablesDeclaration variablesDeclaration = (VariablesDeclaration) declaration;
      if (!(variablesDeclaration.getBaseType().getWithoutQualifiers() instanceof SamplerType)) {
        continue;
      }
      for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
        if (vdi.getName().equals(samplerName)) {
          prunedSamplerDeclaration = Optional.of(variablesDeclaration);
        } else if (vdi.getName().equals(canonicalSamplerName)) {
          canonicalSamplerDeclaration = Optional.of(variablesDeclaration);
        }
      }
    }

    // We remove the sampler declaration that is being pruned.
    prunedSamplerDeclaration.ifPresent(variablesDeclaration -> removeSamplerDeclaration(tu,
        samplerName, variablesDeclaration));

    if (useOfCanonicalSamplerWasAdded) {
      // A use of the canonical sampler was added, so we need to move it to the top of the
      // translation unit (or add it, if it wasn't present in the first place).

      // We remove the canonical sampler (if it was present in the first place), and then add a
      // declaration of the canonical sampler to the start of the module.
      // TODO(https://github.com/google/graphicsfuzz/issues/1075): This is currently special-cased
      //  for the sampler2D type.
      canonicalSamplerDeclaration.ifPresent(variablesDeclaration -> removeSamplerDeclaration(tu,
          canonicalSamplerName, variablesDeclaration));
      tu.addDeclaration(new VariablesDeclaration(
          new QualifiedType(SamplerType.SAMPLER2D,
              Collections.singletonList(TypeQualifier.UNIFORM)),
                    new VariableDeclInfo(canonicalSamplerName, null, null)
      ));
    }

  }

  private static void removeSamplerDeclaration(TranslationUnit tu, String samplerName,
                                               VariablesDeclaration samplerVariablesDeclaration) {
    if (samplerVariablesDeclaration.getNumDecls() == 1) {
      tu.removeTopLevelDeclaration(samplerVariablesDeclaration);
    } else {
      for (int i = 0; i < samplerVariablesDeclaration.getNumDecls(); i++) {
        if (samplerName.equals(samplerVariablesDeclaration.getDeclInfo(i).getName())) {
          samplerVariablesDeclaration.removeDeclInfo(i);
          break;
        }
      }
    }
  }

  private static boolean isDonated(String uniformName) {
    return uniformName.startsWith(Constants.LIVE_PREFIX)
        || uniformName.startsWith(Constants.DEAD_PREFIX);
  }

}
