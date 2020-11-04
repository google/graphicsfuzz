#version 320 es

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
