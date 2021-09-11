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
  uint matrix_number = 0u;
  for(int cols = 2; cols <= 4; cols++)
  {
    for(int rows = 2; rows <= 4; rows++)
    {
      for(int c = 0; c < cols; c++)
      {
        for(int r = 0; r < rows; r++)
        {
          switch(matrix_number)
          {
            case 0u:
              m22[c][r] = one;
              break;
            case 1u:
              m23[c][r] = one;
              break;
            case 2u:
              m24[c][r] = one;
              break;
            case 3u:
              m32[c][r] = one;
              break;
            case 4u:
              m33[c][r] = one;
              break;
            case 5u:
              m34[c][r] = one;
              break;
            case 6u:
              m42[c][r] = one;
              break;
            case 7u:
              m43[c][r] = one;
              break;
            case 8u:
              m44[c][r] = one;
              break;
          }
        }
      }
      matrix_number = matrix_number + 1u;
    }
  }

  float sums[9];

  int sum_index = 0;
  for(int cols = 2; cols <= 4; cols++)
  {
    for(int rows = 2; rows <= 4; rows++)
    {
      sums[sum_index] = 0.0;
      for(int c = 0; c < cols; c++)
      {
        for(int r = 0; r < rows; r++)
        {
          switch(sum_index)
          {
            case 0:
              sums[sum_index] += m22[c][r];
              break;
            case 1:
              sums[sum_index] += m23[c][r];
              break;
            case 2:
              sums[sum_index] += m24[c][r];
              break;
            case 3:
              sums[sum_index] += m32[c][r];
              break;
            case 4:
              sums[sum_index] += m33[c][r];
              break;
            case 5:
              sums[sum_index] += m34[c][r];
              break;
            case 6:
              sums[sum_index] += m42[c][r];
              break;
            case 7:
              sums[sum_index] += m43[c][r];
              break;
            case 8:
              sums[sum_index] += m44[c][r];
              break;
          }
        }
      }
      sums[sum_index] /= 16.0;
      sum_index ++;
    }
  }

  int region_x = int(gl_FragCoord.x / (resolution.x / 3.0));
  int region_y = int(gl_FragCoord.y / (resolution.x / 3.0));
  int overall_region = region_y * 3 + region_x;

  if (overall_region > 0 && overall_region < 9) {
    _GLF_color = vec4(vec3(sums[overall_region]), 1.0);
  } else {
    _GLF_color = vec4(vec3(0.0), 1.0);
  }
}
