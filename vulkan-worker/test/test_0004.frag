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

layout(set = 0, binding = 0) uniform buf1 {
  vec2 resolution;
};

vec3 pickColor(int i) {
  return vec3(float(i) / 100.0);
}

vec3 mand(float xCoord, float yCoord) {
  float height = resolution.y;
  float width = resolution.x;

  float c_re = (xCoord - width/2.0)*4.0/width;
  float c_im = (yCoord - height/2.0)*4.0/width;
  float x = 0.0, y = 0.0;
  int iteration = 0;
  for (int k = 0; k < 1000; k++) {
    if (x*x+y*y > 4.0) {
      break;
    }
    float x_new = x*x - y*y + c_re;
    y = 2.0*x*y + c_im;
    x = x_new;
    iteration++;
  }
  if (iteration < 1000) {
    return pickColor(iteration);
  } else {
    return vec3(0.0);
  }
}

void main() {
  vec3 data[9];
  for (int i = 0; i < 3; i++) {
    for (int j = 0; j < 3; j++) {
      data[3*j + i] = mand(gl_FragCoord.x + float(i - 1), gl_FragCoord.y + float(j - 1));
    }
  }
  vec3 sum = vec3(0.0);
  for (int i = 0; i < 9; i++) {
    sum += data[i];
  }
  sum /= vec3(9.0);
  _GLF_color = vec4(sum, 1.0);
}
