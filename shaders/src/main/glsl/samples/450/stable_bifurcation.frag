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
This shader is basically a fancy random number generator
using a simple bifurcation algorithm. All the math is
done using integers, so as long as the integer width is
sufficient (which "precision highp int" should provide),
the math itself is deterministic.

The randomness comes from the unstability of the
algorithm as the number of iterations increases.

To understand what's going on, this youtube video
has a very good explanation:
https://youtube.com/watch?v=ovJcsL7vyrk

After a random number is produced, its modulo is used
to pick a color from the table of 16 colors.
*/

void main() {
  // pos is screen coordinates in 0..1 range
  vec2 pos = gl_FragCoord.xy / resolution;
  // lin is screen coordinates in 0..9 range, integer steps;
  // this creates a grid pattern.
  ivec2 lin = ivec2(int(pos.x * 10.0), int(pos.y * 10.0));
  // number of iterations per grid cell is x+y*10, so max is 99
  int iters = lin.x + lin.y * 10;
  int v = 100;
  int i;
  for (i = 0; i < iters; i++) {
    v = (4 * v * (1000 - v)) / 1000;
  /*
  Value of v after each iteration; after a few initial cycles
  the values become periodic.
  360 921 291 825 577 976  93 337 893 382 944 211 665 891 388
  949 193 623 939 229
  706 830 564 983  66 246 741 767 714 816 600 960 153 518
  998   7  27 105 375 937 236 721 804 630 932 253 755 739 771
  706 830 564 983  66 246 741 767 714 816 600 960 153 518
  998   7  27 105 375 937 236 721 804 630 932 253 755 739 771
  706 830 564 983  66 246 741 767 714 816 600 960 153 518
  998   7  27 105 375 937 236
  */
  }
  _GLF_color = pal[v % 16];
}

