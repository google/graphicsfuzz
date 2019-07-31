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

struct Obj{
    int numbers[10];
};

Obj obj;

void swap(int i, int j) { 
    int temp = obj.numbers[i];
    obj.numbers[i] = obj.numbers[j];
    obj.numbers[j] = temp;
} 

int performPartition(int l, int h) { 
    int x = obj.numbers[h]; 
    int i = (l - 1); 
  
    for (int j = l; j <= h - 1; j++) { 
        if (obj.numbers[j] <= x) { 
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

vec3 palette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a - b * sin( 8.0 * (c * t + d + d));
}

void main() {
    for (int i = int(injectionSwitch.x); i < 10; i ++) {
        obj.numbers[i] = (10 - i) * int(injectionSwitch.y);
    }
    quicksort();
    vec2 uv = gl_FragCoord.xy / resolution;

    vec3 color = palette(tanh(uv.x), vec3( float(obj.numbers[6]) * 0.1 ), vec3(0.5, float(obj.numbers[4]) * 0.1, 0.8), trunc(vec3(injectionSwitch.y)), vec3(injectionSwitch.x, 0.33, 0.67));
    if (uv.x > (1.0/4.0)) {
        int count = int(injectionSwitch.x);
        do {
            color += palette(uv.x, vec3(0.5, float(obj.numbers[8]) * 0.1, 0.2), vec3(0.5), trunc(vec3(injectionSwitch.y)), vec3(float(obj.numbers[4]) * 0.1, 0.33, 0.67));
            color[0] *= fwidth(color.y) * ldexp(color.x, count);
            color.y /= tanh(color[0]) / cosh(color[1]);
            count++;
        } while (count != obj.numbers[int(injectionSwitch.x)]);
    }
    if (uv.x > (2.0/4.0)) {
        int count = int(injectionSwitch.x);
        do {
            color -= palette(trunc(uv.x), vec3(float(obj.numbers[4]) * 0.1), trunc(vec3(0.1)), vec3(float(obj.numbers[int(injectionSwitch.y)]) * 0.1), vec3(injectionSwitch.x, float(obj.numbers[2]) * 0.1 , float(obj.numbers[8]) * 0.1));
            color.x *= fwidth(color[1]) * ldexp(gl_FragCoord.x, count) + (isinf(color.y) ? asinh(color.y): fwidth(gl_FragCoord[0]));
            color[0] += (isnan(color[0]) ? tanh(color.x): trunc(color.y));
            count++;
        } while (count != obj.numbers[1]);
    }
    if (uv.x > (3.0/4.0)) {
        int count = int(injectionSwitch.x);
        do {
            color -= palette(uv.x, vec3(float(obj.numbers[int(injectionSwitch.x)]) * 0.1), vec3(0.6), trunc(vec3(0.1)), vec3(injectionSwitch.x, 0.2, float(obj.numbers[int(injectionSwitch.x)]) * 0.1));
            color[0] += fwidth(color.x) * fwidth(color.y);
            count++;
        } while (count != obj.numbers[2]);
    }

    _GLF_color = vec4(color, injectionSwitch.y);

}
