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

in vec3 a_position;

void main(void) {

    float thetaX = 10.5;
    float thetaY = 1.1;
    float thetaZ = 0.3;

    mat3 rotateX;
    rotateX[0] = vec3(1.0, 0.0, 0.0);
    rotateX[1] = vec3(0.0, cos(thetaX), sin(thetaX));
    rotateX[2] = vec3(0.0, -sin(thetaX), cos(thetaX));

    mat3 rotateY;
    rotateY[0] = vec3(cos(thetaY), 0.0, -sin(thetaY));
    rotateY[1] = vec3(0.0, 1.0, 0.0);
    rotateY[2] = vec3(sin(thetaY), 0.0, cos(thetaY));

    mat3 rotateZ;
    rotateZ[0] = vec3(cos(thetaZ), sin(thetaZ), 0);
    rotateZ[1] = vec3(-sin(thetaZ), cos(thetaZ), 0.0);
    rotateZ[2] = vec3(0.0, 0.0, 1.0);

    gl_Position = vec4(0.5 * rotateZ * rotateY * rotateX * a_position, 1.0);
}
