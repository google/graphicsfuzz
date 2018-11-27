# glsl-reduce introduction: my shader is being weird, now what?

**Shader compiler-writers** are all too often faced with the situation where they
have a large, complex shader that causes their compiler to crash, and wish they
had a small, simple shader that would trigger the same problem: the small shader
would make debugging easier and would be small enough to be added to a
regression test suite forever more.

**Game developers** can end up in a similar situation;
a shader is causing an issue on a particular driver or platform,
such as bad performance or crashes,
and they wish they had a smaller, simpler shader
that would trigger the same issue:
the small shader would reveal which lines cause the issue,
perhaps allowing certain patterns or features to be avoided,
and would be free from irrelevant (and possibly proprietary)
code, making it more convenient for inclusion in a bug report
that can be shared with other developers.

More generally, it's common to have a complex shader that causes something
interesting to happen when consumed by an application or tool, and to want to
find a small, simple shader that *still* causes the interesting thing to happen.

`glsl-reduce` is an automated reducer for GLSL shaders.  You give it a fragment,
vertex or compute shader and a script that checks the property you care about,
most likely by running an application that uses the shader.
The tool will then proceed to shrink your shader down to a smaller, simpler form
that still has the property.

To learn more, check out the [walkthrough](glsl-reduce-walkthrough.md) and [manual](glsl-reduce.md).
