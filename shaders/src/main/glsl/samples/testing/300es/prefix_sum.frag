#version 300 es

/*
 * Copyright 2018 The GraphicsFuzz Project Authors
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

void main(void) {
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
  if (int(gl_FragCoord.x) < 4) {
    _GLF_color = vec4(A[0]/resolution.x, A[1]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < 8) {
    _GLF_color = vec4(A[2]/resolution.x, A[3]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < 12) {
    _GLF_color = vec4(A[4]/resolution.x, A[5]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < 16) {
    _GLF_color = vec4(A[6]/resolution.x, A[7]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < 20) {
    _GLF_color = vec4(A[8]/resolution.x, A[9]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < 24) {
    _GLF_color = vec4(A[10]/resolution.x, A[11]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < 28) {
    _GLF_color = vec4(A[12]/resolution.x, A[13]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < 32) {
    _GLF_color = vec4(A[14]/resolution.x, A[15]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < 36) {
    _GLF_color = vec4(A[16]/resolution.x, A[17]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < 36) {
    _GLF_color = vec4(A[18]/resolution.x, A[19]/resolution.y, 1.0, 1.0);
  } else {
    discard;
  }

}
