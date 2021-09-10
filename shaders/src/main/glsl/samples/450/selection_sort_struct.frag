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

uniform vec2 resolution;

struct Obj {
  float odd_numbers[10];
  float even_numbers[10];
};

void main() {
  Obj obj;

  // Initialize first 10 odd numbers to the array.
  int odd_index = 0;
  float odd_number = 1.0;
  while (odd_index <= 9) {
    obj.odd_numbers[odd_index] = odd_number;
    odd_number += 2.0;
    odd_index++;
  }
  // Similarly, initialize even numbers and iterate backward.
  int even_index = 9;
  float even_number = 0.0;
  while (even_index >= 0) {
    obj.even_numbers[even_index] = even_number;
    even_number += 2.;
    even_index--;
  }

  // Perform the selection sort.
  for (int i = 0; i < 9; i++) {
    int index = i;
    for (int j = i + 1; j < 10; j++) {
      if (obj.even_numbers[j] < obj.even_numbers[index]) {
        index = j;
      }
    }
    float smaller_number = obj.even_numbers[index];
    obj.even_numbers[index] = obj.even_numbers[i];
    obj.even_numbers[i] = smaller_number;
  }

  vec2 uv = gl_FragCoord.xy/resolution.xy;
  vec3 col =  tan(pow(uv.xxx, uv.yyy) +
  vec3(
    obj.odd_numbers[int(floor(gl_FragCoord.x/1000.0))],
    obj.even_numbers[int(floor(gl_FragCoord.y/1000.0))],
    sinh(uv.x)
  ));

  _GLF_color.rgb = col;
  _GLF_color.a = 1.0;
}
