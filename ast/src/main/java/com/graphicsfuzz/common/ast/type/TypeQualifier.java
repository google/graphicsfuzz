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

package com.graphicsfuzz.common.ast.type;

public class TypeQualifier {

  private final String text;

  /**
   * Deliberately package-visible: we do not want arbitrary type qualifiers
   * to be created, except via designated subclasses.
   */
  TypeQualifier(String text) {
    // Do not permit external creation
    this.text = text;
  }

  public static final TypeQualifier INVARIANT = new TypeQualifier("invariant");
  public static final TypeQualifier PRECISE = new TypeQualifier("precise");
  public static final TypeQualifier CENTROID = new TypeQualifier("centroid");
  public static final TypeQualifier SAMPLE = new TypeQualifier("sample");
  public static final TypeQualifier CONST = new TypeQualifier("const");
  public static final TypeQualifier ATTRIBUTE = new TypeQualifier("attribute");
  public static final TypeQualifier VARYING = new TypeQualifier("varying");
  public static final TypeQualifier IN_PARAM = new TypeQualifier("in");
  public static final TypeQualifier OUT_PARAM = new TypeQualifier("out");
  public static final TypeQualifier INOUT_PARAM = new TypeQualifier("inout");
  public static final TypeQualifier UNIFORM = new TypeQualifier("uniform");
  public static final TypeQualifier COHERENT = new TypeQualifier("coherent");
  public static final TypeQualifier VOLATILE = new TypeQualifier("volatile");
  public static final TypeQualifier RESTRICT = new TypeQualifier("restrict");
  public static final TypeQualifier READONLY = new TypeQualifier("readonly");
  public static final TypeQualifier WRITEONLY = new TypeQualifier("writeonly");
  public static final TypeQualifier FLAT = new TypeQualifier("flat");
  public static final TypeQualifier SMOOTH = new TypeQualifier("smooth");
  public static final TypeQualifier NOPERSPECTIVE = new TypeQualifier("noperspective");
  public static final TypeQualifier HIGHP = new TypeQualifier("highp");
  public static final TypeQualifier MEDIUMP = new TypeQualifier("mediump");
  public static final TypeQualifier LOWP = new TypeQualifier("lowp");
  public static final TypeQualifier SHADER_INPUT = new TypeQualifier("in");
  public static final TypeQualifier SHADER_OUTPUT = new TypeQualifier("out");
  public static final TypeQualifier BUFFER = new TypeQualifier("buffer");
  public static final TypeQualifier SHARED = new TypeQualifier("shared");

  @Override
  public String toString() {
    return text;
  }

}
