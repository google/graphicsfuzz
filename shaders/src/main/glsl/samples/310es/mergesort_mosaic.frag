#version 310 es

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

layout(location = 0) out vec4 _GLF_color;

uniform vec2 injectionSwitch;

uniform vec2 resolution;

int data[10];

void mergeSort() {
    const int n = 10;
    int i, j, k, size, l1, h1, l2, h2, temp[10];

    for (size = 1; size < n; size = size * 2 ) {
        l1 = 0;
        k = 0;
        while (l1 + size < n) {
            h1 = l1 + size - 1;
            l2 = h1 + 1;
            h2 = l2 + size - 1;

            if (h2 >= n) {
                h2 = n-1;
            }

            i = l1;
            j = l2;

            while (i <= h1 && j <= h2) {
                if (data[i] <= data[j]) {
                    temp[k++] = data[i++];
                } else {
                    temp[k++] = data[j++];
                }
            }

            while (i <= h1) {
                temp[k++] = data[i++];
            }
            while (j <= h2) {
                temp[k++] = data[j++];
            }
            l1 = h2 + 1;
        }

        for (i = l1; k < n; i++) {
            temp[k++] = data[i];
        }

        for (i = 0; i < n; i++) {
            data[i] = temp[i];
        }
    }
}

void main() {    
    int i = int(injectionSwitch.x);
    do {
        switch(i) {
            case 0:
                data[i] = 4;
            break;
            case 1:
                data[i] = 3 ;
            break;
            case 2:
                data[i] = 2 ;
            break;
            case 3:
                data[i] = 1 ;
            break;
            case 4:
                data[i] = 0 ;
            break;
            case 5:
                data[i] = -1;
            break;
            case 6:
                data[i] = -2;
            break;
            case 7:
                data[i] = -3;
            break;
            case 8:
                data[i] = -4;
            break;
            case 9:
                data[i] = -5;
            break;
            }
            data[i] *= int(dFdx(fwidth(float(data[i]))) + ldexp(injectionSwitch.y, i));
            i++;
        } while (i < 10);

        mergeSort();

        mat3 pos = mat3(
            vec3(4, 0, int(injectionSwitch.y)) * ldexp(0.5, 2),
            vec3(0, -5, int(injectionSwitch.y)) * ldexp(0.1, 5),
            fwidth(vec3(1, 8, int(injectionSwitch.y))* dFdy(ldexp(injectionSwitch.y, 0))));

        vec3 vecCoor = pos * vec3(gl_FragCoord.xx / resolution.yx, 1);
        vecCoor = roundEven(vecCoor);
        vec2 color;

        do {
            if (int(gl_FragCoord[1]) < 30) {
                color = fract(sin(vecCoor.yx - trunc(float(data[0]))));
                color[1] += dFdy(vecCoor[1] * vecCoor[0]);
                break;
            } else if (int(gl_FragCoord[1]) < 60) {
                color = fract(sin(vecCoor.yx - trunc(float(data[1]))));
                color.y -= dFdx(color.x) * dFdy(color.y) + (isinf(color.y) ? asinh(color.y): fwidth(color.y));
                break;
            } else if (int(gl_FragCoord[1]) < 90) {
                color = fract(sin(vecCoor.yx - trunc(float(data[2]))));
                color.x += atanh(color.x) * cosh(injectionSwitch.y);
                break;
            } else if (int(gl_FragCoord[1]) < 120) {
                color = fract(sin(vecCoor.yx - trunc(float(data[3]))));
                color.y -= cosh(color[0] + dFdy(gl_FragCoord.y));
                break;
            } else if (int(gl_FragCoord[1]) < 150) {
                discard;
                break;
            } else if (int(gl_FragCoord[1]) < 180) {
                color = fract(sin(vecCoor.yx - trunc(float(data[4]))));
                color[1] -= asinh(gl_FragCoord.y * dFdy(color.y));
                break;
            } else if (int(gl_FragCoord[1]) < 210) {
                color = fract(sin(vecCoor.yx - trunc(float(data[5]))));
                color.y -= tanh(color.x) / cosh(color.y);
                break;
            } else if (int(gl_FragCoord[1]) < 240) {
                color = fract(sin(vecCoor.yx - trunc(float(data[6]))));
                color.y -= tanh(color.x) / cosh(color.y) + (isnan(color[0]) ? tanh(color.x): trunc(color.x));
                break;
            } else if (int(gl_FragCoord[1]) < 270) {
                color = fract(sin(vecCoor.yx - trunc(float(data[7]))));
                color.y -= degrees(color.x) - ldexp(color.y, 5);
                break;
            } else{
                discard;
            }
        } while(0 == int(injectionSwitch.x));

        _GLF_color = vec4(vec3(color, trunc(injectionSwitch.y)), injectionSwitch.y);

}
