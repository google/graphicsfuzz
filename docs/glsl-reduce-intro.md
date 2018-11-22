# glsl-reduce intro: my shader is crashing the compiler, now what?

Shader compiler-writers are all too often faced with the situation where they
have a large, complex shader that causes their compiler to crash, and wish they
had a small, simple shader that would trigger the same problem: the small shader
would make debugging easier and would be small enough to be added to a
regression test suite forever more.

More generally, it's common to have a complex shader that causes something
interesting to happen when consumed by an application or tool, and to want to
find a small, simple shader that still causes the interesting thing to happen.
The interesting thing might be that a particular frame renders way too slowly,
the RAM consumed by the shader compiler snowballs, the shader crashes shader
compilers from distinct GPU vendors A and B but not that of vendor C, or
whatever.

glsl-reduce is an automated reducer for GLSL shaders.  You give it a fragment,
vertex or compute shader and a script describing the property you care about.
The tool will then proceed to shrink your shader down to a smaller, simpler form
that still has the property.

To learn more, checkout out the [walkthrough and user documentation](docs/glsl-reduce.md)
