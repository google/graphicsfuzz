#version 100

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

#ifndef REDUCER
#define _GLF_ZERO(X, Y)          (Y)
#define _GLF_ONE(X, Y)           (Y)
#define _GLF_FALSE(X, Y)         (Y)
#define _GLF_TRUE(X, Y)          (Y)
#define _GLF_IDENTITY(X, Y)      (Y)
#define _GLF_DEAD(X)             (X)
#define _GLF_FUZZED(X)           (X)
#define _GLF_WRAPPED_LOOP(X)     X
#define _GLF_WRAPPED_IF_TRUE(X)  X
#define _GLF_WRAPPED_IF_FALSE(X) X
#define _GLF_SWITCH(X)           X
#endif

// END OF GENERATED HEADER

precision highp float;
precision highp int;

uniform vec2 injectionSwitch;

uniform vec2 resolution;

struct Intersection {
    bool hit;
    float hit_distance;
};
uniform float GLF_live11time;

uniform vec2 GLF_live11resolution;

bool checkSwap(float a, float b)
{
    {
        vec4 GLF_live11gl_FragColor = _GLF_FUZZED(vec4(1.0));
        vec4 GLF_live11gl_FragCoord = _GLF_FUZZED(vec4(1.0));
        {
            vec4 GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ;
            vec2 GLF_merged2_0_1_1_1_1_1PQ;
            GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw = GLF_merged2_0_1_1_1_1_1PQ;
            vec2 GLF_live11uv = GLF_live11gl_FragCoord.xy / GLF_live11resolution.xy - .5;
            vec3 GLF_live11dir = vec3(GLF_live11uv * 1.900, 1.);
            float Q = GLF_live11time * 0.0 + .5;
            GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.y = Q;
            GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw.y = GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.y;
            float P = 0.0;
            GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.x = P;
            GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw.x = GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.x;
            mat2 GLF_live11rot1 = mat2(cos(GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw.x), sin(GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw.x), - sin(GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw.x), cos(GLF_merged3_0_1_1_1_1_1_2_2_25PQGLF_merged2_0_1_1_1_1_1PQ.zw.x));
            mat2 GLF_live11rot2 = GLF_live11rot1;
            vec3 GLF_live11from = vec3(0., 0., 0.);
            float GLF_live11s = .1, GLF_live11fade = .2;
            vec3 GLF_live11v = vec3(0.4);
            for(
                int GLF_live11r = 0;
                GLF_live11r < 10;
                GLF_live11r ++
            )
                {
                    vec3 GLF_live11p = GLF_live11from + GLF_live11s * GLF_live11dir * .5;
                    float GLF_live11pa, GLF_live11a = GLF_live11pa = 0.;
                    for(
                        int GLF_live11i = 0;
                        GLF_live11i < 10;
                        GLF_live11i ++
                    )
                        {
                        }
                    float GLF_live11dm = max(0., 0.400 - GLF_live11a * GLF_live11a * .001);
                    if(GLF_live11r > 3)
                        GLF_live11fade *= 1. - GLF_live11dm;
                }
        }
    }
    return gl_FragCoord.y < resolution.y / 2.0 ? a > b : a < b;
}
void main()
{
    float data[10];
    for(
        int i = 0;
        i < 10;
        i ++
    )
        {
            data[i] = float(10 - i) * injectionSwitch.y;
        }
    for(
        int i = 0;
        i < 9;
        i ++
    )
        {
            Intersection(false, 0.0);
            for(
                int j = 0;
                j < 10;
                j ++
            )
                {
                    if(j < i + 1)
                        {
                            continue;
                        }
                    bool doSwap = checkSwap(data[i], data[j]);
                    if(doSwap)
                        {
                            float temp = data[i];
                            data[i] = data[j];
                            data[j] = temp;
                        }
                }
        }
    if(gl_FragCoord.x < resolution.x / 2.0)
        {
            gl_FragColor = vec4(data[0] / 10.0, data[5] / 10.0, data[9] / 10.0, 1.0);
        }
    else
        {
            gl_FragColor = vec4(data[5] / 10.0, data[9] / 10.0, data[0] / 10.0, 1.0);
        }
}
