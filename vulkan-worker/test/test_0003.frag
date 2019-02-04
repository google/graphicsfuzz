#version 310 es

// Copyright 2019 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

precision mediump float;

layout(location = 0) out vec4 _GLF_color;

layout(set = 0, binding = 0) uniform buf0 {
  vec2 injectionSwitch;
};

layout(set = 0, binding = 1) uniform buf1 {
  vec2 resolution;
};

float nb_mod(float limit, float ref) {
    float s = 0.0;
    for (int i = 1; i < 800; i++) {
        if (mod(float(i), ref) <= 0.01) {
            s += 0.2;
        }
        if (float(i) >= limit) {
            return s;
        }
    }
    return s;
}

void main()
{
    vec4 c = vec4(0.0, 0.0, 0.0, 1.0);
    float ref = floor(resolution.x / 8.0);

    c.x = nb_mod(gl_FragCoord.x, ref);
    c.y = nb_mod(gl_FragCoord.y, ref);
    c.z = c.x + c.y;

    for (int i = 0; i < 3; i++) {
        if (c[i] >= 1.0) {
            c[i] = c[i] * c[i];
        }
    }
    c.x = mod(c.x, 1.0);
    c.y = mod(c.y, 1.0);
    c.z = mod(c.z, 1.0);
    _GLF_color = c;
}
