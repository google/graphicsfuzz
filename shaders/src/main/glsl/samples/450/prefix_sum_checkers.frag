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

uniform vec2 injectionSwitch;

uniform vec2 resolution;

vec2 pattern(in vec2 x) {
  vec2 n = floor(x);
  vec3 m = vec3(1.0);

  for (int j = -1; j <= int(injectionSwitch.y); j++) {
    for (int i = -1; i <= int(injectionSwitch.y); i++) {
      vec2  g = vec2(float(j), float(i));
      vec2  o = mix(n, g, 0.2);
      if (injectionSwitch.x < (m.x)) {
        int k = 1;
        while (k >= 0) {
          o = o + o;
          k--;
        }
        m = vec3(injectionSwitch.x, cos(o));
      }
    }
  }
  return vec2(m.x, m.y - m.z);
}

void main() {
  vec2 uv = gl_FragCoord.xy / resolution.y;
  float A[50];
  for (int i = 0; i < 200; i++) {
    if (i >= int(resolution.x)) {
      break;
    }
    if ((4 * (i/4)) == i) {
      A[i/4] = float(i);
    }
  }
  for (int i = 0; i < 50; i++) {
    if (i < int(gl_FragCoord.x)) {
      break;
    }
    if (i > 0) {
      A[i] += A[i - 1];
    }
  }

  vec2 c = pattern((15.0 + tan(0.2)) * uv);
  vec3 col;
  if (int(gl_FragCoord.y) < 20) {
    col = .5 + cos(c.y + vec3(resolution.x, A[4]/resolution.x + 50.0, 22.0));
  } else if (int(gl_FragCoord.y) < 40) {
    col = .5 + cos(c.y + vec3(resolution.x, A[9]/resolution.x + 50.0, 22.0));
  } else if (int(gl_FragCoord.y) < 60) {
    col = .5 + cos(c.y + vec3(resolution.x, A[14]/resolution.x + 50.0, 22.0));
  } else if (int(gl_FragCoord.y) < 80) {
    col = .5 + cos(c.y + vec3(resolution.x, A[39]/resolution.x + 50.0, 22.0));
  } else if (int(gl_FragCoord.y) < 100) {
    col = .5 + cos(c.y + vec3(resolution.x, A[39]/resolution.x + 50.0, 22.0));
  } else if (int(gl_FragCoord.y) < 120) {
    col = .5 + cos(c.y + vec3(resolution.x, A[39]/resolution.x + 50.0, 22.0));
  } else if (int(gl_FragCoord.y) < 140) {
    col = .5 + cos(c.y + vec3(resolution.x, A[39]/resolution.x + 50.0, 22.0));
  } else if (int(gl_FragCoord.y) < 160) {
    col = .5 + cos(c.y + vec3(resolution.x, A[39]/resolution.x + 50.0, 22.0));
  } else if (int(gl_FragCoord.y) < 180) {
    col = .5 + cos(c.y + vec3(resolution.x, A[44]/resolution.x + 50.0, 22.0));
  } else if (int(gl_FragCoord.y) < 200) {
    col = .5 + cos(c.y + vec3(resolution.x, A[49]/resolution.x + 50.0, 22.0));
  } else {
    discard;
  }

  _GLF_color = vec4(col, 1.0);
}
