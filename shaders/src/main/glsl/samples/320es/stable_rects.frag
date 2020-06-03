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

const vec4 picdata[8] = vec4[8](
  vec4(4, 4, 20, 4),
  vec4(4, 4, 4, 20),
  vec4(4, 20, 20, 4),
  vec4(20, 4, 4, 8),
  vec4(8, 6, 4, 2),
  vec4(2, 12, 2, 4),
  vec4(16, 2, 4, 4),
  vec4(12, 22, 4, 4)
);

int index;
int state[16];

bool collision(vec2 pos, vec4 quad) {
  if (pos.x < quad.x) {
    return false;
  }
  if (pos.y < quad.y) {
    return false;
  }
  if (pos.x > quad.x + quad.z) {
    return false;
  }
  if (pos.y > quad.y + quad.w) {
    return false;
  }
  return true;
}

vec4 match(vec2 pos) {
  int i;
  vec4 res = vec4(0.5, 0.5, 1.0, 1.0);
  for (i = 0; i < 8; i++) {
    if (collision(pos, picdata[i])) {
      res = pal[(int(picdata[i].x) * int(picdata[i].y) + i * 9 + 11) % 16];
    }
  }
  return res;
}

void main() {
  vec2 lin = gl_FragCoord.xy / resolution;
  lin = floor(lin * 32.0);
  _GLF_color = match(lin.xy);
}
