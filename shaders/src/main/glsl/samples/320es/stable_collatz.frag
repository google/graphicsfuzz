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
This shader uses the collatz formula to generate
random numbers.

The collatz formula is simply:
f(n) = n / 2, if n is even
f(n) = n * 3 + 1, if n is odd

The algorithm, when iterated, seems to converge
to 1, but the number of iterations required is
not obvious from the input value, and it has not
actually been proven that all values eventually
converge to 1, but a counter-example has not 
been found either.

Since all the math uses integers, it is
deterministic.
*/

int collatz(int v) {
  int count = 0;
  while (v > 1) {
    if ((v & 1) == 1) {
      v = 3 * v + 1;
    }
    else {
      v /= 2;
    }
    count++;
  }
  return count;
}

void main() {
  vec2 lin = gl_FragCoord.xy / resolution;
  lin = floor(lin * 8.0);
  int v = int(lin.x) * 8 + int(lin.y);
  _GLF_color = pal[collatz(v) % 16];
}
