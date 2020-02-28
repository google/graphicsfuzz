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

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import com.graphicsfuzz.common.typing.Scope;

public class SamplerType extends BuiltinType {

  private SamplerType() {
    // SamplerType is in essence an enumeration.
    // No need to override .equals() and .hashCode()
  }

  public static final SamplerType SAMPLER1D = new SamplerType();
  public static final SamplerType SAMPLER2D = new SamplerType();
  public static final SamplerType SAMPLER2DRECT = new SamplerType();
  public static final SamplerType SAMPLER3D = new SamplerType();
  public static final SamplerType SAMPLERCUBE = new SamplerType();
  public static final SamplerType SAMPLEREXTERNALOES = new SamplerType();
  public static final SamplerType SAMPLER1DSHADOW = new SamplerType();
  public static final SamplerType SAMPLER2DSHADOW = new SamplerType();
  public static final SamplerType SAMPLER2DRECTSHADOW = new SamplerType();
  public static final SamplerType SAMPLERCUBESHADOW = new SamplerType();
  public static final SamplerType SAMPLER1DARRAY = new SamplerType();
  public static final SamplerType SAMPLER2DARRAY = new SamplerType();
  public static final SamplerType SAMPLER1DARRAYSHADOW = new SamplerType();
  public static final SamplerType SAMPLER2DARRAYSHADOW = new SamplerType();
  public static final SamplerType SAMPLERBUFFER = new SamplerType();
  public static final SamplerType SAMPLERCUBEARRAY = new SamplerType();
  public static final SamplerType SAMPLERCUBEARRAYSHADOW = new SamplerType();
  public static final SamplerType ISAMPLER1D = new SamplerType();
  public static final SamplerType ISAMPLER2D = new SamplerType();
  public static final SamplerType ISAMPLER2DRECT = new SamplerType();
  public static final SamplerType ISAMPLER3D = new SamplerType();
  public static final SamplerType ISAMPLERCUBE = new SamplerType();
  public static final SamplerType ISAMPLER1DARRAY = new SamplerType();
  public static final SamplerType ISAMPLER2DARRAY = new SamplerType();
  public static final SamplerType ISAMPLERBUFFER = new SamplerType();
  public static final SamplerType ISAMPLERCUBEARRAY = new SamplerType();
  public static final SamplerType USAMPLER1D = new SamplerType();
  public static final SamplerType USAMPLER2D = new SamplerType();
  public static final SamplerType USAMPLER2DRECT = new SamplerType();
  public static final SamplerType USAMPLER3D = new SamplerType();
  public static final SamplerType USAMPLERCUBE = new SamplerType();
  public static final SamplerType USAMPLER1DARRAY = new SamplerType();
  public static final SamplerType USAMPLER2DARRAY = new SamplerType();
  public static final SamplerType USAMPLERBUFFER = new SamplerType();
  public static final SamplerType USAMPLERCUBEARRAY = new SamplerType();
  public static final SamplerType SAMPLER2DMS = new SamplerType();
  public static final SamplerType ISAMPLER2DMS = new SamplerType();
  public static final SamplerType USAMPLER2DMS = new SamplerType();
  public static final SamplerType SAMPLER2DMSARRAY = new SamplerType();
  public static final SamplerType ISAMPLER2DMSARRAY = new SamplerType();
  public static final SamplerType USAMPLER2DMSARRAY = new SamplerType();

  @Override
  public String toString() {
    if (this == SAMPLER1D) {
      return "sampler1D";
    }
    if (this == SAMPLER2D) {
      return "sampler2D";
    }
    if (this == SAMPLER2DRECT) {
      return "sampler2DRect";
    }
    if (this == SAMPLER3D) {
      return "sampler3D";
    }
    if (this == SAMPLERCUBE) {
      return "samplerCube";
    }
    if (this == SAMPLEREXTERNALOES) {
      return "samplerExternalOES";
    }
    if (this == SAMPLER1DSHADOW) {
      return "sampler1DShadow";
    }
    if (this == SAMPLER2DSHADOW) {
      return "sampler2DShadow";
    }
    if (this == SAMPLER2DRECTSHADOW) {
      return "sampler2DRectShadow";
    }
    if (this == SAMPLERCUBESHADOW) {
      return "samplerCubeShadow";
    }
    if (this == SAMPLER1DARRAY) {
      return "sampler1DArray";
    }
    if (this == SAMPLER2DARRAY) {
      return "sampler2DArray";
    }
    if (this == SAMPLER1DARRAYSHADOW) {
      return "sampler1DArrayShadow";
    }
    if (this == SAMPLER2DARRAYSHADOW) {
      return "sampler2DArrayShadow";
    }
    if (this == SAMPLERBUFFER) {
      return "samplerBuffer";
    }
    if (this == SAMPLERCUBEARRAY) {
      return "samplerCubeArray";
    }
    if (this == SAMPLERCUBEARRAYSHADOW) {
      return "samplerCubeArrayShadow";
    }
    if (this == ISAMPLER1D) {
      return "isampler1D";
    }
    if (this == ISAMPLER2D) {
      return "isampler2D";
    }
    if (this == ISAMPLER2DRECT) {
      return "isampler2DRect";
    }
    if (this == ISAMPLER3D) {
      return "isampler3D";
    }
    if (this == ISAMPLERCUBE) {
      return "isamplerCube";
    }
    if (this == ISAMPLER1DARRAY) {
      return "isampler1DArray";
    }
    if (this == ISAMPLER2DARRAY) {
      return "isampler2DArray";
    }
    if (this == ISAMPLERBUFFER) {
      return "isamplerBuffer";
    }
    if (this == ISAMPLERCUBEARRAY) {
      return "isamplerCubeArray";
    }
    if (this == USAMPLER1D) {
      return "usampler1D";
    }
    if (this == USAMPLER2D) {
      return "usampler2D";
    }
    if (this == USAMPLER2DRECT) {
      return "usampler2DRect";
    }
    if (this == USAMPLER3D) {
      return "usampler3D";
    }
    if (this == USAMPLERCUBE) {
      return "usamplerCube";
    }
    if (this == USAMPLER1DARRAY) {
      return "usampler1DArray";
    }
    if (this == USAMPLER2DARRAY) {
      return "usampler2DArray";
    }
    if (this == USAMPLERBUFFER) {
      return "usamplerBuffer";
    }
    if (this == USAMPLERCUBEARRAY) {
      return "usamplerCubeArray";
    }
    if (this == SAMPLER2DMS) {
      return "sampler2DMS";
    }
    if (this == ISAMPLER2DMS) {
      return "isampler2DMS";
    }
    if (this == USAMPLER2DMS) {
      return "usampler2DMS";
    }
    if (this == SAMPLER2DMSARRAY) {
      return "sampler2DMSArray";
    }
    if (this == ISAMPLER2DMSARRAY) {
      return "isampler2DMSArray";
    }
    if (this == USAMPLER2DMSARRAY) {
      return "usampler2DMSArray";
    }
    throw new RuntimeException("Invalid type");
  }

  @Override
  public Expr getCanonicalConstant(Scope scope) {
    assert !hasCanonicalConstant(scope);
    throw new RuntimeException("No canonical constant for " + this);
  }

  @Override
  public boolean hasCanonicalConstant(Scope unused) {
    return false;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitSamplerType(this);
  }

}
