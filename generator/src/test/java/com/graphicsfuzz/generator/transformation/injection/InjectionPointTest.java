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

package com.graphicsfuzz.generator.transformation.injection;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.typing.Scope;
import java.util.Optional;
import org.junit.Test;

public class InjectionPointTest {

  @Test
  public void testScopeAtInjectionPoint() {

    // Simple test written in response to a stack overflow error

    final Scope scope = new Scope(null);
    assertNotNull(
      new InjectionPoint(null, false, scope) {

        @Override
        public void inject(Stmt stmt) {
          throw new RuntimeException();
        }

        @Override
        public Stmt getNextStmt() {
          throw new RuntimeException();
        }

        @Override
        public boolean hasNextStmt() {
          throw new RuntimeException();
        }

        @Override
        public void replaceNext(Stmt stmt) {
          throw new RuntimeException();
        }

      }.scopeAtInjectionPoint());

  }

  @Test
  public void testThatScopeIsCloned() {
    Scope s = new Scope(null);
    s.add("v", BasicType.INT, Optional.empty());
    IInjectionPoint injectionPoint = new InjectionPoint(null, false, s) {
      @Override
      public void inject(Stmt stmt) {
        throw new RuntimeException();
      }

      @Override
      public Stmt getNextStmt() {
        throw new RuntimeException();
      }

      @Override
      public boolean hasNextStmt() {
        throw new RuntimeException();
      }

      @Override
      public void replaceNext(Stmt stmt) {
        throw new RuntimeException();
      }
    };

    s.add("w", BasicType.INT, Optional.empty());

    assertNull(injectionPoint.scopeAtInjectionPoint().lookupType("w"));

  }

}