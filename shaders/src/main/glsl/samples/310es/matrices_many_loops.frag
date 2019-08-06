#version 310 es

precision highp float;

layout(location = 0) out vec4 _GLF_color;

// The following matrix uniforms will be populated with 1.0 at every
// entry.

uniform mat2x2 m22;

uniform mat2x3 m23;

uniform mat2x4 m24;

uniform mat3x2 m32;

uniform mat3x3 m33;

uniform mat3x4 m34;

uniform mat4x2 m42;

uniform mat4x3 m43;

uniform mat4x4 m44;

vec2 resolution;

// The following macro populates sums[INDEX] to be the sum of all the
// entries in MAT (which must be a COLS x ROWS matrix) divided by
// 16x.0.

#define POPULATE(MAT, COLS, ROWS, INDEX) \
  sums[INDEX] = 0.0;                     \
  for (int c = 0; c < COLS; c++) {       \
    for (int r = 0; r < ROWS; r++) {     \
      sums[INDEX] += MAT[c][r];          \
    }                                    \
  }                                      \
  sums[INDEX] /= 16.0;

void main()
{
  resolution = vec2(256.0);

  float sums[9];

  POPULATE(m22, 2, 2, 0);
  POPULATE(m23, 2, 3, 1);
  POPULATE(m24, 2, 4, 2);
  POPULATE(m32, 3, 2, 3);
  POPULATE(m33, 3, 3, 4);
  POPULATE(m34, 3, 4, 5);
  POPULATE(m42, 4, 2, 6);
  POPULATE(m43, 4, 3, 7);
  POPULATE(m44, 4, 4, 8);

  int region_x = int(gl_FragCoord.x / (resolution.x / 3.0));
  int region_y = int(gl_FragCoord.y / (resolution.x / 3.0));
  int overall_region = region_y * 3 + region_x;

  if (overall_region > 0 && overall_region < 9) {
    _GLF_color = vec4(vec3(sums[overall_region]), 1.0);
  } else {
    _GLF_color = vec4(vec3(0.0), 1.0);
  }
}
