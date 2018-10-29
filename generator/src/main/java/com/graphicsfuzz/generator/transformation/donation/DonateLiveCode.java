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

package com.graphicsfuzz.generator.transformation.donation;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.TruncateLoops;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.RemoveDiscardStatements;
import com.graphicsfuzz.generator.transformation.injection.RemoveImmediateBreakAndContinueStatements;
import com.graphicsfuzz.generator.transformation.injection.RemoveReturnStatements;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DonateLiveCode extends DonateCode {

  public static final String NAME = "donate_live_code";
  private final boolean avoidLongLoops;

  public DonateLiveCode(Function<IRandom, Boolean> probabilityOfDonation, File donorsDirectory,
        GenerationParams generationParams, boolean avoidLongLoops) {
    super(probabilityOfDonation, donorsDirectory, generationParams);
    this.avoidLongLoops = avoidLongLoops;
  }

  @Override
  Stmt prepareStatementToDonate(IInjectionPoint injectionPoint,
        DonationContext donationContext, TransformationProbabilities probabilities,
        IRandom generator, ShadingLanguageVersion shadingLanguageVersion) {
    List<Stmt> donatedStmts = new ArrayList<>();
    for (Map.Entry<String, Type> vars : donationContext.getFreeVariables().entrySet()) {
      Type type = vars.getValue();
      if (typeRefersToUniform(type)) {
        // A uniform variable has to be globally-scoped.  As a result this variable will be
        // donated as a global, so we should not re-declare it here.
        continue;
      }
      type = dropQualifiersThatCannotBeUsedForLocalVariable(type);

      ScalarInitializer initializer;
      // We fuzz a const expression because we need to ensure we don't generate side-effects to
      // non-injected code
      if (isLoopLimiter(vars.getKey(), type.getWithoutQualifiers())) {
        initializer = new ScalarInitializer(new IntConstantExpr("0"));
      } else {
        initializer = getScalarInitializer(injectionPoint, donationContext, type, true,
              generator, shadingLanguageVersion);
      }
      donatedStmts.add(new DeclarationStmt(
            new VariablesDeclaration(type,
                  new VariableDeclInfo(vars.getKey(), null, initializer))));
    }
    donatedStmts.add(donationContext.getDonorFragment());
    BlockStmt donatedStmt = new BlockStmt(donatedStmts, true);
    new RemoveImmediateBreakAndContinueStatements(donatedStmt);
    new RemoveReturnStatements(donatedStmt);
    new RemoveDiscardStatements(donatedStmt);
    return donatedStmt;
  }

  @Override
  String getPrefix() {
    return Constants.LIVE_PREFIX;
  }

  @Override
  void adaptTranslationUnitForSpecificDonation(TranslationUnit tu, IRandom generator) {
    if (avoidLongLoops) {
      new TruncateLoops(3 + generator.nextInt(5), addPrefix(""), tu, false);
    }
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
