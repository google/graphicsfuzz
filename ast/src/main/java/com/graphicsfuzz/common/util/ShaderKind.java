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

public enum ShaderKind {

  FRAGMENT, VERTEX, COMPUTE;

  public static ShaderKind fromExtension(String extension) {
    switch (extension) {
      case "frag":
        return FRAGMENT;
      case "vert":
        return VERTEX;
      case "comp":
        return COMPUTE;
      default:
        throw new IllegalArgumentException("Unknown shader extension '" + extension + "'");
    }
  }

  public String getFileExtension() {
    switch (this) {
      case FRAGMENT:
        return "frag";
      case VERTEX:
        return "vert";
      case COMPUTE:
        return "comp";
      default:
        throw new RuntimeException("Unreachable.");
    }
  }
}
