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
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.TruncateLoops;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class DonateLiveCodeTransformation extends DonateCodeTransformation {

  public static final String NAME = "donate_live_code";

  private final boolean allowLongLoops;

  public DonateLiveCodeTransformation(Function<IRandom, Boolean> probabilityOfDonation,
                                      File donorsDirectory,
                                      GenerationParams generationParams,
                                      boolean allowLongLoops) {
    super(probabilityOfDonation, donorsDirectory, generationParams);
    this.allowLongLoops = allowLongLoops;
  }

  @Override
  Stmt prepareStatementToDonate(IInjectionPoint injectionPoint,
                                DonationContext donationContext,
                                TransformationProbabilities probabilities,
                                IRandom generator, ShadingLanguageVersion shadingLanguageVersion) {
    List<Stmt> donatedStmts = new ArrayList<>();
    for (Map.Entry<String, Type> vars : donationContext.getFreeVariables().entrySet()) {
      final Type type = vars.getValue();

      final Type typeWithRestrictedQualifiers =
          dropQualifiersThatCannotBeUsedForLocalVariable(type);

      final Initializer initializer;
      // We fuzz a const expression because we need to ensure we don't generate side-effects to
      // non-injected code
      if (isLoopLimiter(vars.getKey(), typeWithRestrictedQualifiers.getWithoutQualifiers())) {
        initializer = new Initializer(new IntConstantExpr("0"));
      } else {
        initializer = getInitializer(injectionPoint, donationContext, typeWithRestrictedQualifiers,
            true, generator, shadingLanguageVersion);
      }

      final ImmutablePair<Type, ArrayInfo> baseTypeAndArrayInfo =
          Typer.getBaseTypeArrayInfo(typeWithRestrictedQualifiers);
      donatedStmts.add(new DeclarationStmt(
          new VariablesDeclaration(baseTypeAndArrayInfo.left,
              new VariableDeclInfo(vars.getKey(), baseTypeAndArrayInfo.right, initializer))));
    }
    donatedStmts.add(donationContext.getDonorFragment());
    BlockStmt donatedStmt = new BlockStmt(donatedStmts, true);
    new RemoveImmediateBreakStatements(donatedStmt);
    new RemoveImmediateContinueStatements(donatedStmt);
    new RemoveImmediateCaseLabels(donatedStmt);
    new RemoveReturnStatements(donatedStmt);
    return donatedStmt;
  }

  @Override
  String getPrefix() {
    return Constants.LIVE_PREFIX;
  }

  @Override
  void adaptTranslationUnitForSpecificDonation(TranslationUnit tu, IRandom generator) {
    if (!allowLongLoops) {
      new TruncateLoops(3 + generator.nextInt(5), addPrefix(""), tu);
    }
    // We get rid of all discard statements from the donor, since if an injected discard is ever
    // reached it will change the semantics of the shader.
    new RemoveDiscardStatements(tu);
  }

  private boolean isLoopLimiter(String name, Type type) {
    return name.contains("looplimiter")
        && type == BasicType.INT;
  }

  @Override
  public String getName() {
    return NAME;
  }
}
