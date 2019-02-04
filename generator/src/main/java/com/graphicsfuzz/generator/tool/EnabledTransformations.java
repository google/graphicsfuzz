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

package com.graphicsfuzz.generator.tool;

import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.controlflow.AddDeadBarriers;
import com.graphicsfuzz.generator.transformation.controlflow.AddDeadOutputVariableWrites;
import com.graphicsfuzz.generator.transformation.controlflow.AddJumpStmts;
import com.graphicsfuzz.generator.transformation.controlflow.AddLiveOutputVariableWrites;
import com.graphicsfuzz.generator.transformation.controlflow.AddSwitchStmts;
import com.graphicsfuzz.generator.transformation.controlflow.AddWrappingConditionalStmts;
import com.graphicsfuzz.generator.transformation.controlflow.SplitForLoops;
import com.graphicsfuzz.generator.transformation.donation.DonateDeadCode;
import com.graphicsfuzz.generator.transformation.donation.DonateLiveCode;
import com.graphicsfuzz.generator.transformation.mutator.MutateExpressions;
import com.graphicsfuzz.generator.transformation.outliner.OutlineStatements;
import com.graphicsfuzz.generator.transformation.structifier.Structification;
import com.graphicsfuzz.generator.transformation.vectorizer.VectorizeStatements;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EnabledTransformations {

  private final Set<Class<? extends ITransformation>> enabledTransformations;

  public EnabledTransformations() {
    enabledTransformations = new HashSet<>(allTransformations());
  }

  public static List<Class<? extends ITransformation>> allTransformations() {
    return Arrays.asList(
        DonateDeadCode.class,
        AddJumpStmts.class,
        DonateLiveCode.class,
        MutateExpressions.class,
        OutlineStatements.class,
        SplitForLoops.class,
        Structification.class,
        AddSwitchStmts.class,
        VectorizeStatements.class,
        AddWrappingConditionalStmts.class,
        AddLiveOutputVariableWrites.class,
        AddDeadOutputVariableWrites.class,
        AddDeadBarriers.class
    );
  }

  public static List<Class<? extends ITransformation>> namesToList(String commaSeparatedNames) {
    return Arrays.asList(commaSeparatedNames.split(","))
        .stream()
        .map(EnabledTransformations::nameToClass)
        .collect(Collectors.toList());
  }

  private static Class<? extends ITransformation> nameToClass(String name) {
    switch (name) {
      case DonateDeadCode.NAME:
        return DonateDeadCode.class;
      case AddJumpStmts.NAME:
        return AddJumpStmts.class;
      case DonateLiveCode.NAME:
        return DonateLiveCode.class;
      case MutateExpressions.NAME:
        return MutateExpressions.class;
      case OutlineStatements.NAME:
        return OutlineStatements.class;
      case SplitForLoops.NAME:
        return SplitForLoops.class;
      case Structification.NAME:
        return Structification.class;
      case AddSwitchStmts.NAME:
        return AddSwitchStmts.class;
      case VectorizeStatements.NAME:
        return VectorizeStatements.class;
      case AddWrappingConditionalStmts.NAME:
        return AddWrappingConditionalStmts.class;
      case AddLiveOutputVariableWrites.NAME:
        return AddLiveOutputVariableWrites.class;
      case AddDeadOutputVariableWrites.NAME:
        return AddDeadOutputVariableWrites.class;
      case AddDeadBarriers.NAME:
        return AddDeadBarriers.class;
      default:
        throw new RuntimeException("Unknown transformation '" + name + "'");
    }

  }

  public void disable(Class<? extends ITransformation> transformationClass) {
    enabledTransformations.remove(transformationClass);
  }

  public boolean isEnabledDead() {
    return isEnabled(DonateDeadCode.class);
  }

  public boolean isEnabledJump() {
    return isEnabled(AddJumpStmts.class);
  }

  public boolean isEnabledLive() {
    return isEnabled(DonateLiveCode.class);
  }

  public boolean isEnabledMutate() {
    return isEnabled(MutateExpressions.class);
  }

  public boolean isEnabledOutline() {
    return isEnabled(OutlineStatements.class);
  }

  public boolean isEnabledSplit() {
    return isEnabled(SplitForLoops.class);
  }

  public boolean isEnabledStruct() {
    return isEnabled(Structification.class);
  }

  public boolean isEnabledSwitch() {
    return isEnabled(AddSwitchStmts.class);
  }

  public boolean isEnabledVec() {
    return isEnabled(VectorizeStatements.class);
  }

  public boolean isEnabledWrap() {
    return isEnabled(AddWrappingConditionalStmts.class);
  }

  public boolean isEnabledDeadFragColorWrites() {
    return isEnabled(AddDeadOutputVariableWrites.class);
  }

  public boolean isEnabledLiveFragColorWrites() {
    return isEnabled(AddLiveOutputVariableWrites.class);
  }

  public boolean isEnabledDeadBarriers() {
    return isEnabled(AddDeadBarriers.class);
  }

  private boolean isEnabled(Class<? extends ITransformation> transformationClass) {
    return enabledTransformations.contains(transformationClass);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("enabledDead: " + isEnabledDead() + "\n");
    sb.append("enabledJump: " + isEnabledJump() + "\n");
    sb.append("enabledLive: " + isEnabledLive() + "\n");
    sb.append("enabledMutate: " + isEnabledMutate() + "\n");
    sb.append("enabledOutline: " + isEnabledOutline() + "\n");
    sb.append("enabledSplit: " + isEnabledSplit() + "\n");
    sb.append("enabledStruct: " + isEnabledStruct() + "\n");
    sb.append("enabledSwitch: " + isEnabledSwitch() + "\n");
    sb.append("enabledVec: " + isEnabledVec() + "\n");
    sb.append("enabledWrap: " + isEnabledWrap() + "\n");
    sb.append("enabledDeadFragColorWrites: " + isEnabledDeadFragColorWrites() + "\n");
    sb.append("enabledLiveFragColorWrites: " + isEnabledLiveFragColorWrites() + "\n");
    sb.append("enabledDeadBarriers: " + isEnabledDeadBarriers() + "\n");
    return sb.toString();
  }

}
