#version 310 es

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

void main(void) {
  uint uselessOutVariable;
  float A[50];
  int i = bitfieldExtract(0, bitCount(int(injectionSwitch.x)), int(injectionSwitch.x));
  do {
    if (i >= int(resolution.x)) {
      break;
    }
    if (findLSB(16 * int(injectionSwitch.y)) * (i/findMSB(16 * int(injectionSwitch.y))) == i) {
      A[i/findLSB(16 * int(injectionSwitch.y))] = float(i);
    }
    i++;
  } while(i < bitfieldInsert(200 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x)));
  i = findLSB(int(injectionSwitch.x));
  do {
    if (i < int(gl_FragCoord.x)) {
      break;
    }
    if (i > findMSB(int(injectionSwitch.x))) {
      A[i] += A[usubBorrow(uint(i), uint(injectionSwitch.y), uselessOutVariable)];
    }
    i++;
  } while(i < bitfieldInsert(50 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x)));
  if (int(gl_FragCoord.x) < findLSB(1048576)) {
    _GLF_color = vec4(A[bitfieldReverse(int(injectionSwitch.x))]/resolution.x, A[findMSB(16)]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < bitfieldInsert(40 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x))) {
    _GLF_color = vec4(A[findLSB(32 * int(injectionSwitch.y))]/resolution.x, A[findMSB(512)]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < bitfieldInsert(60 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x))) {
    _GLF_color = vec4(A[findMSB(1024 * int(injectionSwitch.y))]/resolution.x, A[findLSB(16384)]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < bitfieldInsert(80 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x))) {
    _GLF_color = vec4(A[findLSB(32768 * int(injectionSwitch.y))]/resolution.x, A[findMSB(524288)]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < bitfieldInsert(100 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x))) {
    _GLF_color = vec4(A[findMSB(1048576)]/resolution.x, A[findLSB(16777216 * int(injectionSwitch.y))]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < bitfieldInsert(120 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x))) {
    _GLF_color = vec4(A[findLSB(33554432)]/resolution.x, A[findMSB(536870912 * int(injectionSwitch.y))]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < bitfieldInsert(140 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x))) {
    _GLF_color = vec4(A[findMSB(1073741824)]/resolution.x, A[bitfieldInsert(34, 0, 0, 0)]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < bitfieldInsert(160 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x))) {
    _GLF_color = vec4(A[bitfieldInsert(35, 0, 0, int(injectionSwitch.x))]/resolution.x, A[bitfieldInsert(39, 0, 0, int(injectionSwitch.x))]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < bitfieldInsert(180 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x))) {
    _GLF_color = vec4(A[bitfieldInsert(40, 0, 0, int(injectionSwitch.x))]/resolution.x, A[bitfieldInsert(44, 0, 0, int(injectionSwitch.x))]/resolution.y, 1.0, 1.0);
  } else if (int(gl_FragCoord.x) < bitfieldInsert(180 * int(injectionSwitch.y), 0, 0, int(injectionSwitch.x))) {
    _GLF_color = vec4(A[bitfieldInsert(45, 0, 0, int(injectionSwitch.x))]/resolution.x, A[bitfieldInsert(49, 0, 0, int(injectionSwitch.x))]/resolution.y, 1.0, 1.0);
  } else {
    discard;
  }

}

