#version 450

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
precision highp int;

layout(location = 0) out vec4 _GLF_color;

uniform vec2 injectionSwitch;

uniform vec2 resolution;

bool checkSwap(float a, float b)
{
    return gl_FragCoord.y < resolution.y / 2.0 ? a > b : a < b;
}
void main()
{
    int msb10 = 1024;
    int msb9 = 512;
    uint uselessOutVariable;
    float data[10];
    for(int i = bitfieldReverse(int(injectionSwitch.x)); i < findMSB(msb10); i++)
    {
        data[i] = float(usubBorrow(uint(10), uint(i), uselessOutVariable)) * injectionSwitch.y;
    }
    int i = bitfieldExtract(int(injectionSwitch.x), bitCount(0), int(injectionSwitch.x));
    do
    {
        for(int j = bitfieldExtract(int(injectionSwitch.x), 0, 0); j < findLSB(msb10); j++)
        {
            if(uint(j) < uaddCarry(uint(i), 1u, uselessOutVariable))
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
        i++;
    } while(i < findMSB(msb9));
    if(gl_FragCoord.x < resolution.x / 2.0)
    {
        _GLF_color = vec4(data[findMSB(1)] / 10.0, data[findLSB(32)] / 10.0, data[findMSB(msb9)] / 10.0, 1.0);
    }
    else
    {
        _GLF_color = vec4(data[findLSB(32)] / 10.0, data[findMSB(msb9)] / 10.0, data[findMSB(1)] / 10.0, 1.0);
    }
}

