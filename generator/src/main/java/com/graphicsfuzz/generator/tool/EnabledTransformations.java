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

import com.graphicsfuzz.generator.transformation.AddDeadBarrierTransformation;
import com.graphicsfuzz.generator.transformation.AddDeadOutputWriteTransformation;
import com.graphicsfuzz.generator.transformation.AddJumpTransformation;
import com.graphicsfuzz.generator.transformation.AddLiveOutputWriteTransformation;
import com.graphicsfuzz.generator.transformation.AddSwitchTransformation;
import com.graphicsfuzz.generator.transformation.AddWrappingConditionalTransformation;
import com.graphicsfuzz.generator.transformation.DonateDeadCodeTransformation;
import com.graphicsfuzz.generator.transformation.DonateLiveCodeTransformation;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.IdentityTransformation;
import com.graphicsfuzz.generator.transformation.OutlineStatementsTransformation;
import com.graphicsfuzz.generator.transformation.SplitForLoopTransformation;
import com.graphicsfuzz.generator.transformation.StructificationTransformation;
import com.graphicsfuzz.generator.transformation.VectorizeTransformation;
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
        DonateDeadCodeTransformation.class,
        AddJumpTransformation.class,
        DonateLiveCodeTransformation.class,
        IdentityTransformation.class,
        OutlineStatementsTransformation.class,
        SplitForLoopTransformation.class,
        StructificationTransformation.class,
        AddSwitchTransformation.class,
        VectorizeTransformation.class,
        AddWrappingConditionalTransformation.class,
        AddLiveOutputWriteTransformation.class,
        AddDeadOutputWriteTransformation.class,
        AddDeadBarrierTransformation.class
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
      case DonateDeadCodeTransformation.NAME:
        return DonateDeadCodeTransformation.class;
      case AddJumpTransformation.NAME:
        return AddJumpTransformation.class;
      case DonateLiveCodeTransformation.NAME:
        return DonateLiveCodeTransformation.class;
      case IdentityTransformation.NAME:
        return IdentityTransformation.class;
      case OutlineStatementsTransformation.NAME:
        return OutlineStatementsTransformation.class;
      case SplitForLoopTransformation.NAME:
        return SplitForLoopTransformation.class;
      case StructificationTransformation.NAME:
        return StructificationTransformation.class;
      case AddSwitchTransformation.NAME:
        return AddSwitchTransformation.class;
      case VectorizeTransformation.NAME:
        return VectorizeTransformation.class;
      case AddWrappingConditionalTransformation.NAME:
        return AddWrappingConditionalTransformation.class;
      case AddLiveOutputWriteTransformation.NAME:
        return AddLiveOutputWriteTransformation.class;
      case AddDeadOutputWriteTransformation.NAME:
        return AddDeadOutputWriteTransformation.class;
      case AddDeadBarrierTransformation.NAME:
        return AddDeadBarrierTransformation.class;
      default:
        throw new RuntimeException("Unknown transformation '" + name + "'");
    }

  }

  public void disable(Class<? extends ITransformation> transformationClass) {
    enabledTransformations.remove(transformationClass);
  }

  public boolean isEnabledDead() {
    return isEnabled(DonateDeadCodeTransformation.class);
  }

  public boolean isEnabledJump() {
    return isEnabled(AddJumpTransformation.class);
  }

  public boolean isEnabledLive() {
    return isEnabled(DonateLiveCodeTransformation.class);
  }

  public boolean isEnabledMutate() {
    return isEnabled(IdentityTransformation.class);
  }

  public boolean isEnabledOutline() {
    return isEnabled(OutlineStatementsTransformation.class);
  }

  public boolean isEnabledSplit() {
    return isEnabled(SplitForLoopTransformation.class);
  }

  public boolean isEnabledStruct() {
    return isEnabled(StructificationTransformation.class);
  }

  public boolean isEnabledSwitch() {
    return isEnabled(AddSwitchTransformation.class);
  }

  public boolean isEnabledVec() {
    return isEnabled(VectorizeTransformation.class);
  }

  public boolean isEnabledWrap() {
    return isEnabled(AddWrappingConditionalTransformation.class);
  }

  public boolean isEnabledDeadFragColorWrites() {
    return isEnabled(AddDeadOutputWriteTransformation.class);
  }

  public boolean isEnabledLiveFragColorWrites() {
    return isEnabled(AddLiveOutputWriteTransformation.class);
  }

  public boolean isEnabledDeadBarriers() {
    return isEnabled(AddDeadBarrierTransformation.class);
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
