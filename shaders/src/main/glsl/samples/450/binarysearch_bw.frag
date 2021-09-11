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

layout(location = 0) out vec4 _GLF_color;

uniform vec2 injectionSwitch;

uniform vec2 resolution;

struct BinarySearchObject{
    int prime_numbers[10];
};

vec2 brick(vec2 uv) {
    int a = 4;
    do {
        uv.y -= step(injectionSwitch.y, uv.x);
        uv.x -= fract(tanh(uv.x)) / ldexp(injectionSwitch.y, findMSB(a));
        a--;
    } while (a > int(injectionSwitch.x));
    int b = 3;
    do {
        uv.y -= step(injectionSwitch.y, uv.x) + float(a);
        uv.x *= (isnan(uv.y) ? cosh(gl_FragCoord.y) : tanh(gl_FragCoord.x));
        b--;
    } while (b > int(injectionSwitch.x));
    int c = 2;
    do {
        uv.y -= step(injectionSwitch.y, uv.x) + float(a) + float(b);
        uv.x += ldexp(injectionSwitch.y, isinf(uv.y + uv.x) ? findMSB(b) : findMSB(a));
        c--;
    } while (c > int(injectionSwitch.x));
    int d = 1;
    do {
        uv.y -= step(injectionSwitch.y, uv.x) + float(a) + float(b) + float(c);
        d--;
    } while (d > int(injectionSwitch.x));
    return fract(uv);
}

float patternize(vec2 uv) {
    vec2 size = vec2(0.45);
    vec2 st = smoothstep(size, size, uv);
     switch (int(mod(gl_FragCoord.y, 5.0))) {
        case 0:
            return mix(pow(st.x, injectionSwitch.y), st.x, size.y);
        break;
        case 1:
            return mix(pow(uv.y, injectionSwitch.y), st.y, size.x);
        break;
        case 2:
            discard;
        break;
        case 3:
            return mix(pow(uv.y, injectionSwitch.y), uv.y, size.y);
        break;
        case 4:
            return mix(pow(st.y, injectionSwitch.y), st.x, size.x); 
        break;
     }
}

int binarySearch(BinarySearchObject obj, int x) {
    int l = 0, r = 9;
    while (l <= r) {
        int m = (l + r) / 2;
        if (obj.prime_numbers[m] == x) {
            return m;
        }
        
        if (obj.prime_numbers[m] < x) {
            l = m + 1;  
        } else {
            r = m - 1; 
        }
    }
    // If an element is not present in the array we return -1.
    return -1;
}  

void main() {
    BinarySearchObject obj;
    // Initialize first 10 prime numbers to the array.
    for (int i = 0; i < 10; i++) {
        if (i == 0) {
            obj.prime_numbers[i] = 2;
        } else if (i == 1) {
            obj.prime_numbers[i] = 3;
        } else if (i == 2) {
            obj.prime_numbers[i] = 5;
        } else if (i == 3) {
            obj.prime_numbers[i] = 7;
        } else if (i == 4) {
            obj.prime_numbers[i] = 11;
        } else if (i == 5) {
            obj.prime_numbers[i] = 13;
        } else if (i == 6) {
            obj.prime_numbers[i] = 17;
        } else if (i == 7) {
            obj.prime_numbers[i] = 19;
        } else if (i == 8) {
            obj.prime_numbers[i] = 23;
        } else if (i == 9) {
            obj.prime_numbers[i] = 29;
        }
    }

    vec2 uv = (gl_FragCoord.xy / resolution.x) * vec2(resolution.x / resolution.y, 1.0);
    vec2 b = brick(uv * 7.0);
    vec3 color = vec3(patternize(b));

    if (gl_FragCoord.y < resolution.y / 1.1) {
        // We are going to search the item in array by giving the value of the item index 4 and 0 in an array.
        if (binarySearch(obj, obj.prime_numbers[4]) != -(int(resolution.y)) && binarySearch(obj, obj.prime_numbers[0]) >= -(int(resolution.x))) {
            color.yz -= dot(float(binarySearch(obj, obj.prime_numbers[4])), float(binarySearch(obj, obj.prime_numbers[0])));
        } else {
            discard;
        }
    } else {
        // The following condition is true as there is no value 1 in the array.
        if (binarySearch(obj, 1) == -1) {
            discard;
        } else {
            color.yz += color.yz;
        }
    }

    _GLF_color = vec4(color, injectionSwitch.y);

}
