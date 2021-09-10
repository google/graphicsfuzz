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
precision highp int;

layout(location = 0) out vec4 _GLF_color;

uniform vec2 resolution;

/*
This shader creates an array of 10 items, populates it
with reversed values, and performs a quicksort to put
the values in correct order. The sorted values are then
used to produce a colorful image, which will display
incorrectly if the sorting failed.

Sorting itself uses purely integer operations, while the
image-producing part uses linear floating point
operations (divisions, additions, normalize) which
should be numerically stable.
*/

struct QuicksortObject{
    int numbers[10];
};

QuicksortObject obj;

 void swap(int i, int j) {
     int temp = obj.numbers[i];
     obj.numbers[i] = obj.numbers[j];
     obj.numbers[j] = temp;
 }

 // Since "partition" is the preserved word, we add a prefix
 // to this function name to prevent an error.
 int performPartition(int l, int h) {
     // The rightmost element is chosen as a pivot.
     int pivot = obj.numbers[h];
     int i = l - 1;

     for (int j = l; j <= h - 1; j++) {
         if (obj.numbers[j] <= pivot) {
             // For every item that is smaller (or equal)
             // than the pivot, move them back
             i++;
             swap(i, j);
         }
     }
     // Finally, move the pivot in place - all previous
     // items are smaller than pivot.
     i++;
     swap(i, h);
     // Return the new position of the pivot
     return i;
 }

 void quicksort() {
     // Low and high ranges of elements
     int l = 0, h = 9;
     // Only two elements of this stack are ever actually used.
     int stack[10];
     // Since this top is pre-incremented, it starts from -1
     int top = -1;

     stack[++top] = l;
     stack[++top] = h;

     while (top >= 0) {
         h = stack[top--];
         l = stack[top--];

         int p = performPartition(l, h);
 /*
   Status after each call to performPartition:
   1  81  64  49  36  25  16   9   4 100    l:0 h:9 p:0
   1  81  64  49  36  25  16   9   4 100    l:1 h:9 p:9
   1   4  64  49  36  25  16   9  81 100    l:1 h:8 p:1
   1   4  64  49  36  25  16   9  81 100    l:2 h:8 p:8
   1   4   9  49  36  25  16  64  81 100    l:2 h:7 p:2
   1   4   9  49  36  25  16  64  81 100    l:3 h:7 p:7
   1   4   9  16  36  25  49  64  81 100    l:3 h:6 p:3
   1   4   9  16  36  25  49  64  81 100    l:4 h:6 p:6
   1   4   9  16  25  36  49  64  81 100    l:4 h:5 p:4
 */

         // Shrink the range
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

void main() {
    // Initialize decreasing values to an array starting from 10.
    for (int i = 0; i < 10; i ++) {
        obj.numbers[i] = (10 - i);
        obj.numbers[i] = obj.numbers[i] * obj.numbers[i];
        // Values end up being 100, 81, 64, 49, 36, 25, 16, 9, 4 and 1
    }

    quicksort();

    // uv gets screen coordinates in 0..1 range
    vec2 uv = gl_FragCoord.xy / resolution;

    // start color from an arbituary vector
    vec3 color = vec3(1.0, 2.0, 3.0);

    // add sorted values to the color vector elements
    // based on various screen coordinate criteria
    color.x += float(obj.numbers[0]);
    if (uv.x > (1.0 / 4.0)) {
      color.x += float(obj.numbers[1]);
    }
    if (uv.x > (2.0 / 4.0)) {
      color.y += float(obj.numbers[2]);
    }
    if(uv.x > (3.0 / 4.0)) {
      color.z += float(obj.numbers[3]);
    }
    color.y += float(obj.numbers[4]);
    if (uv.y > (1.0 / 4.0)) {
      color.x += float(obj.numbers[5]);
    }
    if (uv.y > (2.0 / 4.0)) {
      color.y += float(obj.numbers[6]);
    }
    if(uv.y > (3.0 / 4.0)) {
      color.z += float(obj.numbers[7]);
    }
    color.z += float(obj.numbers[8]);
    if (abs(uv.x - uv.y) < 0.25) {
      color.x += float(obj.numbers[9]);
    }

    // final vector is normalized to produce color
    // elements in 0..1 range
    _GLF_color = vec4(normalize(color), 1.0);
}

