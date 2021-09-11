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
This shader repeatedly reads a texel and uses it to
calculate the next set of coordinates until an
arbituary criteria is met, in this case the sum
of the texel colors must be under 1, up to 16
iterations.

Before reading again the coordinates are normalized
to integer positions within the texture in order
to minimize rounding errors.

Any potential instability comes from sampling the
texture. If a rounding error causes a different
texel to be chosen (or in case of bilinear, weighted
average of various texels), the result will differ.
*/

uniform sampler2D tex;

void main()
{
    int i = 0;
    vec2 coord = gl_FragCoord.xy * (1.0 / 256.0);
    vec4 texel = texture(tex, coord);
    while (texel.x + texel.y + texel.z > 1.0 && i < 16)
    {
        coord = texel.xz + texel.yy;
        coord = floor(coord * 256.0) / 256.0;
        texel = texture(tex, coord);
        i++;
    }
    _GLF_color = texel;
}

