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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.util.IRandom;
import java.lang.reflect.Field;

public class TransformationProbabilities {

  private int probSubstituteFreeVariable; // Probability of substituting vs declaring a free var
  private int probDonateDeadCodeAtStmt; // Probability of donating dead code at a statement
  private int probDonateLiveCodeAtStmt; // Probability of donating live code at a statement
  private int probInjectJumpAtStmt; // Probability of injecting a jump at a statement
  private int probWrapStmtInConditional; // Probability of wrapping a statement in a conditional
  private int probMutatePoint; // Probability of mutating a point
  private int probVectorizeStmts; // Probability of vectorizing a set of statements
  private int probSplitLoops; // Probability of splitting a loop
  private int probStructify; // Probability of structifying a variable
  private int probOutline; // Probability of outlining a statement
  private int probAddLiveFragColorWrites; // Probability of adding a live write to gl_FragColor
  private int probAddDeadFragColorWrites; // Probability of outlining a dead write to gl_FragColor
  private int probSwitchify; // Probability of applying switchification

  private TransformationProbabilities(int probSubstituteFreeVariable, int probDonateDeadCodeAtStmt,
      int probDonateLiveCodeAtStmt, int probInjectJumpAtStmt, int probWrapStmtInConditional,
      int probMutatePoint, int probVectorizeStmts, int probSplitLoops, int probStructify,
      int probOutline,
      int probAddLiveFragColorWrites,
      int probAddDeadFragColorWrites,
      int probSwitchify) {
    this.probSubstituteFreeVariable = probSubstituteFreeVariable;
    this.probDonateDeadCodeAtStmt = probDonateDeadCodeAtStmt;
    this.probDonateLiveCodeAtStmt = probDonateLiveCodeAtStmt;
    this.probInjectJumpAtStmt = probInjectJumpAtStmt;
    this.probWrapStmtInConditional = probWrapStmtInConditional;
    this.probMutatePoint = probMutatePoint;
    this.probVectorizeStmts = probVectorizeStmts;
    this.probSplitLoops = probSplitLoops;
    this.probStructify = probStructify;
    this.probOutline = probOutline;
    this.probAddLiveFragColorWrites = probAddLiveFragColorWrites;
    this.probAddDeadFragColorWrites = probAddDeadFragColorWrites;
    this.probSwitchify = probSwitchify;
  }

  public static final TransformationProbabilities DEFAULT_PROBABILITIES =
      new TransformationProbabilities(80, 20, 20, 20, 20, 20, 20, 20, 20,
          20, 20, 20, 20);

  public static final TransformationProbabilities SMALL_PROBABILITIES =
      new TransformationProbabilities(80, 5, 5, 5, 5, 3, 3, 5, 3,
          5, 5, 5, 5);

  public static final TransformationProbabilities AGGRESSIVE_CONTROL_FLOW =
      new TransformationProbabilities(DEFAULT_PROBABILITIES.probSubstituteFreeVariable,
          DEFAULT_PROBABILITIES.probDonateDeadCodeAtStmt,
          DEFAULT_PROBABILITIES.probDonateLiveCodeAtStmt,
          70,
          70,
          DEFAULT_PROBABILITIES.probMutatePoint,
          DEFAULT_PROBABILITIES.probVectorizeStmts,
          DEFAULT_PROBABILITIES.probSplitLoops,
          DEFAULT_PROBABILITIES.probStructify,
          70,
          70,
          70,
          70);

  // Useful for testing; add similar for others when needed
  public static final TransformationProbabilities ZERO =
      new TransformationProbabilities(
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0);

  public static final TransformationProbabilities onlySplitLoops() {
    TransformationProbabilities result = ZERO;
    result.probSplitLoops = 100;
    return result;
  }

  public static final TransformationProbabilities onlyStructify() {
    TransformationProbabilities result = ZERO;
    result.probStructify = 100;
    return result;
  }

  public static final TransformationProbabilities onlyVectorize() {
    TransformationProbabilities result = ZERO;
    result.probVectorizeStmts = 100;
    return result;
  }

  public static final TransformationProbabilities onlyVectorizeAndMutate() {
    TransformationProbabilities result = ZERO;
    result.probVectorizeStmts = 100;
    result.probMutatePoint = 100;
    return result;
  }

  public static final TransformationProbabilities onlyOutlineStatements() {
    TransformationProbabilities result = ZERO;
    result.probOutline = 100;
    return result;
  }

  public static final TransformationProbabilities onlyMutateExpressions() {
    TransformationProbabilities result = ZERO;
    result.probMutatePoint = 100;
    return result;
  }

  public static final TransformationProbabilities onlyWrap() {
    TransformationProbabilities result = ZERO;
    result.probWrapStmtInConditional = 100;
    return result;
  }

  public static final TransformationProbabilities onlyAddJumps() {
    TransformationProbabilities result = ZERO;
    result.probInjectJumpAtStmt = 100;
    return result;
  }

  public static final TransformationProbabilities onlyAddDeadFragColorWrites() {
    TransformationProbabilities result = ZERO;
    result.probAddDeadFragColorWrites = 100;
    return result;
  }

  public static final TransformationProbabilities onlyAddLiveFragColorWrites() {
    TransformationProbabilities result = ZERO;
    result.probAddLiveFragColorWrites = 100;
    return result;
  }

  public static final TransformationProbabilities onlyLiveCodeAlwaysSubstitute() {
    TransformationProbabilities result = ZERO;
    result.probDonateLiveCodeAtStmt = 100;
    result.probSubstituteFreeVariable = 100;
    return result;
  }

  public static TransformationProbabilities closeToDefaultProbabilities(IRandom generator) {
    return closeTo(generator, DEFAULT_PROBABILITIES);
  }

  public static final TransformationProbabilities likelyDonateDeadCode() {
    TransformationProbabilities result = ZERO;
    result.probSubstituteFreeVariable = 50;
    result.probDonateDeadCodeAtStmt = 60;
    return result;
  }

  public static final TransformationProbabilities likelyDonateLiveCode() {
    TransformationProbabilities result = ZERO;
    result.probSubstituteFreeVariable = 50;
    result.probDonateLiveCodeAtStmt = 60;
    return result;
  }

  public static TransformationProbabilities randomProbabilitiesSinglePass(IRandom generator) {
    return new TransformationProbabilities(
        randomProbability(generator, 0, 100),
        randomProbability(generator, 5, 30),
        randomProbability(generator, 5, 30),
        randomProbability(generator, 5, 50),
        randomProbability(generator, 5, 50),
        randomProbability(generator, 2, 20),
        randomProbability(generator, 5, 70),
        randomProbability(generator, 5, 50),
        randomProbability(generator, 5, 20),
        randomProbability(generator, 5, 40),
        randomProbability(generator, 5, 30),
        randomProbability(generator, 5, 30),
        randomProbability(generator, 5, 30)
    );
  }

  public static TransformationProbabilities randomProbabilitiesMultiPass(IRandom generator) {
    return new TransformationProbabilities(
          randomProbability(generator, 0, 100),
          randomProbability(generator, 2, 10),
          randomProbability(generator, 2, 10),
          randomProbability(generator, 2, 15),
          randomProbability(generator, 2, 15),
          randomProbability(generator, 2, 8),
          randomProbability(generator, 2, 30),
          randomProbability(generator, 2, 15),
          randomProbability(generator, 2, 8),
          randomProbability(generator, 2, 15),
          randomProbability(generator, 2, 10),
          randomProbability(generator, 2, 10),
          randomProbability(generator, 2, 10)
    );
  }

  /**
   * Generate random number in range approx 0--99, but avoiding extremes.
   * @param generator Random number generator
   * @return The random number
   */
  private static int randomProbability(IRandom generator, int min, int max) {
    return generator.nextInt(max - min) + min;
  }

  public static TransformationProbabilities closeTo(IRandom generator,
      TransformationProbabilities probabilities) {
    return new TransformationProbabilities(
        closeTo(probabilities.probSubstituteFreeVariable, generator),
        closeTo(probabilities.probDonateDeadCodeAtStmt, generator),
        closeTo(probabilities.probDonateLiveCodeAtStmt, generator),
        closeTo(probabilities.probInjectJumpAtStmt, generator),
        closeTo(probabilities.probWrapStmtInConditional, generator),
        closeTo(probabilities.probMutatePoint, generator),
        closeTo(probabilities.probVectorizeStmts, generator),
        closeTo(probabilities.probSplitLoops, generator),
        closeTo(probabilities.probStructify, generator),
        closeTo(probabilities.probOutline, generator),
        closeTo(probabilities.probAddLiveFragColorWrites, generator),
        closeTo(probabilities.probAddDeadFragColorWrites, generator),
        closeTo(probabilities.probSwitchify, generator));
  }

  private static int closeTo(int probability, IRandom generator) {
    final int epsilon = 10;
    assert probability >= epsilon;
    assert probability <= 100 - epsilon;
    int result = probability + generator.nextInt(2 * epsilon) - epsilon;
    assert result >= 0;
    assert result <= 100;
    return result;
  }

  public boolean substituteFreeVariable(IRandom generator) {
    return choose(probSubstituteFreeVariable, generator);
  }

  public boolean donateDeadCodeAtStmt(IRandom generator) {
    return choose(probDonateDeadCodeAtStmt, generator);
  }

  public boolean donateLiveCodeAtStmt(IRandom generator) {
    return choose(probDonateLiveCodeAtStmt, generator);
  }

  public boolean injectJumpAtStmt(IRandom generator) {
    return choose(probInjectJumpAtStmt, generator);
  }

  public boolean wrapStmtInConditional(IRandom generator) {
    return choose(probWrapStmtInConditional, generator);
  }

  public boolean mutatePoint(IRandom generator) {
    return choose(probMutatePoint, generator);
  }

  public boolean vectorizeStmts(IRandom generator) {
    return choose(probVectorizeStmts, generator);
  }

  public boolean splitLoops(IRandom generator) {
    return choose(probSplitLoops, generator);
  }

  public boolean structify(IRandom generator) {
    return choose(probStructify, generator);
  }

  public boolean outlineStatements(IRandom generator) {
    return choose(probOutline, generator);
  }

  public boolean addLiveFragColorWrites(IRandom generator) {
    return choose(probAddLiveFragColorWrites,
        generator);
  }

  public boolean addDeadFragColorWrites(IRandom generator) {
    return choose(probAddDeadFragColorWrites, generator);
  }

  public boolean switchify(IRandom generator) {
    return choose(probSwitchify, generator);
  }

  private boolean choose(int threshold, IRandom generator) {
    return generator.nextInt(100) < threshold;
  }

  @Override
  public String toString() {
    String result = "";
    for (Field field : this.getClass().getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Object value = null;
      try {
        value = field.get(this);
        result += field.getName() + ": " + value.toString() + "\n";
      } catch (IllegalAccessException exception) {
        result += exception.getMessage();
      }
    }
    return result;
  }

}
