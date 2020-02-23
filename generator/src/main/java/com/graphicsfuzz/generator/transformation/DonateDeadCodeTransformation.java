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

package com.graphicsfuzz.generator.transformation;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ApplySubstitution;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.fuzzer.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.transformation.donation.DonationContext;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.RemoveDiscardStatements;
import com.graphicsfuzz.generator.util.RemoveImmediateBreakStatements;
import com.graphicsfuzz.generator.util.RemoveImmediateCaseLabels;
import com.graphicsfuzz.generator.util.RemoveImmediateContinueStatements;
import com.graphicsfuzz.generator.util.RemoveReturnStatements;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DonateDeadCodeTransformation extends DonateCodeTransformation {

  public static final String NAME = "donate_dead_code";

  public DonateDeadCodeTransformation(Function<IRandom, Boolean> probabilityOfDonation,
                                      File donorsDirectory,
                                      GenerationParams generationParams) {
    super(probabilityOfDonation, donorsDirectory, generationParams);
  }

  @Override
  Stmt prepareStatementToDonate(IInjectionPoint injectionPoint, DonationContext donationContext,
                                TransformationProbabilities probabilities, IRandom generator,
                                ShadingLanguageVersion shadingLanguageVersion) {
    Map<String, String> substitution = new HashMap<>();
    List<Stmt> donatedStmts = new ArrayList<>();
    for (String name : asSortedList(donationContext.getFreeVariables().keySet())) {
      boolean dealtWithFreeVariable = false;
      Type type = donationContext.getFreeVariables().get(name);
      if (probabilities.substituteFreeVariable(generator)) {
        List<String> options =
            injectionPoint.scopeAtInjectionPoint().namesOfAllVariablesInScope()
                .stream()
                .filter(x -> injectionPoint.scopeAtInjectionPoint().lookupType(x).equals(type))
                .collect(Collectors.toList());

        // Now filter out all candidate variables for which a variable with the same name is
        // defined in the com.graphicsfuzz.generator.transformation.donation
        // context
        options = options.stream().filter(x ->
            !donationContext.getDeclaredVariableNames().contains(x)).collect(Collectors.toList());

        if (options.size() > 0) {
          substitution.put(name, options.get(generator.nextInt(options.size())));
          dealtWithFreeVariable = true;
        }
      }
      if (!dealtWithFreeVariable) {

        // We will make up a new variable, but we will give it a special name
        // in case it clashes with the name of a variable already in this scope

        String newName = "donor_replacement" + name;
        substitution.put(name, newName);

        final Initializer initializer = getInitializer(
            injectionPoint,
            donationContext,
            type,
            type.hasQualifier(TypeQualifier.CONST),
            generator,
            shadingLanguageVersion);

        final ArrayInfo arrayInfo = type.getWithoutQualifiers() instanceof ArrayType
            ? ((ArrayType) type.getWithoutQualifiers()).getArrayInfo().clone()
            : null;

        donatedStmts.add(new DeclarationStmt(
            new VariablesDeclaration(dropQualifiersThatCannotBeUsedForLocalVariable(type),
                new VariableDeclInfo(newName, arrayInfo,
                    initializer))));
      }
    }

    Stmt substitutedDonorFragment = donationContext.getDonorFragment();
    new ApplySubstitution(substitution, substitutedDonorFragment);

    donatedStmts.add(substitutedDonorFragment);

    IfStmt donatedStmt = new IfStmt(
        new OpaqueExpressionGenerator(generator, generationParams, shadingLanguageVersion)
            .makeDeadCondition(
                new Fuzzer(new FuzzingContext(injectionPoint.scopeAtInjectionPoint()),
                    shadingLanguageVersion, generator,
                    generationParams)), new BlockStmt(donatedStmts, true), null);

    // We cannot have 'case' and 'default' labels occurring in the donated statement, unless they
    // are nested in their own 'switch' statement.  In principle we could allow these if the
    // injection point happens to be in a 'switch' already, but this would require some fiddly
    // checks that do not seem worth doing.
    new RemoveImmediateCaseLabels(donatedStmt);

    // If the injection point is in a loop then it's fine for the injected code to have continue
    // statements (this is a dead code injection, so semantics will not be changed).  But otherwise
    // they would make the shader invalid.
    if (!injectionPoint.inLoop()) {
      new RemoveImmediateContinueStatements(donatedStmt);
    }

    // If the injection point is in a loop or switch then it's fine for the injected code to have
    // break statements (this is a dead code injection, so semantics will not be changed).  But
    // otherwise they would make the shader invalid.
    if (!(injectionPoint.inLoop() || injectionPoint.inSwitch())) {
      new RemoveImmediateBreakStatements(donatedStmt);
    }

    if (generationParams.getShaderKind() != ShaderKind.FRAGMENT) {
      new RemoveDiscardStatements(donatedStmt);
    }

    if (incompatibleReturnTypes(injectionPoint.getEnclosingFunction(),
        donationContext.getEnclosingFunction())) {
      new RemoveReturnStatements(donatedStmt);
    }

    return donatedStmt;

  }

  @Override
  String getPrefix() {
    return Constants.DEAD_PREFIX;
  }

  private List<String> asSortedList(Set<String> keys) {
    final List<String> sortedKeys = new ArrayList<>();
    sortedKeys.addAll(keys);
    sortedKeys.sort(String::compareTo);
    return sortedKeys;
  }

  private boolean incompatibleReturnTypes(FunctionDefinition f1, FunctionDefinition f2) {
    return !f1.getPrototype().getReturnType().equals(f2.getPrototype().getReturnType());
  }

  @Override
  void adaptTranslationUnitForSpecificDonation(TranslationUnit tu, IRandom generator) {
    // No adaptation necessary.
  }

  @Override
  public String getName() {
    return NAME;
  }

}
