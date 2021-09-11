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

int MATRIX_N = 4;

uniform mat4 matrix_a_uni;

void main()
{
    // We need to modify A in place, so we have to copy the uniform
    mat4 matrix_a = mat4(matrix_a_uni);
    vec4 matrix_b = gl_FragCoord.wxyz;

    vec4 matrix_u = vec4(0.0);

    float magnitudeX = 0.0; 
    float alpha1 = 0.0;
    float alpha2 = 0.0;
    float alpha3 = 0.0;
    float beta = 0.0;
    // QR factorization
    for(int k = 0; k < MATRIX_N - 1; k++)
    {
        // define vector x as the column A(k) only with entries k - n
        // creating our U vector at the same time we create X magnitude.
        for(int x = MATRIX_N - 1; x >= k; x--) // start from the bottom and go up.
        {
            magnitudeX += pow(matrix_a[x][k], 2.0);
            matrix_u[x] = matrix_a[x][k];
        }
        magnitudeX = sqrt(magnitudeX);
        // entry k is the top of our U vector for this iteration
        // define U as x - sign(x1) (magnitude X) (elementary matrix 1)
        matrix_u[k] -= (sign(matrix_u[k]) * magnitudeX);

        // alpha1 = U(T) * U gives us the dot product
        for(int u = MATRIX_N - 1; u >= k; u--)
        {
            alpha1 += pow(matrix_u[u], 2.0);
        }

        // alpha2 = 2.0 / (U(T) * U) = 2.0 / alpha1
        alpha2 = 2.0 / alpha1;

        for(int j = k; j < MATRIX_N; j++)
        {
            // evaluate alpha3 = U(T) * a(j) updated k - 1 times
            for(int a = MATRIX_N - 1; a >= k; a--)
            {
                alpha3 += matrix_u[a] * matrix_a[a][j];
            }

            beta = alpha2 * alpha3;

            // evaluate a(j) updated k times = a(j) updated k - 1 times - (beta * U)
            for(int a = MATRIX_N - 1; a >= k; a--)
            {
                matrix_a[a][j] = matrix_a[a][j] - (beta * matrix_u[a]);
            }
            alpha3 = 0.0;
            beta = 0.0;
        }

        // evaluate alpha3 = U(T) * a(j) updated k - 1 times
        for(int b = MATRIX_N - 1; b >= k; b--)
        {
            alpha3 += matrix_u[b] * matrix_b[b];
        }
        
        // evaluate beta = alpha2 * alpha3
        beta = alpha2 * alpha3;

        // evaluate b(j) updated k times = b(j) updated k - 1 times - (beta * U)
        for(int b = MATRIX_N - 1; b >= k; b--)
        {
            matrix_b[b] = matrix_b[b] - (beta * matrix_u[b]);
        }

        magnitudeX = 0.0;
        alpha1 = 0.0;
        alpha2 = 0.0;
        alpha3 = 0.0;
        beta = 0.0;
    }

    // back substitution algorithm
    for(int i = (MATRIX_N - 1); i >= 0; i--)
    {
        for(int j = (MATRIX_N - 1); j >= (i + 1); j--)
        {
            matrix_b[i] -= (matrix_a[i][j] * matrix_b[j]);
        }

        // final operation, using the diagonal; matrix_b becomes the solution matrix x
        matrix_b[i] /= matrix_a[i][i];
    }
    _GLF_color = tan(matrix_b);
    _GLF_color.a = 1.0;
}

