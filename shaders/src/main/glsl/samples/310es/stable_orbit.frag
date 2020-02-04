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

ivec2 iter(ivec2 p)
{
	if (p.x > 0)
		p.y--;
	if (p.x < 0)
		p.y++;
	p.x += p.y / 2;
	return p;
}

void main()
{
    vec2 pos = gl_FragCoord.xy / resolution;
	ivec2 ipos = ivec2(int(pos.x*8.0),int(pos.y*8.0));
	int v = (ipos.x & 5) | (ipos.y & 10);
	int w = (ipos.y & 5) | (ipos.x & 10);
	ivec2 p = ivec2(v * 8 + w, 0);
	int i;
	for (i = 0; i < 100; i++)
		p = iter(p);
	
	if (p.x < 0) p.x = -p.x;
	while (p.x > 15) p.x -= 16;

	_GLF_color = pal[p.x];

}