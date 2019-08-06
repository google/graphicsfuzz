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

void main()
{
  resolution = vec2(256.0);

  float sums[9];

  int sum_index = 0;
  for(int cols = 2; cols <= 4; cols++)
  {
    for(int rows = 2; rows <= 4; rows++)
    {
      sums[sum_index] = 0.0;
      for(int c = 0; c < cols; c++)
      {
        for(int r = 0; r < rows; r++)
        {
          switch(sum_index)
          {
            case 0:
              sums[sum_index] += m22[c][r];
              break;
            case 1:
              sums[sum_index] += m23[c][r];
              break;
            case 2:
              sums[sum_index] += m24[c][r];
              break;
            case 3:
              sums[sum_index] += m32[c][r];
              break;
            case 4:
              sums[sum_index] += m33[c][r];
              break;
            case 5:
              sums[sum_index] += m34[c][r];
              break;
            case 6:
              sums[sum_index] += m42[c][r];
              break;
            case 7:
              sums[sum_index] += m43[c][r];
              break;
            case 8:
              sums[sum_index] += m44[c][r];
              break;
          }
        }
      }
      sums[sum_index] /= 16.0;
      sum_index ++;
    }
  }

  int region_x = int(gl_FragCoord.x / (resolution.x / 3.0));
  int region_y = int(gl_FragCoord.y / (resolution.x / 3.0));
  int overall_region = region_y * 3 + region_x;

  if (overall_region > 0 && overall_region < 9) {
    _GLF_color = vec4(vec3(sums[overall_region]), 1.0);
  } else {
    _GLF_color = vec4(vec3(0.0), 1.0);
  }
}
