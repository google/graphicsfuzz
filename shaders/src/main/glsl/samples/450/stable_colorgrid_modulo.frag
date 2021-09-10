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

uniform vec2 resolution;

/*
This shader produces a grid of colored 32x32 pixel
squares in a rather roundabout way.

While floating point numbers are used, the values
are largely integer. In the portion of the code which
produces the final image, multiplication and normalize
operations are used.
*/

/*
Returns -0.5 if limit is in the range [0, 31), -0.1 if limit is the
range [32, 63), etc., where the returned value steps up by 0.4 each
time limit steps up by 32.

The parameter 'thirty_two' is guaranteed to have the value 32.0.
*/
float compute_value(float limit, float thirty_two) {
    float result = -0.5;

    // The loop will not really execute 800 times, as 'limit' is bounded by
    // gl_FragCoord, and there is an early exit based on 'limit'.
    for (int i = 1; i < 800; i++) {
        if ((i % 32) == 0) {
            // Avoid computing e.g. mod(float(32), round(32.0)),
            // which could be sensitive to round-off if mutated.
            result += 0.4;
        } else {
            if (mod(float(i), round(thirty_two)) <= 0.01) {
                // This should never get executed, because the
                // previous if condition would get triggered.
                result += 100.0;
            }
            // if i % 32 isn't zero, result is untouched
        }
        // early exit based on gl_FragCoord:
        if (float(i) >= limit) {
            return result;
        }
    }
    return result;
}

void main()
{
    vec3 c = vec3(7.0, 8.0, 9.0);

    // This is guaranteed to be 32.0, as resolution.x is 256.0.
    // Use of uniform avoids compiler optimizations.
    float thirty_two = round(resolution.x / 8.0);

    c.x = compute_value(gl_FragCoord.x, thirty_two);
    c.y = compute_value(gl_FragCoord.y, thirty_two);
    c.z = c.x + c.y;

    for (int i = 0; i < 3; i++) {
        if (c[i] >= 1.0) {
            c[i] = c[i] * c[i];
        }
    }

    _GLF_color = vec4(normalize(abs(c)), 1.0);
}
