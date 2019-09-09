# Google Summer of Code 2019 Report - Abel Briggs

###### Personal Links:
###### [Github](https://github.com/abelbriggs1) - [Linkedin](https://www.linkedin.com/in/abelbriggs/)

###### Proposal:
###### [Enhancing Metamorphic Testing Tools For Graphics Drivers](https://summerofcode.withgoogle.com/projects/#6749896743321600)

###### Mentors: 
###### Alastair Donaldson, Hugues Evrard

## Preface

Before this report begins, I'd like to deliver a short spiel directed towards potential future
viewers (and likely GSoC hopefuls) of this document.

While Google Summer of Code may look daunting to the prospective student applicant, I implore anyone
who even has a passing interest in the program to consider applying/proposing for an organization
that strikes your fancy (and do not be intimidated when looking at past GSoC projects!). Mentorship 
is an invaluable experience to any engineer (especially junior engineers) and not all workplaces 
have the budget and/or manpower to support it. The best feature of Google Summer of Code is that 
this problem vanishes - you are paired up with mentors that have 'help my student succeed' as a 
major goal, and this is a fundamental cornerstone of the program. I had little experience in 
proper software collaboration and in working on large/domain-specific codebases before I began 
submitting PRs to GraphicsFuzz - the code reviews and mentoring that I was given during the program 
were instrumental in making me a better and more confident engineer.

Thanks to Ally, Hugues and Paul for their continued mentorship and support over
the 2019 summer.

With that said...

## Deliverables

During and throughout the summer of 2019, my mentors and I worked out the kinks in my original
proposal and developed some fairly solid objectives to work towards. This report is intended to
detail what those deliverables are, how they were delivered and what could still use work in the
future.

These deliverables were:

 - [Enhance GraphicsFuzz's shader generator by making it more aware of OpenGL Shading Language's built-in functions.](#graphicsfuzz-shader-generator---glsl-built-in-support)
 - [Enhance GraphicsFuzz's shader generator by adding additional ways to generate opaque values and new identity transformations.](#graphicsfuzz-shader-generator---identities-and-opaque-value-generation)
 - [Add a new 'worker' program](#write-worker-script-that-uses-piglit-test-framework-to-render-images)
 that takes GraphicsFuzz shader jobs from a server, renders the shader job via Mesa's open-source 
 OpenGL test framework Piglit, and sends the results back to the server.
 - [Add new shaders to GraphicsFuzz's fuzz test set](#add-new-shaders-to-graphicsfuzzs-test-set) - brand new shaders as well as derivatives of the
  original test set.
 - [Apply GraphicsFuzz to the Mesa open-source graphics driver suite](#apply-graphicsfuzz-to-the-nouveau-open-source-graphics-driver),
 specifically the NVIDIA reverse-engineered driver nouveau, the open-source driver that was easiest 
 for me to run tests on.
 
### GraphicsFuzz shader generator - GLSL built-in support

When GraphicsFuzz generates fuzzed/arbitrary expressions with the assumption that they won't be
executed (e.g. in `(true ? x : y)`, y will never be executed), it is able to generate calls to GLSL
built-in functions(think `abs()`, `sqrt()`, etc.) with arbitrary values as arguments. To do this, however,
GraphicsFuzz needs to know what types to fuzz expressions for, or it will cause type errors.

The following PRs involve cross-checking built-in functions with the language version they were
introduced in, then adding their function prototypes to GraphicsFuzz.

[#521](https://github.com/google/graphicsfuzz/pull/521):
Add built-in support for GLSL Integer Functions

[#528](https://github.com/google/graphicsfuzz/pull/528):
Add built-in function support for GLSL Vector Relational Functions

[#549](https://github.com/google/graphicsfuzz/pull/549):
Add built-in function support for GLSL 3.20 Fragment Processing Functions

[#563](https://github.com/google/graphicsfuzz/pull/563):
Refactor exponential builtins into separate function

Additionally, minor issues arose from adding support for these functions - some of these functions
involve using `out` parameters and modify said parameters during their execution. This would
potentially cause 'side effects' - lvalues being modified when not expected. The following PR
relates to mitigating this issue.

[#533](https://github.com/google/graphicsfuzz/pull/533):
Check for side effects with lvalue parameters of built-in functions

#### Leftovers

The following issues are nits or minor problems related to built-ins that were left unfixed due to 
time or knowledge constraints.

[#550](https://github.com/google/graphicsfuzz/issues/550):
Ensure certain function prototypes can use non-uniform shader input variables

[#570](https://github.com/google/graphicsfuzz/issues/570):
Be less conservative about when FunctionCallExprTemplates yield expressions that have side effects

### GraphicsFuzz shader generator - identities and opaque value generation

Among the various operations that GraphicsFuzz's generator can do to a shader when fuzzing, two of
the most important are identity transformations and opaque value generation.

An identity transformation is a function that, given an expression `e`, transforms `e -> f`, where `f` is
an expression equivalent for all intents and purposes to `e` (*semantically equivalent*). GraphicsFuzz
is able to use various properties of GLSL data types and built-in functions to craft these identity
transformations, and can recursively apply them to create obscenely complicated expressions.

For a toy example of an identity: Let `e` be a float of any valid value and let `identity(x) = x * 1.0`. 
Then `e` is semantically equivalent to `identity(e)`.

An opaque value in the context of GraphicsFuzz is an rvalue expression that is guaranteed to have
a certain value, yet the value is not obvious to a compiler. The generator introduces opaque zeroes
or opaque ones whenever required by other GraphicsFuzz transformations. Like identity transformations,
these opaque values exploit invariants and properties of GLSL data types and built-in functions, and
they can be recursively applied to complicate expressions.

For a toy example of opaque values: Let `e = 0.0`. Notice that `0.0 == sqrt(0.0) == abs(0.0) == 
float(0 >> 8) == injectionSwitch.x == abs(sqrt(float(0 >> 8)))`. Then `0.0` can be replaced with
any of these expressions.

The following PRs directly involve adding new ways to generate opaque values or identity transformations.

[#509](https://github.com/google/graphicsfuzz/pull/509):
Generate ternary identities for nonscalar types

[#512](https://github.com/google/graphicsfuzz/pull/512):
Add new transformation for rewriting nonscalars as their constructors

[#525](https://github.com/google/graphicsfuzz/pull/525):
Fix matrix constructors generating identities for the wrong base type

[#527](https://github.com/google/graphicsfuzz/pull/527):
Enhance matrix constructor identity to potentially use all elements of matrix

[#553](https://github.com/google/graphicsfuzz/pull/553):
Add new identities for integer types that use bitwise operations

[#562](https://github.com/google/graphicsfuzz/pull/562):
Add new ways to generate opaque zero/one from bitwise operations

[#580](https://github.com/google/graphicsfuzz/pull/580):
Ensure bitwise shift opaque one shifts left/right by same value

[#641](https://github.com/google/graphicsfuzz/pull/641):
Refactor shift opaque to use identities instead of fuzzed clamped expressions

[#642](https://github.com/google/graphicsfuzz/pull/642):
Add function to generate opaque zero/one from dot product of two vectors

[#648](https://github.com/google/graphicsfuzz/pull/648):
Add function to make opaque zero/one from determinant of a matrix

[#649](https://github.com/google/graphicsfuzz/pull/649):
Added identity to double transpose a matrix

[#682](https://github.com/google/graphicsfuzz/pull/682):
Add function to make opaque zero vec3 from cross product of equal vec3s

[#683](https://github.com/google/graphicsfuzz/pull/683):
Add identity to multiply rectangular matrix/vector by identity matrix

[#695](https://github.com/google/graphicsfuzz/pull/695):
Fix opaque dot function inverted isZero indices

[#704](https://github.com/google/graphicsfuzz/pull/704):
Add identity to put data in a larger type and pull it back out

#### Leftovers

The following issues are nits or minor problems related to opaque values/identity transformations
that were left unfixed due to time or knowledge constraints, or are issues out of the scope of 
GraphicsFuzz.

[#552](https://github.com/google/graphicsfuzz/issues/552):
Rewrite composite identity tries to index into shader output variables in GLES

[#637](https://github.com/google/graphicsfuzz/issues/637):
Construct identity matrix by multiplying rectangular zero-one matrices

[#653](https://github.com/google/graphicsfuzz/issues/653):
Be less conservative with matrix functions in constexpr contexts

Related glslangValidator issue: https://github.com/KhronosGroup/glslang/issues/1865.

### Write worker script that uses Piglit test framework to render images

[Mesa](https://mesa.freedesktop.org/) is an open-source graphics driver suite that is able to power
a massive range of graphics hardware across the last twenty years of computing. To help deal with the 
variance in hardware and driver implementations, contributors to Mesa wrote a graphics driver
test framework called [Piglit](https://piglit.freedesktop.org/). Among its huge suite of GLSL and
Vulkan tests, Piglit includes a rendering program, `shader_runner`, that can take user-supplied
`.shader_test` files, render the included shader programs, test images for correct color values,
 and more.
 
For running shaders, GraphicsFuzz uses a client-server model. In GraphicsFuzz's toolset, a webserver
and several 'workers', or clients, are supplied. These workers and the server communicate to each
other via an [Apache Thrift](https://thrift.apache.org/) specification.
 
To run a test set, the user starts the webserver and a worker, then selects a test set to run 
(via UI or otherwise). For each shader, the webserver sends the shader source code and a JSON file
that details any additional information the shader needs (such as uniform data) to the worker - the
worker then renders the shader in any way it sees fit and returns the rendered image
(if there was one), logs, etc.

When reporting bugs in Mesa's drivers, it is most convenient for the Mesa developers to have a
`.shader_test` file that they can easily plug into Piglit as a test case. Additionally, the OpenGL ES
GraphicsFuzz worker is rather unwieldy to compile/use for desktop platforms, and so it was decided
that the development of a Piglit/`shader_runner` worker was worth pursuing, along with a script to
systematically convert GraphicsFuzz shader jobs into Piglit `.shader_test` files.

The following PRs directly involve the Piglit converter script or worker.

[#577](https://github.com/google/graphicsfuzz/pull/577):
Add script to convert a GraphicsFuzz shader job to a piglit shader test

[#584](https://github.com/google/graphicsfuzz/pull/584):
Fix piglit converter script not inserting GL ES version

[#613](https://github.com/google/graphicsfuzz/pull/613):
Fix piglit converter not accepting blank JSON

[#617](https://github.com/google/graphicsfuzz/pull/617):
Add piglit shader_runner_gles3 worker script

[#631](https://github.com/google/graphicsfuzz/pull/631):
docs: Add documentation for piglit worker

[#632](https://github.com/google/graphicsfuzz/pull/632):
Piglit worker improvements

[#638](https://github.com/google/graphicsfuzz/pull/638):
graphicsfuzz-piglit-converter: support more uniform types

In addition, a contribution was made to Piglit upstream to allow `shader_runner` to handle a case
when unused uniform data was optimized out of a compiled shader.

[shader_runner](https://gitlab.freedesktop.org/mesa/piglit/merge_requests/92):
Add command line option to ignore uniform if it isn't found in shader

#### Leftovers

The following issues are nits or minor problems related to the Piglit worker or converter script
that were left unfixed due to time or knowledge constraints.

[#597](https://github.com/google/graphicsfuzz/issues/597):
Support compute shaders in graphicsfuzz_piglit_converter

### Add new shaders to GraphicsFuzz's test set

With the exception of compute shaders, GraphicsFuzz had a fairly limited set of five simple test
shaders for mutation. These shaders sufficed for finding simple bugs, but were limited to GLES 1.00
features and were unable to test the full extent of GLSL even with significant additions
to the shader generator.

The following PRs directly involve adding new shaders to GraphicsFuzz's test suite.

[#605](https://github.com/google/graphicsfuzz/pull/605):
Add new versions of GraphicsFuzz shaders that use Integer Functions

[#647](https://github.com/google/graphicsfuzz/pull/647):
New 310es shader: householder_lattice

| householder_lattice.frag |
| ------------------------ |
| ![householder_lattice](https://i.imgur.com/ih3ORPH.png) |

[#698](https://github.com/google/graphicsfuzz/pull/698):
New 310es shader - Muller's method

| muller.frag |
| ----------- |
| ![muller](https://i.imgur.com/recmnjw.png) |

### Apply GraphicsFuzz to the nouveau open-source graphics driver

[nouveau](https://nouveau.freedesktop.org/wiki/) is a reverse-engineered open-source graphics driver
for NVIDIA graphics hardware that is developed under the umbrella of the Mesa driver suite. Because
of limitations on modern NVIDIA cards due to NVIDIA requiring signed firmware to access
key hardware on graphics cards past the [Maxwell](https://nouveau.freedesktop.org/wiki/CodeNames/#nv110familymaxwell)
generation, nouveau receives little use in comparison to other Mesa drivers or NVIDIA's closed-source
binaries, making it a prime target for fuzzing.

The following are bugs reported to the Mesa bug tracker found via fuzzing.

[Bug 110953](https://bugs.freedesktop.org/show_bug.cgi?id=110953):
Adding a redundant single-iteration do-while loop causes different image to be rendered

| Reference | Variant |
| --------- | ------- |
| ![reference](https://i.imgur.com/PBY0tCP.png) | ![variant](https://i.imgur.com/BHbVc3x.png)

[Bug 111006](https://bugs.freedesktop.org/show_bug.cgi?id=111006):
Adding a uniform-dependent if-statement in shader renders a different image

| Reference | Variant |
| --------- | ------- |
| ![reference](https://i.imgur.com/0Q8dSRZ.png) | ![variant](https://i.imgur.com/uaZCU8t.png)

[Bug 111167](https://bugs.freedesktop.org/show_bug.cgi?id=111167):
Dividing zero by a uniform in loop header causes segfault in nv50_ir::NVC0LegalizeSSA::handleDIV

```
for (int i = 1; 1 <= (0 / int(injectionSwitch.y)); 1)
{
}
```

This snippet of shader code causes nouveau to crash when trying to handle the division.

Stacktrace:
```
#0  std::_Deque_iterator<nv50_ir::ValueRef, nv50_ir::ValueRef&, nv50_ir::ValueRef*>::_Deque_iterator (
    __x=<error reading variable: Cannot access memory at address 0xb0>, 
    this=<synthetic pointer>) at /usr/include/c++/8/bits/stl_deque.h:1401
#1  std::_Deque_iterator<nv50_ir::ValueRef, nv50_ir::ValueRef&, nv50_ir::ValueRef*>::operator+ (__n=0, this=0xb0) at /usr/include/c++/8/bits/stl_deque.h:230
#2  std::_Deque_iterator<nv50_ir::ValueRef, nv50_ir::ValueRef&, nv50_ir::ValueRef*>::operator[] (__n=0, this=0xb0) at /usr/include/c++/8/bits/stl_deque.h:247
#3  std::deque<nv50_ir::ValueRef, std::allocator<nv50_ir::ValueRef> >::operator[] (__n=0, this=0xa0) at /usr/include/c++/8/bits/stl_deque.h:1404
#4  nv50_ir::Instruction::getSrc (s=0, this=0x0)
    at ../src/gallium/drivers/nouveau/codegen/nv50_ir.h:827
#5  nv50_ir::NVC0LegalizeSSA::handleDIV (this=0x7ffd7753af60, i=0x55d2e1b132a0)
    at ../src/gallium/drivers/nouveau/codegen/nv50_ir_lowering_nvc0.cpp:54
#6  0x00007fc7191cb4b3 in nv50_ir::NVC0LegalizeSSA::visit (
    this=0x7ffd7753af60, bb=<optimized out>)
    at ../src/gallium/drivers/nouveau/codegen/nv50_ir_lowering_nvc0.cpp:334
#7  0x00007fc719111928 in nv50_ir::Pass::doRun (this=0x7ffd7753af60, 
    func=<optimized out>, ordered=<optimized out>, skipPhi=true)
    at ../src/gallium/drivers/nouveau/codegen/nv50_ir_bb.cpp:500
#8  0x00007fc7191119f4 in nv50_ir::Pass::doRun (this=0x7ffd7753af60, 
    prog=<optimized out>, ordered=false, skipPhi=true)
    at ../src/gallium/drivers/nouveau/codegen/nv50_ir_inlines.h:413
```

#### Leftovers

[Bug 111006](https://bugs.freedesktop.org/show_bug.cgi?id=111006) involves a critical component of
GraphicsFuzz's fuzzing techniques - preventing a compiler from optimizing uni-directional
branches (like an if-statement that is always true) by using a variable that is unknown until runtime.
While this bug remains unfixed, it is difficult to continue fuzz testing without running into a
large number of potential duplicate issues. This issue prevented continued fuzzing over the second
half of GSoC 2019.

### Miscellaneous

While working on GraphicsFuzz for Google Summer of Code 2019, minor problems that were not directly
related to the detailed deliverables cropped up.

The following PRs involve miscellaneous refactoring or changes to GraphicsFuzz.

[#507](https://github.com/google/graphicsfuzz/pull/507):
Add additional tests for empty for statements

[#532](https://github.com/google/graphicsfuzz/pull/532):
Refactor BasicType with documentation and exception improvements

[#535](https://github.com/google/graphicsfuzz/pull/535):
Remove pointless static numColumns function

[#537](https://github.com/google/graphicsfuzz/pull/537):
Refactor code to use isType functions when possible

[#587](https://github.com/google/graphicsfuzz/pull/587):
Refactor common python script functions into one common lib

[#668](https://github.com/google/graphicsfuzz/pull/668):
Refactor transposedMatrixType() to use makeMatrixType()

[#670](https://github.com/google/graphicsfuzz/pull/670):
Fix making array accesses inbounds with uint index accesses



