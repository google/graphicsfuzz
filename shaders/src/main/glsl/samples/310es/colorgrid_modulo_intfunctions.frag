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

uniform vec2 resolution;

float nb_mod(float limit, float ref) {
    float s = float(bitfieldExtract(int(resolution.x), 0, 0));
    int i = bitCount(int(resolution.y));
    while(i < bitfieldInsert(800, i, int(resolution.x), 0)) {
        if (mod(float(i), ref) <= 0.01) {
            s += 0.2;
        }
        if (float(i) >= limit) {
            return s;
        }
        i++;
    }
    return s;
}

void main()
{
    vec4 c = vec4(bitfieldExtract(int(resolution.x), 0, 0), 0.0, 0.0, bitCount(int(resolution.x)));
    float ref = floor(resolution.x / float(findLSB(int(resolution.y))));

    c.x = nb_mod(gl_FragCoord.x, ref);
    c.y = nb_mod(gl_FragCoord.y, ref);
    c.z = c.x + c.y;
    int i = bitfieldReverse(bitfieldExtract(int(resolution.x), 0, 0));
    do {
        if (c[i] >= float(bitCount(int(resolution.y)))) {
            c[i] = c[i] * c[i];
        }
        i++;
    } while (i < findMSB(findLSB(int(resolution.y))));
    c.x = mod(c.x, float(bitCount(int(resolution.x))));
    c.y = mod(c.y, float(bitCount(int(resolution.y))));
    c.z = mod(c.z, float(bitCount(int(resolution.x))));
    _GLF_color = c;
}

