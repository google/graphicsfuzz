#version 310 es

precision highp float;

layout(location = 0) out vec4 _GLF_color;
uniform vec2 resolution;

const vec4 pal[16] = vec4[16](
    vec4(0.0,0.0,0.0,0.0),
    vec4(0.5,0.0,0.0,0.0),
    vec4(0.0,0.5,0.0,0.0),    
    vec4(0.5,0.5,0.0,0.0),
    vec4(0.0,0.0,0.5,0.0),
    vec4(0.5,0.0,0.5,0.0),    
    vec4(0.0,0.5,0.5,0.0),    
    vec4(0.5,0.5,0.5,0.0),
    
    vec4(0.0,0.0,0.0,0.0),
    vec4(1.0,0.0,0.0,0.0),
    vec4(0.0,1.0,0.0,0.0),
    vec4(1.0,1.0,0.0,0.0),
    vec4(0.0,0.0,1.0,0.0),
    vec4(1.0,0.0,1.0,0.0),
    vec4(0.0,1.0,1.0,0.0),
    vec4(1.0,1.0,1.0,0.0));

void main()
{
    vec2 pos = gl_FragCoord.xy / resolution;
	ivec2 lin = ivec2(int(pos.x * 10.0), int(pos.y * 10.0));
	int iters = lin.x + lin.y * 10;
	int v = 100;
	int i;
	for (i = 0; i < iters; i++)
		v = (4 * v * (1000 - v)) / 1000;
	_GLF_color = pal[v % 16];
}