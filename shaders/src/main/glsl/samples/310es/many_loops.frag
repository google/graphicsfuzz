#version 310 es

precision highp float;

layout(location = 0) out vec4 _GLF_color;

vec2 resolution;

// The following macro populates sums[INDEX] to be:
//  (COLS x ROWS) / 16.0
// via a nest of loops.

#define POPULATE(COLS, ROWS, INDEX) \
  sums[INDEX] = 0.0;                     \
  for (int c = 0; c < COLS; c++) {       \
    for (int r = 0; r < ROWS; r++) {     \
      sums[INDEX] += 1.0;                \
    }                                    \
  }                                      \
  sums[INDEX] /= 16.0;

void main()
{
  resolution = vec2(256.0);

  float sums[9];

  POPULATE(2, 2, 0);
  POPULATE(2, 3, 1);
  POPULATE(2, 4, 2);
  POPULATE(3, 2, 3);
  POPULATE(3, 3, 4);
  POPULATE(3, 4, 5);
  POPULATE(4, 2, 6);
  POPULATE(4, 3, 7);
  POPULATE(4, 4, 8);

  int region_x = int(gl_FragCoord.x / (resolution.x / 3.0));
  int region_y = int(gl_FragCoord.y / (resolution.x / 3.0));
  int overall_region = region_y * 3 + region_x;

  if (overall_region > 0 && overall_region < 9) {
    _GLF_color = vec4(vec3(sums[overall_region]), 1.0);
  } else {
    _GLF_color = vec4(vec3(0.0), 1.0);
  }
}
