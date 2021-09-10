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

struct QuicksortObject{
    int numbers[10];
};

QuicksortObject obj;

 void swap(int i, int j) {
     int temp = obj.numbers[i];
     obj.numbers[i] = obj.numbers[j];
     obj.numbers[j] = temp;
 }

 // Since "partition" is the preserved word, we add prefix to this function name to prevent an error.
 int performPartition(int l, int h) {
     // The rightmost element is chosen as a pivot.
     int pivot = obj.numbers[h];
     int i = (l - 1);
  
     for (int j = l; j <= h - 1; j++) {
         if (obj.numbers[j] <= pivot) {
             i++;
             swap(i, j);
         }
     }
     swap(i + 1, h);
     return (i + 1);
 }

 void quicksort() {
     int l = 0, h = 9;
     int stack[10];
     int top = -1;
  
     stack[++top] = l;
     stack[++top] = h;
  
     while (top >= 0) {
         h = stack[top--];
         l = stack[top--];

         int p = performPartition(l, h);
         if (p - 1 > l) {
             stack[++top] = l;
             stack[++top] = p - 1;
         }
         if (p + 1 < h) {
             stack[++top] = p + 1;
             stack[++top] = h;
         }
     }
 }

vec3 palette(vec3 a, vec3 b, vec3 c, vec3 d) {
    return fract(mix(d * a, a * c, b + d - c));
}

float randomize(vec2 co)
{
    return float(floor(fract(sin(dot(co.xy ,vec2(12.5, 3.))) * 4250. + 0.02) + 0.5));
}

bool puzzlelize(vec2 pos)
{
    return randomize(pos.xy) < .12;
}

void main() {
    // Initialize decreasing values to an array starting from 10.
    for (int i = int(injectionSwitch.x); i < 10; i ++) {
        obj.numbers[i] = (10 - i) * int(injectionSwitch.y);
    }
    quicksort();
    vec2 grid = vec2(20, 20);
    vec2 uv = gl_FragCoord.xy / resolution;
    vec3 color = palette(vec3(float(obj.numbers[4]) * 0.1), vec3(0.9, float(obj.numbers[8]) * 0.1, 0.8), trunc(vec3(injectionSwitch.y)), vec3(injectionSwitch.x, 0.3, 0.7));
    if (uv.x > (1.0 / 4.0)) {
        int count = int(injectionSwitch.x);
        do {
            color += palette(vec3(0.5, float(obj.numbers[8]) * 0.1, 0.2), vec3(0.5), trunc(vec3(injectionSwitch.y)), vec3(float(obj.numbers[4]) * 0.1, injectionSwitch.x, 0.6));
            count++;
        } while (count != obj.numbers[int(injectionSwitch.x)]);
        grid = vec2(count + obj.numbers[8], count + obj.numbers[6]);
    }
    if (uv.x > (2.0 / 4.0)) {
        int count = int(injectionSwitch.x);
        do {
            color -= palette(vec3(float(obj.numbers[4]) * 0.1), trunc(vec3(0.1)), vec3(float(obj.numbers[int(injectionSwitch.y)]) * 0.1), vec3(injectionSwitch.x, float(obj.numbers[2]) * 0.1 , float(obj.numbers[8]) * 0.1));
            count++;
        } while (count != obj.numbers[1]);
        grid += vec2(count + int(injectionSwitch.y), count + int(injectionSwitch.x));
    }
    if(uv.x > (3.0 / 4.0)) {
        int count = int(injectionSwitch.x);
        do {
            color -= palette(vec3(float(obj.numbers[int(injectionSwitch.y)]) * 0.1), vec3(0.8), trunc(vec3(0.1)), vec3(injectionSwitch.x, 0.6, float(obj.numbers[int(injectionSwitch.x)]) * 0.1));
            count++;
        } while (count != obj.numbers[2]);
        grid += vec2(count + obj.numbers[3], count + obj.numbers[3]);
    }

    vec2 position = vec2(gl_FragCoord.x, resolution.x - gl_FragCoord.y);
    position = floor(position / grid);
    _GLF_color = vec4(color, injectionSwitch.y) + vec4(!puzzlelize(position));

}
