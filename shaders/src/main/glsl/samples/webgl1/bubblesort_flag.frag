#version 100
//WebGL

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

precision mediump float;

uniform vec2 injectionSwitch;

uniform vec2 resolution;

bool checkSwap(float a, float b)
{
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
