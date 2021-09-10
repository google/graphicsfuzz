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

const vec4 pal[16] = vec4[16](
  vec4(0.0, 0.0, 0.0, 1.0),
  vec4(0.5, 0.0, 0.0, 1.0),
  vec4(0.0, 0.5, 0.0, 1.0),
  vec4(0.5, 0.5, 0.0, 1.0),
  vec4(0.0, 0.0, 0.5, 1.0),
  vec4(0.5, 0.0, 0.5, 1.0),
  vec4(0.0, 0.5, 0.5, 1.0),
  vec4(0.5, 0.5, 0.5, 1.0),

  vec4(0.0, 0.0, 0.0, 1.0),
  vec4(1.0, 0.0, 0.0, 1.0),
  vec4(0.0, 1.0, 0.0, 1.0),
  vec4(1.0, 1.0, 0.0, 1.0),
  vec4(0.0, 0.0, 1.0, 1.0),
  vec4(1.0, 0.0, 1.0, 1.0),
  vec4(0.0, 1.0, 1.0, 1.0),
  vec4(1.0, 1.0, 1.0, 1.0));

/*
This shader uses a simple iterative algorithm
to generate random numbers. The function was
inspired by simple orbit mechanism where several
variables affect each other at the same time,
leading to unpredictable movement.

All of the math is integer based, so the result
is deterministic.

After the number is generated, the modulo of its
absolute value is used to pick the pixel's color
from the table.
*/

ivec2 iter(ivec2 p) {
  if (p.x > 0) {
    p.y--;
  }
  if (p.x < 0) {
    p.y++;
  }
  p.x += p.y / 2;
  return p;
}

void main() {
  // pos is screen coordinates in 0..1 range
  vec2 pos = gl_FragCoord.xy / resolution;
  // ipos is screen coordinates in 0..7 range in integer steps.
  // This creates a tile pattern.
  ivec2 ipos = ivec2(int(pos.x * 8.0), int(pos.y * 8.0));
  // Initial postion is derived from the screen coordinates.
  int v = (ipos.x & 5) | (ipos.y & 10);
  int w = (ipos.y & 5) | (ipos.x & 10);
  ivec2 p = ivec2(v * 8 + w, 0);
  // The coordinates are then iterated through the orbit function
  int i;
  for (i = 0; i < 100; i++) {
    p = iter(p);
  }

  /*
  The maximum initial value is 135:
  31 & 5 = 5, 31 & 10 = 10. (5+10)*8+(5+10) = 135.

  Example values after each iteration for the initial value of 135:
   135  134  133  131  129  126  123  119  115  110  105   99   93
    86   79   71   63   54   45   35   25   14    3   -9  -20  -31
   -41  -51  -60  -69  -77  -85  -92  -99 -105 -111 -116 -121 -125
  -129 -132 -135 -137 -139 -140 -141 -141 -141 -141 -140 -139 -137
  -135 -132 -129 -125 -121 -116 -111 -105  -99  -92  -85  -77  -69
   -60  -51  -41  -31  -20   -9    3   14   25   35   45   54   63
    71   79   86   93   99  105  110  115  119  123  126  129  131
   133  134  135  135  135  135  134  133  131
  */

  // Absolute value
  if (p.x < 0) {
    p.x = -p.x;
  }

  // Modulo
  while (p.x > 15) {
    p.x -= 16;
  }

  _GLF_color = pal[p.x];
}
