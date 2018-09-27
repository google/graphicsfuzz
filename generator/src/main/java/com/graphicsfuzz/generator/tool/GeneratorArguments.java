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

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import java.io.File;

public class GeneratorArguments {

  private final ShadingLanguageVersion shadingLanguageVersion;
  private final int seed;
  private final boolean small;
  private final boolean avoidLongLoops;
  private final boolean multiPass;
  private final boolean aggressivelyComplicateControlFlow;
  private final boolean replaceFloatLiterals;
  private final File donorsFolder;
  private final boolean generateUniformBindings;
  private final int maxUniforms;
  private final EnabledTransformations enabledTransformations;

  public GeneratorArguments(
        ShadingLanguageVersion shadingLanguageVersion,
        int seed,
        boolean small,
        boolean avoidLongLoops,
        boolean multiPass,
        boolean aggressivelyComplicateControlFlow,
        boolean replaceFloatLiterals,
        File donorsFolder,
        boolean generateUniformBindings,
        int maxUniforms,
        EnabledTransformations enabledTransformations) {
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.seed = seed;
    this.small = small;
    this.avoidLongLoops = avoidLongLoops;
    this.multiPass = multiPass;
    this.aggressivelyComplicateControlFlow = aggressivelyComplicateControlFlow;
    this.replaceFloatLiterals = replaceFloatLiterals;
    this.donorsFolder = donorsFolder;
    this.generateUniformBindings = generateUniformBindings;
    this.maxUniforms = maxUniforms;
    this.enabledTransformations = enabledTransformations;
  }

  public ShadingLanguageVersion getShadingLanguageVersion() {
    return shadingLanguageVersion;
  }

  public int getSeed() {
    return seed;
  }

  public boolean getSmall() {
    return small;
  }

  public boolean getAvoidLongLoops() {
    return avoidLongLoops;
  }

  public boolean getMultiPass() {
    return multiPass;
  }

  public boolean getAggressivelyComplicateControlFlow() {
    return aggressivelyComplicateControlFlow;
  }

  public boolean getReplaceFloatLiterals() {
    return replaceFloatLiterals;
  }

  public File getDonorsFolder() {
    return donorsFolder;
  }

  public boolean getGenerateUniformBindings() {
    return generateUniformBindings;
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

  @Override
  public final String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("shadingLanguageVersion: " + shadingLanguageVersion + "\n");
    sb.append("small: " + small + "\n");
    sb.append("avoidLongLoops: " + avoidLongLoops + "\n");
    sb.append("multiPass: " + multiPass + "\n");
    sb.append("aggressivelyComplicateControlFlow: " + aggressivelyComplicateControlFlow + "\n");
    sb.append("replaceFloatLiterals: " + replaceFloatLiterals + "\n");
    sb.append("donorsFolder: " + donorsFolder.getName() + "\n");
    sb.append("generateUniformBindings: " + generateUniformBindings + "\n");
    sb.append("maxUniforms: " + (limitUniforms() ? "-" : maxUniforms) + "\n");
    sb.append("enabledTransformations: " + enabledTransformations + "\n");
    return sb.toString();
  }

}
