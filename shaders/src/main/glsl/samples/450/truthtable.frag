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

precision highp int;
precision highp float;

layout(location = 0) out vec4 _GLF_color;

/*
Even though this shader is technically stable, debugging
it would be such a nightmare that it probably should not
be used as a stable reference.
*/

void main()
{
    ivec2 icoord = ivec2(255, 255) - ivec2(gl_FragCoord.xy);
    int A = (icoord.x & 1) != 0 ? 0 :~0; 
    int B = (icoord.x & 2) != 0 ? 0 :~0; 
    int C = (icoord.x & 4) != 0 ? 0 :~0; 
    int D = (icoord.x & 8) != 0 ? 0 :~0; 
    int E = (icoord.x & 16) != 0 ? 0 :~0; 

    int F = (icoord.y & 1) != 0 ? 0 :~0; 
    int G = (icoord.y & 2) != 0 ? 0 :~0; 
    int H = (icoord.y & 4) != 0 ? 0 :~0; 
    int I = (icoord.y & 8) != 0 ? 0 :~0; 
    int J = (icoord.y & 16) != 0 ? 0 :~0; 

    int res = (A|~C|~D|~E|F|G|H|~I|~J)&(B|~C|~D|~E|F|G|H|~I|~J)&(~A|C|~D|~E|F|~H|I|~J)&(A|~B|~D|~E|G|~H|I|~J);
    res &= (A|B|~C|D|~E|F|G|~H|I|~J)&(B|~C|~D|~E|~F|~G|H|I|~J)&(A|C|~D|~E|~G|H|I|~J)&(A|C|~D|~E|~F|H|I|~J);
    res &= (A|B|~C|D|~E|~G|H|I|~J)&(~A|C|D|~E|~G|H|I|~J)&(~A|~B|~C|~D|E|~G|H|I|~J)&(A|~B|D|~E|G|H|I|~J);
    res &= (~A|B|~C|D|~E|G|H|I|~J)&(A|~B|~D|E|G|H|I|~J)&(A|~C|~D|E|F|G|H|I|~J)&(~A|C|~D|E|F|G|H|I|~J);
    res &= (~A|~B|C|~D|~E|~F|~G|~H|~I|J)&(A|~B|C|D|~E|~F|~G|~H|~I|J)&(~A|B|C|D|~E|~G|~H|~I|J);
    res &= (~C|~D|E|~F|~G|~H|~I|J)&(~A|~C|~D|E|~G|~H|~I|J)&(B|~D|E|~F|~H|~I|J);
    res &= (A|B|C|~D|E|~G|~H|~I|J)&(B|C|D|~E|F|~G|~H|~I|J);
    res &= (A|~C|D|E|F|~G|~H|~I|J)&(B|~C|D|E|F|~G|~H|J);
    res &= (~A|~B|C|D|E|F|~G|~H|~I|J);
    res &= (A|B|~C|~D|E|G|~H|~I|J);
    res &= (~B|D|E|~F|G|~H|~I|J);
    res &= (~B|C|~D|E|F|G|~H|~I|J);
    res &= (~A|~B|~C|D|E|F|G|~I|J)&(~B|~C|D|E|~F|~G|H|~I|J);
    res &= (~A|B|C|D|E|H|~I|J)&(B|C|D|E|F|~G|H|~I|J)&(A|B|C|~D|E|F|G|H|~I|J);
    res &= (A|~B|C|D|E|F|G|H|~I|J)&(~B|D|E|~F|~G|~H|I|J)&(~A|~B|C|~E|~G|~H|I|~J);
    res &= (~A|~D|~E|~F|G|~H|I|~J)&(A|B|~E|~F|~G|H|I|~J)&(~B|C|D|~E|F|~G|H|I|~J);
    res &= (~B|~C|~D|E|F|~G|H|I|~J)&(~A|~B|C|~D|G|H|I|~J)&(~C|D|~E|~F|G|H|I|~J);
    res &= (~A|B|C|E|F|G|~H|~I|J)&(~A|B|D|E|~G|H|~I|J);
    res &= (A|B|~C|~D|~E|~F|~H|I|~J);
    res &= (~A|~C|~E|~F|G|~H|I|~J);
    res &= (A|~B|~C|D|~E|~F|~H|I|~J);
    res &= (B|~C|D|~E|~F|~G|~H|I|~J)&(~A|~B|~C|D|~E|F|~H|I|~J)&(~B|~C|D|~E|F|~G|~H|I|~J);

    _GLF_color = vec4(vec3(float(res == 0 ? 0.0 : 1.0)), 1.0);
}

