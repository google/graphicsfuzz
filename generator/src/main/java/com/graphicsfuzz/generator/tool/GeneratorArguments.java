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

import com.graphicsfuzz.common.util.ShaderKind;
import java.io.File;
import java.util.Optional;

public class GeneratorArguments {

  private final boolean small;
  private final boolean allowLongLoops;
  private final boolean singlePass;
  private final boolean aggressivelyComplicateControlFlow;
  private final File donorsFolder;
  private final boolean isVulkan;
  private final int maxUniforms;
  private final EnabledTransformations enabledTransformations;
  private final boolean addInjectionSwitch;
  private final Optional<ShaderKind> onlyFuzzShaderStage;
  private final float pushConstantProbability;

  public GeneratorArguments(
        boolean small,
        boolean allowLongLoops,
        boolean singlePass,
        boolean aggressivelyComplicateControlFlow,
        File donorsFolder,
        boolean isVulkan,
        int maxUniforms,
        EnabledTransformations enabledTransformations,
        boolean addInjectionSwitch,
        Optional<ShaderKind> onlyFuzzShaderStage,
        float pushConstantProbability) {
    this.small = small;
    this.allowLongLoops = allowLongLoops;
    this.singlePass = singlePass;
    this.aggressivelyComplicateControlFlow = aggressivelyComplicateControlFlow;
    this.donorsFolder = donorsFolder;
    this.isVulkan = isVulkan;
    this.maxUniforms = maxUniforms;
    this.enabledTransformations = enabledTransformations;
    this.addInjectionSwitch = addInjectionSwitch;
    this.onlyFuzzShaderStage = onlyFuzzShaderStage;
    this.pushConstantProbability = pushConstantProbability;
  }

  public Optional<ShaderKind> getOnlyFuzzShaderStage() {
    return onlyFuzzShaderStage;
  }

  public boolean getSmall() {
    return small;
  }

  public boolean getAllowLongLoops() {
    return allowLongLoops;
  }

  public boolean getSinglePass() {
    return singlePass;
  }

  public boolean getAggressivelyComplicateControlFlow() {
    return aggressivelyComplicateControlFlow;
  }

  public File getDonorsFolder() {
    return donorsFolder;
  }

  public boolean getIsVulkan() {
    return isVulkan;
  }

  public int getMaxUniforms() {
    return maxUniforms;
  }

  public boolean limitUniforms() {
    return getMaxUniforms() > 0;
  }

  public EnabledTransformations getEnabledTransformations() {
    return enabledTransformations;
  }

  public boolean getAddInjectionSwitch() {
    return addInjectionSwitch;
  }

  public float getPushConstantProbability() {
    return pushConstantProbability;
  }

  @Override
  public final String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("small: " + small + "\n");
    sb.append("allowLongLoops: " + allowLongLoops + "\n");
    sb.append("singlePass: " + singlePass + "\n");
    sb.append("aggressivelyComplicateControlFlow: " + aggressivelyComplicateControlFlow + "\n");
    sb.append("donorsFolder: " + donorsFolder.getName() + "\n");
    sb.append("isVulkan: " + isVulkan + "\n");
    sb.append("maxUniforms: " + (limitUniforms() ? "-" : maxUniforms) + "\n");
    sb.append("enabledTransformations: " + enabledTransformations + "\n");
    sb.append("addInjectionSwitch: " + addInjectionSwitch + "\n");
    sb.append("pushConstantProbability: " + pushConstantProbability + "\n");
    return sb.toString();
  }

}
