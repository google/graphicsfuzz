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
  private final String referencePrefix;
  private final int seed;
  private final boolean small;
  private final boolean avoidLongLoops;
  private final boolean multiPass;
  private final boolean aggressivelyComplicateControlFlow;
  private final boolean replaceFloatLiterals;
  private final File donorsFolder;
  private final File outputFolder;
  private final String outputPrefix;
  private final EnabledTransformations enabledTransformations;

  public GeneratorArguments(
        ShadingLanguageVersion shadingLanguageVersion,
        String referencePrefix,
        int seed,
        boolean small,
        boolean avoidLongLoops,
        boolean multiPass,
        boolean aggressivelyComplicateControlFlow,
        boolean replaceFloatLiterals,
        File donorsFolder,
        File outputFolder,
        String outputPrefix,
        EnabledTransformations enabledTransformations) {
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.referencePrefix = referencePrefix;
    this.seed = seed;
    this.small = small;
    this.avoidLongLoops = avoidLongLoops;
    this.multiPass = multiPass;
    this.aggressivelyComplicateControlFlow = aggressivelyComplicateControlFlow;
    this.replaceFloatLiterals = replaceFloatLiterals;
    this.donorsFolder = donorsFolder;
    this.outputFolder = outputFolder;
    this.outputPrefix = outputPrefix;
    this.enabledTransformations = enabledTransformations;
  }

  public ShadingLanguageVersion getShadingLanguageVersion() {
    return shadingLanguageVersion;
  }

  public boolean hasReferenceFragmentShader() {
    return getReferenceFragmentShader().exists();
  }

  public File getReferenceFragmentShader() {
    return getFileFromPrefixWithExtension(".frag");
  }

  public boolean hasReferenceVertexShader() {
    return getReferenceVertexShader().exists();
  }

  public File getReferenceVertexShader() {
    return getFileFromPrefixWithExtension(".vert");
  }

  public File getUniforms() {
    return getFileFromPrefixWithExtension(".json");
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

  public File getOutputFolder() {
    return outputFolder;
  }

  public String getOutputPrefix() {
    return outputPrefix;
  }

  public File getLicense() {
    return getFileFromPrefixWithExtension(".license");
  }

  public EnabledTransformations getEnabledTransformations() {
    return enabledTransformations;
  }

  @Override
  public final String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("shadingLanguageVersion: " + shadingLanguageVersion + "\n");
    sb.append("referenceFragmentShader: "
        + (hasReferenceFragmentShader() ? getReferenceFragmentShader().getName() : "-") + "\n");
    sb.append("referenceVertexShader: "
        + (hasReferenceVertexShader() ? getReferenceVertexShader().getName() : "-") + "\n");
    sb.append("uniforms: " + getUniforms().getName() + "\n");
    sb.append("seed: " + seed + "\n");
    sb.append("small: " + small + "\n");
    sb.append("avoidLongLoops: " + avoidLongLoops + "\n");
    sb.append("multiPass: " + multiPass + "\n");
    sb.append("aggressivelyComplicateControlFlow: " + aggressivelyComplicateControlFlow + "\n");
    sb.append("replaceFloatLiterals: " + replaceFloatLiterals + "\n");
    sb.append("donorsFolder: " + donorsFolder.getName() + "\n");
    sb.append("outputFolder: " + outputFolder.getName() + "\n");
    sb.append("outputPrefix: " + outputPrefix + "\n");
    sb.append("license: " + getLicense().getName() + "\n");
    sb.append("enabledTransformations: " + enabledTransformations + "\n");
    return sb.toString();
  }

  private File getFileFromPrefixWithExtension(String extension) {
    return new File(referencePrefix + extension);
  }

}
