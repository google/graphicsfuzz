/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.ast.stmt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

public class DefaultCaseLabelTest {

  @Test
  public void testDefaultCaseLabel() {
    assertEquals("default:\n", new DefaultCaseLabel().getText());
    final DefaultCaseLabel defaultCaseLabel = new DefaultCaseLabel();
    assertNotSame(defaultCaseLabel, defaultCaseLabel.clone());
  }

}
