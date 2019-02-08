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
#endif

// END OF GENERATED HEADER

precision mediump float;

const vec2 injectionSwitch = vec2(0.0, 1.0);

float time = 0.0;

vec2 mouse = vec2(0.0);

vec2 resolution = vec2(256.0, 256.0);

const float PI = 3.1415926535;

struct R {
    vec3 o;
    vec3 d;
};
struct S {
    float c;
    vec3 n;
    float d;
    R r;
};
const float N = 50.0;

vec4 GLF_live2gl_FragColor;

vec4 GLF_live2gl_FragCoord;

float GLF_live2time;

vec2 GLF_live2resolution;

const float GLF_live2N = 50.0;

mat2 GLF_live2rotate2D(float GLF_live2a)
{
    vec2 GLF_live2cs = vec2(cos(GLF_live2a), sin(GLF_live2a));
    return mat2(GLF_live2cs.x, - GLF_live2cs.y, GLF_live2cs.y, GLF_live2cs.x);
}
vec2 GLF_live2rotate(float GLF_live2a, vec2 GLF_live2p)
{
    return GLF_live2p * GLF_live2rotate2D(GLF_live2a);
}
vec3 GLF_live2hsbToRGB(float GLF_live2h, float GLF_live2s, float GLF_live2b)
{
    return GLF_live2b * (1.0 - GLF_live2s) + (GLF_live2b - GLF_live2b * (1.0 - GLF_live2s)) * clamp(abs(abs(6.0 * (GLF_live2h - vec3(0, 1, 2) / 3.0)) - 3.0) - 1.0, 0.0, 1.0);
}
vec3 GLF_live2colorFunc(float GLF_live2h)
{
    return GLF_live2hsbToRGB(fract(GLF_live2h), 1.0, (0.5 + (sin(GLF_live2time) * 0.5 + 0.5)));
}
vec3 GLF_live2drawShape(in vec2 GLF_live2pixel, in vec2 GLF_live2square, in vec3 GLF_live2setting)
{
    if((GLF_live2pixel.x - (GLF_live2setting.x) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x) > GLF_live2square.y) && ! ((GLF_live2pixel.x - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.y)))
        return GLF_live2colorFunc(GLF_live2setting.z / 40.0);
    return vec3(0.0);
}
vec4 GLF_live0gl_FragColor;

vec4 GLF_live0gl_FragCoord;

float GLF_live0time;

vec2 GLF_live0resolution;

mat2 rotate2D(float a)
{
    vec2 cs = vec2(cos(a), sin(a));
    return mat2(cs.x, - cs.y, _GLF_IDENTITY(cs, (_GLF_IDENTITY(cs, vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (_GLF_IDENTITY(cs, vec2(1.0, 1.0) * (cs))))) + vec2(0.0, 0.0)).y, cs.x);
    {
        float GLF_live2a = _GLF_FUZZED((float((vec2(-991.393, 7.7) != vec2(-74.65, -5.6)))));
        {
            vec2 GLF_live2cs = vec2(cos(GLF_live2a), sin(GLF_live2a));
            mat2(GLF_live2cs.x, - GLF_live2cs.y, GLF_live2cs.y, GLF_live2cs.x);
        }
    }
}
vec2 rotate(float a, vec2 p)
{
    return p * rotate2D(a);
}
vec3 hsbToRGB(float h, float s, float b)
{
    return b * (1.0 - _GLF_IDENTITY(s, (_GLF_IDENTITY(s, (_GLF_IDENTITY(s, (s) * 1.0)) * _GLF_ONE(1.0, injectionSwitch.y))) + 0.0)) + _GLF_IDENTITY((b - b * (1.0 - s)) * clamp(abs(abs(6.0 * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / 3.0)) - 3.0) - 1.0, 0.0, 1.0), vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)) + (_GLF_IDENTITY((b - b * (1.0 - s)) * clamp(abs(abs(6.0 * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / 3.0)) - 3.0) - 1.0, 0.0, 1.0), ((b - _GLF_IDENTITY(b, (_GLF_IDENTITY(b, 1.0 * (_GLF_IDENTITY(b, (b) + 0.0)))) * 1.0) * (_GLF_IDENTITY(1.0, _GLF_ONE(1.0, injectionSwitch.y) * (_GLF_IDENTITY(1.0, (1.0) + 0.0))) - s)) * clamp(_GLF_IDENTITY(_GLF_IDENTITY(abs(abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)))) - 3.0), _GLF_IDENTITY(vec3(0.0, 0.0, 0.0), vec3(0.0, 0.0, 0.0) + (vec3(0.0, 0.0, 0.0))) + (_GLF_IDENTITY(abs(abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)))) - 3.0), (abs(abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)))) - 3.0)) * vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))))) - 1.0, (_GLF_IDENTITY(_GLF_IDENTITY(abs(abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)))) - 3.0), _GLF_IDENTITY(vec3(0.0, 0.0, 0.0), vec3(0.0, 0.0, 0.0) + (vec3(0.0, 0.0, 0.0))) + (_GLF_IDENTITY(abs(abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)))) - 3.0), (abs(abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)))) - 3.0)) * vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))))) - 1.0, vec3(1.0, 1.0, 1.0) * (_GLF_IDENTITY(abs(abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)))) - 3.0), _GLF_IDENTITY(vec3(0.0, 0.0, 0.0), vec3(0.0, 0.0, 0.0) + (vec3(0.0, 0.0, 0.0))) + (_GLF_IDENTITY(abs(abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)))) - 3.0), (abs(_GLF_IDENTITY(abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (_GLF_IDENTITY(h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)), (_GLF_IDENTITY(h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)), (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0))) + vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))) + _GLF_IDENTITY(vec3(0.0, 0.0, 0.0), vec3(1.0, 1.0, 1.0) * (vec3(0.0, 0.0, 0.0)))))), (abs(_GLF_IDENTITY(6.0, 1.0 * (6.0)) * (_GLF_IDENTITY(h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)), (_GLF_IDENTITY(h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0)), (h - vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, 1 * (0))) * 1), 1, 2) / _GLF_IDENTITY(3.0, 1.0 * (3.0))) + vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))) + _GLF_IDENTITY(vec3(0.0, 0.0, 0.0), vec3(1.0, 1.0, 1.0) * (vec3(0.0, 0.0, 0.0))))))) * vec3(1.0, 1.0, 1.0)) - 3.0)) * vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))))) - 1.0))) * vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))), 0.0, 1.0)) * vec3(1.0, 1.0, 1.0))));
}
vec3 colorFunc(float h)
{
    return hsbToRGB(fract(h), 1.0, (0.5 + (sin(time) * 0.5 + 0.5)));
}
vec3 drawShape(in vec2 pixel, in vec2 square, in vec3 setting)
{
    if((pixel.x - (_GLF_IDENTITY(setting, vec3(1.0, 1.0, 1.0) * (_GLF_IDENTITY(setting, (_GLF_IDENTITY(setting, vec3(0.0, 0.0, 0.0) + (setting))) * vec3(1.0, 1.0, 1.0)))).x) < square.x && pixel.x + (setting.x) > square.x && pixel.y - (setting.x) < square.y && pixel.y + (setting.x) > _GLF_IDENTITY(square, (_GLF_IDENTITY(square, vec2(1.0, 1.0) * (_GLF_IDENTITY(square, (square) + vec2(0.0, 0.0))))) * vec2(1.0, 1.0)).y) && ! ((_GLF_IDENTITY(pixel, (_GLF_IDENTITY(pixel, (_GLF_IDENTITY(pixel, (pixel) + vec2(0.0, 0.0))) + vec2(0.0, 0.0))) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))).x - (_GLF_IDENTITY(setting, (_GLF_IDENTITY(setting, vec3(0.0, 0.0, 0.0) + (_GLF_IDENTITY(setting, vec3(0.0, 0.0, 0.0) + (setting))))) + vec3(0.0, 0.0, 0.0)).x - _GLF_IDENTITY(setting, vec3(0.0, 0.0, 0.0) + (setting)).y) < square.x && pixel.x + (_GLF_IDENTITY(setting, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (setting)).x - setting.y) > square.x && pixel.y - (setting.x - setting.y) < _GLF_IDENTITY(square, vec2(1.0, 1.0) * (square)).y && pixel.y + (setting.x - setting.y) > square.y)))
        return colorFunc(setting.z / 40.0);
    return vec3(0.0);
}
vec4 psh(in R r, in vec4 sph)
{
    {
        vec2 GLF_live2square = _GLF_FUZZED((+ ((PI / vec2(832.591, 5454.2688)) + (- PI))));
        vec3 GLF_live2setting = _GLF_FUZZED((+ vec4(N, PI, vec2(5495.1659, 636.022)).xyz));
        vec2 GLF_live2pixel = _GLF_FUZZED(vec2((vec4(-3426.0544, N, vec2(-640.654, -0.3)) / (PI))));
        {
            if((GLF_live2pixel.x - (GLF_live2setting.x) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x) > GLF_live2square.y) && ! ((GLF_live2pixel.x - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.y)))
                GLF_live2colorFunc(GLF_live2setting.z / 40.0);
            vec3(0.0);
        }
    }
    if(_GLF_DEAD(_GLF_IDENTITY(_GLF_FALSE(_GLF_IDENTITY(false, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED((resolution.tsts != vec4(time, vec2(47.84, 95.00), PI))) : _GLF_IDENTITY(false, (true ? false : _GLF_FUZZED((bvec4(false, false, true, false) != bvec4(true, true, true, false))))))), (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, (injectionSwitch) + vec2(0.0, 0.0)).y)), (true ? _GLF_FALSE(false, (_GLF_IDENTITY(injectionSwitch, _GLF_IDENTITY(vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)), (vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))) + vec2(0.0, 0.0)) * (injectionSwitch)).x > injectionSwitch.y)) : _GLF_FUZZED(bool(time))))))
        {
            {
                vec2 GLF_live2square = _GLF_FUZZED(vec2(((N) * (PI + N))));
                vec3 GLF_live2setting = _GLF_FUZZED(vec3(364.228, 1486.9927, 6.7));
                vec2 GLF_live2pixel = _GLF_FUZZED((vec2((PI)) - (vec2(PI, 3769.8116) * PI)));
                {
                    if((GLF_live2pixel.x - (GLF_live2setting.x) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x) > GLF_live2square.y) && ! ((GLF_live2pixel.x - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.y)))
                        GLF_live2colorFunc(GLF_live2setting.z / 40.0);
                    vec3(0.0);
                }
            }
            return vec4(1.0);
        }
    vec3 oc = r.o - sph.xyz;
    if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
        {
            {
                vec3 GLF_live0p = _GLF_FUZZED((- vec3((PI), PI, (PI))));
                const float GLF_live0FORMUPARAM = _GLF_FUZZED(N);
                const int GLF_live0INNER_ITERS = _GLF_FUZZED((((-391 / -71472) + (-89053 - -21581))));
                for(
                    int GLF_live0i = 0;
                    GLF_live0i < GLF_live0INNER_ITERS;
                    GLF_live0i ++
                )
                    {
                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                    }
            }
            return vec4(_GLF_IDENTITY(1.0, (_GLF_IDENTITY(1.0, (true ? 1.0 : _GLF_FUZZED(mix(time, N, PI))))) + _GLF_ZERO(0.0, injectionSwitch.x)));
        }
    float b = dot(oc, r.d);
    float c = dot(oc, oc) - _GLF_IDENTITY(sph, _GLF_IDENTITY(vec4(0.0, 0.0, 0.0, 0.0), (vec4(0.0, 0.0, 0.0, 0.0)) * vec4(1.0, 1.0, 1.0, 1.0)) + (_GLF_IDENTITY(sph, (_GLF_IDENTITY(sph, (sph) + vec4(0.0, 0.0, 0.0, 0.0))) + vec4(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))))).w * sph.w;
    float h = b * b - c;
    if(h < 0.0)
        {
            if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
                {
                    if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
                        {
                            vec2 donor_replacementuv = _GLF_FUZZED(clamp((step(b, _GLF_IDENTITY(vec2(-96.98, -7564.6737), vec2(1.0, 1.0) * (_GLF_IDENTITY(vec2(-96.98, -7564.6737), vec2(1.0, 1.0) * (_GLF_IDENTITY(vec2(-96.98, -7564.6737), (vec2(-96.98, -7564.6737)) + vec2(0.0, 0.0))))))) + (vec2(2.5, 36.30) / injectionSwitch)), (_GLF_IDENTITY((c >= c) ? mouse.r : (true ? h : PI), _GLF_ZERO(0.0, injectionSwitch.x) + ((c >= _GLF_IDENTITY(c, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(ceil(step(PI, b))) : c))) ? mouse.r : _GLF_IDENTITY((_GLF_IDENTITY(true, false || (_GLF_IDENTITY(true, _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) && (true)))) ? h : PI), (_GLF_IDENTITY((_GLF_IDENTITY(true, false || (_GLF_IDENTITY(true, _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) && (true)))) ? h : PI), (true ? _GLF_IDENTITY((_GLF_IDENTITY(true, false || (_GLF_IDENTITY(true, _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) && (true)))) ? h : PI), ((_GLF_IDENTITY(true, false || (_GLF_IDENTITY(true, _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) && (true)))) ? h : PI)) + 0.0) : _GLF_FUZZED(injectionSwitch.t)))) + 0.0)))), oc.b));
                            mat2 donor_replacementma = _GLF_FUZZED((dot((_GLF_IDENTITY(h, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(h, 0.0 + (h)) : _GLF_FUZZED((++ oc[0])))) - b), PI) / (fract(b) + (+ mat2(8399.0073, -2.4, -50.05, 44.28)))));
                            {
                                vec4 GLF_live2gl_FragColor = _GLF_FUZZED((+ ((vec4(-3558.3859, -4.4, 298.488, 986.579) * mat4(433.201, 93.59, -4.5, 633.777, -55.19, 5248.2238, 1.0, 9.1, 5.3, 98.60, -93.34, 8.6, 1918.9167, 5926.1970, -725.134, -678.266)) * vec3(-9.6, 2.2, -2257.5892).yyxx)));
                                vec4 GLF_live2gl_FragCoord = _GLF_FUZZED((((vec4(6.1, -0.6, -24.04, -2.6) * mat4(-5523.5433, 395.708, 8.8, 4.9, -156.676, 588.751, -8.9, 7255.0973, -9.6, -9505.9550, -7.5, 0.7, -5.2, 1.1, 1962.9027, -5355.1263))) - (mat4(PI) * (+ vec4(-989.691, 6.8, -166.923, -5767.0940)))));
                                {
                                    float GLF_live2angle = sin(GLF_live2time) * 0.1;
                                    mat2 GLF_live2rotation = mat2(cos(GLF_live2angle), - sin(GLF_live2angle), sin(GLF_live2angle), cos(GLF_live2angle));
                                    vec2 GLF_live2aspect = GLF_live2resolution.xy / min(GLF_live2resolution.x, GLF_live2resolution.y);
                                    vec2 GLF_live2position = (GLF_live2gl_FragCoord.xy / GLF_live2resolution.xy) * GLF_live2aspect;
                                    vec2 GLF_live2center = vec2(0.5) * GLF_live2aspect;
                                    GLF_live2position *= GLF_live2rotation;
                                    GLF_live2center *= GLF_live2rotation;
                                    vec3 GLF_live2color = vec3(0.0);
                                    for(
                                        int GLF_live2i = 0;
                                        GLF_live2i < 40;
                                        GLF_live2i ++
                                    )
                                        {
                                            vec3 GLF_live2d = GLF_live2drawShape(GLF_live2position, GLF_live2center + vec2(sin(float(GLF_live2i) / 10.0 + GLF_live2time) / 4.0, 0.0), vec3(0.01 + sin(float(GLF_live2i) / 100.0), 0.01, float(GLF_live2i)));
                                            if(length(GLF_live2d) != 0.0)
                                                GLF_live2color = GLF_live2d;
                                        }
                                    GLF_live2gl_FragColor = vec4(GLF_live2color, 1.0);
                                }
                            }
                            int donor_replacementi = _GLF_FUZZED(int(normalize((_GLF_IDENTITY(false, (_GLF_IDENTITY(false, (false ? _GLF_FUZZED((true)) : false))) && true) ? -9760.9810 : _GLF_IDENTITY(-8.5, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? -8.5 : _GLF_FUZZED((++ donor_replacementuv.r))))))));
                            const float donor_replacementFORMUPARAM = _GLF_FUZZED(_GLF_IDENTITY(((+ 4.8) / (vec2(7851.2243, -47.84) + -4.0)).g, (_GLF_IDENTITY(((+ 4.8) / (vec2(7851.2243, -47.84) + -4.0)).g, 1.0 * ((_GLF_IDENTITY((+ 4.8), (true ? (+ 4.8) : _GLF_FUZZED(350.162))) / (vec2(7851.2243, -47.84) + -4.0)).g))) * 1.0));
                            {
                                const int GLF_live0ITERATIONS = _GLF_FUZZED((+ ((true ? -53578 : -78754))));
                                float GLF_live0t = _GLF_FUZZED(float(int((312.060 + -2.5))));
                                mat2 GLF_live0ma = _GLF_FUZZED((mat2(vec2(-99.96, -1.8).g) + (- (mat2(-6500.2221, -4.3, -96.82, 772.317) + donor_replacementFORMUPARAM))));
                                float GLF_live0c = _GLF_FUZZED(N);
                                vec2 GLF_live0uv = _GLF_FUZZED(((vec2(-6.0, 3227.3978).xy / (vec2(6.8, -335.751) / vec2(-3.3, 7.0)))));
                                const float GLF_live0FORMUPARAM = _GLF_FUZZED(((- 90.97)));
                                float GLF_live0v1 = _GLF_FUZZED((vec4(vec3(-2911.8075, -9.2, 0.8), PI)).g);
                                const int GLF_live0INNER_ITERS = _GLF_FUZZED((((-76534 * 21415) / (- 74768)) - ((true ? -94888 : -1672) + ivec4(-32179, 74551, 19464, 40583).q)));
                                float GLF_live0v2 = _GLF_FUZZED((((PI - -1.3) - (PI + PI)) * N));
                                for(
                                    int GLF_live0i = 0;
                                    GLF_live0i < GLF_live0ITERATIONS;
                                    GLF_live0i ++
                                )
                                    {
                                        float GLF_live0s = float(GLF_live0i) * .035;
                                        vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                                        GLF_live0p.xy *= GLF_live0ma;
                                        GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                                        for(
                                            int GLF_live0i = 0;
                                            GLF_live0i < GLF_live0INNER_ITERS;
                                            GLF_live0i ++
                                        )
                                            {
                                                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                            }
                                        GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                                        GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                                        GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                                    }
                            }
                            const int donor_replacementINNER_ITERS = _GLF_FUZZED(_GLF_IDENTITY((ivec4(-12554, -54784, ivec2(41376, _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(-5023) : _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(51838) : _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(-20105) : -96225)))))))).w + (-34712 / ivec3(9097, -57882, 60165)).r), (false ? _GLF_FUZZED(((-188.457 == donor_replacementFORMUPARAM) ? (33433 * 67758) : (- 30440))) : _GLF_IDENTITY((ivec4(-12554, -54784, ivec2(41376, _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(-5023) : _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(51838) : _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(-20105) : -96225)))))))).w + (-34712 / ivec3(9097, -57882, 60165)).r), (_GLF_IDENTITY((ivec4(-12554, -54784, ivec2(41376, _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(-5023) : _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(51838) : _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(-20105) : -96225)))))))).w + (-34712 / ivec3(9097, -57882, 60165)).r), ((ivec4(-12554, -54784, ivec2(41376, _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(-5023) : _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(51838) : _GLF_IDENTITY(-96225, (false ? _GLF_FUZZED(-20105) : -96225)))))))).w + (_GLF_IDENTITY(-34712, (_GLF_IDENTITY(true, true && (true)) ? _GLF_IDENTITY(-34712, 1 * (-34712)) : _GLF_FUZZED(((11016 + -32659))))) / ivec3(9097, -57882, 60165)).r)) * 1)) * 1))));
                            {
                                const int GLF_live0ITERATIONS = _GLF_FUZZED((+ donor_replacementINNER_ITERS));
                                float GLF_live0t = _GLF_FUZZED(PI);
                                mat2 GLF_live0ma = _GLF_FUZZED(mat2(mat3(mat4(mat3(926.878, 178.989, -8691.1314, 9316.0870, -3498.7615, 74.15, -3.2, 948.169, -1897.6143)))));
                                float GLF_live0c = _GLF_FUZZED((+ (- vec3(8926.2791, 53.72, 44.91).y)));
                                vec2 GLF_live0uv = _GLF_FUZZED(vec2(1.4, 14.39));
                                const float GLF_live0FORMUPARAM = _GLF_FUZZED((+ (+ vec3(370.172, -1473.5425, -923.521)).y));
                                float GLF_live0v1 = _GLF_FUZZED((- (vec2(-75.48, 0.7).g * (+ PI))));
                                const int GLF_live0INNER_ITERS = _GLF_FUZZED(int((bvec4(true, bvec3(false, false, false)) != bvec4(false, false, true, false))));
                                float GLF_live0v2 = _GLF_FUZZED(N);
                                for(
                                    int GLF_live0i = 0;
                                    GLF_live0i < GLF_live0ITERATIONS;
                                    GLF_live0i ++
                                )
                                    {
                                        float GLF_live0s = float(GLF_live0i) * .035;
                                        vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                                        GLF_live0p.xy *= GLF_live0ma;
                                        GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                                        for(
                                            int GLF_live0i = 0;
                                            GLF_live0i < GLF_live0INNER_ITERS;
                                            GLF_live0i ++
                                        )
                                            {
                                                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                            }
                                        GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                                        GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                                        GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                                    }
                            }
                            float donor_replacementv1 = _GLF_FUZZED(_GLF_IDENTITY((-- b), (_GLF_IDENTITY((-- b), (true ? (-- b) : _GLF_FUZZED(PI)))) * _GLF_ONE(1.0, injectionSwitch.y)));
                            float donor_replacementv2 = _GLF_FUZZED(mod((++ oc), (vec3(4782.4920, -2.3, -9.9) + c))[0]);
                            {
                                float s = float(_GLF_IDENTITY(donor_replacementi, (_GLF_IDENTITY(donor_replacementi, 0 + (donor_replacementi))) * int(_GLF_ONE(1.0, injectionSwitch.y)))) * .035;
                                {
                                    vec3 GLF_live0p = _GLF_FUZZED((((+ PI) / (- vec3(-72.96, -437.301, 9.2))) + (vec3(PI, vec2(50.73, 410.450)) * (vec3(537.973, -6932.2623, -0.1) * vec3(24.52, -0.8, -656.337)))));
                                    const float GLF_live0FORMUPARAM = _GLF_FUZZED(N);
                                    {
                                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                    }
                                }
                                vec3 p = _GLF_IDENTITY(s, (s) * 1.0) * vec3(_GLF_IDENTITY(donor_replacementuv, (donor_replacementuv) + vec2(0.0, 0.0)), _GLF_IDENTITY(1.0, (_GLF_IDENTITY(1.0, (true ? _GLF_IDENTITY(1.0, 0.0 + (1.0)) : _GLF_FUZZED(reflect(donor_replacementFORMUPARAM, b))))) + _GLF_ZERO(0.0, injectionSwitch.x)) + sin(time * .015));
                                p.xy *= donor_replacementma;
                                p += vec3(.22, _GLF_IDENTITY(.3, _GLF_ONE(1.0, injectionSwitch.y) * (_GLF_IDENTITY(.3, (.3) + 0.0))), _GLF_IDENTITY(s - 1.5, (_GLF_IDENTITY(s - 1.5, 0.0 + (s - 1.5))) * 1.0) - sin(b * .13) * _GLF_IDENTITY(.1, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(b) : _GLF_IDENTITY(.1, (_GLF_IDENTITY(.1, (false ? _GLF_FUZZED(donor_replacementv1) : .1))) * _GLF_ONE(1.0, injectionSwitch.y)))));
                                {
                                    float GLF_live0t = _GLF_FUZZED((vec4(-1.9, PI, vec2(2062.6782, -499.318)) - vec2(-13.38, 7826.3326).r).r);
                                    mat2 GLF_live0ma = _GLF_FUZZED(((-557.366 / vec3(9.5, -8.2, 4.1)).z + (mat2(5.9, N, 6951.6302, PI))));
                                    float GLF_live0c = _GLF_FUZZED(PI);
                                    int GLF_live0i = _GLF_FUZZED((+ (donor_replacementINNER_ITERS - ivec4(22160, 59368, 43222, -9535)).w));
                                    vec2 GLF_live0uv = _GLF_FUZZED(vec2(72.80, 3.3));
                                    const float GLF_live0FORMUPARAM = _GLF_FUZZED(-0.7);
                                    float GLF_live0v1 = _GLF_FUZZED((vec2(268.202, -569.550).rrg.t * N));
                                    const int GLF_live0INNER_ITERS = _GLF_FUZZED(int((PI + vec3(6901.7424, 7308.1401, -8903.6381)).s));
                                    float GLF_live0v2 = _GLF_FUZZED((N * (+ vec2(-5.3, -795.346))).r);
                                    {
                                        float GLF_live0s = float(GLF_live0i) * .035;
                                        vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                                        GLF_live0p.xy *= GLF_live0ma;
                                        GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                                        for(
                                            int GLF_live0i = 0;
                                            GLF_live0i < GLF_live0INNER_ITERS;
                                            GLF_live0i ++
                                        )
                                            {
                                                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                            }
                                        GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                                        GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                                        GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                                    }
                                }
                                for(
                                    int i = 0;
                                    i < donor_replacementINNER_ITERS;
                                    i ++
                                )
                                    {
                                        p = abs(p) / dot(p, p) - donor_replacementFORMUPARAM;
                                    }
                                donor_replacementv1 += dot(p, p) * .0015 * (2.8 + sin(length(donor_replacementuv.xy * 18.0) + .5 - _GLF_IDENTITY(b * .7, (false ? _GLF_FUZZED(length(abs(p))) : b * .7))));
                                donor_replacementv2 += dot(p, p) * .0025 * (1.5 + sin(length(donor_replacementuv.xy * 13.5) + 1.21 - _GLF_IDENTITY(b, 0.0 + (b)) * _GLF_IDENTITY(.3, (_GLF_IDENTITY(.3, (true ? .3 : _GLF_FUZZED(donor_replacementuv[0])))) + 0.0)));
                                h = length(p.xy * .3) * .85;
                                {
                                    vec3 GLF_live2d = _GLF_FUZZED(((+ vec3(-4.8)) * mat3(-3.6, -4.9, 9070.4314, 572.645, -5478.6463, -6.1, -1.0, 659.126, 7146.3948)));
                                    vec3 GLF_live2color = _GLF_FUZZED(vec3((PI * vec4(97.89, 38.88, -3.2, 661.552)).a));
                                    if(length(GLF_live2d) != 0.0)
                                        GLF_live2color = GLF_live2d;
                                }
                            }
                        }
                    {
                        vec2 GLF_live2center = _GLF_FUZZED((((- vec2(2208.0572, 5.2)) + (vec2(48.86, -3.9) * vec2(-1609.9640, 3.4))) * (- (mat2(8.1, 75.40, 677.563, 9.9) - PI))));
                        int GLF_live2i = _GLF_FUZZED((+ (+ (false ? -10259 : -74352))));
                        vec3 GLF_live2color = _GLF_FUZZED((vec3(-8962.4807, -184.438, 5317.8249) + 67.03));
                        vec2 GLF_live2position = _GLF_FUZZED(((- (vec2(142.610, 707.596) / vec2(4.0, 7.8))) / (vec4(3.2, -0.5, -8719.4804, -7894.4483).w)));
                        {
                            vec3 GLF_live2d = GLF_live2drawShape(GLF_live2position, GLF_live2center + vec2(sin(float(GLF_live2i) / 10.0 + GLF_live2time) / 4.0, 0.0), vec3(0.01 + sin(float(GLF_live2i) / 100.0), 0.01, float(GLF_live2i)));
                            if(length(GLF_live2d) != 0.0)
                                GLF_live2color = GLF_live2d;
                        }
                    }
                    return vec4(1.0);
                }
            return vec4(- 1);
        }
    if(_GLF_DEAD(_GLF_FALSE(_GLF_IDENTITY(false, (_GLF_IDENTITY(false, (false) && true)) && true), (injectionSwitch.x > injectionSwitch.y))))
        {
            vec2 donor_replacementsquare = _GLF_FUZZED(((smoothstep(injectionSwitch, vec2(818.085, 989.014), mouse) - clamp(mouse, h, -9096.0228))));
            {
                vec4 GLF_live0gl_FragColor = _GLF_FUZZED((- vec3(506.488, -20.01, 24.54)).xyz.bbbg);
                vec4 GLF_live0gl_FragCoord = _GLF_FUZZED(vec4(-88.47, -410.620, 478.300, -99.49));
                {
                    vec2 GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2;
                    const float GLF_live0ZOOM = 1.0;
                    const float GLF_live0FORMUPARAM = 0.59989;
                    const int GLF_live0ITERATIONS = 105;
                    const int GLF_live0INNER_ITERS = 10;
                    vec2 GLF_live0uv = (GLF_live0gl_FragCoord.xy / GLF_live0resolution.xy) - .5 / GLF_live0ZOOM;
                    float GLF_live0t = GLF_live0time * .10 + ((.25 + .05 * sin(GLF_live0time * .1)) / (length(GLF_live0uv.xy) + .07)) * 2.2;
                    GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.x = GLF_live0t;
                    float GLF_live0si = sin(GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.x);
                    float GLF_live0co = cos(GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.x);
                    mat2 GLF_live0ma = mat2(GLF_live0co, GLF_live0si, - GLF_live0si, GLF_live0co);
                    float GLF_live0c = 0.0;
                    float GLF_live0v1 = 0.0;
                    float GLF_live0v2 = 0.0;
                    GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.y = GLF_live0v2;
                    for(
                        int GLF_live0i = 0;
                        GLF_live0i < GLF_live0ITERATIONS;
                        GLF_live0i ++
                    )
                        {
                            float GLF_live0s = float(GLF_live0i) * .035;
                            vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                            GLF_live0p.xy *= GLF_live0ma;
                            GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.x * .13) * .1);
                            for(
                                int GLF_live0i = 0;
                                GLF_live0i < GLF_live0INNER_ITERS;
                                GLF_live0i ++
                            )
                                {
                                    GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                }
                            GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.x * .7));
                            GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.y += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.x * .3));
                            GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                        }
                    float GLF_live0len = length(GLF_live0uv);
                    GLF_live0v1 *= smoothstep(.7, .0, GLF_live0len);
                    GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.y *= smoothstep(.6, .0, GLF_live0len);
                    float GLF_live0re = clamp(GLF_live0c, 0.0, 1.0);
                    float GLF_live0gr = clamp((GLF_live0v1 + GLF_live0c) * .25, 0.0, 1.0);
                    float GLF_live0bl = clamp(GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2.y, 0.0, 1.0);
                    vec3 GLF_live0col = vec3(GLF_live0re, GLF_live0gr, GLF_live0bl) + smoothstep(0.15, .0, GLF_live0len) * 0.9;
                    GLF_live0gl_FragColor = vec4(GLF_live0col, 1.0);
                }
            }
            vec2 donor_replacementpixel = _GLF_FUZZED((ceil(mouse) / floor((c))));
            vec3 donor_replacementsetting = _GLF_FUZZED(cross(vec3(PI, vec3(89.06, -7.9, -8456.1829).b, oc.z), (_GLF_IDENTITY((oc / vec3(-1166.4457, -8.1, 7.4)), vec3(1.0, 1.0, 1.0) * (_GLF_IDENTITY((oc / vec3(-1166.4457, -8.1, 7.4)), ((_GLF_IDENTITY(oc / vec3(-1166.4457, _GLF_IDENTITY(-8.1, (true ? _GLF_IDENTITY(-8.1, (true ? -8.1 : _GLF_FUZZED(sph.x))) : _GLF_FUZZED((-- sph.b)))), 7.4), vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (oc / vec3(-1166.4457, _GLF_IDENTITY(-8.1, (true ? _GLF_IDENTITY(-8.1, (true ? -8.1 : _GLF_FUZZED(sph.x))) : _GLF_FUZZED((-- sph.b)))), 7.4))))) * vec3(1.0, 1.0, 1.0)))) + refract(time, h, b))));
            {
                if((donor_replacementpixel.x - (donor_replacementsetting.x) < donor_replacementsquare.x && donor_replacementpixel.x + (donor_replacementsetting.x) > donor_replacementsquare.x && _GLF_IDENTITY(donor_replacementpixel, (donor_replacementpixel) + _GLF_IDENTITY(vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)), (vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))) * vec2(1.0, 1.0))).y - (_GLF_IDENTITY(donor_replacementsetting, (_GLF_IDENTITY(donor_replacementsetting, (donor_replacementsetting) * vec3(1.0, 1.0, 1.0))) * vec3(1.0, 1.0, 1.0)).x) < donor_replacementsquare.y && donor_replacementpixel.y + (donor_replacementsetting.x) > donor_replacementsquare.y) && ! ((donor_replacementpixel.x - (donor_replacementsetting.x - donor_replacementsetting.y) < donor_replacementsquare.x && donor_replacementpixel.x + (donor_replacementsetting.x - donor_replacementsetting.y) > donor_replacementsquare.x && _GLF_IDENTITY(donor_replacementpixel, (_GLF_IDENTITY(donor_replacementpixel, (_GLF_IDENTITY(donor_replacementpixel, (donor_replacementpixel) + vec2(0.0, 0.0))) + vec2(0.0, 0.0))) + vec2(0.0, 0.0)).y - (donor_replacementsetting.x - donor_replacementsetting.y) < donor_replacementsquare.y && donor_replacementpixel.y + (donor_replacementsetting.x - donor_replacementsetting.y) > donor_replacementsquare.y)))
                    colorFunc(donor_replacementsetting.z / 40.0);
                vec3(0.0);
                {
                    float GLF_live2h = _GLF_FUZZED(((N * vec3(7436.6465, -70.59, -4.9)).z / (N / vec3(-290.205, 339.174, -6.2)).p));
                    {
                        GLF_live2hsbToRGB(fract(GLF_live2h), 1.0, (0.5 + (sin(GLF_live2time) * 0.5 + 0.5)));
                    }
                }
            }
            {
                vec4 GLF_live0gl_FragColor = _GLF_FUZZED(((vec4(N) / (true ? 3.1 : PI)) * float(int(N))));
                vec4 GLF_live0gl_FragCoord = _GLF_FUZZED((vec3(854.919, -5.5, 7.4).zyx.grbb + vec3(-3.5, -4.8, 1589.4740).sst.stst));
                {
                    const float GLF_live0ZOOM = 1.0;
                    const float GLF_live0FORMUPARAM = 0.59989;
                    const int GLF_live0ITERATIONS = 105;
                    const int GLF_live0INNER_ITERS = 10;
                    vec2 GLF_live0uv = (GLF_live0gl_FragCoord.xy / GLF_live0resolution.xy) - .5 / GLF_live0ZOOM;
                    float GLF_live0t = GLF_live0time * .10 + ((.25 + .05 * sin(GLF_live0time * .1)) / (length(GLF_live0uv.xy) + .07)) * 2.2;
                    float GLF_live0si = sin(GLF_live0t);
                    float GLF_live0co = cos(GLF_live0t);
                    mat2 GLF_live0ma = mat2(GLF_live0co, GLF_live0si, - GLF_live0si, GLF_live0co);
                    float GLF_live0c = 0.0;
                    float GLF_live0v1 = 0.0;
                    float GLF_live0v2 = 0.0;
                    for(
                        int GLF_live0i = 0;
                        GLF_live0i < GLF_live0ITERATIONS;
                        GLF_live0i ++
                    )
                        {
                            float GLF_live0s = float(GLF_live0i) * .035;
                            vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                            GLF_live0p.xy *= GLF_live0ma;
                            GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                            for(
                                int GLF_live0i = 0;
                                GLF_live0i < GLF_live0INNER_ITERS;
                                GLF_live0i ++
                            )
                                {
                                    GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                }
                            GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                            GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                            GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                        }
                    float GLF_live0len = length(GLF_live0uv);
                    GLF_live0v1 *= smoothstep(.7, .0, GLF_live0len);
                    GLF_live0v2 *= smoothstep(.6, .0, GLF_live0len);
                    float GLF_live0re = clamp(GLF_live0c, 0.0, 1.0);
                    float GLF_live0gr = clamp((GLF_live0v1 + GLF_live0c) * .25, 0.0, 1.0);
                    float GLF_live0bl = clamp(GLF_live0v2, 0.0, 1.0);
                    vec3 GLF_live0col = vec3(GLF_live0re, GLF_live0gr, GLF_live0bl) + smoothstep(0.15, .0, GLF_live0len) * 0.9;
                    GLF_live0gl_FragColor = vec4(GLF_live0col, 1.0);
                }
            }
        }
    {
        vec3 GLF_live0p = _GLF_FUZZED((vec4(672.287, 7.9, 1.7, -4.9).zwzx - vec3(45.12, -4.7, -1432.7532).z).abr);
        const float GLF_live0FORMUPARAM = _GLF_FUZZED((+ ((-989.540 / PI) - PI)));
        const int GLF_live0INNER_ITERS = _GLF_FUZZED((((false ? bvec2(false, true) : bvec2(true, false)) == bvec2(true, true)) ? (-50380 + ivec4(42580, -89071, -83516, -52445)).y : int((N * PI))));
        for(
            int GLF_live0i = 0;
            GLF_live0i < GLF_live0INNER_ITERS;
            GLF_live0i ++
        )
            {
                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
            }
    }
    h = sqrt(h);
    {
        vec2 GLF_live2square = _GLF_FUZZED(vec2((vec4(8.1, 9.3, -45.56, -600.433).qsq * (vec3(833.708, 3153.9224, -7716.3821) * mat3(1.3, 3.7, -7156.2158, -6698.6587, 8.2, -46.57, -2.6, -4.9, 0.8)))));
        vec3 GLF_live2setting = _GLF_FUZZED((+ ((true ? -700.235 : PI) / (- vec3(-2.3, 416.211, -3682.9355)))));
        vec2 GLF_live2pixel = _GLF_FUZZED(vec2(PI));
        if((GLF_live2pixel.x - (GLF_live2setting.x) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x) > GLF_live2square.y) && ! ((GLF_live2pixel.x - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.y)))
            GLF_live2colorFunc(GLF_live2setting.z / 40.0);
    }
    return vec4(- b - h, normalize((r.o + r.d * (- _GLF_IDENTITY(b, _GLF_ONE(1.0, injectionSwitch.y) * (_GLF_IDENTITY(b, (false ? _GLF_FUZZED(b) : b)))) - h)) - sph.xyz));
}
vec4 hitPlane(in R r, in vec3 c, in vec3 n)
{
    float l = - dot(r.o - c, n) / dot(r.d, n);
    if(l < _GLF_IDENTITY(0.0, 1.0 * (_GLF_IDENTITY(0.0, (0.0) + 0.0))))
        {
            return vec4(_GLF_IDENTITY(- 1, 0 + (_GLF_IDENTITY(- 1, (_GLF_IDENTITY(- 1, (true ? - 1 : _GLF_FUZZED(-58949)))) * 1))));
            if(_GLF_DEAD(_GLF_FALSE(_GLF_IDENTITY(false, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(false, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? false : _GLF_FUZZED((mat3(-17.19, 1.0, -42.78, -1680.0406, -3523.0693, 7207.9219, -16.76, -3.6, 3.6) == mat3(-43.40, 6.2, 381.901, 6.7, -5197.1786, 5329.7895, -29.87, 334.706, -4.2))))) : _GLF_FUZZED((ivec4(62497, ivec2(52768, -27900), 6262) == (ivec4(-20514, -9773, -84500, -52811) + -7121))))), (injectionSwitch.x > injectionSwitch.y))))
                return vec4(_GLF_IDENTITY(1.0, 1.0 * (_GLF_IDENTITY(1.0, 0.0 + (1.0)))));
        }
    return vec4(l, n);
    {
        const int GLF_live0ITERATIONS = _GLF_FUZZED((+ ((73931 + 77138))));
        float GLF_live0t = _GLF_FUZZED((N + N));
        mat2 GLF_live0ma = _GLF_FUZZED((((- mat2(74.54, 1.9, 516.899, 65.15)) + (mat2(97.48, -7.0, -1937.8073, -2133.2051)))));
        float GLF_live0c = _GLF_FUZZED((+ ((+ N) - vec3(2246.3562, -2.3, -2282.7914).t)));
        vec2 GLF_live0uv = _GLF_FUZZED(vec2(vec4((false ? vec2(449.854, 6.9) : vec2(-60.99, -3.7)), (PI + N), (-5127.4957 / PI))));
        const float GLF_live0FORMUPARAM = _GLF_FUZZED(PI);
        float GLF_live0v1 = _GLF_FUZZED(float((+ (-30722 * -29156))));
        const int GLF_live0INNER_ITERS = _GLF_FUZZED((int(-6072.8121)));
        float GLF_live0v2 = _GLF_FUZZED((true ? vec2(-8.2, -492.806) : vec2(13.55, 64.99)).ttt.b);
        for(
            int GLF_live0i = 0;
            GLF_live0i < GLF_live0ITERATIONS;
            GLF_live0i ++
        )
            {
                float GLF_live0s = float(GLF_live0i) * .035;
                vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                GLF_live0p.xy *= GLF_live0ma;
                GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                for(
                    int GLF_live0i = 0;
                    GLF_live0i < GLF_live0INNER_ITERS;
                    GLF_live0i ++
                )
                    {
                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                    }
                GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                GLF_live0c = length(GLF_live0p.xy * .3) * .85;
            }
    }
}
S opU(out S hd, in vec4 s1, in vec4 s2, in vec2 ca)
{
    {
        const int GLF_live0ITERATIONS = _GLF_FUZZED(-1959);
        float GLF_live0t = _GLF_FUZZED(float(((-72666))));
        mat2 GLF_live0ma = _GLF_FUZZED(((mat2(9.4, -15.83, 212.024, 97.44) + -1.0) - (mat2(mat4(79.42, -7.6, -2723.9096, -0.0, -6.5, 93.91, 5410.9327, 246.016, -63.25, -931.546, -61.26, 6.0, -0.7, -4483.6831, 2501.1031, -668.963)) - (- mat2(3.7, 4.2, -8.7, -34.76)))));
        float GLF_live0c = _GLF_FUZZED((vec4(PI, vec2(4.5, 779.021), N) / (PI + vec4(-0.6, -0.9, -8.0, -79.52))).s);
        vec2 GLF_live0uv = _GLF_FUZZED(vec2(4.6, 4.9));
        const float GLF_live0FORMUPARAM = _GLF_FUZZED(vec3((N * PI), (vec2(-495.184, 1.5) + vec2(-24.46, -58.43))).b);
        float GLF_live0v1 = _GLF_FUZZED(float(((PI / PI) < N)));
        const int GLF_live0INNER_ITERS = _GLF_FUZZED(int(((PI - 1651.9948) / float(-88621))));
        float GLF_live0v2 = _GLF_FUZZED(N);
        for(
            int GLF_live0i = 0;
            GLF_live0i < GLF_live0ITERATIONS;
            GLF_live0i ++
        )
            {
                float GLF_live0s = float(GLF_live0i) * .035;
                vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                GLF_live0p.xy *= GLF_live0ma;
                GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                for(
                    int GLF_live0i = 0;
                    GLF_live0i < GLF_live0INNER_ITERS;
                    GLF_live0i ++
                )
                    {
                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                    }
                GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                GLF_live0c = length(GLF_live0p.xy * .3) * .85;
            }
    }
    if((_GLF_IDENTITY(s1, (_GLF_IDENTITY(s1, vec4(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (_GLF_IDENTITY(s1, vec4(1.0, 1.0, 1.0, 1.0) * (s1))))) * vec4(1.0, 1.0, 1.0, 1.0)).x < _GLF_IDENTITY(s2, vec4(0.0, 0.0, 0.0, 0.0) + (s2)).x || s2.x <= 0.0) && s1.x > _GLF_IDENTITY(0.0, (_GLF_IDENTITY(0.0, (_GLF_IDENTITY(0.0, 1.0 * (0.0))) + 0.0)) * 1.0))
        {
            hd.c = ca.x;
            hd.d = s1.x;
            hd.n = s1.yzw;
        }
    else
        if((_GLF_IDENTITY(s2, vec4(0.0, 0.0, 0.0, 0.0) + (_GLF_IDENTITY(s2, vec4(1.0, 1.0, 1.0, 1.0) * (s2)))).x < _GLF_IDENTITY(s1, (_GLF_IDENTITY(s1, (s1) * vec4(1.0, 1.0, 1.0, 1.0))) * vec4(1.0, 1.0, 1.0, 1.0)).x || s1.x <= 0.0) && s2.x > 0.0)
            {
                {
                    float GLF_live2h = _GLF_FUZZED(N);
                    {
                        GLF_live2hsbToRGB(fract(GLF_live2h), 1.0, (0.5 + (sin(GLF_live2time) * 0.5 + 0.5)));
                    }
                }
                hd.c = ca.y;
                hd.d = s2.x;
                if(_GLF_DEAD(_GLF_IDENTITY(_GLF_IDENTITY(false, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(false, true && (_GLF_IDENTITY(false, (true ? false : _GLF_FUZZED(false))))) : _GLF_FUZZED((distance(mouse, vec2(-845.483, -860.643)) != distance(mouse, resolution))))), _GLF_TRUE(_GLF_IDENTITY(true, (false ? _GLF_FUZZED(bvec4(bvec2(true, true), false, false).z) : _GLF_IDENTITY(true, (true) || _GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))), (injectionSwitch.x < injectionSwitch.y)) && (false))))
                    {
                        vec2 donor_replacementuv = _GLF_FUZZED(mix(faceforward(refract(vec2(_GLF_IDENTITY(-9.5, 0.0 + (-9.5)), -2438.8900), injectionSwitch, _GLF_IDENTITY(time, (true ? time : _GLF_FUZZED(float(ivec3(73553, 28197, 67005)[1]))))), _GLF_IDENTITY((3.6 + mouse), ((3.6 + mouse)) * vec2(1.0, 1.0)), (ca --)), min(mat2(1.3, -6.5, 89.06, -1193.9143)[0], (+ vec2(-6.6, 2389.1711))), smoothstep(clamp(_GLF_IDENTITY(-5.0, _GLF_ZERO(0.0, injectionSwitch.x) + (-5.0)), time, -9.6), mod(PI, PI), mat2(-8131.9729, -7139.7320, 2362.2703, -83.74)[0])));
                        float donor_replacementc = _GLF_FUZZED(dot(smoothstep(normalize(_GLF_IDENTITY(time, 0.0 + (_GLF_IDENTITY(time, (time) + 0.0)))), distance(vec3(126.342, -6973.3495, -22.61), vec3(_GLF_IDENTITY(-5213.6900, (false ? _GLF_FUZZED(N) : _GLF_IDENTITY(-5213.6900, (false ? _GLF_FUZZED(refract(PI, N, time)) : _GLF_IDENTITY(-5213.6900, 1.0 * (-5213.6900)))))), _GLF_IDENTITY(7.6, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(7.6, (7.6) * 1.0) : _GLF_FUZZED(vec3(time, ca).s))), 496.516)), (-- ca)), (abs(resolution) - vec3(_GLF_IDENTITY(7.3, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(7.3, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(length(ca)) : 7.3)) : _GLF_FUZZED((length(vec3(4.1, -8.2, 4.3)) + ca.x)))), -3506.8899, _GLF_IDENTITY(-3.7, _GLF_ONE(1.0, injectionSwitch.y) * (-3.7))).zx)));
                        mat2 donor_replacementma = _GLF_FUZZED(((ca[1]) + (length(138.535) / mat2(time, time, time, PI))));
                        float donor_replacementt = _GLF_FUZZED((s1.g / (ca[1] ++)));
                        int donor_replacementi = _GLF_FUZZED((+ ivec3(15967, -82920, -28627)).y);
                        const int donor_replacementINNER_ITERS = _GLF_FUZZED(_GLF_IDENTITY((_GLF_IDENTITY(ivec2(-91481, 48980).tttt.a, 1 * (ivec2(-91481, 48980).tttt.a)) / (_GLF_IDENTITY((bvec2(false, true) != bvec2(false, true)) ? 80901 : ivec2(8326, -3426).r, ((bvec2(false, true) != bvec2(false, true)) ? 80901 : ivec2(8326, -3426).r) + 0))), (_GLF_IDENTITY((_GLF_IDENTITY(ivec2(-91481, 48980).tttt.a, 1 * (ivec2(-91481, 48980).tttt.a)) / (_GLF_IDENTITY((bvec2(false, true) != bvec2(false, true)) ? 80901 : ivec2(8326, -3426).r, ((bvec2(false, true) != bvec2(false, true)) ? 80901 : ivec2(8326, -3426).r) + 0))), ((_GLF_IDENTITY(ivec2(-91481, 48980).tttt.a, 1 * (ivec2(-91481, 48980).tttt.a)) / (_GLF_IDENTITY((bvec2(false, true) != bvec2(false, true)) ? 80901 : ivec2(8326, -3426).r, ((bvec2(false, true) != bvec2(false, true)) ? 80901 : ivec2(8326, -3426).r) + 0)))) * 1)) * 1));
                        float donor_replacementv1 = _GLF_FUZZED(distance(max(_GLF_IDENTITY(s1, _GLF_IDENTITY(vec4(1.0, 1.0, 1.0, 1.0), vec4(1.0, 1.0, 1.0, 1.0) * (vec4(1.0, 1.0, 1.0, 1.0))) * (s1)).ptt, dot(_GLF_IDENTITY(vec3(42.67, 229.528, -48.47), vec3(1.0, 1.0, 1.0) * (vec3(_GLF_IDENTITY(42.67, 1.0 * (_GLF_IDENTITY(42.67, (true ? 42.67 : _GLF_FUZZED(normalize(PI)))))), 229.528, -48.47))), vec3(6658.1755, _GLF_IDENTITY(-9.1, (-9.1) * 1.0), 4.1))), sign((vec3(-2.2, -35.32, _GLF_IDENTITY(-123.708, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(-123.708, (-123.708) + _GLF_ZERO(0.0, injectionSwitch.x)) : _GLF_FUZZED(mix(vec3(-69.96, -6.0, -5.0), vec3(-5240.6667, 9.6, 1889.9082), vec3(-4731.9081, 7.1, 8171.2912)).p)))) * vec3(264.231, -5592.9634, 952.052)))));
                        {
                            vec2 GLF_live2square = _GLF_FUZZED(vec2((vec4(vec2(-9.7, 6633.8089), PI, N))));
                            vec3 GLF_live2setting = _GLF_FUZZED(((1.9 / vec2(0.5, 9.8)).x * ((-7603.2308 - vec3(-3.7, -62.50, -21.65)) - float(true))));
                            vec2 GLF_live2pixel = _GLF_FUZZED((PI + ((false ? vec2(1926.3720, 1.3) : vec2(-87.97, -9.1)) - (289.379 + vec2(70.53, -5602.9416)))));
                            {
                                if((GLF_live2pixel.x - (GLF_live2setting.x) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x) > GLF_live2square.y) && ! ((GLF_live2pixel.x - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.y)))
                                    GLF_live2colorFunc(GLF_live2setting.z / 40.0);
                                vec3(0.0);
                            }
                        }
                        float donor_replacementv2 = _GLF_FUZZED(distance(clamp((s2), distance(vec4(_GLF_IDENTITY(-6747.8427, 1.0 * (_GLF_IDENTITY(-6747.8427, 1.0 * (-6747.8427)))), -1.4, -3.7, _GLF_IDENTITY(0.7, (_GLF_IDENTITY(0.7, (false ? _GLF_FUZZED((++ donor_replacementt)) : 0.7))) * 1.0)), vec4(-791.116, -8.3, 126.357, 7915.1192)), distance(_GLF_IDENTITY(vec2(-6770.5793, 7.1), (_GLF_IDENTITY(vec2(-6770.5793, 7.1), (vec2(-6770.5793, 7.1)) + vec2(0.0, 0.0))) * _GLF_IDENTITY(vec2(1.0, 1.0), (vec2(1.0, 1.0)) + vec2(0.0, 0.0))), injectionSwitch)), smoothstep(sign(_GLF_IDENTITY(PI, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(PI, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(PI, (true ? PI : _GLF_FUZZED(N))) : _GLF_FUZZED(mod(PI, 31.42)))) : _GLF_FUZZED(abs(donor_replacementuv[1]))))), (true ? _GLF_IDENTITY(PI, (true ? _GLF_IDENTITY(PI, (false ? _GLF_FUZZED(smoothstep(time, time, donor_replacementc)) : PI)) : _GLF_FUZZED(distance((donor_replacementc * vec2(313.650, -32.60)), (vec2(895.116, -8.4) + donor_replacementuv))))) : _GLF_IDENTITY(time, (time) + _GLF_IDENTITY(0.0, 1.0 * (0.0)))), sign(s1))));
                        {
                            float s = float(donor_replacementi) * .035;
                            vec3 p = s * vec3(donor_replacementuv, _GLF_IDENTITY(1.0, 1.0 * (_GLF_IDENTITY(1.0, (false ? _GLF_FUZZED(smoothstep(donor_replacementv1, donor_replacementt, N)) : 1.0)))) + sin(time * .015));
                            p.xy *= donor_replacementma;
                            {
                                vec3 GLF_live0p = _GLF_FUZZED((vec3(2635.1461, -2766.1165, 316.453) / ((N / PI) * (vec3(-0.6, -6202.9371, -2.3) * mat3(60.75, 6.0, 889.515, -2.7, -941.825, -2826.3449, -2.7, 576.999, -7.6)))));
                                const float GLF_live0FORMUPARAM = _GLF_FUZZED((true ? vec4(-1.6, 7.2, 50.18, -7.7) : vec4(-2.5, 94.80, 9868.6965, -1.3)).xwww.x);
                                const int GLF_live0INNER_ITERS = _GLF_FUZZED(((+ donor_replacementINNER_ITERS) * (ivec2(-41803, 95874))).x);
                                for(
                                    int GLF_live0i = 0;
                                    GLF_live0i < GLF_live0INNER_ITERS;
                                    GLF_live0i ++
                                )
                                    {
                                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                    }
                            }
                            p += vec3(.22, .3, _GLF_IDENTITY(s, (false ? _GLF_FUZZED((donor_replacementt)) : s)) - 1.5 - sin(_GLF_IDENTITY(donor_replacementt, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(fract(distance(p, vec3(-519.903, 6.2, 2.1)))) : donor_replacementt)) * .13) * .1);
                            for(
                                int i = 0;
                                i < donor_replacementINNER_ITERS;
                                i ++
                            )
                                {
                                    p = abs(p) / dot(p, p) - PI;
                                }
                            donor_replacementv1 += dot(p, p) * .0015 * (2.8 + sin(length(donor_replacementuv.xy * 18.0) + .5 - donor_replacementt * .7));
                            donor_replacementv2 += dot(p, p) * .0025 * (1.5 + sin(length(donor_replacementuv.xy * 13.5) + 1.21 - donor_replacementt * .3));
                            donor_replacementc = length(_GLF_IDENTITY(p, (p) + vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))).xy * _GLF_IDENTITY(.3, (false ? _GLF_FUZZED(dot((p --), (donor_replacementv1 / vec3(-1005.9999, -3.6, 5.5)))) : _GLF_IDENTITY(.3, (.3) + 0.0)))) * _GLF_IDENTITY(.85, (_GLF_IDENTITY(.85, 1.0 * (.85))) + 0.0);
                            {
                                vec2 GLF_live2center = _GLF_FUZZED(((vec2(-2.6, -7935.6324).ts / (false ? 83.64 : PI)) + N));
                                vec3 GLF_live2color = _GLF_FUZZED(((false ? vec4(997.669, 667.154, -8968.6289, -54.89) : vec4(-6672.5289, 2266.1787, -3.2, -70.42)) * vec3(-5009.1321, -2.0, 36.40).ggrb).pqp);
                                vec2 GLF_live2position = _GLF_FUZZED(((563.781 * N) * (vec2(-163.144, 25.91))).tt);
                                for(
                                    int GLF_live2i = 0;
                                    GLF_live2i < 40;
                                    GLF_live2i ++
                                )
                                    {
                                        vec3 GLF_live2d = GLF_live2drawShape(GLF_live2position, GLF_live2center + vec2(sin(float(GLF_live2i) / 10.0 + GLF_live2time) / 4.0, 0.0), vec3(0.01 + sin(float(GLF_live2i) / 100.0), 0.01, float(GLF_live2i)));
                                        if(length(GLF_live2d) != 0.0)
                                            GLF_live2color = GLF_live2d;
                                    }
                            }
                        }
                        {
                            vec2 GLF_live2center = _GLF_FUZZED((+ ((vec4(6.4, -970.694, -6.1, 2.2) == vec4(-669.156, -88.05, -3.8, -1041.2666)) ? (+ vec2(6.6, 51.85)) : (vec2(-2.1, -0.9) * vec2(1584.5247, -1453.6449)))));
                            int GLF_live2i = _GLF_FUZZED(ivec2(ivec2(-69941, 31914).r).g);
                            vec3 GLF_live2color = _GLF_FUZZED(((24.49 * vec2(60.36, -7105.7141)) + (+ -830.232)).yxx);
                            vec2 GLF_live2position = _GLF_FUZZED(vec2(0.0));
                            {
                                vec3 GLF_live2d = GLF_live2drawShape(GLF_live2position, GLF_live2center + vec2(sin(float(GLF_live2i) / 10.0 + GLF_live2time) / 4.0, 0.0), vec3(0.01 + sin(float(GLF_live2i) / 100.0), 0.01, float(GLF_live2i)));
                                if(length(GLF_live2d) != 0.0)
                                    GLF_live2color = GLF_live2d;
                            }
                        }
                    }
                hd.n = s2.yzw;
                if(_GLF_DEAD(_GLF_IDENTITY(_GLF_FALSE(false, (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, vec2(0.0, 0.0) + (injectionSwitch)).y)), (false ? _GLF_FUZZED((ivec4(94744, -67522, -50536, 60621) == ivec4(-90221, _GLF_IDENTITY(39105, (39105) + int(_GLF_ZERO(0.0, injectionSwitch.x))), _GLF_IDENTITY(-73940, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED((+ ivec3(19184, 4904, -64325)).r) : _GLF_IDENTITY(-73940, 1 * (_GLF_IDENTITY(-73940, (-73940) + 0))))), 29790))) : _GLF_IDENTITY(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)), (_GLF_FALSE(false, (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, vec2(0.0, 0.0) + (_GLF_IDENTITY(injectionSwitch, (injectionSwitch) + vec2(0.0, 0.0)))).y))) || false)))))
                    {
                        vec2 donor_replacementresolution = _GLF_FUZZED(ceil((mouse.st + clamp(8.8, PI, _GLF_IDENTITY(time, _GLF_ONE(1.0, injectionSwitch.y) * (_GLF_IDENTITY(time, (_GLF_IDENTITY(time, (time) * 1.0)) + _GLF_ZERO(0.0, injectionSwitch.x))))))));
                        if(_GLF_WRAPPED_IF_TRUE(_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y))))
                            {
                                float angle = sin(time) * 0.1;
                                mat2 rotation = mat2(cos(angle), - sin(angle), sin(angle), cos(angle));
                                vec2 aspect = _GLF_IDENTITY(donor_replacementresolution, (donor_replacementresolution) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))).xy / min(donor_replacementresolution.x, donor_replacementresolution.y);
                                vec2 position = _GLF_IDENTITY((gl_FragCoord.xy / donor_replacementresolution.xy), vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (_GLF_IDENTITY((gl_FragCoord.xy / donor_replacementresolution.xy), vec2(1.0, 1.0) * ((_GLF_IDENTITY(gl_FragCoord, vec4(0.0, 0.0, 0.0, 0.0) + (gl_FragCoord)).xy / donor_replacementresolution.xy))))) * aspect;
                                vec2 center = vec2(0.5) * aspect;
                                position *= rotation;
                                {
                                    vec2 GLF_merged2_0_1_10_1_1_11GLF_live0cGLF_live0v1;
                                    const int GLF_live0ITERATIONS = _GLF_FUZZED(-86408);
                                    float GLF_live0t = _GLF_FUZZED(PI);
                                    mat2 GLF_live0ma = _GLF_FUZZED((((mat2(6.2, -4.0, 58.28, 9406.3426) * mat2(-7.7, 5.5, -6.8, -145.505)) + mat2(N, 7.3, N, N))));
                                    float GLF_live0c = _GLF_FUZZED(-54.79);
                                    GLF_merged2_0_1_10_1_1_11GLF_live0cGLF_live0v1.x = GLF_live0c;
                                    vec2 GLF_live0uv = _GLF_FUZZED((- vec2((+ PI), N)));
                                    const float GLF_live0FORMUPARAM = _GLF_FUZZED(PI);
                                    float GLF_live0v1 = _GLF_FUZZED((- N));
                                    GLF_merged2_0_1_10_1_1_11GLF_live0cGLF_live0v1.y = GLF_live0v1;
                                    const int GLF_live0INNER_ITERS = _GLF_FUZZED(((int(true) / ivec4(-13767, 92002, -63611, -27237).b)));
                                    float GLF_live0v2 = _GLF_FUZZED((+ (vec2(42.74, 9.1) * N).s));
                                    for(
                                        int GLF_live0i = 0;
                                        GLF_live0i < GLF_live0ITERATIONS;
                                        GLF_live0i ++
                                    )
                                        {
                                            float GLF_live0s = float(GLF_live0i) * .035;
                                            vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                                            GLF_live0p.xy *= GLF_live0ma;
                                            GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                                            for(
                                                int GLF_live0i = 0;
                                                GLF_live0i < GLF_live0INNER_ITERS;
                                                GLF_live0i ++
                                            )
                                                {
                                                    GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                                }
                                            GLF_merged2_0_1_10_1_1_11GLF_live0cGLF_live0v1.y += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                                            GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                                            GLF_merged2_0_1_10_1_1_11GLF_live0cGLF_live0v1.x = length(GLF_live0p.xy * .3) * .85;
                                        }
                                }
                                center *= rotation;
                                vec3 color = vec3(0.0);
                                {
                                    vec3 GLF_live0p = _GLF_FUZZED(((+ vec3(PI, vec2(-7.8, 5236.1882)))));
                                    const float GLF_live0FORMUPARAM = _GLF_FUZZED(((vec4(-1.5, -6471.6132, -467.293, -1.3) * vec4(-19.11, -3.9, 130.691, 7953.0435)).y));
                                    {
                                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                    }
                                }
                                if(_GLF_WRAPPED_IF_FALSE(_GLF_IDENTITY(_GLF_FALSE(false, (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, vec2(1.0, 1.0) * (_GLF_IDENTITY(injectionSwitch, (injectionSwitch) * vec2(1.0, 1.0)))).y)), false || (_GLF_IDENTITY(_GLF_FALSE(false, (_GLF_IDENTITY(injectionSwitch, (injectionSwitch) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))).x > injectionSwitch.y)), false || (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))))))
                                    {
                                    }
                                else
                                    {
                                        {
                                            float GLF_live0t = _GLF_FUZZED(735.359);
                                            mat2 GLF_live0ma = _GLF_FUZZED(((+ (true ? N : N)) / mat2((mat3(7.0, -48.65, 7.3, 18.16, -8787.2511, -44.30, -92.54, 0.1, -8019.5794) - N))));
                                            float GLF_live0c = _GLF_FUZZED(N);
                                            int GLF_live0i = _GLF_FUZZED(((- ivec4(42803, 61043, 60039, -2178)).g - ((-84585 * 83809) / ivec2(-60563, 68709).r)));
                                            vec2 GLF_live0uv = _GLF_FUZZED((vec2(float(true), (-1505.6213 / -0.1)) - float((bvec2(false, false) == bvec2(true, false)))));
                                            const float GLF_live0FORMUPARAM = _GLF_FUZZED(float(ivec3(-3700).z));
                                            float GLF_live0v1 = _GLF_FUZZED(PI);
                                            const int GLF_live0INNER_ITERS = _GLF_FUZZED((+ (- (59618))));
                                            float GLF_live0v2 = _GLF_FUZZED(float((int(false) < (-72902 - 9972))));
                                            {
                                                float GLF_live0s = float(GLF_live0i) * .035;
                                                vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                                                GLF_live0p.xy *= GLF_live0ma;
                                                GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                                                for(
                                                    int GLF_live0i = 0;
                                                    GLF_live0i < GLF_live0INNER_ITERS;
                                                    GLF_live0i ++
                                                )
                                                    {
                                                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                                    }
                                                GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                                                GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                                                GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                                            }
                                        }
                                        for(
                                            int i = 0;
                                            i < 40;
                                            i ++
                                        )
                                            {
                                                vec3 d = drawShape(_GLF_IDENTITY(position, (_GLF_IDENTITY(position, (_GLF_IDENTITY(position, (position) * vec2(1.0, 1.0))) + vec2(0.0, 0.0))) * _GLF_IDENTITY(vec2(1.0, 1.0), (vec2(1.0, 1.0)) + vec2(0.0, 0.0))), center + vec2(sin(float(i) / 10.0 + time) / 4.0, _GLF_IDENTITY(0.0, (_GLF_IDENTITY(0.0, (_GLF_IDENTITY(0.0, 1.0 * (0.0))) * 1.0)) + _GLF_IDENTITY(0.0, (false ? _GLF_FUZZED(time) : 0.0)))), vec3(0.01 + sin(float(i) / _GLF_IDENTITY(100.0, (true ? _GLF_IDENTITY(100.0, (100.0) + 0.0) : _GLF_FUZZED(ca[1])))), 0.01, float(_GLF_IDENTITY(i, (_GLF_IDENTITY(i, (i) + 0)) + int(_GLF_ZERO(0.0, injectionSwitch.x))))));
                                                {
                                                    float GLF_live0t = _GLF_FUZZED(((vec4(-5.9, 5317.6081, -19.11, -6227.5694).q * (PI * PI)) - (vec2(-48.71, -631.535) * mat2(-1.8, 616.649, -6179.7589, -457.312)).x));
                                                    mat2 GLF_live0ma = _GLF_FUZZED(((float(23381) / mat2(mat4(778.952, 12.32, 9.1, -547.485, -970.007, 82.37, -159.692, 6676.1738, 3891.6070, -9.4, -3023.3360, 24.41, 850.447, 1832.2233, -666.351, 68.71)))));
                                                    float GLF_live0c = _GLF_FUZZED(vec4((N + vec2(-3410.8438, 2.0)), (N + PI), float(-74297)).a);
                                                    int GLF_live0i = _GLF_FUZZED((+ (ivec3(52519, 33646, -31507) * ivec3(79361, 94253, 17191)).t));
                                                    vec2 GLF_live0uv = _GLF_FUZZED(vec2(((vec3(-9.9, -6181.8074, 6.7) / vec3(-3431.6789, 178.688, 85.08)))));
                                                    const float GLF_live0FORMUPARAM = _GLF_FUZZED((- (- N)));
                                                    float GLF_live0v1 = _GLF_FUZZED(-50.55);
                                                    const int GLF_live0INNER_ITERS = _GLF_FUZZED(((- int(PI)) * 581));
                                                    float GLF_live0v2 = _GLF_FUZZED((- (PI / vec3(-0.8, 9.0, 205.615)).b));
                                                    {
                                                        float GLF_live0s = float(GLF_live0i) * .035;
                                                        vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                                                        GLF_live0p.xy *= GLF_live0ma;
                                                        GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                                                        for(
                                                            int GLF_live0i = 0;
                                                            GLF_live0i < GLF_live0INNER_ITERS;
                                                            GLF_live0i ++
                                                        )
                                                            {
                                                                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                                            }
                                                        GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                                                        GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                                                        GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                                                    }
                                                }
                                                if(length(d) != 0.0)
                                                    color = d;
                                            }
                                    }
                                {
                                    vec3 GLF_live0p = _GLF_FUZZED((- ((vec3(1.3, -413.558, 9.9) - vec3(8.8, -728.804, 9398.7582)) * (PI - mat3(-7.9, -7805.3882, -0.6, 9.8, 0.1, -70.79, -4435.7616, 317.351, 7912.0748)))));
                                    const float GLF_live0FORMUPARAM = _GLF_FUZZED(PI);
                                    {
                                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                    }
                                }
                                gl_FragColor = vec4(color, 1.0);
                            }
                        else
                            {
                            }
                    }
            }
    return hd;
    {
        vec2 GLF_live2square = _GLF_FUZZED((((+ vec2(72.37, -67.98)) - vec2(vec4(-46.80, -7.5, -302.844, 221.691))) / (vec2(-3.7, -42.59) + (-20.24 * N))));
        vec3 GLF_live2setting = _GLF_FUZZED((- (- vec2(-8.4, 55.00).stt)));
        vec2 GLF_live2pixel = _GLF_FUZZED(vec2(float((true ? 59626 : -10269)), ((false) ? 59.76 : (PI * PI))));
        if((GLF_live2pixel.x - (GLF_live2setting.x) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x) > GLF_live2square.y) && ! ((GLF_live2pixel.x - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.y)))
            GLF_live2colorFunc(GLF_live2setting.z / 40.0);
    }
}
S scene(in R r)
{
    {
        vec4 GLF_merged3_0_1_10_1_2_11_3_1_11GLF_live0cGLF_live0uvGLF_live0v1;
        const int GLF_live0ITERATIONS = _GLF_FUZZED((- (int(-1602.2879))));
        float GLF_live0t = _GLF_FUZZED(((vec2(1.6, -171.334).y) * N));
        mat2 GLF_live0ma = _GLF_FUZZED((((-7.5 - mat2(3231.4230, 533.365, 607.695, -503.923)) - mat2(mat3(-338.129, 1841.9477, -865.718, -42.92, -37.26, 0.5, 3602.5860, 0.2, 52.56))) + mat2(mat3(PI))));
        float GLF_live0c = _GLF_FUZZED(N);
        GLF_merged3_0_1_10_1_2_11_3_1_11GLF_live0cGLF_live0uvGLF_live0v1.x = GLF_live0c;
        vec2 GLF_live0uv = _GLF_FUZZED((((vec2(-1889.0459, -7779.5927) + vec2(-63.06, 3.0)) + vec2(PI))));
        GLF_merged3_0_1_10_1_2_11_3_1_11GLF_live0cGLF_live0uvGLF_live0v1.yz = GLF_live0uv;
        const float GLF_live0FORMUPARAM = _GLF_FUZZED(PI);
        float GLF_live0v1 = _GLF_FUZZED(((- vec3(-6.8, -74.67, 7.1)).x));
        GLF_merged3_0_1_10_1_2_11_3_1_11GLF_live0cGLF_live0uvGLF_live0v1.w = GLF_live0v1;
        const int GLF_live0INNER_ITERS = _GLF_FUZZED((+ (int(-617.252))));
        float GLF_live0v2 = _GLF_FUZZED(PI);
        for(
            int GLF_live0i = 0;
            GLF_live0i < GLF_live0ITERATIONS;
            GLF_live0i ++
        )
            {
                float GLF_live0s = float(GLF_live0i) * .035;
                vec3 GLF_live0p = GLF_live0s * vec3(GLF_merged3_0_1_10_1_2_11_3_1_11GLF_live0cGLF_live0uvGLF_live0v1.yz, 1.0 + sin(GLF_live0time * .015));
                GLF_live0p.xy *= GLF_live0ma;
                GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                for(
                    int GLF_live0i = 0;
                    GLF_live0i < GLF_live0INNER_ITERS;
                    GLF_live0i ++
                )
                    {
                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                    }
                GLF_merged3_0_1_10_1_2_11_3_1_11GLF_live0cGLF_live0uvGLF_live0v1.w += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_merged3_0_1_10_1_2_11_3_1_11GLF_live0cGLF_live0uvGLF_live0v1.yz.xy * 18.0) + .5 - GLF_live0t * .7));
                GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_merged3_0_1_10_1_2_11_3_1_11GLF_live0cGLF_live0uvGLF_live0v1.yz.xy * 13.5) + 1.21 - GLF_live0t * .3));
                GLF_merged3_0_1_10_1_2_11_3_1_11GLF_live0cGLF_live0uvGLF_live0v1.x = length(GLF_live0p.xy * .3) * .85;
            }
    }
    S hd = S(0.0, _GLF_IDENTITY(vec3(0), vec3(0.0, 0.0, 0.0) + (_GLF_IDENTITY(vec3(0), (_GLF_IDENTITY(vec3(0), vec3(0.0, 0.0, 0.0) + (vec3(0)))) * vec3(1.0, 1.0, 1.0)))), - 1.0, r);
    {
        vec3 GLF_live0p = _GLF_FUZZED(((PI - (N + vec3(0.9, 0.3, -538.758))) * ((- vec3(568.863, 4.7, -74.59)) - vec3(N))));
        const float GLF_live0FORMUPARAM = _GLF_FUZZED(4.3);
        const int GLF_live0INNER_ITERS = _GLF_FUZZED(ivec3((54457), (ivec2(-48340, 90146) - -48128)).g);
        for(
            int GLF_live0i = 0;
            GLF_live0i < GLF_live0INNER_ITERS;
            GLF_live0i ++
        )
            {
                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
            }
    }
    vec4 d1 = psh(r, _GLF_IDENTITY(vec4(0, 0, 0, 1), (_GLF_IDENTITY(vec4(0, 0, 0, 1), (vec4(0, 0, 0, 1)) * vec4(1.0, 1.0, 1.0, 1.0))) * vec4(1.0, 1.0, 1.0, 1.0)));
    vec4 d2 = hitPlane(r, vec3(0, _GLF_IDENTITY(- _GLF_IDENTITY(2, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(2, (2) * 1) : _GLF_FUZZED((ivec4(48150, -74682, 12687, 91925).g - int(true))))), (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(((false ? 62767 : -19536) * ivec4(77530, 18991, -40550, -50501)[2])) : _GLF_IDENTITY(- _GLF_IDENTITY(2, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(2, (2) * 1) : _GLF_FUZZED((ivec4(48150, -74682, 12687, 91925).g - int(true))))), 0 + (- _GLF_IDENTITY(2, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(2, (2) * 1) : _GLF_FUZZED((ivec4(48150, -74682, 12687, 91925).g - int(true))))))))), 0), _GLF_IDENTITY(vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, (_GLF_IDENTITY(0, (false ? _GLF_FUZZED(84872) : 0))) * 1)) * _GLF_IDENTITY(1, (1) * 1)), 1, 0), (_GLF_IDENTITY(vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, (_GLF_IDENTITY(0, (false ? _GLF_FUZZED(84872) : 0))) * 1)) * _GLF_IDENTITY(1, (1) * 1)), 1, 0), vec3(0.0, 0.0, 0.0) + (vec3(_GLF_IDENTITY(0, (_GLF_IDENTITY(0, (_GLF_IDENTITY(0, (false ? _GLF_FUZZED(84872) : 0))) * 1)) * _GLF_IDENTITY(1, (1) * 1)), 1, 0)))) * vec3(1.0, 1.0, 1.0)));
    if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
        {
            {
                vec3 GLF_live0p = _GLF_FUZZED((((ivec4(18728, -77996, -73961, 71266) + ivec4(-7440, 39184, 92903, -56790)) != (+ ivec4(-15856, 58461, 39232, 84890))) ? (- vec2(-3.3, -4.7).rgr) : ((vec3(8.9, -2.0, 93.36) * 914.906))));
                const float GLF_live0FORMUPARAM = _GLF_FUZZED(float(((53460 + 18575))));
                {
                    GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                }
            }
            {
                vec3 GLF_merged3_0_1_2_1_1_2_2_1_2resiv2;
                const float ZOOM = 1.0;
                {
                    vec3 GLF_live0p = _GLF_FUZZED(((vec3(vec4(779.895, -337.792, -41.59, -317.956))) - (bvec4(false, false, true, true).z ? (true ? N : N) : float(true))));
                    const float GLF_live0FORMUPARAM = _GLF_FUZZED((N / (vec4(4732.3521, 658.154, 8.2, 727.902) / vec4(-2930.2464, 31.16, -619.404, 3697.8217))).w);
                    {
                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                    }
                }
                const float FORMUPARAM = 0.59989;
                const int ITERATIONS = 105;
                const int INNER_ITERS = 10;
                vec2 uv = (gl_FragCoord.xy / _GLF_IDENTITY(mouse, (_GLF_IDENTITY(mouse, (_GLF_IDENTITY(mouse, (mouse) * vec2(1.0, 1.0))) + vec2(0.0, 0.0))) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))).xy) - .5 / _GLF_IDENTITY(ZOOM, (_GLF_IDENTITY(ZOOM, (ZOOM) * 1.0)) + 0.0);
                float t = time * .10 + ((.25 + .05 * sin(_GLF_IDENTITY(time * .1, (_GLF_IDENTITY(time * .1, (_GLF_IDENTITY(time * .1, (true ? time * .1 : _GLF_FUZZED(FORMUPARAM)))) * 1.0)) + _GLF_ZERO(0.0, injectionSwitch.x)))) / (length(_GLF_IDENTITY(uv, vec2(0.0, 0.0) + (uv)).xy) + .07)) * 2.2;
                float si = sin(t);
                GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.y = si;
                float co = cos(_GLF_IDENTITY(t, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(distance(d1.wx, vec2(FORMUPARAM))) : t)));
                mat2 ma = mat2(_GLF_IDENTITY(co, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(d1[0]) : co)), _GLF_IDENTITY(GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.y, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(length(fract(t))) : _GLF_IDENTITY(GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.y, (_GLF_IDENTITY(GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.y, 0.0 + (GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.y))) * 1.0))), - _GLF_IDENTITY(GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.y, (GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.y) * _GLF_IDENTITY(1.0, 1.0 * (1.0))), co);
                float c = 0.0;
                float v1 = 0.0;
                {
                    vec3 GLF_live0p = _GLF_FUZZED(vec3(5.6, -2.1, -7.0));
                    const float GLF_live0FORMUPARAM = _GLF_FUZZED(PI);
                    {
                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                    }
                }
                float v2 = 0.0;
                GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.z = v2;
                if(_GLF_WRAPPED_IF_FALSE(false))
                    {
                    }
                else
                    {
                        for(
                            int i = 0;
                            i < ITERATIONS;
                            i ++
                        )
                            {
                                float s = float(i) * .035;
                                vec3 p = s * vec3(uv, _GLF_IDENTITY(1.0, 1.0 * (_GLF_IDENTITY(1.0, 1.0 * (_GLF_IDENTITY(1.0, (1.0) * 1.0))))) + sin(time * .015));
                                p.xy *= ma;
                                p += vec3(.22, .3, s - _GLF_IDENTITY(1.5, _GLF_ZERO(0.0, injectionSwitch.x) + (_GLF_IDENTITY(1.5, (1.5) + 0.0))) - sin(_GLF_IDENTITY(t, (false ? _GLF_FUZZED(uv.xy.r) : _GLF_IDENTITY(t, (_GLF_IDENTITY(t, (t) + 0.0)) + 0.0))) * _GLF_IDENTITY(.13, 0.0 + (.13))) * .1);
                                for(
                                    int i = 0;
                                    i < INNER_ITERS;
                                    i ++
                                )
                                    {
                                        p = abs(p) / dot(p, p) - FORMUPARAM;
                                    }
                                v1 += dot(p, p) * .0015 * (2.8 + sin(length(_GLF_IDENTITY(uv, (_GLF_IDENTITY(uv, (_GLF_IDENTITY(uv, (uv) + vec2(0.0, 0.0))) + vec2(0.0, 0.0))) * vec2(1.0, 1.0)).xy * 18.0) + .5 - t * .7));
                                GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.z += dot(p, p) * .0025 * (1.5 + sin(length(uv.xy * 13.5) + _GLF_IDENTITY(1.21, _GLF_IDENTITY(1.0, (1.0) + 0.0) * (_GLF_IDENTITY(1.21, (_GLF_IDENTITY(1.21, (true ? 1.21 : _GLF_FUZZED(-4221.4735)))) * 1.0))) - _GLF_IDENTITY(t * .3, (false ? _GLF_FUZZED(dot(vec4(p, 11.17), vec4(time, vec3(-3.8, 896.729, 3.7)))) : _GLF_IDENTITY(t, _GLF_ONE(1.0, injectionSwitch.y) * (t)) * .3))));
                                c = length(p.xy * .3) * .85;
                                {
                                    vec4 GLF_live2gl_FragColor = _GLF_FUZZED((+ (vec4(1200.8884, 9022.9716, -778.658, 17.16) - vec4(-3051.9834, 733.199, -8.3, 1.5))).tqts);
                                    vec4 GLF_live2gl_FragCoord = _GLF_FUZZED(vec4(N, PI, vec2((+ PI), (FORMUPARAM))));
                                    {
                                        float GLF_live2angle = sin(GLF_live2time) * 0.1;
                                        mat2 GLF_live2rotation = mat2(cos(GLF_live2angle), - sin(GLF_live2angle), sin(GLF_live2angle), cos(GLF_live2angle));
                                        vec2 GLF_live2aspect = GLF_live2resolution.xy / min(GLF_live2resolution.x, GLF_live2resolution.y);
                                        vec2 GLF_live2position = (GLF_live2gl_FragCoord.xy / GLF_live2resolution.xy) * GLF_live2aspect;
                                        vec2 GLF_live2center = vec2(0.5) * GLF_live2aspect;
                                        GLF_live2position *= GLF_live2rotation;
                                        GLF_live2center *= GLF_live2rotation;
                                        vec3 GLF_live2color = vec3(0.0);
                                        for(
                                            int GLF_live2i = 0;
                                            GLF_live2i < 40;
                                            GLF_live2i ++
                                        )
                                            {
                                                vec3 GLF_live2d = GLF_live2drawShape(GLF_live2position, GLF_live2center + vec2(sin(float(GLF_live2i) / 10.0 + GLF_live2time) / 4.0, 0.0), vec3(0.01 + sin(float(GLF_live2i) / 100.0), 0.01, float(GLF_live2i)));
                                                if(length(GLF_live2d) != 0.0)
                                                    GLF_live2color = GLF_live2d;
                                            }
                                        GLF_live2gl_FragColor = vec4(GLF_live2color, 1.0);
                                    }
                                }
                            }
                    }
                float len = length(uv);
                {
                    vec2 GLF_live2square = _GLF_FUZZED((vec4(FORMUPARAM, -916.612, FORMUPARAM, PI).ar / (- vec3(-10.64, -3554.8836, 80.61).p)));
                    vec3 GLF_live2setting = _GLF_FUZZED((((FORMUPARAM / FORMUPARAM) - (FORMUPARAM - vec3(65.20, 495.606, 3664.1947))) * (float(true) * (N - vec3(-967.512, -0.7, 8.9)))));
                    vec2 GLF_live2pixel = _GLF_FUZZED(vec2(719.676, (+ 6.0)));
                    if((GLF_live2pixel.x - (GLF_live2setting.x) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x) > GLF_live2square.y) && ! ((GLF_live2pixel.x - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.y)))
                        GLF_live2colorFunc(GLF_live2setting.z / 40.0);
                }
                v1 *= smoothstep(.7, _GLF_IDENTITY(.0, (_GLF_IDENTITY(false, (true ? false : _GLF_FUZZED(false))) ? _GLF_FUZZED((d2.g ++)) : _GLF_IDENTITY(.0, (false ? _GLF_FUZZED(smoothstep(v1, ZOOM, N)) : _GLF_IDENTITY(.0, (.0) * 1.0))))), len);
                GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.z *= smoothstep(_GLF_IDENTITY(.6, _GLF_IDENTITY(_GLF_ZERO(0.0, injectionSwitch.x), (_GLF_ZERO(0.0, injectionSwitch.x)) * 1.0) + (.6)), .0, len);
                float re = clamp(_GLF_IDENTITY(c, (c) + 0.0), _GLF_IDENTITY(0.0, (_GLF_IDENTITY(false, true && (false)) ? _GLF_FUZZED(v1) : 0.0)), _GLF_IDENTITY(1.0, 0.0 + (_GLF_IDENTITY(1.0, (false ? _GLF_FUZZED(c) : _GLF_IDENTITY(1.0, (true ? 1.0 : _GLF_FUZZED(t))))))));
                GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.x = re;
                float gr = clamp((v1 + c) * .25, 0.0, 1.0);
                float bl = clamp(_GLF_IDENTITY(GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.z, (_GLF_IDENTITY(GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.z, 0.0 + (_GLF_IDENTITY(GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.z, (true ? GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.z : _GLF_FUZZED(v1)))))) + 0.0), 0.0, 1.0);
                vec3 col = vec3(GLF_merged3_0_1_2_1_1_2_2_1_2resiv2.x, gr, bl) + smoothstep(0.15, .0, len) * 0.9;
                gl_FragColor = vec4(col, _GLF_IDENTITY(1.0, (1.0) + 0.0));
            }
        }
    if(_GLF_DEAD(_GLF_FALSE(_GLF_IDENTITY(false, _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) && (_GLF_IDENTITY(false, true && (false)))), (injectionSwitch.x > injectionSwitch.y))))
        return S(1.0, vec3(_GLF_IDENTITY(1.0, (_GLF_IDENTITY(true, (false ? _GLF_FUZZED(true) : true)) ? _GLF_IDENTITY(1.0, (true ? 1.0 : _GLF_FUZZED((1.6 / N)))) : _GLF_FUZZED(PI)))), 1.0, R(vec3(1.0), vec3(1.0)));
    return opU(hd, d1, _GLF_IDENTITY(d2, (_GLF_IDENTITY(d2, (d2) + vec4(0.0, 0.0, 0.0, 0.0))) + vec4(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))), vec2(82, 640));
}
S rt(in R r)
{
    {
        vec3 GLF_live0p = _GLF_FUZZED((((PI == 3526.6373) ? vec3(vec4(4.7, -9.9, -8.6, 86.25)) : vec3(N, N, N)) - vec4(21.80, 215.329, -339.869, -2108.1810).y));
        const float GLF_live0FORMUPARAM = _GLF_FUZZED(((vec2(4323.7832, -9.0) - vec2(-3.7, -6.3)).y - PI));
        {
            GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
        }
    }
    if(_GLF_WRAPPED_IF_TRUE(_GLF_TRUE(_GLF_IDENTITY(true, (true ? _GLF_IDENTITY(true, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED((ivec3(57797, 90774, 93027) != ivec3(62507, -48554, -21886))) : _GLF_IDENTITY(true, false || (true)))) : _GLF_FUZZED((vec3(N, time, PI) != reflect(vec3(5.1, -968.762, -6705.0408), vec3(191.494, -8869.9801, -92.99)))))), (injectionSwitch.x < injectionSwitch.y))))
        {
            if(_GLF_DEAD(_GLF_IDENTITY(_GLF_IDENTITY(false, (true ? false : _GLF_FUZZED(_GLF_IDENTITY(false, (_GLF_IDENTITY(true, (true) && true) ? _GLF_IDENTITY(false, (_GLF_IDENTITY(false, (false ? _GLF_FUZZED(false) : false))) || false) : _GLF_FUZZED((normalize(N) < distance(mouse, injectionSwitch)))))))), _GLF_IDENTITY(true, false || (true)) && (_GLF_IDENTITY(_GLF_IDENTITY(false, false || (_GLF_IDENTITY(false, false || (_GLF_IDENTITY(false, (false) && true))))), (true ? false : _GLF_FUZZED(_GLF_IDENTITY(false, (false) && _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y))))))))))
                return S(1.0, vec3(1.0), 1.0, R(vec3(1.0), _GLF_IDENTITY(vec3(1.0), vec3(0.0, 0.0, 0.0) + (_GLF_IDENTITY(vec3(1.0), (vec3(1.0)) * vec3(1.0, 1.0, 1.0))))));
        }
    else
        {
        }
    return scene(r);
    if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
        return S(_GLF_IDENTITY(1.0, 0.0 + (_GLF_IDENTITY(1.0, (false ? _GLF_FUZZED(dot(time, N)) : 1.0)))), vec3(_GLF_IDENTITY(1.0, (false ? _GLF_FUZZED(dot(max(vec3(2.9, -7.0, 635.334), vec3(-2.5, 1707.6306, 2417.7150)), cross(vec3(-7963.4792, 2372.1204, 7.5), vec3(1.0, -40.91, -4.5)))) : _GLF_IDENTITY(1.0, 0.0 + (1.0))))), 1.0, R(vec3(_GLF_IDENTITY(1.0, 0.0 + (_GLF_IDENTITY(1.0, 0.0 + (1.0))))), vec3(_GLF_IDENTITY(1.0, _GLF_ONE(1.0, injectionSwitch.y) * (1.0)))));
}
mat3 camera(in vec3 eye, in vec3 lat)
{
    if(_GLF_DEAD(_GLF_IDENTITY(_GLF_FALSE(false, (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, vec2(1.0, 1.0) * (_GLF_IDENTITY(injectionSwitch, (injectionSwitch) + vec2(0.0, 0.0)))).y)), (_GLF_FALSE(_GLF_IDENTITY(false, (_GLF_IDENTITY(false, (false) || false)) && _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y))), (injectionSwitch.x > injectionSwitch.y))) || false)))
        return mat3(1.0);
    vec3 ww = normalize(_GLF_IDENTITY(lat - eye, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (lat - eye)));
    if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
        {
            {
                const int GLF_live0ITERATIONS = _GLF_FUZZED(-74418);
                float GLF_live0t = _GLF_FUZZED((vec2(PI, PI) + (5413.0616 + 2.8)).r);
                mat2 GLF_live0ma = _GLF_FUZZED(mat2(1.8, 7.7, -8978.2751, 8204.9494));
                float GLF_live0c = _GLF_FUZZED((bvec3(bvec2(false, true), true).p ? float((true == true)) : float(ivec2(63857, 80856).r)));
                vec2 GLF_live0uv = _GLF_FUZZED((float(bvec3(true, false, true).b) / (+ vec2(4.0, -1.8)).yy));
                const float GLF_live0FORMUPARAM = _GLF_FUZZED(7.6);
                float GLF_live0v1 = _GLF_FUZZED(float(bool(ivec3(-93729, 58295, -694).z)));
                const int GLF_live0INNER_ITERS = _GLF_FUZZED((ivec4(49820, -53381, -29328, 65585).xx * ivec2(ivec4(-43624, 748, 7940, 72587))).g);
                float GLF_live0v2 = _GLF_FUZZED((- -355.183));
                for(
                    int GLF_live0i = 0;
                    GLF_live0i < GLF_live0ITERATIONS;
                    GLF_live0i ++
                )
                    {
                        float GLF_live0s = float(GLF_live0i) * .035;
                        vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                        GLF_live0p.xy *= GLF_live0ma;
                        GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                        for(
                            int GLF_live0i = 0;
                            GLF_live0i < GLF_live0INNER_ITERS;
                            GLF_live0i ++
                        )
                            {
                                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                            }
                        GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                        GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                        GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                    }
            }
            vec2 donor_replacementuv = _GLF_FUZZED(smoothstep(max(time, PI), sign(_GLF_IDENTITY(time, 1.0 * (time))), _GLF_IDENTITY(abs(vec3(80.49, 4118.1422, -804.399)), vec3(1.0, 1.0, 1.0) * (_GLF_IDENTITY(abs(vec3(80.49, 4118.1422, -804.399)), (_GLF_IDENTITY(abs(vec3(80.49, 4118.1422, -804.399)), vec3(1.0, 1.0, 1.0) * (abs(_GLF_IDENTITY(vec3(80.49, 4118.1422, -804.399), vec3(0.0, 0.0, 0.0) + (_GLF_IDENTITY(vec3(80.49, 4118.1422, -804.399), vec3(0.0, 0.0, 0.0) + (vec3(80.49, 4118.1422, _GLF_IDENTITY(-804.399, (_GLF_IDENTITY(-804.399, (_GLF_IDENTITY(-804.399, 0.0 + (-804.399))) * 1.0)) * _GLF_IDENTITY(1.0, (true ? 1.0 : _GLF_FUZZED(677.580))))))))))))) * vec3(1.0, 1.0, 1.0))))).gb);
            float donor_replacementc = _GLF_FUZZED(float((step(PI, time) > (512.139))));
            {
                const int GLF_live0ITERATIONS = _GLF_FUZZED(((int(PI)) * 41622));
                float GLF_live0t = _GLF_FUZZED(-9.1);
                mat2 GLF_live0ma = _GLF_FUZZED((((true ? N : PI) - (-2.2 / mat2(-3.7, -14.49, -963.019, -4.8))) + (- float(false))));
                float GLF_live0c = _GLF_FUZZED((+ (vec4(2133.5114, -22.99, 1302.5190, 0.2) * vec4(61.55, -56.93, 2790.1542, -3.8))).w);
                vec2 GLF_live0uv = _GLF_FUZZED((((N - vec2(-524.109, 8.4)) + (N)) / ((20.77 / N) - 3969.4150)));
                const float GLF_live0FORMUPARAM = _GLF_FUZZED((- (N)));
                float GLF_live0v1 = _GLF_FUZZED(((PI / 9683.4404) * (4.0 / vec4(336.975, -6.6, -8.7, 575.007))).x);
                const int GLF_live0INNER_ITERS = _GLF_FUZZED((+ ivec3(45537, ivec2(-37683, 92641)).x));
                float GLF_live0v2 = _GLF_FUZZED(((vec2(36.05, 7133.9636).s + float(true)) * ((-9.5) * (N * 898.188))));
                for(
                    int GLF_live0i = 0;
                    GLF_live0i < GLF_live0ITERATIONS;
                    GLF_live0i ++
                )
                    {
                        float GLF_live0s = float(GLF_live0i) * .035;
                        vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                        GLF_live0p.xy *= GLF_live0ma;
                        GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                        for(
                            int GLF_live0i = 0;
                            GLF_live0i < GLF_live0INNER_ITERS;
                            GLF_live0i ++
                        )
                            {
                                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                            }
                        GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                        GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                        GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                    }
            }
            const int donor_replacementITERATIONS = _GLF_FUZZED(_GLF_IDENTITY(-70055, (true ? _GLF_IDENTITY(-70055, (-70055) + 0) : _GLF_FUZZED((- ivec4(2382, -87644, 69435, 93965).z)))));
            mat2 donor_replacementma = _GLF_FUZZED(mat2((faceforward(PI, time, _GLF_IDENTITY(-703.520, (true ? _GLF_IDENTITY(-703.520, _GLF_ONE(1.0, injectionSwitch.y) * (-703.520)) : _GLF_FUZZED(sign(distance(resolution, vec2(2.2, 2.6))))))))));
            float donor_replacementt = _GLF_FUZZED(distance((mix(_GLF_IDENTITY(resolution, (_GLF_IDENTITY(resolution, (resolution) * vec2(1.0, 1.0))) * vec2(1.0, 1.0)), injectionSwitch, time) - fract(time)), clamp((+ injectionSwitch), max(resolution, PI), step(injectionSwitch, _GLF_IDENTITY(resolution, (_GLF_IDENTITY(resolution, vec2(0.0, 0.0) + (resolution))) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))))));
            const float donor_replacementFORMUPARAM = _GLF_FUZZED(PI);
            const int donor_replacementINNER_ITERS = _GLF_FUZZED((- (_GLF_IDENTITY(ivec3(8118, -82452, -52912).r, (ivec3(_GLF_IDENTITY(8118, (_GLF_IDENTITY(8118, (true ? 8118 : _GLF_FUZZED((40581))))) * _GLF_IDENTITY(1, (true ? 1 : _GLF_FUZZED(9277)))), _GLF_IDENTITY(-82452, 0 + (_GLF_IDENTITY(-82452, 0 + (-82452)))), -52912).r) * _GLF_IDENTITY(1, (1) + 0)) - (_GLF_IDENTITY(-28144, _GLF_IDENTITY(0, (true ? 0 : _GLF_FUZZED(39279))) + (_GLF_IDENTITY(-28144, (-28144) * 1))) / 95680))));
            {
                vec4 GLF_live0gl_FragColor = _GLF_FUZZED((vec4((+ vec3(558.754, -43.19, -9944.6912)), float(false)) * mat4(float(49837))));
                vec4 GLF_live0gl_FragCoord = _GLF_FUZZED((((+ mat4(2564.9909, -3.2, -7488.1199, -527.558, -2510.6671, 31.21, 20.24, -3197.5639, 1489.6609, 7.5, 5.7, -853.823, 636.766, 6.2, -6.6, -69.32)) == (mat4(-8.8, 5.3, -4977.4263, -5953.5777, -2162.5698, 53.05, 4534.9381, 4.3, -1.7, -1.9, 109.277, 31.36, 78.48, 3906.1376, 6826.2654, -182.694) / N)) ? vec4((vec2(4.4, -6.6) * -0.8), vec3(62.27, -551.334, -4372.5514).rg) : ((vec4(5106.3623, 107.083, 8.9, -3.8) * mat4(-411.477, -777.047, 2527.5944, -998.157, -6558.9485, -83.48, 281.536, -797.322, -93.84, -9692.3015, 36.85, -12.06, 244.413, 57.38, 1562.0256, 3.5)) / vec3(1.9, 3.9, 6.6).r)));
                {
                    vec3 GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2;
                    const float GLF_live0ZOOM = 1.0;
                    const float GLF_live0FORMUPARAM = 0.59989;
                    const int GLF_live0ITERATIONS = 105;
                    const int GLF_live0INNER_ITERS = 10;
                    vec2 GLF_live0uv = (GLF_live0gl_FragCoord.xy / GLF_live0resolution.xy) - .5 / GLF_live0ZOOM;
                    float GLF_live0t = GLF_live0time * .10 + ((.25 + .05 * sin(GLF_live0time * .1)) / (length(GLF_live0uv.xy) + .07)) * 2.2;
                    float GLF_live0si = sin(GLF_live0t);
                    float GLF_live0co = cos(GLF_live0t);
                    mat2 GLF_live0ma = mat2(GLF_live0co, GLF_live0si, - GLF_live0si, GLF_live0co);
                    float GLF_live0c = 0.0;
                    float GLF_live0v1 = 0.0;
                    GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.y = GLF_live0v1;
                    float GLF_live0v2 = 0.0;
                    GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.z = GLF_live0v2;
                    for(
                        int GLF_live0i = 0;
                        GLF_live0i < GLF_live0ITERATIONS;
                        GLF_live0i ++
                    )
                        {
                            float GLF_live0s = float(GLF_live0i) * .035;
                            vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                            GLF_live0p.xy *= GLF_live0ma;
                            GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                            for(
                                int GLF_live0i = 0;
                                GLF_live0i < GLF_live0INNER_ITERS;
                                GLF_live0i ++
                            )
                                {
                                    GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                }
                            GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.y += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                            GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.z += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                            GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                        }
                    float GLF_live0len = length(GLF_live0uv);
                    GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.x = GLF_live0len;
                    GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.y *= smoothstep(.7, .0, GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.x);
                    GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.z *= smoothstep(.6, .0, GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.x);
                    float GLF_live0re = clamp(GLF_live0c, 0.0, 1.0);
                    float GLF_live0gr = clamp((GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.y + GLF_live0c) * .25, 0.0, 1.0);
                    float GLF_live0bl = clamp(GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.z, 0.0, 1.0);
                    vec3 GLF_live0col = vec3(GLF_live0re, GLF_live0gr, GLF_live0bl) + smoothstep(0.15, .0, GLF_merged3_0_1_12_1_1_11_2_1_11GLF_live0lenGLF_live0v1GLF_live0v2.x) * 0.9;
                    GLF_live0gl_FragColor = vec4(GLF_live0col, 1.0);
                }
            }
            float donor_replacementv1 = _GLF_FUZZED(floor(distance(step(lat, _GLF_IDENTITY(ww, vec3(0.0, 0.0, 0.0) + (_GLF_IDENTITY(ww, vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)) + (ww))))), min(lat, time))));
            float donor_replacementv2 = _GLF_FUZZED(lat.yxz[2]);
            for(
                int i = 0;
                i < donor_replacementITERATIONS;
                i ++
            )
                {
                    float s = float(i) * _GLF_IDENTITY(.035, (true ? .035 : _GLF_FUZZED((abs(PI) * (false ? time : donor_replacementv1)))));
                    vec3 p = s * vec3(donor_replacementuv, 1.0 + sin(time * .015));
                    {
                        vec3 GLF_live0p = _GLF_FUZZED((((N + vec3(3424.5076, 48.26, -5.4)) + float(false)) * donor_replacementFORMUPARAM));
                        const float GLF_live0FORMUPARAM = _GLF_FUZZED((- ((PI * N) - (+ donor_replacementFORMUPARAM))));
                        {
                            GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                        }
                    }
                    p.xy *= donor_replacementma;
                    p += vec3(_GLF_IDENTITY(.22, (_GLF_IDENTITY(.22, 1.0 * (.22))) * _GLF_ONE(1.0, injectionSwitch.y)), .3, _GLF_IDENTITY(s - 1.5, 1.0 * (s - 1.5)) - sin(donor_replacementt * .13) * .1);
                    for(
                        int i = 0;
                        i < donor_replacementINNER_ITERS;
                        i ++
                    )
                        {
                            p = abs(p) / dot(_GLF_IDENTITY(p, vec3(0.0, 0.0, 0.0) + (_GLF_IDENTITY(p, vec3(0.0, 0.0, 0.0) + (p)))), p) - donor_replacementFORMUPARAM;
                        }
                    donor_replacementv1 += dot(_GLF_IDENTITY(p, vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)) + (p)), p) * .0015 * (_GLF_IDENTITY(2.8, _GLF_ZERO(0.0, injectionSwitch.x) + (_GLF_IDENTITY(2.8, (2.8) + 0.0))) + sin(length(donor_replacementuv.xy * 18.0) + .5 - _GLF_IDENTITY(donor_replacementt * .7, (_GLF_IDENTITY(donor_replacementt * .7, (donor_replacementt * .7) + 0.0)) + 0.0)));
                    donor_replacementv2 += dot(p, p) * _GLF_IDENTITY(.0025, 0.0 + (_GLF_IDENTITY(.0025, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? .0025 : _GLF_FUZZED(eye.g))))) * (1.5 + sin(length(donor_replacementuv.xy * 13.5) + 1.21 - donor_replacementt * .3));
                    donor_replacementc = length(p.xy * .3) * .85;
                }
        }
    {
        vec3 GLF_live0p = _GLF_FUZZED(((- vec3(-2297.0915, -7.7, 382.173)).yzz));
        const float GLF_live0FORMUPARAM = _GLF_FUZZED(vec3(219.400, -41.50, -4.1).p);
        const int GLF_live0INNER_ITERS = _GLF_FUZZED((31781 / ((true ? -50095 : 17276) * ivec4(51784, -60095, -69037, -66979).t)));
        for(
            int GLF_live0i = 0;
            GLF_live0i < GLF_live0INNER_ITERS;
            GLF_live0i ++
        )
            {
                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
            }
    }
    vec3 uu = normalize(cross(_GLF_IDENTITY(vec3(0, 1, 0), (vec3(0, 1, 0)) * _GLF_IDENTITY(vec3(1.0, 1.0, 1.0), (vec3(1.0, 1.0, 1.0)) + vec3(0.0, 0.0, 0.0))), ww));
    vec3 vv = normalize(cross(ww, uu));
    return mat3(uu, vv, ww);
}
vec3 getColor(in float n)
{
    return (0.46 + 0.3 * sin(_GLF_IDENTITY(vec3(0.03, 0.08, 0.1), vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)) + (_GLF_IDENTITY(vec3(0.03, 0.08, 0.1), vec3(0.0, 0.0, 0.0) + (_GLF_IDENTITY(vec3(0.03, 0.08, 0.1), (vec3(0.03, 0.08, 0.1)) + vec3(0.0, 0.0, 0.0)))))) * _GLF_IDENTITY(n, 1.0 * (n)))).rgb;
    if(_GLF_WRAPPED_IF_FALSE(_GLF_IDENTITY(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)), (_GLF_IDENTITY(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)), false || (_GLF_FALSE(false, (_GLF_IDENTITY(injectionSwitch, vec2(1.0, 1.0) * (_GLF_IDENTITY(injectionSwitch, vec2(0.0, 0.0) + (injectionSwitch)))).x > injectionSwitch.y))))) || _GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)))))
        {
        }
    else
        {
            if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
                return vec3(1.0);
        }
    {
        vec3 GLF_live0p = _GLF_FUZZED((((false ? mat3(-5648.3902, -40.41, 596.457, -45.36, -7874.6517, 9.7, -59.23, -562.331, 32.23) : mat3(3.1, 7.6, -887.700, 39.70, 7.6, 790.977, 1357.7116, -406.178, 0.9)) / (- PI)) * ((vec3(991.319, -226.632, -2506.0952) + PI) / -0.9)));
        const float GLF_live0FORMUPARAM = _GLF_FUZZED(N);
        const int GLF_live0INNER_ITERS = _GLF_FUZZED(ivec3(7771, 75361, 54162).tps.zy.x);
        for(
            int GLF_live0i = 0;
            GLF_live0i < GLF_live0INNER_ITERS;
            GLF_live0i ++
        )
            {
                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
            }
    }
    for(
        int _injected_loop_counter_0 = _GLF_IDENTITY(0, (true ? 0 : _GLF_FUZZED(73251)));
        _GLF_WRAPPED_LOOP(_injected_loop_counter_0 < _GLF_IDENTITY(1, 1 * (_GLF_IDENTITY(1, 0 + (1)))));
        _injected_loop_counter_0 ++
    )
        {
            if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, (_GLF_IDENTITY(injectionSwitch, (_GLF_IDENTITY(injectionSwitch, vec2(0.0, 0.0) + (injectionSwitch))) + vec2(0.0, 0.0))) * _GLF_IDENTITY(vec2(1.0, 1.0), vec2(1.0, 1.0) * (vec2(1.0, 1.0)))).y))))
                {
                    vec3 donor_replacementp = _GLF_FUZZED(mat3(mat2(-5.4, 34.19, -943.605, -2.2))[0]);
                    const int donor_replacementINNER_ITERS = _GLF_FUZZED((+ (int(true))));
                    for(
                        int i = 0;
                        i < donor_replacementINNER_ITERS;
                        i ++
                    )
                        {
                            {
                                vec3 GLF_live0p = _GLF_FUZZED((- vec3(-7.0, 0.9, -4.1).bbr.sps));
                                const float GLF_live0FORMUPARAM = _GLF_FUZZED(float((39832 != (- -46031))));
                                const int GLF_live0INNER_ITERS = _GLF_FUZZED((3168 / ((true ? -29933 : donor_replacementINNER_ITERS) * (- donor_replacementINNER_ITERS))));
                                for(
                                    int GLF_live0i = 0;
                                    GLF_live0i < GLF_live0INNER_ITERS;
                                    GLF_live0i ++
                                )
                                    {
                                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                    }
                            }
                            donor_replacementp = abs(donor_replacementp) / dot(donor_replacementp, _GLF_IDENTITY(donor_replacementp, vec3(1.0, 1.0, 1.0) * (_GLF_IDENTITY(donor_replacementp, vec3(0.0, 0.0, 0.0) + (_GLF_IDENTITY(donor_replacementp, (donor_replacementp) + vec3(0.0, 0.0, 0.0))))))) - PI;
                        }
                    {
                        vec2 GLF_live2center = _GLF_FUZZED(vec2(-3.3, -88.35));
                        vec3 GLF_live2color = _GLF_FUZZED((N - vec3((5.9))));
                        vec2 GLF_live2position = _GLF_FUZZED((vec4(-688.708, 973.702, PI, -9.7) * (mat4(-6.6, -9.9, -6.0, 0.9, 6.4, -3504.7275, 38.72, -4.9, -4.9, -0.5, 56.39, 944.559, 7.3, 11.07, -464.890, -3.6) + mat4(-10.17, 626.183, 3.1, -3.7, -0.8, 7.3, 9.5, 9.3, 75.70, -5.7, -1.1, -1410.5044, -5625.2238, -5.5, 8505.6779, 2.8))).st);
                        for(
                            int GLF_live2i = 0;
                            GLF_live2i < 40;
                            GLF_live2i ++
                        )
                            {
                                vec3 GLF_live2d = GLF_live2drawShape(GLF_live2position, GLF_live2center + vec2(sin(float(GLF_live2i) / 10.0 + GLF_live2time) / 4.0, 0.0), vec3(0.01 + sin(float(GLF_live2i) / 100.0), 0.01, float(GLF_live2i)));
                                if(length(GLF_live2d) != 0.0)
                                    GLF_live2color = GLF_live2d;
                            }
                    }
                }
            {
                vec3 GLF_live2d = _GLF_FUZZED((vec3(PI) - -938.373));
                vec3 GLF_live2color = _GLF_FUZZED(vec3(vec3(-5.3, N, PI).zzzy));
                if(length(GLF_live2d) != 0.0)
                    GLF_live2color = GLF_live2d;
            }
        }
}
vec3 gc(in vec3 pos)
{
    if(_GLF_DEAD(false))
        {
            vec3 donor_replacementd = _GLF_FUZZED(sign(pos.bgr));
            vec3 donor_replacementcolor = _GLF_FUZZED(_GLF_IDENTITY(pos, (_GLF_IDENTITY(pos, (pos) * vec3(1.0, 1.0, 1.0))) * vec3(1.0, 1.0, 1.0)));
            if(length(_GLF_IDENTITY(donor_replacementd, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (_GLF_IDENTITY(donor_replacementd, vec3(0.0, 0.0, 0.0) + (donor_replacementd))))) != 0.0)
                donor_replacementcolor = donor_replacementd;
        }
    if(_GLF_WRAPPED_IF_FALSE(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
        {
        }
    else
        {
            if(_GLF_DEAD(_GLF_FALSE(_GLF_IDENTITY(false, (_GLF_IDENTITY(false, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED((bvec2(true, true) != bvec2(true, false))) : false))) || false), (_GLF_IDENTITY(injectionSwitch, vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)) + (_GLF_IDENTITY(injectionSwitch, vec2(1.0, 1.0) * (injectionSwitch)))).x > injectionSwitch.y))))
                return vec3(1.0);
            {
                const int GLF_live0ITERATIONS = _GLF_FUZZED(((ivec4(71490, -26791, 16198, -52128) * ivec4(-85285, 42322, -65344, 73071)).z - (true ? ivec2(-1422, 81705) : ivec2(-17234, -90782)).g));
                float GLF_live0t = _GLF_FUZZED((-561.909 - N));
                mat2 GLF_live0ma = _GLF_FUZZED((mat2(PI) + N));
                float GLF_live0c = _GLF_FUZZED(((- (PI)) * (vec2(-384.810, -778.126).g + (N * PI))));
                vec2 GLF_live0uv = _GLF_FUZZED((((vec2(2.1, 719.803) + N) + (-22.24 / PI)) + ((-88632 < 371) ? vec2(6.1, -961.258).y : N)));
                const float GLF_live0FORMUPARAM = _GLF_FUZZED(PI);
                float GLF_live0v1 = _GLF_FUZZED((- (+ vec4(0.6, 8751.2524, 7.5, -1928.6872)).r));
                const int GLF_live0INNER_ITERS = _GLF_FUZZED(int((ivec4(-91536, 89480, 44275, -28983) == (true ? ivec4(-8154, -89623, -90101, -21080) : ivec4(34930, 47700, 65757, 74782)))));
                float GLF_live0v2 = _GLF_FUZZED((+ PI));
                for(
                    int GLF_live0i = 0;
                    GLF_live0i < GLF_live0ITERATIONS;
                    GLF_live0i ++
                )
                    {
                        float GLF_live0s = float(GLF_live0i) * .035;
                        vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                        GLF_live0p.xy *= GLF_live0ma;
                        GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                        for(
                            int GLF_live0i = 0;
                            GLF_live0i < GLF_live0INNER_ITERS;
                            GLF_live0i ++
                        )
                            {
                                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                            }
                        GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                        GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                        GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                    }
            }
        }
    return vec3(1) * mod(floor(pos.x) + floor(_GLF_IDENTITY(pos, (_GLF_IDENTITY(pos, (pos) + vec3(0.0, 0.0, 0.0))) + vec3(0.0, 0.0, 0.0)).z), 2.0);
    if(_GLF_DEAD(false))
        return vec3(1.0);
}
vec3 oo(in vec2 v, in float r)
{
    float mx = v.x * PI * _GLF_IDENTITY(2.0, (true ? 2.0 : _GLF_FUZZED((v[1]))));
    float my = min(v.y, _GLF_IDENTITY(0.99, (true ? _GLF_IDENTITY(0.99, (true ? _GLF_IDENTITY(0.99, (true ? 0.99 : _GLF_FUZZED(r))) : _GLF_FUZZED((+ time)))) : _GLF_FUZZED(faceforward((N * r), (mx), vec4(-390.541, 7.6, -622.686, 0.7).w))))) * PI * 0.5;
    return vec3(cos(my) * cos(mx), sin(my), cos(_GLF_IDENTITY(my, 1.0 * (_GLF_IDENTITY(my, (_GLF_IDENTITY(my, (false ? _GLF_FUZZED(mx) : my))) + _GLF_ZERO(0.0, injectionSwitch.x))))) * sin(mx)) * r;
    if(_GLF_DEAD(_GLF_IDENTITY(false, _GLF_FALSE(false, (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, vec2(1.0, 1.0) * (_GLF_IDENTITY(injectionSwitch, (injectionSwitch) * vec2(1.0, 1.0)))).y)) || (false))))
        {
            vec2 donor_replacementsquare = _GLF_FUZZED(_GLF_IDENTITY(((_GLF_IDENTITY(vec2(r, PI), (vec2(r, PI)) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) - clamp((_GLF_IDENTITY(vec2(41.61, -8280.2995) + r, (vec2(41.61, -8280.2995) + r) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))), (r + vec2(-7.2, -7066.7300)), (- v))), (_GLF_IDENTITY(((_GLF_IDENTITY(vec2(r, PI), (vec2(r, PI)) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) - clamp((_GLF_IDENTITY(vec2(41.61, -8280.2995) + r, (vec2(41.61, -8280.2995) + r) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))), (r + vec2(-7.2, -7066.7300)), (- v))), ((_GLF_IDENTITY((_GLF_IDENTITY(vec2(r, PI), (vec2(r, PI)) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) - clamp((_GLF_IDENTITY(vec2(41.61, -8280.2995) + r, (vec2(41.61, -8280.2995) + r) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))), (r + vec2(-7.2, -7066.7300)), (- v)), _GLF_IDENTITY(vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)), vec2(0.0, 0.0) + (vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))) + (_GLF_IDENTITY((_GLF_IDENTITY(vec2(r, PI), (vec2(r, PI)) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) - clamp((_GLF_IDENTITY(vec2(41.61, -8280.2995) + r, (vec2(41.61, -8280.2995) + r) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))), (r + vec2(-7.2, -7066.7300)), (- v)), (_GLF_IDENTITY((_GLF_IDENTITY(vec2(r, PI), (vec2(r, PI)) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) - clamp((_GLF_IDENTITY(vec2(41.61, -8280.2995) + r, (vec2(41.61, -8280.2995) + r) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))), (r + vec2(-7.2, -7066.7300)), (- v)), vec2(1.0, 1.0) * ((_GLF_IDENTITY(vec2(r, PI), (vec2(r, PI)) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) - clamp(_GLF_IDENTITY((_GLF_IDENTITY(vec2(41.61, -8280.2995) + r, (vec2(41.61, -8280.2995) + r) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))), vec2(1.0, 1.0) * (_GLF_IDENTITY((_GLF_IDENTITY(vec2(41.61, -8280.2995) + r, (vec2(41.61, -8280.2995) + r) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))), ((_GLF_IDENTITY(vec2(41.61, -8280.2995) + r, (vec2(41.61, -8280.2995) + r) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))))) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))))), (_GLF_IDENTITY(r, (_GLF_IDENTITY(r, (r) * 1.0)) + 0.0) + vec2(-7.2, -7066.7300)), (_GLF_IDENTITY(- v, (- v) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))))))) * vec2(1.0, 1.0)))))) + vec2(0.0, 0.0))) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))));
            vec2 donor_replacementpixel = _GLF_FUZZED(v);
            vec3 donor_replacementsetting = _GLF_FUZZED(step(dot(clamp(mouse, _GLF_IDENTITY(injectionSwitch, (_GLF_IDENTITY(injectionSwitch, (_GLF_IDENTITY(injectionSwitch, vec2(1.0, 1.0) * (injectionSwitch))) * vec2(1.0, 1.0))) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))), _GLF_IDENTITY(resolution, (resolution) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))), (_GLF_IDENTITY(resolution + my, (_GLF_IDENTITY(resolution + my, vec2(0.0, 0.0) + (_GLF_IDENTITY(resolution + my, (resolution + _GLF_IDENTITY(my, _GLF_ZERO(0.0, injectionSwitch.x) + (_GLF_IDENTITY(my, (false ? _GLF_FUZZED(abs(r)) : _GLF_IDENTITY(my, 0.0 + (my))))))) * vec2(1.0, 1.0))))) * vec2(1.0, 1.0)))), vec3(length(mx), vec2(r, 2.1))));
            {
                vec2 GLF_live2square = _GLF_FUZZED(vec3((PI * PI), (PI - vec2(-2294.2189, -40.44))).xz);
                vec3 GLF_live2setting = _GLF_FUZZED((vec3(3243.1492, 5.4, -78.51) * ((mat3(-186.848, 303.392, -6350.8833, 99.11, -3.3, 145.788, 393.093, -6466.5754, -96.35)) * (vec3(8498.2915, -97.47, 35.75) / 58.46))));
                vec2 GLF_live2pixel = _GLF_FUZZED((((vec2(3157.9224, -1610.0132)) * (mat2(-30.47, -9657.8970, 8.1, -0.3) * mat2(489.017, 11.25, 4.0, -763.326))) - (vec2(-367.051, -754.214) * (- mat2(-5855.0347, 374.010, 1895.3878, -8.7)))));
                {
                    if((GLF_live2pixel.x - (GLF_live2setting.x) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x) > GLF_live2square.y) && ! ((GLF_live2pixel.x - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.y)))
                        GLF_live2colorFunc(GLF_live2setting.z / 40.0);
                    vec3(0.0);
                }
            }
            {
                if((donor_replacementpixel.x - (donor_replacementsetting.x) < donor_replacementsquare.x && _GLF_IDENTITY(donor_replacementpixel, vec2(0.0, 0.0) + (_GLF_IDENTITY(donor_replacementpixel, vec2(0.0, 0.0) + (donor_replacementpixel)))).x + (_GLF_IDENTITY(donor_replacementsetting, (donor_replacementsetting) + vec3(0.0, 0.0, 0.0)).x) > donor_replacementsquare.x && donor_replacementpixel.y - (donor_replacementsetting.x) < _GLF_IDENTITY(donor_replacementsquare, vec2(0.0, 0.0) + (_GLF_IDENTITY(donor_replacementsquare, (donor_replacementsquare) + vec2(0.0, 0.0)))).y && donor_replacementpixel.y + (donor_replacementsetting.x) > donor_replacementsquare.y) && ! ((donor_replacementpixel.x - (donor_replacementsetting.x - donor_replacementsetting.y) < donor_replacementsquare.x && _GLF_IDENTITY(donor_replacementpixel, (donor_replacementpixel) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))).x + (_GLF_IDENTITY(donor_replacementsetting, (_GLF_IDENTITY(donor_replacementsetting, (donor_replacementsetting) * vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) + vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))).x - donor_replacementsetting.y) > _GLF_IDENTITY(donor_replacementsquare, (_GLF_IDENTITY(donor_replacementsquare, (_GLF_IDENTITY(donor_replacementsquare, vec2(1.0, 1.0) * (donor_replacementsquare))) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))).x && _GLF_IDENTITY(donor_replacementpixel, (_GLF_IDENTITY(donor_replacementpixel, vec2(0.0, 0.0) + (_GLF_IDENTITY(donor_replacementpixel, (donor_replacementpixel) + vec2(0.0, 0.0))))) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))).y - (donor_replacementsetting.x - donor_replacementsetting.y) < donor_replacementsquare.y && donor_replacementpixel.y + (donor_replacementsetting.x - donor_replacementsetting.y) > donor_replacementsquare.y)))
                    {
                        {
                            float GLF_live2a = _GLF_FUZZED(-9.7);
                            {
                                vec2 GLF_live2cs = vec2(cos(GLF_live2a), sin(GLF_live2a));
                                mat2(GLF_live2cs.x, - GLF_live2cs.y, GLF_live2cs.y, GLF_live2cs.x);
                            }
                        }
                        return colorFunc(donor_replacementsetting.z / 40.0);
                    }
                return vec3(0.0);
            }
            {
                float GLF_live0t = _GLF_FUZZED(4.3);
                mat2 GLF_live0ma = _GLF_FUZZED((+ mat2((6.4 * N), N, (-2.0 / -2.2), (N - -68.63))));
                float GLF_live0c = _GLF_FUZZED((float(ivec4(-16030, -9180, 34655, -37794).r)));
                int GLF_live0i = _GLF_FUZZED((((-9676 / -85640) * (-44825 * -88766)) / (+ ivec2(-23831, -56606).y)));
                vec2 GLF_live0uv = _GLF_FUZZED(((-49349 == -79957) ? vec2(-4.8, -31.43).xxy : (vec3(-972.271, -4224.0429, -4.6) + PI)).xz);
                const float GLF_live0FORMUPARAM = _GLF_FUZZED(N);
                float GLF_live0v1 = _GLF_FUZZED(float((99462 / ivec3(-76087, -1923, 75660)).x));
                const int GLF_live0INNER_ITERS = _GLF_FUZZED(4908);
                float GLF_live0v2 = _GLF_FUZZED(((PI / (PI - N))));
                {
                    float GLF_live0s = float(GLF_live0i) * .035;
                    vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                    GLF_live0p.xy *= GLF_live0ma;
                    GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                    for(
                        int GLF_live0i = 0;
                        GLF_live0i < GLF_live0INNER_ITERS;
                        GLF_live0i ++
                    )
                        {
                            GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                        }
                    GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                    GLF_live0v2 += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                    GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                }
            }
        }
}
vec3 go(in vec2 fragCoord)
{
    vec3 zz = vec3(0.5, _GLF_IDENTITY(0.8, (0.8) + 0.0), 0.9);
    if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
        return vec3(1.0);
    {
        vec4 GLF_live0gl_FragColor = _GLF_FUZZED(vec4(((- N) - (- 9.4)), vec3(PI, vec2(-1.1, 442.202).r, (- PI))));
        vec4 GLF_live0gl_FragCoord = _GLF_FUZZED(vec4((- (false ? N : N)), ((+ 93.66) / (vec2(-2.6, -46.19) + vec2(-222.731, -1693.5544))), float((mat4(299.422, -62.88, -74.71, -7.4, -888.854, 7.2, -788.961, 894.208, 8219.0960, -3.7, 7779.3012, 288.206, -111.614, -796.849, -9.7, 1.3) != mat4(-2461.6881, 0.6, -2.3, 7852.4834, -8.1, -0.9, 867.822, -73.21, 717.402, -4336.5519, 6.8, 765.779, -9.9, -18.43, -0.1, -9918.0939)))));
        {
            vec2 GLF_merged2_0_1_11_1_1_11GLF_live0coGLF_live0v2;
            const float GLF_live0ZOOM = 1.0;
            const float GLF_live0FORMUPARAM = 0.59989;
            const int GLF_live0ITERATIONS = 105;
            const int GLF_live0INNER_ITERS = 10;
            vec2 GLF_live0uv = (GLF_live0gl_FragCoord.xy / GLF_live0resolution.xy) - .5 / GLF_live0ZOOM;
            float GLF_live0t = GLF_live0time * .10 + ((.25 + .05 * sin(GLF_live0time * .1)) / (length(GLF_live0uv.xy) + .07)) * 2.2;
            float GLF_live0si = sin(GLF_live0t);
            float GLF_live0co = cos(GLF_live0t);
            GLF_merged2_0_1_11_1_1_11GLF_live0coGLF_live0v2.x = GLF_live0co;
            mat2 GLF_live0ma = mat2(GLF_merged2_0_1_11_1_1_11GLF_live0coGLF_live0v2.x, GLF_live0si, - GLF_live0si, GLF_merged2_0_1_11_1_1_11GLF_live0coGLF_live0v2.x);
            float GLF_live0c = 0.0;
            float GLF_live0v1 = 0.0;
            float GLF_live0v2 = 0.0;
            GLF_merged2_0_1_11_1_1_11GLF_live0coGLF_live0v2.y = GLF_live0v2;
            for(
                int GLF_live0i = 0;
                GLF_live0i < GLF_live0ITERATIONS;
                GLF_live0i ++
            )
                {
                    float GLF_live0s = float(GLF_live0i) * .035;
                    vec3 GLF_live0p = GLF_live0s * vec3(GLF_live0uv, 1.0 + sin(GLF_live0time * .015));
                    GLF_live0p.xy *= GLF_live0ma;
                    GLF_live0p += vec3(.22, .3, GLF_live0s - 1.5 - sin(GLF_live0t * .13) * .1);
                    for(
                        int GLF_live0i = 0;
                        GLF_live0i < GLF_live0INNER_ITERS;
                        GLF_live0i ++
                    )
                        {
                            GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                        }
                    GLF_live0v1 += dot(GLF_live0p, GLF_live0p) * .0015 * (2.8 + sin(length(GLF_live0uv.xy * 18.0) + .5 - GLF_live0t * .7));
                    GLF_merged2_0_1_11_1_1_11GLF_live0coGLF_live0v2.y += dot(GLF_live0p, GLF_live0p) * .0025 * (1.5 + sin(length(GLF_live0uv.xy * 13.5) + 1.21 - GLF_live0t * .3));
                    GLF_live0c = length(GLF_live0p.xy * .3) * .85;
                }
            float GLF_live0len = length(GLF_live0uv);
            GLF_live0v1 *= smoothstep(.7, .0, GLF_live0len);
            GLF_merged2_0_1_11_1_1_11GLF_live0coGLF_live0v2.y *= smoothstep(.6, .0, GLF_live0len);
            float GLF_live0re = clamp(GLF_live0c, 0.0, 1.0);
            float GLF_live0gr = clamp((GLF_live0v1 + GLF_live0c) * .25, 0.0, 1.0);
            float GLF_live0bl = clamp(GLF_merged2_0_1_11_1_1_11GLF_live0coGLF_live0v2.y, 0.0, 1.0);
            vec3 GLF_live0col = vec3(GLF_live0re, GLF_live0gr, GLF_live0bl) + smoothstep(0.15, .0, GLF_live0len) * 0.9;
            GLF_live0gl_FragColor = vec4(GLF_live0col, 1.0);
        }
    }
    float aspect = resolution.x / resolution.y;
    {
        vec3 GLF_live0p = _GLF_FUZZED((((N) - (mat3(-1.9, -9817.7895, -2601.9913, 1974.4450, -4492.1603, 894.187, 627.081, 2617.6276, -95.87) * vec3(52.55, 8.3, 5.5))) - ((120.576 * vec3(7.5, -882.498, 8.3)) / (vec3(-1.7, -647.704, 243.211) + PI))));
        const float GLF_live0FORMUPARAM = _GLF_FUZZED(PI);
        const int GLF_live0INNER_ITERS = _GLF_FUZZED(int(((+ N) < vec2(2.0, -6037.6863).t)));
        for(
            int GLF_live0i = 0;
            GLF_live0i < GLF_live0INNER_ITERS;
            GLF_live0i ++
        )
            {
                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
            }
    }
    vec2 uv = (fragCoord / _GLF_IDENTITY(resolution, (_GLF_IDENTITY(resolution, vec2(0.0, 0.0) + (_GLF_IDENTITY(resolution, (resolution) * vec2(1.0, 1.0))))) + vec2(0.0, 0.0))) * 2.0 - 1.0;
    uv.x *= aspect;
    if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, vec2(1.0, 1.0) * (injectionSwitch)).y))))
        {
            mat2 donor_replacementma = _GLF_FUZZED((((_GLF_IDENTITY(time, (_GLF_IDENTITY(time, (true ? _GLF_IDENTITY(time, (time) * 1.0) : _GLF_FUZZED(dot(1.8, 1.6))))) + 0.0) / _GLF_IDENTITY(aspect, (_GLF_IDENTITY(aspect, (aspect) + 0.0)) * _GLF_ONE(1.0, injectionSwitch.y))) + (mat2(_GLF_IDENTITY(6850.1958, (true ? _GLF_IDENTITY(6850.1958, (false ? _GLF_FUZZED(sign(PI)) : 6850.1958)) : _GLF_FUZZED(smoothstep((-- aspect), dot(zz, vec3(-568.075, -1.8, 5360.4392)), (aspect + 99.47))))), -24.52, -4.4, -84.43) - aspect))));
            int donor_replacementi = _GLF_FUZZED(ivec3(_GLF_IDENTITY((_GLF_IDENTITY(- ivec2(-1072, -8749), _GLF_IDENTITY(ivec2(1, 1), ivec2(0, 0) + (ivec2(1, 1))) * (_GLF_IDENTITY(- ivec2(-1072, -8749), ivec2(1, 1) * (- ivec2(-1072, -8749)))))), ivec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)) + (_GLF_IDENTITY((_GLF_IDENTITY(- ivec2(-1072, -8749), _GLF_IDENTITY(ivec2(1, 1), ivec2(0, 0) + (ivec2(1, 1))) * (_GLF_IDENTITY(- ivec2(-1072, -8749), ivec2(1, 1) * (- ivec2(-1072, -8749)))))), ((_GLF_IDENTITY(- ivec2(-1072, -8749), _GLF_IDENTITY(ivec2(1, 1), ivec2(0, 0) + (ivec2(1, 1))) * (_GLF_IDENTITY(- ivec2(-1072, -8749), ivec2(1, 1) * (- ivec2(-1072, -8749))))))) + ivec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))))), _GLF_IDENTITY((-27583 * _GLF_IDENTITY(-90795, (_GLF_IDENTITY(-90795, 1 * (-90795))) + 0)), (true ? _GLF_IDENTITY((-27583 * _GLF_IDENTITY(-90795, (_GLF_IDENTITY(-90795, 1 * (-90795))) + 0)), (true ? _GLF_IDENTITY((-27583 * _GLF_IDENTITY(-90795, (_GLF_IDENTITY(-90795, 1 * (-90795))) + 0)), 1 * ((-27583 * _GLF_IDENTITY(-90795, (_GLF_IDENTITY(-90795, 1 * (-90795))) + 0)))) : _GLF_FUZZED((10348 - -89773)))) : _GLF_FUZZED((+ int(false)))))).z);
            const int donor_replacementINNER_ITERS = _GLF_FUZZED(((_GLF_IDENTITY((-66925 * -30013) / _GLF_IDENTITY(ivec3(91358, -15422, -90824), _GLF_IDENTITY(ivec3(1, 1, 1), (ivec3(1, 1, 1)) + ivec3(0, 0, 0)) * (_GLF_IDENTITY(ivec3(91358, -15422, -90824), ivec3(1, 1, 1) * (_GLF_IDENTITY(ivec3(91358, -15422, -90824), ivec3(0, 0, 0) + (ivec3(91358, -15422, _GLF_IDENTITY(-90824, (true ? -90824 : _GLF_FUZZED((ivec4(-79272, -76367, 65062, -28111) + 10946).y)))))))))).r, (_GLF_IDENTITY((-66925 * -30013) / _GLF_IDENTITY(ivec3(91358, -15422, -90824), _GLF_IDENTITY(ivec3(1, 1, 1), (ivec3(1, 1, 1)) + ivec3(0, 0, 0)) * (_GLF_IDENTITY(ivec3(91358, -15422, -90824), ivec3(1, 1, 1) * (_GLF_IDENTITY(ivec3(91358, -15422, -90824), ivec3(0, 0, 0) + (ivec3(91358, -15422, _GLF_IDENTITY(-90824, (true ? -90824 : _GLF_FUZZED((ivec4(-79272, -76367, 65062, -28111) + 10946).y)))))))))).r, ((_GLF_IDENTITY(-66925, 0 + (-66925)) * -30013) / _GLF_IDENTITY(ivec3(91358, -15422, -90824), _GLF_IDENTITY(ivec3(1, 1, 1), (ivec3(1, 1, 1)) + ivec3(0, 0, 0)) * (_GLF_IDENTITY(ivec3(91358, -15422, -90824), ivec3(1, 1, 1) * (_GLF_IDENTITY(ivec3(91358, -15422, -90824), ivec3(0, 0, 0) + (ivec3(91358, -15422, _GLF_IDENTITY(-90824, (true ? -90824 : _GLF_FUZZED((ivec4(-79272, -76367, 65062, -28111) + 10946).y)))))))))).r) * 1)) * 1))));
            {
                float s = float(donor_replacementi) * .035;
                vec3 p = s * vec3(uv, _GLF_IDENTITY(1.0, _GLF_IDENTITY(1.0, (false ? _GLF_FUZZED(-8407.0991) : 1.0)) * (_GLF_IDENTITY(1.0, 0.0 + (_GLF_IDENTITY(1.0, 0.0 + (1.0)))))) + sin(_GLF_IDENTITY(time, 0.0 + (time)) * .015));
                p.xy *= donor_replacementma;
                p += vec3(.22, _GLF_IDENTITY(.3, (.3) + _GLF_IDENTITY(0.0, (0.0) + 0.0)), s - 1.5 - sin(_GLF_IDENTITY(aspect * .13, (_GLF_IDENTITY(true, (true) || false) ? aspect * _GLF_IDENTITY(.13, (true ? _GLF_IDENTITY(.13, (false ? _GLF_FUZZED((N / s)) : .13)) : _GLF_FUZZED(floor(vec3(6412.4317, -6.5, -4.1)).b))) : _GLF_FUZZED((normalize(s) + reflect(N, s)))))) * .1);
                for(
                    int i = 0;
                    i < donor_replacementINNER_ITERS;
                    i ++
                )
                    {
                        p = abs(p) / dot(_GLF_IDENTITY(p, (_GLF_IDENTITY(p, vec3(0.0, 0.0, 0.0) + (p))) + vec3(0.0, 0.0, 0.0)), _GLF_IDENTITY(p, (_GLF_IDENTITY(p, vec3(0.0, 0.0, 0.0) + (p))) * vec3(1.0, 1.0, 1.0))) - PI;
                    }
                aspect += dot(p, _GLF_IDENTITY(p, (_GLF_IDENTITY(p, vec3(1.0, 1.0, 1.0) * (p))) + vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))) * .0015 * (2.8 + sin(length(uv.xy * 18.0) + .5 - _GLF_IDENTITY(aspect * .7, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? _GLF_IDENTITY(aspect * .7, (_GLF_IDENTITY(aspect * .7, (aspect * .7) + 0.0)) + 0.0) : _GLF_FUZZED((uv[1]))))));
                aspect += dot(p, p) * _GLF_IDENTITY(.0025, (true ? _GLF_IDENTITY(.0025, 1.0 * (_GLF_IDENTITY(.0025, (.0025) + 0.0))) : _GLF_FUZZED(refract(distance(fragCoord, fragCoord), time, distance(injectionSwitch, uv))))) * (1.5 + sin(length(uv.xy * 13.5) + 1.21 - _GLF_IDENTITY(aspect, (_GLF_IDENTITY(aspect, 0.0 + (aspect))) + 0.0) * .3));
                {
                    float GLF_live2a = _GLF_FUZZED(PI);
                    {
                        vec2 GLF_live2cs = vec2(cos(GLF_live2a), sin(GLF_live2a));
                        mat2(GLF_live2cs.x, - GLF_live2cs.y, GLF_live2cs.y, GLF_live2cs.x);
                    }
                }
                aspect = length(p.xy * .3) * .85;
            }
        }
    vec2 cur = mouse * 2.0 - _GLF_IDENTITY(1.0, (false ? _GLF_FUZZED(smoothstep(vec4(999.815, -5432.7730, -7.2, 1460.9690).q, normalize(aspect), fragCoord[0])) : _GLF_IDENTITY(1.0, (_GLF_IDENTITY(1.0, (false ? _GLF_FUZZED(PI) : 1.0))) + _GLF_ZERO(0.0, injectionSwitch.x))));
    vec3 ro = oo(_GLF_IDENTITY(cur, vec2(0.0, 0.0) + (_GLF_IDENTITY(cur, (cur) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))))), 5.0);
    vec3 rd = camera(ro, vec3(0)) * normalize(vec3(uv, 1.0));
    R r = R(ro, _GLF_IDENTITY(rd, (_GLF_IDENTITY(rd, vec3(1.0, 1.0, 1.0) * (rd))) + vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))));
    {
        vec3 GLF_live0p = _GLF_FUZZED((((vec3(-9361.7151, -9.6, 6.5) + N) * mat3(-1821.8430, N, N, 9.9, PI, PI, N, 6541.1857, 2.9)) / ((vec3(-8.8, 174.243, 68.47) - -103.092) * (vec3(7.7, -5.9, -7.5) * vec3(9410.7157, 4716.6065, -3.3)))));
        const float GLF_live0FORMUPARAM = _GLF_FUZZED((bool((- N)) ? ((PI - 5.2) - (PI * PI)) : vec2(-991.037, 7.5).yy.s));
        const int GLF_live0INNER_ITERS = _GLF_FUZZED(((13968) / ivec2(50599, 92403)).r);
        for(
            int GLF_live0i = 0;
            GLF_live0i < GLF_live0INNER_ITERS;
            GLF_live0i ++
        )
            {
                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
            }
    }
    S trc = rt(r);
    if(trc.d > 0.0)
        {
            vec3 hit = r.o + r.d * trc.d;
            if(hit.y <= - 1.99)
                {
                    zz = gc(_GLF_IDENTITY(hit, (_GLF_IDENTITY(hit, (_GLF_IDENTITY(hit, (hit) * vec3(1.0, 1.0, 1.0))) * vec3(1.0, 1.0, 1.0))) + vec3(0.0, 0.0, 0.0)));
                    if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
                        {
                            if(_GLF_DEAD(_GLF_IDENTITY(_GLF_FALSE(false, (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, (injectionSwitch) + vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))).y)), _GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) || (_GLF_IDENTITY(_GLF_FALSE(_GLF_IDENTITY(false, (false) && _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y))), (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, (injectionSwitch) * vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y))).y)), (_GLF_IDENTITY(false, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(((false ? bvec3(false, true, false) : bvec3(true, true, false)) == bvec3(true))) : false)) ? _GLF_FUZZED(false) : _GLF_FALSE(_GLF_IDENTITY(false, (false) && _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y))), (injectionSwitch.x > injectionSwitch.y))))))))
                                {
                                    vec2 donor_replacementuv = _GLF_FUZZED(floor(fragCoord));
                                    const int donor_replacementITERATIONS = _GLF_FUZZED(ivec2(-78997).tst.r);
                                    {
                                        float GLF_live2h = _GLF_FUZZED(vec2(-9273.1630).gr.t);
                                        {
                                            GLF_live2hsbToRGB(fract(GLF_live2h), 1.0, (0.5 + (sin(GLF_live2time) * 0.5 + 0.5)));
                                        }
                                    }
                                    mat2 donor_replacementma = _GLF_FUZZED((mat2(-35.76, -7370.2805, -9719.7770, _GLF_IDENTITY(0.0, 0.0 + (_GLF_IDENTITY(0.0, (true ? 0.0 : _GLF_FUZZED((-- aspect))))))) * (matrixCompMult(mat2(3.3, -6.0, 71.80, -6.7), mat2(-6.2, -8.9, -894.976, 4.3)))));
                                    const int donor_replacementINNER_ITERS = _GLF_FUZZED((- ((_GLF_IDENTITY(54603, (false ? _GLF_FUZZED((+ ivec2(-90783, 31077).x)) : _GLF_IDENTITY(54603, 1 * (_GLF_IDENTITY(54603, 0 + (54603))))))) * (_GLF_IDENTITY(-22313, 1 * (-22313)) - -5018))));
                                    for(
                                        int i = 0;
                                        i < donor_replacementITERATIONS;
                                        i ++
                                    )
                                        {
                                            float s = float(i) * _GLF_IDENTITY(.035, (true ? _GLF_IDENTITY(.035, 0.0 + (_GLF_IDENTITY(.035, 0.0 + (.035)))) : _GLF_FUZZED(((aspect)))));
                                            vec3 p = _GLF_IDENTITY(s, 1.0 * (s)) * vec3(donor_replacementuv, 1.0 + sin(time * .015));
                                            {
                                                float GLF_live2s = _GLF_FUZZED((PI - PI));
                                                float GLF_live2b = _GLF_FUZZED((((PI) / N)));
                                                float GLF_live2h = _GLF_FUZZED((((N)) + -139.571));
                                                {
                                                    GLF_live2b * (1.0 - GLF_live2s) + (GLF_live2b - GLF_live2b * (1.0 - GLF_live2s)) * clamp(abs(abs(6.0 * (GLF_live2h - vec3(0, 1, 2) / 3.0)) - 3.0) - 1.0, 0.0, 1.0);
                                                }
                                            }
                                            p.xy *= donor_replacementma;
                                            p += _GLF_IDENTITY(vec3(.22, .3, s - 1.5 - sin(aspect * .13) * .1), (vec3(.22, .3, s - 1.5 - sin(aspect * .13) * .1)) * vec3(1.0, 1.0, 1.0));
                                            for(
                                                int i = 0;
                                                i < donor_replacementINNER_ITERS;
                                                i ++
                                            )
                                                {
                                                    p = abs(p) / dot(p, p) - PI;
                                                }
                                            aspect += dot(p, _GLF_IDENTITY(p, (_GLF_IDENTITY(p, vec3(1.0, 1.0, 1.0) * (_GLF_IDENTITY(p, vec3(0.0, 0.0, 0.0) + (p))))) + vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))) * .0015 * (2.8 + sin(length(donor_replacementuv.xy * 18.0) + .5 - aspect * .7));
                                            aspect += dot(_GLF_IDENTITY(p, _GLF_IDENTITY(vec3(0.0, 0.0, 0.0), (vec3(0.0, 0.0, 0.0)) + vec3(0.0, 0.0, 0.0)) + (_GLF_IDENTITY(p, (p) + vec3(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))))), p) * .0025 * (1.5 + sin(length(donor_replacementuv.xy * 13.5) + 1.21 - aspect * .3));
                                            aspect = length(_GLF_IDENTITY(p, vec3(0.0, 0.0, 0.0) + (p)).xy * _GLF_IDENTITY(.3, (true ? _GLF_IDENTITY(.3, (false ? _GLF_FUZZED(vec3(499.427, 245.671, -2.1)[2]) : .3)) : _GLF_FUZZED(((+ PI) + aspect))))) * .85;
                                        }
                                    {
                                        vec3 GLF_live0p = _GLF_FUZZED((+ (vec2(-5.4, 824.892) * mat2(-86.40, -0.6, -2.6, -8.6))).grr);
                                        const float GLF_live0FORMUPARAM = _GLF_FUZZED(vec3(1025.8164).t);
                                        const int GLF_live0INNER_ITERS = _GLF_FUZZED(37826);
                                        for(
                                            int GLF_live0i = 0;
                                            GLF_live0i < GLF_live0INNER_ITERS;
                                            GLF_live0i ++
                                        )
                                            {
                                                GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                            }
                                    }
                                }
                            {
                                vec2 GLF_live2square = _GLF_FUZZED((+ vec3(vec2(-1.0, 1.0), N)).bb);
                                vec3 GLF_live2setting = _GLF_FUZZED(vec4(-4978.5112, 5.2, -962.164, -5408.4741).sst.ptt.psp);
                                vec2 GLF_live2pixel = _GLF_FUZZED(((+ (N + mat2(-988.567, -4.5, -8.9, -5.3))) * ((- vec2(4713.6092, 7.7)) + (vec2(-1.7, 2.5) + 2.4))));
                                {
                                    if((GLF_live2pixel.x - (GLF_live2setting.x) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x) > GLF_live2square.y) && ! ((GLF_live2pixel.x - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.x && GLF_live2pixel.x + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.x && GLF_live2pixel.y - (GLF_live2setting.x - GLF_live2setting.y) < GLF_live2square.y && GLF_live2pixel.y + (GLF_live2setting.x - GLF_live2setting.y) > GLF_live2square.y)))
                                        GLF_live2colorFunc(GLF_live2setting.z / 40.0);
                                    vec3(0.0);
                                }
                            }
                            return vec3(_GLF_IDENTITY(1.0, (1.0) + 0.0));
                        }
                }
            else
                {
                    {
                        float GLF_live2a = _GLF_FUZZED(float(((- ivec4(-34805, 5724, -52504, -80039)) == (ivec4(-9600, -26363, 47322, 15608) - -24984))));
                        {
                            vec2 GLF_live2cs = vec2(cos(GLF_live2a), sin(GLF_live2a));
                            mat2(GLF_live2cs.x, - GLF_live2cs.y, GLF_live2cs.y, GLF_live2cs.x);
                        }
                    }
                    zz = getColor(trc.c);
                    if(_GLF_DEAD(_GLF_IDENTITY(false, true && (_GLF_IDENTITY(false, _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) && (_GLF_IDENTITY(false, (_GLF_IDENTITY(false, (false ? _GLF_FUZZED(true) : false))) && true)))))))
                        return vec3(1.0);
                    if(_GLF_DEAD(_GLF_FALSE(_GLF_IDENTITY(false, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED(((N + vec4(9199.8105, -5.3, 1.8, -875.049)) != (aspect * vec4(-9.5, 88.29, 5.0, -3529.6490)))) : _GLF_IDENTITY(false, (false ? _GLF_FUZZED((true || false)) : false)))), (injectionSwitch.x > injectionSwitch.y))))
                        {
                            vec2 donor_replacementuv = _GLF_FUZZED(_GLF_IDENTITY(max((- vec2(vec4(302.283, 69.59, -3463.5595, 9.8))), (+ vec4(-389.696, 283.604, 2.8, 1478.7285)).ag), (max((_GLF_IDENTITY(- _GLF_IDENTITY(vec2(vec4(302.283, 69.59, -3463.5595, _GLF_IDENTITY(9.8, 0.0 + (_GLF_IDENTITY(9.8, (9.8) * 1.0))))), (_GLF_IDENTITY(vec2(vec4(302.283, 69.59, -3463.5595, _GLF_IDENTITY(9.8, 0.0 + (_GLF_IDENTITY(9.8, (9.8) * 1.0))))), (vec2(vec4(_GLF_IDENTITY(302.283, (false ? _GLF_FUZZED(clamp(dot(aspect, -702.363), (aspect), length(PI))) : 302.283)), 69.59, -3463.5595, _GLF_IDENTITY(9.8, 0.0 + (_GLF_IDENTITY(9.8, (9.8) * 1.0)))))) * vec2(1.0, 1.0))) + _GLF_IDENTITY(vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)), vec2(0.0, 0.0) + (vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x))))), (- _GLF_IDENTITY(vec2(vec4(302.283, 69.59, -3463.5595, _GLF_IDENTITY(9.8, 0.0 + (_GLF_IDENTITY(9.8, (9.8) * 1.0))))), (_GLF_IDENTITY(vec2(vec4(302.283, 69.59, -3463.5595, _GLF_IDENTITY(9.8, 0.0 + (_GLF_IDENTITY(9.8, (9.8) * 1.0))))), (vec2(vec4(_GLF_IDENTITY(302.283, (false ? _GLF_FUZZED(clamp(dot(aspect, -702.363), (aspect), length(PI))) : 302.283)), 69.59, -3463.5595, _GLF_IDENTITY(9.8, 0.0 + (_GLF_IDENTITY(9.8, (9.8) * 1.0)))))) * vec2(1.0, 1.0))) + _GLF_IDENTITY(vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)), vec2(0.0, 0.0) + (vec2(_GLF_ZERO(0.0, injectionSwitch.x), _GLF_ZERO(0.0, injectionSwitch.x)))))) + vec2(0.0, 0.0))), (_GLF_IDENTITY(+ vec4(_GLF_IDENTITY(-389.696, _GLF_ONE(1.0, injectionSwitch.y) * (-389.696)), 283.604, _GLF_IDENTITY(2.8, (_GLF_IDENTITY(2.8, _GLF_ONE(1.0, injectionSwitch.y) * (2.8))) * _GLF_IDENTITY(1.0, 0.0 + (1.0))), 1478.7285), _GLF_IDENTITY(vec4(1.0, 1.0, 1.0, 1.0), vec4(1.0, 1.0, 1.0, 1.0) * (vec4(1.0, 1.0, 1.0, 1.0))) * (+ vec4(_GLF_IDENTITY(-389.696, _GLF_ONE(1.0, injectionSwitch.y) * (-389.696)), 283.604, _GLF_IDENTITY(2.8, (_GLF_IDENTITY(2.8, _GLF_ONE(1.0, injectionSwitch.y) * (2.8))) * _GLF_IDENTITY(1.0, 0.0 + (1.0))), 1478.7285)))).ag)) * vec2(1.0, 1.0)));
                            const int donor_replacementITERATIONS = _GLF_FUZZED(((21567 / -47912) + _GLF_IDENTITY(ivec3(-54930, -86590, -48396), (ivec3(-54930, -86590, -48396)) + ivec3(0, 0, 0)).pts).x);
                            mat2 donor_replacementma = _GLF_FUZZED((- (distance(_GLF_IDENTITY(-480.507, (_GLF_IDENTITY(-480.507, (-480.507) * 1.0)) + _GLF_IDENTITY(_GLF_ZERO(0.0, injectionSwitch.x), (false ? _GLF_FUZZED(PI) : _GLF_ZERO(0.0, injectionSwitch.x)))), -588.302) + (mat2(413.137, -7.6, -2.9, -1.6) / mat2(_GLF_IDENTITY(-4.0, _GLF_ONE(1.0, injectionSwitch.y) * (_GLF_IDENTITY(-4.0, 0.0 + (-4.0)))), 146.287, 5.0, _GLF_IDENTITY(303.867, _GLF_ZERO(0.0, injectionSwitch.x) + (_GLF_IDENTITY(303.867, (false ? _GLF_FUZZED(zz.z) : 303.867)))))))));
                            const int donor_replacementINNER_ITERS = _GLF_FUZZED((+ ((bvec3(false, true, _GLF_IDENTITY(true, (_GLF_IDENTITY(true, (false ? _GLF_FUZZED((vec2(7.3, 76.02) == vec2(4448.5424, -37.73))) : true))) && _GLF_IDENTITY(true, true && (true)))) == bvec3(true, true, true)) ? (-89668 * _GLF_IDENTITY(-70128, _GLF_IDENTITY(0, (false ? _GLF_FUZZED(donor_replacementITERATIONS) : 0)) + (_GLF_IDENTITY(-70128, (false ? _GLF_FUZZED(donor_replacementITERATIONS) : _GLF_IDENTITY(-70128, 0 + (-70128))))))) : _GLF_IDENTITY(ivec3(-4754, -75264, 10279).z, (_GLF_IDENTITY(ivec3(-4754, -75264, 10279).z, (ivec3(-4754, _GLF_IDENTITY(-75264, (_GLF_IDENTITY(-75264, (true ? -75264 : _GLF_FUZZED(int(-8984.8039))))) + _GLF_IDENTITY(0, 0 + (0))), 10279).z) + 0)) * 1))));
                            for(
                                int i = 0;
                                i < donor_replacementITERATIONS;
                                i ++
                            )
                                {
                                    float s = float(i) * .035;
                                    vec3 p = s * vec3(_GLF_IDENTITY(donor_replacementuv, vec2(0.0, 0.0) + (_GLF_IDENTITY(donor_replacementuv, (donor_replacementuv) * vec2(1.0, 1.0)))), 1.0 + sin(time * .015));
                                    p.xy *= donor_replacementma;
                                    p += _GLF_IDENTITY(vec3(_GLF_IDENTITY(.22, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED((vec4(-7.7, 6864.3440, -54.17, -608.508) + aspect).r) : _GLF_IDENTITY(.22, 1.0 * (.22)))), .3, s - 1.5 - sin(aspect * _GLF_IDENTITY(.13, (true ? .13 : _GLF_FUZZED((++ ro.z))))) * .1), (vec3(_GLF_IDENTITY(.22, (_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) ? _GLF_FUZZED((vec4(-7.7, 6864.3440, -54.17, -608.508) + aspect).r) : _GLF_IDENTITY(.22, 1.0 * (.22)))), .3, s - 1.5 - sin(_GLF_IDENTITY(aspect * _GLF_IDENTITY(.13, (true ? .13 : _GLF_FUZZED((++ ro.z)))), (aspect * _GLF_IDENTITY(.13, (true ? .13 : _GLF_FUZZED((++ ro.z))))) + 0.0)) * .1)) + vec3(0.0, 0.0, 0.0));
                                    for(
                                        int i = 0;
                                        i < donor_replacementINNER_ITERS;
                                        i ++
                                    )
                                        {
                                            p = abs(p) / dot(p, p) - PI;
                                            {
                                                vec3 GLF_live0p = _GLF_FUZZED(vec3((+ vec3(-60.69, 476.083, 9744.6570)).p, vec4(vec3(-34.22, -8167.4418, 984.220), PI).x, (vec3(796.084, 4854.7190, 844.123) * 58.54).r));
                                                const float GLF_live0FORMUPARAM = _GLF_FUZZED((- float((mat3(-81.58, -518.124, 4.8, 4.8, 89.74, -22.72, -39.42, 7.8, 84.12) == mat3(-118.172, 97.07, -6.0, -697.061, -7.8, -9.5, -1.2, 0.9, 75.44)))));
                                                const int GLF_live0INNER_ITERS = _GLF_FUZZED(int(((823 / ivec2(64395, 78912)) != (donor_replacementINNER_ITERS + ivec2(-24464, -87337)))));
                                                for(
                                                    int GLF_live0i = 0;
                                                    GLF_live0i < GLF_live0INNER_ITERS;
                                                    GLF_live0i ++
                                                )
                                                    {
                                                        GLF_live0p = abs(GLF_live0p) / dot(GLF_live0p, GLF_live0p) - GLF_live0FORMUPARAM;
                                                    }
                                            }
                                        }
                                    {
                                        float GLF_live2h = _GLF_FUZZED(N);
                                        {
                                            GLF_live2hsbToRGB(fract(GLF_live2h), 1.0, (0.5 + (sin(GLF_live2time) * 0.5 + 0.5)));
                                        }
                                    }
                                    aspect += dot(p, p) * .0015 * (2.8 + sin(length(_GLF_IDENTITY(donor_replacementuv, vec2(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (donor_replacementuv)).xy * 18.0) + _GLF_IDENTITY(.5, 1.0 * (_GLF_IDENTITY(.5, 1.0 * (.5)))) - _GLF_IDENTITY(aspect * .7, (true ? _GLF_IDENTITY(aspect * .7, 0.0 + (aspect * _GLF_IDENTITY(.7, (_GLF_IDENTITY(true, true && (true)) ? _GLF_IDENTITY(.7, 0.0 + (.7)) : _GLF_FUZZED(length(fract(time))))))) : _GLF_FUZZED(mix(p, p, aspect).s)))));
                                    aspect += dot(p, p) * .0025 * (_GLF_IDENTITY(1.5, (_GLF_IDENTITY(false, (false) && true) ? _GLF_FUZZED(dot(ro.sspp, vec4(cur, aspect, aspect))) : _GLF_IDENTITY(1.5, (false ? _GLF_FUZZED(sign(time)) : 1.5)))) + sin(length(donor_replacementuv.xy * 13.5) + 1.21 - aspect * _GLF_IDENTITY(.3, _GLF_ZERO(0.0, injectionSwitch.x) + (_GLF_IDENTITY(.3, (true ? .3 : _GLF_FUZZED(faceforward(aspect, time, -37.34))))))));
                                    aspect = length(p.xy * .3) * .85;
                                    {
                                        float GLF_live2h = _GLF_FUZZED(((PI - -3.6) - vec3(9.7, 757.522, -4931.3226).zzzy).s);
                                        {
                                            GLF_live2hsbToRGB(fract(GLF_live2h), 1.0, (0.5 + (sin(GLF_live2time) * 0.5 + 0.5)));
                                        }
                                    }
                                }
                        }
                }
            {
                vec4 GLF_live2gl_FragColor = _GLF_FUZZED((- (vec4(vec3(-73.26, -124.150, -716.578), 311.677) / vec2(9.6, 7.4).t)));
                vec4 GLF_live2gl_FragCoord = _GLF_FUZZED(vec4(N, (+ PI), vec2((true ? 2618.2192 : PI))));
                {
                    float GLF_live2angle = sin(GLF_live2time) * 0.1;
                    mat2 GLF_live2rotation = mat2(cos(GLF_live2angle), - sin(GLF_live2angle), sin(GLF_live2angle), cos(GLF_live2angle));
                    vec2 GLF_live2aspect = GLF_live2resolution.xy / min(GLF_live2resolution.x, GLF_live2resolution.y);
                    vec2 GLF_live2position = (GLF_live2gl_FragCoord.xy / GLF_live2resolution.xy) * GLF_live2aspect;
                    vec2 GLF_live2center = vec2(0.5) * GLF_live2aspect;
                    GLF_live2position *= GLF_live2rotation;
                    GLF_live2center *= GLF_live2rotation;
                    vec3 GLF_live2color = vec3(0.0);
                    for(
                        int GLF_live2i = 0;
                        GLF_live2i < 40;
                        GLF_live2i ++
                    )
                        {
                            vec3 GLF_live2d = GLF_live2drawShape(GLF_live2position, GLF_live2center + vec2(sin(float(GLF_live2i) / 10.0 + GLF_live2time) / 4.0, 0.0), vec3(0.01 + sin(float(GLF_live2i) / 100.0), 0.01, float(GLF_live2i)));
                            if(length(GLF_live2d) != 0.0)
                                GLF_live2color = GLF_live2d;
                        }
                    GLF_live2gl_FragColor = vec4(GLF_live2color, 1.0);
                }
            }
            zz *= max(dot(trc.n, normalize(vec3(_GLF_IDENTITY(0, (true ? 0 : _GLF_FUZZED((ivec2(82957, -33989)[0])))), 1, 0))), 0.0);
        }
    if(_GLF_DEAD(_GLF_FALSE(_GLF_IDENTITY(false, (_GLF_IDENTITY(false, (_GLF_IDENTITY(false, (true ? false : _GLF_FUZZED(true)))) && true)) && true), (injectionSwitch.x > injectionSwitch.y))))
        return vec3(_GLF_IDENTITY(1.0, (_GLF_IDENTITY(1.0, (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? 1.0 : _GLF_FUZZED(vec4(-8.8, 15.65, -34.08, -63.78).b)))) * 1.0));
    return zz;
    if(_GLF_DEAD(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y))))
        {
            if(_GLF_WRAPPED_IF_TRUE(_GLF_IDENTITY(true, (_GLF_IDENTITY(true, (_GLF_IDENTITY(false ? _GLF_FUZZED(true) : _GLF_IDENTITY(true, (_GLF_IDENTITY(true, (true) && true)) && true), false || (_GLF_IDENTITY(false ? _GLF_FUZZED(true) : _GLF_IDENTITY(true, (_GLF_IDENTITY(true, (true) && true)) && true), (false ? _GLF_FUZZED(true) : _GLF_IDENTITY(true, (_GLF_IDENTITY(true, (true) && true)) && true)) && true)))))) && true)))
                {
                    if(_GLF_DEAD(_GLF_IDENTITY(_GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)), (_GLF_FALSE(_GLF_IDENTITY(false, _GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) && (_GLF_IDENTITY(false, _GLF_FALSE(false, (injectionSwitch.x > injectionSwitch.y)) || (false)))), (injectionSwitch.x > _GLF_IDENTITY(injectionSwitch, vec2(1.0, 1.0) * (_GLF_IDENTITY(injectionSwitch, vec2(1.0, 1.0) * (_GLF_IDENTITY(injectionSwitch, (injectionSwitch) * vec2(1.0, 1.0)))))).y))) && true)))
                        {
                            {
                                return aspect * (_GLF_IDENTITY(1.0 - aspect, 1.0 * (_GLF_IDENTITY(1.0 - aspect, (_GLF_IDENTITY(1.0 - aspect, (false ? _GLF_FUZZED(PI) : 1.0 - aspect))) + 0.0)))) + (_GLF_IDENTITY(aspect, 0.0 + (_GLF_IDENTITY(aspect, (aspect) * _GLF_ONE(1.0, injectionSwitch.y)))) - aspect * _GLF_IDENTITY((1.0 - aspect), _GLF_ZERO(0.0, injectionSwitch.x) + (_GLF_IDENTITY((1.0 - aspect), (_GLF_TRUE(true, (injectionSwitch.x < injectionSwitch.y)) ? (_GLF_IDENTITY(1.0 - aspect, 0.0 + (_GLF_IDENTITY(1.0 - aspect, (false ? _GLF_FUZZED(reflect(N, PI)) : 1.0 - aspect))))) : _GLF_FUZZED(ceil(-330.462))))))) * clamp(_GLF_IDENTITY(abs(abs(_GLF_IDENTITY(6.0, (6.0) * _GLF_ONE(1.0, injectionSwitch.y)) * (_GLF_IDENTITY(aspect, _GLF_ZERO(0.0, injectionSwitch.x) + (aspect)) - _GLF_IDENTITY(vec3(0, 1, 2) / 3.0, _GLF_IDENTITY(vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)), vec3(0.0, 0.0, 0.0) + (vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) * (_GLF_IDENTITY(vec3(0, 1, 2) / 3.0, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (vec3(0, 1, 2) / 3.0)))))) - 3.0) - 1.0, (abs(_GLF_IDENTITY(abs(_GLF_IDENTITY(6.0, (6.0) * _GLF_ONE(1.0, injectionSwitch.y)) * (_GLF_IDENTITY(_GLF_IDENTITY(aspect, _GLF_ZERO(0.0, injectionSwitch.x) + (aspect)) - _GLF_IDENTITY(vec3(0, 1, 2) / 3.0, _GLF_IDENTITY(vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)), vec3(0.0, 0.0, 0.0) + (vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) * (_GLF_IDENTITY(vec3(0, 1, 2) / 3.0, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (vec3(0, 1, 2) / 3.0)))), (_GLF_IDENTITY(_GLF_IDENTITY(aspect, _GLF_ZERO(0.0, injectionSwitch.x) + (aspect)) - _GLF_IDENTITY(vec3(0, 1, 2) / 3.0, _GLF_IDENTITY(vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)), vec3(0.0, 0.0, 0.0) + (vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) * (_GLF_IDENTITY(vec3(0, 1, 2) / 3.0, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (vec3(0, 1, 2) / 3.0)))), (_GLF_IDENTITY(aspect, _GLF_ZERO(0.0, injectionSwitch.x) + (aspect)) - _GLF_IDENTITY(vec3(0, 1, 2) / 3.0, _GLF_IDENTITY(vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)), vec3(0.0, 0.0, 0.0) + (vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) * (_GLF_IDENTITY(vec3(0, 1, 2) / 3.0, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (vec3(0, 1, 2) / 3.0))))) + vec3(0.0, 0.0, 0.0))) + vec3(0.0, 0.0, 0.0)))) - 3.0, (abs(_GLF_IDENTITY(6.0, (6.0) * _GLF_ONE(1.0, injectionSwitch.y)) * (_GLF_IDENTITY(_GLF_IDENTITY(aspect, _GLF_ZERO(0.0, injectionSwitch.x) + (aspect)) - _GLF_IDENTITY(vec3(0, 1, 2) / 3.0, _GLF_IDENTITY(vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)), vec3(0.0, 0.0, 0.0) + (vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) * (_GLF_IDENTITY(vec3(0, 1, 2) / 3.0, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (vec3(0, 1, 2) / 3.0)))), (_GLF_IDENTITY(_GLF_IDENTITY(aspect, _GLF_ZERO(0.0, injectionSwitch.x) + (aspect)) - _GLF_IDENTITY(vec3(0, 1, 2) / 3.0, _GLF_IDENTITY(vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)), vec3(0.0, 0.0, 0.0) + (vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) * (_GLF_IDENTITY(vec3(0, 1, 2) / 3.0, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (vec3(0, 1, 2) / 3.0)))), (_GLF_IDENTITY(aspect, _GLF_ZERO(0.0, injectionSwitch.x) + (aspect)) - _GLF_IDENTITY(vec3(0, 1, 2) / 3.0, _GLF_IDENTITY(vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)), vec3(0.0, 0.0, 0.0) + (vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) * (_GLF_IDENTITY(vec3(0, 1, 2) / 3.0, vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)) * (vec3(0, 1, 2) / 3.0))))) + vec3(0.0, 0.0, 0.0))) + vec3(0.0, 0.0, 0.0)))) - 3.0) * vec3(_GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y), _GLF_ONE(1.0, injectionSwitch.y)))) - 1.0) * vec3(1.0, 1.0, 1.0)), _GLF_IDENTITY(0.0, 1.0 * (_GLF_IDENTITY(0.0, (_GLF_IDENTITY(0.0, (true ? 0.0 : _GLF_FUZZED(time)))) + 0.0))), 1.0);
                            }
                        }
                }
            else
                {
                }
            return vec3(1.0);
        }
}
void main()
{
    gl_FragColor = vec4(go(_GLF_IDENTITY(gl_FragCoord, (_GLF_IDENTITY(gl_FragCoord, (gl_FragCoord) * vec4(1.0, 1.0, 1.0, 1.0))) * vec4(1.0, 1.0, 1.0, 1.0)).xy), 1);
}
