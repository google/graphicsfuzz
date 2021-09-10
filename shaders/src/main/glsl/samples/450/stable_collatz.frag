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
This shader uses the collatz formula to generate
random numbers.

The collatz formula is simply:
f(n) = n / 2, if n is even
f(n) = n * 3 + 1, if n is odd

The algorithm, when iterated, seems to converge
to 1, but the number of iterations required is
not obvious from the input value.

As of this writing there's no mathematical proof
that all values converge to 1.

Since all the math uses integers, it is
deterministic.

The shader calculates the number of iterations
for each pixel to reach 1, and uses the modulo
of that number to pick a color from the table.
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
  // lin is screen coordinates in 0..1
  vec2 lin = gl_FragCoord.xy / resolution;
  // now lin is screen coordinates in 0..7 in integer steps
  // this creates a tile pattern of 8x8 pixel tiles
  lin = floor(lin * 8.0);
  // The number we're looking for iterations is the tile
  // number x*8+y
  int v = int(lin.x) * 8 + int(lin.y);
  _GLF_color = pal[collatz(v) % 16];
/*
The maximum number for v is 288 (32*8+32). The produced values are:
  0   0   1   7   2   5   8  16   3  19   6  14   9   9  17  17   4
 12  20  20   7   7  15  15  10  23  10 111  18  18  18 106   5  26
 13  13  21  21  21  34   8 109   8  29  16  16  16 104  11  24  24
 24  11  11 112 112  19  32  19  32  19  19 107 107   6  27  27  27
 14  14  14 102  22 115  22  14  22  22  35  35   9  22 110 110   9
  9  30  30  17  30  17  92  17  17 105 105  12 118  25  25  25  25
 25  87  12  38  12 100 113 113 113  69  20  12  33  33  20  20  33
 33  20  95  20  46 108 108 108  46   7 121  28  28  28  28  28  41
 15  90  15  41  15  15 103 103  23 116 116 116  23  23  15  15  23
 36  23  85  36  36  36  54  10  98  23  23 111 111 111  67  10  49
 10 124  31  31  31  80  18  31  31  31  18  18  93  93  18  44  18
 44 106 106 106  44  13 119 119 119  26  26  26 119  26  18  26  39
 26  26  88  88  13  39  39  39  13  13 101 101 114  26 114  52 114
114  70  70  21  52  13  13  34  34  34 127  21  83  21 127  34  34
 34  52  21  21  96  96  21  21  47  47 109  47 109  65 109 109  47
 47   8 122 122 122  29  29  29  78  29 122  29  21  29  29  42  42
 16  29  91  91  16  16  42  42  16  42  16  60 104 104 104  42
*/
}

