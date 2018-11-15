#version 400

// Copyright 2018 The GraphicsFuzz Project Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable
layout (location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform buf0 {
 vec2 injectionSwitch;
};

layout(set = 0, binding = 1) uniform buf1 {
 float foo;
};

void main() {
   outColor = vec4(1.0, 1.0, 0.0, 1.0);
   if (gl_FragCoord.x < foo) {
     outColor.x = 0.0;
   }
}
