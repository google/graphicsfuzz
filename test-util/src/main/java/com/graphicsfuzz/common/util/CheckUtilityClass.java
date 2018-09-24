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

package com.graphicsfuzz.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class CheckUtilityClass {

  /**
   * Checks that a utility class is well-formed; e.g., that it is
   * final, with a single private constructor and no non-static
   * methods
   * @param utilityClass The class to check
   */
  public static void assertUtilityClassWellDefined(final Class<?> utilityClass)
      throws NoSuchMethodException, InvocationTargetException,
      InstantiationException, IllegalAccessException {
    assertTrue("Utility class must be final",
        Modifier.isFinal(utilityClass.getModifiers()));
    assertEquals("Utility class can only have one constructor", 1,
        utilityClass.getDeclaredConstructors().length);
    final Constructor<?> constructor = utilityClass.getDeclaredConstructor();
    if (constructor.isAccessible()
          || !Modifier.isPrivate(constructor.getModifiers())) {
      fail("Utility class constructor must be private");
    }
    constructor.setAccessible(true);
    constructor.newInstance();
    constructor.setAccessible(false);
    for (final Method method : utilityClass.getMethods()) {
      if (!Modifier.isStatic(method.getModifiers())
          && method.getDeclaringClass().equals(utilityClass)) {
        fail("Utility class can only have static methods; found non-static method:" + method);
      }
    }
  }

}
