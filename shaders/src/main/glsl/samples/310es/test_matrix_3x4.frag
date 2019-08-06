#version 310 es

precision highp float;

layout(location = 0) out vec4 _GLF_color;

uniform mat3x4 m;

#define COLS 3
#define ROWS 4

float summation() {
  float result = 0.0;
  for (int c = 0; c < COLS; c++) {
    for (int r = 0; r < ROWS; r++) {
      result += m[c][r];
    }
  }
  return result;
}

#define EPSILON 0.00001

void main()
{
  float sum = summation();
  if (abs(float(COLS)*float(ROWS) - sum) < EPSILON) {
    _GLF_color = vec4(1.0, 0.0, 0.0, 1.0);
  } else {
    _GLF_color = vec4(0.0, 1.0, 0.0, 1.0);
  }
}
