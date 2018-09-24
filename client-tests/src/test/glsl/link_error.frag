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

precision mediump float;

layout(location = 0) out vec4 _GLF_color;

uniform vec2 resolution; // declared inconsistently between shaders

void main() {
     _GLF_color = vec4(gl_FragCoord.x / resolution.x, gl_FragCoord.y / resolution.y, 0.5, 1.0);
}