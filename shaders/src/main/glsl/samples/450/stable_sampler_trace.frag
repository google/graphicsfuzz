#version 450

/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

/*
This shader reads a texel and uses its green channel
as a height value, and then does trivial search to 
find whether some nearby texel casts a shadow on it.

Since the texture coordinates are only manipulated
at texel-integer steps, the positions inside the 
texels should be stable.

Any potential instability comes from sampling the
texture. If a rounding error causes a different
texel to be chosen (or in case of bilinear, weighted
average of various texels), the result will differ.
*/

uniform sampler2D tex;

void main()
{
    int i = 0;
    vec2 uvstep = vec2(1.0) * (1.0 / 256.0);
    float slope = 2.0 / 256.0;
    vec2 coord = gl_FragCoord.xy * (1.0 / 256.0);
    float refh = texture(tex, coord).y;
    coord -= uvstep;
    refh += slope;
    float h = texture(tex, coord).y;
    while (h < refh && i < 32)
    {
        coord -= uvstep;
        refh += slope;
        h = texture(tex, coord).y;
        i++;
    }
    _GLF_color = vec4(vec3(float(i) * (1.0 / 32.0)), 1.0);
}

