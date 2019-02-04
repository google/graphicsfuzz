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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TransformationProbabilitiesTest {

  @Test
  public void testToString() {
    assertEquals("probSubstituteFreeVariable: 80\n"
        + "probDonateDeadCodeAtStmt: 5\n"
        + "probDonateLiveCodeAtStmt: 5\n"
        + "probInjectJumpAtStmt: 5\n"
        + "probWrapStmtInConditional: 5\n"
        + "probMutatePoint: 3\n"
        + "probVectorizeStmts: 3\n"
        + "probSplitLoops: 5\n"
        + "probStructify: 3\n"
        + "probOutline: 5\n"
        + "probAddLiveFragColorWrites: 5\n"
        + "probAddDeadFragColorWrites: 5\n"
        + "probSwitchify: 5\n"
        + "probInjectDeadBarrierAtStmt: 5\n", TransformationProbabilities.SMALL_PROBABILITIES.toString());
  }

}
