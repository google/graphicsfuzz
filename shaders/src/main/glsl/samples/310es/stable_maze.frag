#version 310 es

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

layout(location = 0) out vec4 _GLF_color;
uniform vec2 resolution;

int map[16 * 16];

void main() {
  vec2 pos = gl_FragCoord.xy / resolution;
  ivec2 ipos = ivec2(int(pos.x * 16.0), int(pos.y * 16.0));

  int i;
  for (i = 0; i < 16 * 16; i++) {
    map[i] = 0;
  }

  ivec2 p = ivec2(0, 0);
  bool canwalk = true;
  int v = 0;
  do {
    v++;
    int directions = 0;

    if (p.x > 0 && map[(p.x - 2) + (p.y) * 16] == 0) {
      directions += 1;
    }
    if (p.y > 0 && map[(p.x) + (p.y - 2) * 16] == 0) {
      directions += 1;
    }
    if (p.x < 14 && map[(p.x + 2) + (p.y) * 16] == 0) {
      directions += 1;
    }
    if (p.y < 14 && map[(p.x) + (p.y + 2) * 16] == 0) {
      directions += 1;
    }

    if (directions == 0) {
      canwalk = false;
      int j;
      for (i = 0; i < 8; i++) {
        for (j = 0; j < 8; j++) {
          if (map[j * 2 + i * 2 * 16] == 0) {
            p.x = j * 2;
            p.y = i * 2;
            canwalk = true;
          }
        }
      }
      map[(p.x) + (p.y) * 16] = 1;
    } else {
      int d = v % directions;
      v += directions;

      if (d >= 0 && p.x > 0 && map[(p.x - 2) + (p.y) * 16] == 0) {
        d--;
        map[(p.x) + (p.y) * 16] = 1;
        map[(p.x - 1) + (p.y) * 16] = 1;
        map[(p.x - 2) + (p.y) * 16] = 1;
        p.x -= 2;
      }
      if (d >= 0 && p.y > 0 && map[(p.x) + (p.y - 2) * 16] == 0) {
        d--;
        map[(p.x) + (p.y) * 16] = 1;
        map[(p.x) + (p.y - 1) * 16] = 1;
        map[(p.x) + (p.y - 2) * 16] = 1;
        p.y -= 2;
      }
      if (d >= 0 && p.x < 14 && map[(p.x + 2) + (p.y) * 16] == 0) {
        d--;
        map[(p.x) + (p.y) * 16] = 1;
        map[(p.x + 1) + (p.y) * 16] = 1;
        map[(p.x + 2) + (p.y) * 16] = 1;
        p.x += 2;
      }
      if (d >= 0 && p.y < 14 && map[(p.x) + (p.y + 2) * 16] == 0) {
        d--;
        map[(p.x) + (p.y) * 16] = 1;
        map[(p.x) + (p.y + 1) * 16] = 1;
        map[(p.x) + (p.y + 2) * 16] = 1;
        p.y += 2;
      }
    }
    if (map[ipos.y * 16 + ipos.x] == 1) {
      _GLF_color = vec4(1.0, 1.0, 1.0, 1.0);
      return;
    }
  }
  while (canwalk);

  _GLF_color = vec4(0.0, 0.0, 0.0, 1.0);
}
