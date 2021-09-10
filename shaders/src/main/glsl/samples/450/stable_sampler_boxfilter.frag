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
This shader reads 3x3 texture samples and sums
them using weights, producing a box filtered version
of the texture.

Since the texture coordinates are only manipulated
at texel-integer steps, the positions inside the 
texels should be stable.

Any potential instability comes from sampling the
texture. If a rounding error causes a different
texel to be chosen (or in case of bilinear, weighted
average of various texels), the result will differ.
*/

uniform sampler2D tex;

const float weights[9] = float[9](
   1.0,   0.5, -0.25,
   0.5,   1.0, -0.5,
  -0.25, -0.5, -1.0);

void main()
{
    vec2 coord = gl_FragCoord.xy * (1.0 / 256.0);
    float uvstep = 1.0 / 256.0;

    vec4 res = vec4(0);

    for (int i = 0; i < 3; i++)
    {
        for (int j = 0; j < 3; j++)
        {
            res += texture(tex, coord + vec2(float(i - 1) * uvstep, float(j - 1) * uvstep)) * weights[i * 3 + j];
        }
    }

    _GLF_color = vec4(res.xyz, 1.0);
}

