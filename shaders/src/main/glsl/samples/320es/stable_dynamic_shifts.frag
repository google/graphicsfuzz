#version 320 es

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

precision highp int;
precision highp float;

layout(location = 0) out vec4 _GLF_color;

/*
This shader performs some bitwise logical
operations based on the fragment coordinates
to produce an image.

The shift operation uses fragment coordinate
as the shifting parameter, making the shifts
dynamic. The shifts are limited to 0..31 range
to avoid undefined behavior.

The shift result is masked to 32 bits to avoid
potential issues with architectures that support
larger than 32 bit integers. 

Everything is done with unsigned integers.

All of the calculation is done with integer math
and all values fit in 32 bits required by highp int,
making this a stable shader.
*/

void main()
{
    uvec2 icoord = uvec2(gl_FragCoord.xy);

    uint res1 = uint(((icoord.x * icoord.y) >> (icoord.x & 31u)) & 0xffffffffu);
    uint res2 = uint(((icoord.x * icoord.y) << (icoord.x & 31u)) & 0xffffffffu);

    float res = float(((res2 & 0x80000000u) != 0u ? 1u : 0u) ^ ((res1 & 1u) != 0u ? 1u : 0u));

    _GLF_color = vec4(res, res, res, 1.0);
}

