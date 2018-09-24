#version 300 es

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

#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
precision highp int;
#else
precision mediump float;
precision mediump int;
#endif
#endif

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

layout(location = 0) out vec4 _GLF_color;

uniform vec2 injectionSwitch;

uniform vec2 resolution;

uniform float GLF_live3iGlobalTime;

const float GLF_live3spheresCount = 40.0;

const float GLF_live3spread = 6.0;

const float GLF_live3initialDistance = 300.0;

const float GLF_live3aureole = 2.5;

const float GLF_live3speed = 0.05;

const float GLF_live3initialSize = 0.5;

vec4 GLF_live3getSphere(float GLF_live3sphereNo, float GLF_live3time)
{
 float GLF_live3lifetimedelta = GLF_live3sphereNo / GLF_live3spheresCount;
 float GLF_live3lifetimeDiv = floor(GLF_live3time + GLF_live3lifetimedelta);
 float GLF_live3lifetimeFrac = GLF_live3time + GLF_live3lifetimedelta - GLF_live3lifetimeDiv;
 ;
 float GLF_live3x = sin(GLF_live3sphereNo / 6.0) * GLF_live3spread;
 float GLF_live3y = sin(GLF_live3sphereNo / 7.0) * GLF_live3spread;
 return vec4(GLF_live3x, GLF_live3y, GLF_live3initialDistance - GLF_live3lifetimeFrac * GLF_live3initialDistance, abs(sin(GLF_live3sphereNo * 3.0)) * GLF_live3initialSize + GLF_live3initialSize);
}
vec3 GLF_live3intersects(vec3 GLF_live3src, vec3 GLF_live3direction, float GLF_live3globalTime)
{
 float GLF_live3alpha = sin(GLF_live3iGlobalTime * GLF_live3speed * 2.0) * 0.1;
 mat2 GLF_live3rotation = mat2(cos(GLF_live3alpha), - sin(GLF_live3alpha), sin(GLF_live3alpha), cos(GLF_live3alpha));
 float GLF_live3z = 0.0;
 float GLF_live3minDistance = GLF_live3initialDistance;
 vec4 GLF_live3final_sphere;
 vec3 GLF_live3final_point;
 vec3 GLF_live3normDirection = normalize(GLF_live3direction);
 float GLF_live3aura = 0.0;
 {
  int GLF_live3_looplimiter0 = 0;
  for(
      float GLF_live3sphereNo = 0.0;
      GLF_live3sphereNo < GLF_live3spheresCount;
      GLF_live3sphereNo ++
  )
   {
    if(GLF_live3_looplimiter0 >= 5)
     {
      break;
     }
    GLF_live3_looplimiter0 ++;
    vec4 GLF_live3sphere = GLF_live3getSphere(GLF_live3sphereNo, GLF_live3globalTime);
    vec4 GLF_live3oldSphere = GLF_live3sphere;
    GLF_live3sphere.xz = GLF_live3sphere.xz * GLF_live3rotation;
    vec3 GLF_live3tcenter = GLF_live3sphere.xyz - GLF_live3src;
    float GLF_live3centerProjectionLength = dot(GLF_live3tcenter, GLF_live3normDirection);
    vec3 GLF_live3projectionPoint = GLF_live3centerProjectionLength * GLF_live3normDirection;
    vec3 GLF_live3projection = GLF_live3sphere.xyz - GLF_live3projectionPoint;
    float GLF_live3l = length(GLF_live3projection);
    if(GLF_live3l < GLF_live3aureole)
     {
      GLF_live3aura += pow(((GLF_live3aureole - GLF_live3l) / GLF_live3aureole), 3.0) * (1.0 - smoothstep(0.0, GLF_live3initialDistance, GLF_live3oldSphere.z) / 4.0);
     }
   }
 }
 vec3 GLF_live3result = vec3(0, 0, 0);
 vec3 GLF_live3light = vec3(0, 0, 1);
 vec3 GLF_live3mapped = GLF_live3final_point;
 vec3 GLF_live3compSphere = vec3(pow(sin(sin(GLF_live3mapped.x * 30.0) * sin(GLF_live3final_sphere.x * 100.0) * 20.0 + sin(GLF_live3mapped.y * 20.0) * 2.0 + sin((GLF_live3mapped.x + GLF_live3mapped.y) * 20.0) * 3.0), 4.0), pow(abs(sin(GLF_live3mapped.y * (4.0 + sin(GLF_live3final_sphere.y * 10.0) * 10.0) + GLF_live3mapped.x * 4.0 + sin(GLF_live3mapped.x * 5.0 + GLF_live3mapped.y) * 1.0)), 50.0) / 2.0, pow(abs(sin(GLF_live3mapped.x + sin(GLF_live3mapped.y * 4.0 + GLF_live3final_sphere.a * 100.0) * 2.0)), 40.0));
 vec3 GLF_live3aureole = vec3(GLF_live3aura, GLF_live3aura / 2.0, GLF_live3aura / 4.0) / 3.0;
 float GLF_live3space = (pow(abs(dot(normalize(GLF_live3direction), normalize(GLF_live3light))), 12.0));
 vec3 GLF_live3compSpace = vec3(GLF_live3space, GLF_live3space, GLF_live3space);
 float GLF_live3blend = smoothstep(0.0, GLF_live3initialDistance, GLF_live3z);
 return GLF_live3result;
}
vec3 pickColor(int i)
{
 return vec3(float(i) / 100.0);
}
vec3 mand(float xCoord, float yCoord)
{
 float height = resolution.y;
 float width = resolution.x;
 float c_re = (xCoord - width / 2.0) * 4.0 / width;
 float c_im = (yCoord - height / 2.0) * 4.0 / width;
 float x = 0.0, y = 0.0;
 int iteration = 0;
 for(
     int k = 0;
     k < 1000;
     k ++
 )
  {
   if(x * x + y * y > 4.0)
    {
     break;
    }
   float x_new = x * x - y * y + c_re;
   y = 2.0 * x * y + c_im;
   x = x_new;
   iteration ++;
   ;
  }
 if(iteration < 1000)
  {
   {
    const float GLF_live3aureole = 908.704;
    const float GLF_live3speed = 34.38;
    vec3 GLF_live3direction = vec3(1.0);
    const float GLF_live3spheresCount = 571.661;
    const float GLF_live3initialDistance = 5.3;
    float GLF_live3globalTime = 3987.7206;
    vec3 GLF_live3src = vec3(1.0);
    {
     float GLF_live3alpha = sin(GLF_live3iGlobalTime * GLF_live3speed * 2.0) * 0.1;
     mat2 GLF_live3rotation = mat2(cos(GLF_live3alpha), - sin(GLF_live3alpha), sin(GLF_live3alpha), cos(GLF_live3alpha));
     float GLF_live3z = 0.0;
     float GLF_live3minDistance = GLF_live3initialDistance;
     vec4 GLF_live3final_sphere;
     vec3 GLF_live3final_point;
     vec3 GLF_live3normDirection = normalize(GLF_live3direction);
     float GLF_live3aura = 0.0;
     vec3 GLF_live3result = vec3(0, 0, 0);
     vec3 GLF_live3light = vec3(0, 0, 1);
     vec3 GLF_live3mapped = GLF_live3final_point;
     ;
     vec3 GLF_live3compSphere = vec3(pow(sin(sin(GLF_live3mapped.x * 30.0) * sin(GLF_live3final_sphere.x * 100.0) * 20.0 + sin(GLF_live3mapped.y * 20.0) * 2.0 + sin((GLF_live3mapped.x + GLF_live3mapped.y) * 20.0) * 3.0), 4.0), pow(abs(sin(GLF_live3mapped.y * (4.0 + sin(GLF_live3final_sphere.y * 10.0) * 10.0) + GLF_live3mapped.x * 4.0 + sin(GLF_live3mapped.x * 5.0 + GLF_live3mapped.y) * 1.0)), 50.0) / 2.0, pow(abs(sin(GLF_live3mapped.x + sin(GLF_live3mapped.y * 4.0 + GLF_live3final_sphere.a * 100.0) * 2.0)), 40.0));
     vec3 GLF_live3aureole = vec3(GLF_live3aura, GLF_live3aura / 2.0, GLF_live3aura / 4.0) / 3.0;
     float GLF_live3space = (pow(abs(dot(normalize(GLF_live3direction), normalize(GLF_live3light))), 12.0));
     vec3 GLF_live3compSpace = vec3(GLF_live3space, GLF_live3space, GLF_live3space);
     float GLF_live3blend = smoothstep(0.0, GLF_live3initialDistance, GLF_live3z);
     if(((injectionSwitch.x > injectionSwitch.y)))
      {
       return vec3(0, 0.1, 0);
      }
     GLF_live3result;
    }
   }
   return pickColor(iteration);
  }
 else
  {
   return vec3(0.0);
  }
}
void main()
{
 vec3 data[9];
 for(
     int i = 0;
     i < 3;
     i ++
 )
  {
   for(
       int j = 0;
       j < 3;
       j ++
   )
    {
     data[3 * j + i] = mand(gl_FragCoord.x + float(i - 1), gl_FragCoord.y + float(j - 1));
    }
  }
 vec3 sum = vec3(0.0);
 {
  vec3 GLF_live3direction = vec3(1.0);
  vec3 GLF_live3src = vec3(1.0);
  float GLF_live3globalTime = 3539.2509;
  vec4(GLF_live3intersects(GLF_live3src, GLF_live3direction, GLF_live3globalTime), 1);
 }
 for(
     int i = 0;
     i < 9;
     i ++
 )
  {
   sum += data[i];
  }
 sum /= vec3(9.0);
 _GLF_color = vec4(sum, 1.0);
}
