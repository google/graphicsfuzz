#version 320 es

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
  vec2 pos = gl_FragCoord.xy / resolution;
  ivec2 ipos = ivec2(int(pos.x * 8.0), int(pos.y * 8.0));
  int v = (ipos.x & 5) | (ipos.y & 10);
  int w = (ipos.y & 5) | (ipos.x & 10);
  ivec2 p = ivec2(v * 8 + w, 0);
  int i;
  for (i = 0; i < 100; i++) {
    p = iter(p);
  }

  if (p.x < 0) {
    p.x = -p.x;
  }
  
  while (p.x > 15) {
    p.x -= 16;
  }

  _GLF_color = pal[p.x];
}
