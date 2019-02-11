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

uniform vec3 iResolution;

uniform float iGlobalTime;

uniform vec4 iMouse;

struct Datum {
    vec3 location;
    float distance;
    float material;
};
mat4 Ry(float angle)
{
    float c = cos(angle);
    float s = sin(angle);
    return mat4(vec4(c, 0, - s, 0), vec4(0, 1, 0, 0), vec4(s, 0, c, 0), vec4(0, 0, 0, 1));
}
mat4 Rz(float angle)
{
    float c = cos(angle);
    float s = sin(angle);
    return mat4(vec4(c, s, 0, 0), vec4(- s, c, 0, 0), vec4(0, 0, 1, 0), vec4(0, 0, 0, 1));
}
mat4 Disp(vec3 displacement)
{
    return mat4(vec4(1, 0, 0, 0), vec4(0, 1, 0, 0), vec4(0, 0, 1, 0), vec4(displacement, 1));
}
float plasma2(vec2 uv)
{
    uv *= 2.;
    float v = sin((uv.x + iGlobalTime));
    v += sin((uv.y + iGlobalTime) / 2.0);
    v += sin((uv.x + uv.y + iGlobalTime) / 2.0);
    vec2 c = uv / 2.0 * vec2(sin(iGlobalTime / 3.0), cos(iGlobalTime / 2.0));
    v += sin(sqrt(c.x * c.x + c.y * c.y + 1.0) + iGlobalTime);
    v = v / 2.0;
    return v;
}
vec2 opU(float d1, float d2, float m1, float m2)
{
    return (d1 < d2) ? vec2(d1, m1) : vec2(d2, m2);
}
float sdCappedCylinder(vec3 p, vec2 h)
{
    vec2 d = abs(vec2(length(p.xz), p.y)) - h;
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}
float sdPlane(vec3 p)
{
    return p.y;
}
float pudBox(vec3 p, vec3 b)
{
    float dist = max(length(max(abs(p) - b, 0.0)) - 1., - 1. * (length(p)));
    return dist - plasma2(5. * p.xy) / 6.;
}
mat4 branch(float a_y, float a_z, float height, float b_len)
{
    mat4 dd = Disp(vec3(b_len * sin(a_z), - height + b_len * cos(a_z), 0));
    return Rz(a_z) * dd * Ry(a_y);
}
float tree(vec3 p)
{
    vec3 GLF_merged1_0_1_3phi;
    vec4 GLF_merged2_1_1_1_2_1_3cphi;
    vec4 GLF_merged1_3_1_1t;
    vec4 GLF_merged1_3_1_4b1_a;
    vec4 GLF_merged1_3_1_3phi;
    vec3 GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t;
    GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t = GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t;
    vec4 hom = vec4(p, 1);
    float b1_a = 2.0;
    GLF_merged1_3_1_4b1_a.w = b1_a;
    GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.x = GLF_merged1_3_1_4b1_a.w;
    float phi = iGlobalTime;
    GLF_merged1_0_1_3phi.x = phi;
    GLF_merged2_1_1_1_2_1_3cphi.z = GLF_merged1_0_1_3phi.x;
    GLF_merged1_3_1_3phi.w = GLF_merged2_1_1_1_2_1_3cphi.z;
    float replication = clamp((iGlobalTime + 2.) / 2., 3., 7.);
    mat4 b1_f = branch(3.14159265 / replication - 3.14159265, GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.x, 3., 9.);
    float r2 = clamp((iGlobalTime + 12.) / 2., 3., 5.);
    ;
    float b2_a = 1.1;
    float b2_size = clamp(iGlobalTime + 1., 1., 5.);
    float c = clamp(iGlobalTime + 2., 1., 5.);
    GLF_merged2_1_1_1_2_1_3cphi.y = c;
    mat4 b2_f = branch(3.14159265 / r2 - 3.14159265, b2_a, - 6., b2_size);
    hom = Ry(- GLF_merged1_3_1_3phi.w) * hom;
    float theta = atan(hom.z, hom.x);
    float t = theta;
    GLF_merged1_3_1_1t.w = t;
    GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z = GLF_merged1_3_1_1t.w;
    GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z = mod(GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z, 2. * 3.14159265);
    float r = length(hom.xz);
    vec3 new_p = vec3(r * cos(GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z), hom.y, r * sin(GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z));
    if((GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z > 2. * 3.14159265 / replication - .1 && GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z < 2. * 3.14159265 / replication + .1) || GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z < .1 && GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z > - .1)
        return 15.;
    hom = Ry(- GLF_merged1_3_1_3phi.w) * b1_f * vec4(new_p, 1);
    theta = atan(hom.z, hom.x);
    GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z = theta;
    GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z = mod(GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z, 2. * 3.14159265);
    r = length(hom.xz);
    vec3 new_p2 = vec3(r * cos(GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z), hom.y, r * sin(GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.z));
    new_p2 = new_p2;
    vec4 b1_p = b1_f * vec4(new_p, 1);
    vec4 b2_p = b2_f * vec4(new_p2, 1);
    vec4 b3_p = Disp(vec3(0., b2_size - 2., 0)) * b2_p;
    float trunk = sdCappedCylinder(p, vec2(3., 6));
    trunk = GLF_merged1_0_1_3phi.z;
    float b1 = sdCappedCylinder(b1_p.xyz, vec2(.5, 6));
    float b2 = sdCappedCylinder(b2_p.xyz, vec2(.25, b2_size));
    float b3 = pudBox(b3_p.xyz, vec3(.5, .5, .5));
    GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.y = b3;
    return min(GLF_merged3_0_1_4_1_1_2_2_1_1b1_ab3t.y, min(b2, min(b1, trunk)));
}
vec2 scene(vec3 ray)
{
    float planeMat = .4;
    float boxMat = .1;
    return opU(tree(ray), sdPlane(ray), boxMat, planeMat);
}
Datum trace(vec3 ray, vec3 viewportxy)
{
    const float hitThreshold = 0.001;
    vec3 p = vec3(0);
    float t = 0.0;
    float m = 0.0;
    for(
        int i = 0;
        i < 100;
        ++ i
    )
        {
            p = viewportxy + (t * ray);
            vec2 data = scene(p);
            float dist = data.x;
            m = data.y;
            t += dist;
            if(dist < hitThreshold)
                {
                    break;
                }
        }
    return Datum(p, t, m);
}
vec3 calcNormal(in vec3 pos)
{
    vec3 eps = vec3(0.001, 0.0, 0.0);
    vec3 nor = vec3(scene(pos + eps.xyy).x - scene(pos - eps.xyy).x, scene(pos + eps.yxy).x - scene(pos - eps.yxy).x, scene(pos + eps.yyx).x - scene(pos - eps.yyx).x);
    return normalize(nor);
}
mat4 LookAtRH(vec3 eye, vec3 target, vec3 up)
{
    vec3 zaxis = normalize(target - eye);
    vec3 xaxis = normalize(cross(up, zaxis));
    vec3 yaxis = cross(zaxis, xaxis);
    return mat4(vec4(xaxis, 0), vec4(yaxis, 0), vec4(zaxis, 0), vec4(eye, 1));
}
float f(in vec2 p)
{
    return sin(p.x + sin(p.y + iGlobalTime * 0.1)) * sin(p.y * p.x * 0.1 + iGlobalTime * 0.2);
}
vec2 field(in vec2 p)
{
    vec2 ep = vec2(.05, 0.);
    vec2 rz = vec2(0);
    for(
        int i = 0;
        i < 7;
        i ++
    )
        {
            float t0 = f(p);
            float t1 = f(p + ep.xy);
            float t2 = f(p + ep.yx);
            vec2 g = vec2((t1 - t0), (t2 - t0)) / ep.xx;
            vec2 t = vec2(- g.y, g.x);
            p += .9 * t + g * 0.3;
            rz = t;
        }
    return rz;
}
vec3 shade(vec3 pos, vec3 nrm, vec4 light)
{
    vec3 toLight = light.xyz - pos;
    float toLightLen = length(toLight);
    toLight = normalize(toLight);
    float comb = 0.1;
    float vis = 1.;
    if(vis > 0.0)
        {
            float diff = 2.0 * max(0.0, dot(nrm, toLight));
            float attn = 1.0 - pow(min(1.0, toLightLen / light.w), 2.0);
            comb += diff * attn * vis;
        }
    return vec3(comb, comb, comb);
}
vec4 color(float t, float m, vec3 p, vec2 uv)
{
    float v = plasma2(5. * p.xy);
    t = clamp(t, - 5., 5.);
    vec3 col = 5. * (1. / t) * vec3(1, sin(3.14 * v), cos(3.14 * v));
    col *= .8;
    if(length(p) > 100.)
        {
            float v2 = plasma2(uv * 20.);
            col = .5 * vec3(sin(v2), sin(v2 + iGlobalTime), sin(v2 + 7.));
            return vec4(col, 0);
        }
    if(abs(m - .4) < .001)
        {
            vec2 fld = field(p.xz * .10);
            col = sin(vec3(- .3, 0.1, 0.5) + fld.x - fld.y) * 0.65 + 0.35;
        }
    vec3 nrm = calcNormal(p);
    vec4 light1 = vec4(20., 20., - 10.0, 40.0);
    vec3 cc = shade(p, nrm, light1);
    return cc.x * vec4(col, 0);
}
void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec2 uv = fragCoord.xy / iResolution.xy;
    uv = uv * 2.0 - 1.0;
    uv.x *= iResolution.x / iResolution.y;
    vec2 mo = iMouse.xy / iResolution.xy;
    float f = 3.;
    f = f;
    vec4 pixel = vec4(uv, 0, 1);
    vec3 position = vec3(0, 0, - 40.);
    position += vec3(20. * cos(iGlobalTime / 5.), 30., - 60. * abs(sin(iGlobalTime / 5.)));
    position += vec3(iMouse.x / 10., 0, iMouse.y / 10.);
    position = vec3(0, 40, 1);
    mat4 mat = LookAtRH(position, vec3(0, 0, 0), vec3(0, 1, 0));
    vec3 pt = (mat * pixel).xyz;
    vec3 ray = normalize(mat * (pixel - vec4(0, 0, - f, 1))).xyz;
    Datum d = trace(ray, pt);
    fragColor = color(d.distance, d.material, d.location, uv);
}
void main(void)
{
    vec2 shadertoy_FragCoord = gl_FragCoord.xy;
    vec4 shadertoy_FragColor;
    mainImage(shadertoy_FragColor, shadertoy_FragCoord);
    gl_FragColor = shadertoy_FragColor;
    gl_FragColor.a = 1.0;
}
