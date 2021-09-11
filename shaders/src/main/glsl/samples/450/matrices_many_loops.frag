#version 450

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

precision highp float;

layout(location = 0) out vec4 _GLF_color;

uniform float one;

uniform vec2 resolution;

#define POPULATE_MAT(MAT, COLS, ROWS) \
  for (int c = 0; c < COLS; c++) {    \
    for (int r = 0; r < ROWS; r++) {  \
      MAT[c][r] = one;                \
    }                                 \
  }

// The following macro populates sums[INDEX] to be the sum of all the
// entries in MAT (which must be a COLS x ROWS matrix) divided by
// 16x.0.

#define POPULATE_SUMS(MAT, COLS, ROWS, INDEX) \
  sums[INDEX] = 0.0;                          \
  for (int c = 0; c < COLS; c++) {            \
    for (int r = 0; r < ROWS; r++) {          \
      sums[INDEX] += MAT[c][r];               \
    }                                         \
  }                                           \
  sums[INDEX] /= 16.0;

mat2x2 m22;

mat2x3 m23;

mat2x4 m24;

mat3x2 m32;

mat3x3 m33;

mat3x4 m34;

mat4x2 m42;

mat4x3 m43;

mat4x4 m44;

void main()
{

  POPULATE_MAT(m22, 2, 2);
  POPULATE_MAT(m23, 2, 3);
  POPULATE_MAT(m24, 2, 4);
  POPULATE_MAT(m32, 3, 2);
  POPULATE_MAT(m33, 3, 3);
  POPULATE_MAT(m34, 3, 4);
  POPULATE_MAT(m42, 4, 2);
  POPULATE_MAT(m43, 4, 3);
  POPULATE_MAT(m44, 4, 4);

  float sums[9];

  POPULATE_SUMS(m22, 2, 2, 0);
  POPULATE_SUMS(m23, 2, 3, 1);
  POPULATE_SUMS(m24, 2, 4, 2);
  POPULATE_SUMS(m32, 3, 2, 3);
  POPULATE_SUMS(m33, 3, 3, 4);
  POPULATE_SUMS(m34, 3, 4, 5);
  POPULATE_SUMS(m42, 4, 2, 6);
  POPULATE_SUMS(m43, 4, 3, 7);
  POPULATE_SUMS(m44, 4, 4, 8);

  int region_x = int(gl_FragCoord.x / (resolution.x / 3.0));
  int region_y = int(gl_FragCoord.y / (resolution.x / 3.0));
  int overall_region = region_y * 3 + region_x;

  if (overall_region > 0 && overall_region < 9) {
    _GLF_color = vec4(vec3(sums[overall_region]), 1.0);
  } else {
    _GLF_color = vec4(vec3(0.0), 1.0);
  }
}
