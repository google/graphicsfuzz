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

precision highp float;
precision highp int;

layout(location = 0) out vec4 _GLF_color;
uniform vec2 resolution;

/*
This shader produces an old Amiga demoscene effect
in a very inefficient way.

A precalculated table of 256 integer positions is used
to see where the tops of the pillars are. For each
pixel in the output, we scan upwards to find the first
pillar top we find, and color the current pixel based on
that. If no pillar tops are found while tracing, we render
black.

Each of the pillars is considerered to be +/- 15 pixels
from the value in the table, with a gradient fill.

Apart from the gradient fill (which is a simple linear
interpolation), everything is done with integer math,
making the result deterministic.

(The original effect was produced by having a single pixel
high framebuffer and tricking the video chip to re-render
it over and over again; by drawing just the single slice
of a pillar, it would stretch downwards until overwritten).
*/

const int dp[256] = int[256](
  115, 133, 150, 164, 176, 184, 190, 192, 191, 187, 181, 172, 163, 153, 143, 134,
  126, 120, 116, 114, 114, 117, 121, 127, 134, 141, 148, 154, 159, 162, 163, 161,
  157, 151, 143, 134, 124, 113, 103, 94, 87, 82, 79, 80, 84, 91, 101, 114, 130, 146,
  164, 182, 199, 215, 229, 240, 249, 254, 256, 254, 250, 243, 233, 223, 212, 200,
  190, 180, 172, 166, 163, 161, 162, 164, 169, 174, 179, 185, 190, 193, 195, 195,
  192, 188, 180, 171, 161, 149, 137, 125, 114, 105, 97, 93, 91, 93, 98, 106, 117,
  130, 145, 161, 177, 193, 208, 221, 231, 239, 243, 244, 242, 236, 228, 218, 207,
  194, 181, 169, 158, 148, 141, 135, 132, 131, 132, 135, 138, 143, 147, 151, 154,
  155, 155, 152, 146, 139, 129, 118, 106, 93, 80, 68, 58, 49, 43, 40, 41, 44, 51,
  61, 73, 87, 103, 119, 134, 149, 162, 173, 181, 186, 188, 186, 181, 174, 164,
  153, 141, 128, 116, 104, 94, 86, 81, 77, 76, 77, 80, 84, 89, 94, 98, 102, 104,
  104, 102, 98, 92, 83, 73, 62, 50, 38, 26, 16, 8, 2, 0, 0, 4, 11, 21, 33, 48, 64, 81,
  98, 114, 129, 141, 151, 158, 161, 161, 158, 152, 144, 134, 123, 112, 100, 90,
  81, 73, 68, 65, 65, 67, 70, 75, 81, 87, 92, 97, 101, 103, 102, 100, 95, 88, 79,
  69, 58, 47, 36, 26, 18, 13, 11, 11, 15, 22, 32, 45, 60, 77, 94);

vec4 trace(ivec2 pos) {
  while (pos.y != 256) {
    // check if the current scanline's pillar is hit
    if (pos.x < dp[pos.y] + 15 &&
        pos.x > dp[pos.y] - 15) {
      // hit; calculate gradient
      float p = (15.0 - abs(float(pos.x - dp[pos.y]))) / 15.0;
      // return pixel color
      return vec4(p, p, p, 1.0);
    }
    // scan upwards
    pos.y++;
  }
  // miss
  return vec4(0.0, 0.0, 0.0, 1.0);
}

void main() {
  // pos is screen coodrdinates in 0..1
  vec2 pos = gl_FragCoord.xy / resolution;
  // ipos is screen coordinates in 0..255 in integer steps.
  ivec2 ipos = ivec2(int(pos.x * 256.0), int(pos.y * 256.0));

  _GLF_color = trace(ipos);
}
