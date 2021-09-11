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

uniform vec3 polynomial;

uniform vec3 initial_xvalues;

float fx(float x)
{
    return polynomial.x * pow(x, 2.0) + polynomial.y * x + polynomial.z;
}

void main()
{
    // n
    float x2 = initial_xvalues.x;
    // n - 1
    float x1 = initial_xvalues.y;
    // n - 2
    float x0 = initial_xvalues.z;
    // temp storage
    float temp = 0.0;
    // constants in the quadratic formula
    // calculated with derived formulas for Muller's method
    float A = 0.0;
    float B = 0.0;
    float C = 0.0;

    while(abs(x2 - x1) >= .000000000000001)
    {
        float h0 = x0 - x2;
        float h1 = x1 - x2;
        float k0 = fx(x0) - fx(x2);
        float k1 = fx(x1) - fx(x2);
        temp = x2;
        A = (((h1) * (k0)) - ((h0) * (k1))) / 
            ((pow((h0), 2.0) * (h1)) - (pow((h1), 2.0) * (h0)));
        B = (((k0) - (A * (pow((h0), 2.0)))) / (h0));
        C = fx(x2);

        // get the new root
        x2 = x2 - ((2.0 * C) / (B + sign(B) * sqrt(pow(B, 2.0) - (4.0 * A * C))));
        
        // move up in the sequence.
        x0 = x1;
        x1 = temp; // x1 = original x2;
    }
    if(x2 <= -0.9 && x2 >= -1.1)
    {
        _GLF_color = vec4(1.0, 0.0, 0.0, 1.0);
    }
    else
    {
        _GLF_color = vec4(0.0, 1.0, 0.0, 1.0);
    }
}
