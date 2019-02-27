
# Testing shaders

These shaders are similar to our sample shaders, but modified to use a resolution of 32x32 and to use smaller loops. In particular, the loops in the `donors` are simplified even further to have small, static bounds. 

We can run these shaders in a reasonable time via SwiftShader (a software renderer) to catch subtle but serious regressions in GraphicsFuzz that would be missed by our unit tests.
