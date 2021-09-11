#version 450

/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

// 32-bit floating-point numbers.
precision highp float;

// Fragment color output.
layout(location = 0) out vec4 color;

void main() {
  // Chessboard row and column corresponding to the current fragment.
  float row = float(int(gl_FragCoord.x) / 16 + 1);
  float column = float(int(gl_FragCoord.y) / 16 + 1);

  // Scalar, vectors and matrices used in the linear algebra instructions.
  float scalar = 1.0;
  vec3 vector_1 = vec3(scalar++ * row, scalar++ * column, scalar++ * row * column);
  vec3 vector_2 = vec3(scalar++ * row, scalar++ * column, scalar++ * row * column);
  mat3x3 matrix_1 = mat3x3(scalar++ * row, scalar++ * column, scalar++ * row * column,
                           scalar++ * row, scalar++ * column, scalar++ * row * column,
                           scalar++ * row, scalar++ * column, scalar++ * row * column);
  mat3x3 matrix_2 = mat3x3(scalar++ * row, scalar++ * column, scalar++ * row * column,
                           scalar++ * row, scalar++ * column, scalar++ * row * column,
                           scalar++ * row, scalar++ * column, scalar++ * row * column);

  // OpVectorTimesScalar
  color.rgb = scalar++ * vector_1;

  // OpMatrixTimesScalar
  color.rgb *= scalar++ * matrix_1;

  // OpVectorTimesMatrix
  color.rgb += vector_1 * matrix_1;

  // OpMatrixTimesVector
  color.rgb += matrix_1 * vector_1;

  // OpMatrixTimesMatrix
  color.rgb *= matrix_1 * matrix_2;

  // OpOuterProduct
  color.rgb *= outerProduct(vector_1, vector_2);

  // OpDot
  color.rgb *= dot(vector_1, vector_2);

  // Final color
  color = vec4(sin(color.rgb), 1.0);
}
