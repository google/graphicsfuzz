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
This shader rasterizes a triangle in perhaps the most
inefficent way possible apart from raytracing, by
checking for each pixel whether we're inside a triangle.

The pointInTriangle code was based on
"Real Time Collision Detection", Morgan-Kaufmann
by Christer Ericson, page 206.

While the code uses floating point, everything is
linear, and a such the result should be deterministic.
*/

float cross2d(vec2 a, vec2 b) {
  return ((a.x) * (b.y) - (b.x) * (a.y));
}

int pointInTriangle(vec2 p, vec2 a, vec2 b, vec2 c) {
  float pab = cross2d(vec2(p.x - a.x, p.y - a.y), vec2(b.x - a.x, b.y - a.y));
  float pbc = cross2d(vec2(p.x - b.x, p.y - b.y), vec2(c.x - b.x, c.y - b.y));
  if (!((pab <  0.0 && pbc <  0.0) ||
        (pab >= 0.0 && pbc >= 0.0))) {
    return 0;
  }
  float pca = cross2d(vec2(p.x - c.x, p.y - c.y), vec2(a.x - c.x, a.y - c.y));
  if (!((pab <  0.0 && pca <  0.0) ||
        (pab >= 0.0 && pca >= 0.0))) {
    return 0;
  }
  return 1;
}

void main() {
  vec2 pos = gl_FragCoord.xy / resolution;
  if (pointInTriangle(pos, vec2(0.7, 0.3), vec2(0.5, 0.9), vec2(0.1, 0.4)) == 1) {
    _GLF_color = vec4(1.0, 0.0, 0.0, 1.0);
  }
  else {
    _GLF_color = vec4(0.0, 0.0, 0.0, 1.0);
  }
}
