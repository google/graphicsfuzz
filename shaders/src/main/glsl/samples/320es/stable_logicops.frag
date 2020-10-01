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
This shader performs various bitwise logical
operations based on the fragment coordinates
to produce an image.

The output values for each channel is either
0 or 1.

All of the calculation is done with integer math
and all values fit in 32 bits required by highp int,
making this a stable shader.
*/


void main()
{
    ivec2 icoord = ivec2(gl_FragCoord.xy);

    int res3 = ((icoord.x >> 5) & 1) ^ ((icoord.y & 32) >> 5);
    int res2 = (icoord.y * icoord.y >> 10) & 1;
    int res1 = (icoord.x * icoord.y >> 9) & 1;

    _GLF_color = vec4(float(res1 ^ res2), float(res2 & res3), float(res1 | res3), 1.0);
}

