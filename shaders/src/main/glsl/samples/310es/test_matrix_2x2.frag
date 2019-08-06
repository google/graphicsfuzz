#version 310 es

precision highp float;
precision highp int;

layout(location = 0) out vec4 _GLF_color;

uniform mat2x2 m;

#define EPSILON 0.00001

void main()
{
  float sum = m[0][0] + m[0][1] + m[1][0] + m[1][1];
  if (abs(4.0 - sum) < EPSILON) {
    _GLF_color = vec4(1.0, 0.0, 0.0, 1.0);
  } else {
    _GLF_color = vec4(0.0, 1.0, 0.0, 1.0);
  }
}
