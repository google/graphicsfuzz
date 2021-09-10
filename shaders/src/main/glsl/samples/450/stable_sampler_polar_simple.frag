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
This shader performs polar coordinate transform
to the texture coordinates and then reads the texture.

In order to minimize the potential stability issues 
with calculating atan (and to make this shader a
bit more interesting) a very rough estimate of
atan is used instead. The result should be stable.

Before reading the texture the coordinates are normalized
to integer positions within the texture in order
to minimize rounding errors.

Any potential instability comes from sampling the
texture. If a rounding error causes a different
texel to be chosen (or in case of bilinear, weighted
average of various texels), the result will differ.
*/

uniform sampler2D tex;

float ReallyApproxNormalizedAtan2(vec2 v)
{
    float pi2 = 1.0 / (355.0 / 113.0);
    
    if (length(v) < 0.001)
    {
        return 0.0;
    }

    vec2 a = abs(v);

    float z;
    if (a.y > a.x)
    {
        z = a.x / a.y;
    }
    else
    {
        z = a.y / a.x;
    }

    float th = (0.97 - 0.19 * z * z) * z * pi2;
    
    if (a.y < a.x)
    { 
        th = 0.5 - th;
    }
    
    if (v.x < 0.0) 
    {
        th = 1.0 - th;
    }
    
    if (v.y < 0.0)
    {
        th = -th;
    }
    
    return th;
}

vec2 polarize(vec2 coord)
{
    vec2 center = coord - vec2(0.5);
    float dist = length(center);
    float angle = ReallyApproxNormalizedAtan2(center);
    return vec2(dist, angle);
}

void main()
{
    vec2 coord = gl_FragCoord.xy * (1.0 / 256.0);
    coord = polarize(coord);
    coord = floor(coord * 256.0) / 256.0;
    _GLF_color = vec4(texture(tex, coord).xyz, 1.0);
}

