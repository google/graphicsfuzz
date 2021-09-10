#version 450

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
precision highp int;

layout(location = 0) out vec4 _GLF_color;

uniform vec2 injectionSwitch;

uniform vec2 resolution;

/*
This shader populates an floating point array with values
in reverse order, and then uses bubble sort to put the values
in the desired order.

To make things a bit more interesting, the order is based on 
the screen coordinates of the shader, so half of the screen
sorts in one order and vice versa.

The resulting data is then used to draw an image on the screen,
dividing the screen in half in another direction.

While the data being sorted is in floating point format, the
data itself consists of integer values. The image drawing uses
division to keep the values in a sane range. All of the operations
are numerically stable.
*/

// Check which order to sort values. For half of the screen
// vertically sort in one order, and for the other half the other.
bool checkSwap(float a, float b)
{
    return gl_FragCoord.y < resolution.y / 2.0 ? a > b : a < b;
}

void main()
{
    float data[10];
    // Populate the data array in a reversed order.
    // Note the use of injectionSwitch.y, which is guaranteed
    // to be 1.0 at run-time, to avoid compiler optimizations.
    for (int i = 0; i < 10; i++)
        {
            data[i] = float(10 - i) * injectionSwitch.y;
        }
 
    // Sort the values
    for (int i = 0; i < 9; i++)
        {
            for (int j = 0; j < 10; j++)
                {
                    // Instead of using the lower bound of j 
                    // directly, we use continue here to do the 
                    // same thing.
                    if (j < i + 1)
                        {
                            continue;
                        }
                    bool doSwap = checkSwap(data[i], data[j]);
                    if (doSwap)
                        {
                            float temp = data[i];
                            data[i] = data[j];
                            data[j] = temp;
                        }
                }
        }
 
    // Draw image based on the sorted values.
    // For half the screen horizontally use one order,
    // and for the other the inverse order of values.
    if (gl_FragCoord.x < resolution.x / 2.0)
        {
            _GLF_color = vec4(data[0] / 10.0, data[5] / 10.0, data[9] / 10.0, 1.0);
        }
    else
        {
            _GLF_color = vec4(data[5] / 10.0, data[9] / 10.0, data[0] / 10.0, 1.0);
        }
}
