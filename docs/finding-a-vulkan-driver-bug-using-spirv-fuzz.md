# Finding a Vulkan driver bug using `spirv-fuzz`

`spirv-fuzz` is a tool that automatically finds bugs
in Vulkan drivers, specifically the SPIR-V shader compiler component of the driver.
The result is an input that, when run on the Vulkan driver,
causes the driver to crash or, more generally, "do the wrong thing";
e.g. render an incorrect image.

> If you just want to find bugs in Vulkan drivers
as quickly as possible
then your best bet is probably to
run [gfauto](https://github.com/google/graphicsfuzz/tree/master/gfauto#gfauto),
which can use `spirv-fuzz` (as well as other tools) to do continuous fuzzing of
desktop and Android Vulkan drivers.
However,
this walkthrough explores using `spirv-fuzz` and `spirv-reduce`
as standalone command line tools.
As well as being a supported use-case,
this also shows what is going on behind-the-scenes when you use `gfauto`.

In this walkthrough,
we will write a simple application (in [AmberScript](https://github.com/google/amber/blob/master/docs/amber_script.md))
that uses the Vulkan API
to render a red square.
We will then run `spirv-fuzz` to find a bug in a Vulkan driver
and then use the "shrink" mode of `spirv-fuzz`,
plus `spirv-reduce`, to reduce
the bug-inducing input.
We will end up with a much simpler input that still triggers the bug
and is suitable for reporting to the driver developers.

## Getting the tools


[![Binder](https://mybinder.org/badge_logo.svg) This walkthrough can be run interactively in your browser by
clicking
here](https://mybinder.org/v2/gh/google/graphicsfuzz/ac20933c3983a84fea2122edf5a518a452cbba89?filepath=docs%2Ffinding-a-vulkan-driver-bug-using-spirv-fuzz.md).

You can use Shift+Enter to execute the Bash snippets
and see the output.
Alternatively, you can copy and paste the Bash snippets
into your terminal on a Linux x86 64-bit machine.
You can also just read it,
but that might be less fun!

The following snippet downloads and extracts
prebuilt versions of the following tools:

* [Amber](https://github.com/google/amber): a tool that executes AmberScript files. An
AmberScript file (written in [AmberScript](https://github.com/google/amber/blob/master/docs/amber_script.md)) allows you to
concisely list graphics commands that will execute on graphics APIs,
like Vulkan.
We will use AmberScript to write a simple "Vulkan program"
that draws a square,
without having to write ~1000 lines of C++.

* [SwiftShader](https://github.com/google/swiftshader): a Vulkan driver that uses your CPU (no GPU required!).

* [glslangValidator](https://github.com/KhronosGroup/glslang): a tool that compiles shaders
written in GLSL (the OpenGL Shading Language).
Shaders are essentially programs that are compiled and run
on the GPU (or CPU in the case of SwiftShader)
to render hardware-accelerated graphics.
We will use `glslangValidator` to compile a GLSL shader into SPIR-V
(the binary intermediate representation used by Vulkan)
suitable for use in our Vulkan program.

* [SPIRV-Tools](https://github.com/KhronosGroup/SPIRV-Tools): a suite of tools for SPIR-V files. We will use:
  * `spirv-fuzz`: the fuzzer itself.
  * `spirv-reduce`: a tool that simplifies SPIR-V by repeatedly removing SPIR-V instructions.
  * `spirv-val`: a validator that finds issues with your SPIR-V.
  * `spirv-dis`: a SPIR-V disassembler that converts SPIR-V (which is a binary format) to human-readable assembly text.
  * `spirv-as`: a SPIR-V assembler that converts SPIR-V assembly text back to SPIR-V.


```bash
curl -fsSL -o amber.zip https://github.com/google/gfbuild-amber/releases/download/github%2Fgoogle%2Fgfbuild-amber%2Fd8acae641ea278ae6a1571797f7bf08747265f15/gfbuild-amber-d8acae641ea278ae6a1571797f7bf08747265f15-Linux_x64_Release.zip

unzip -d amber amber.zip

curl -fsSL -o swiftshader.zip https://github.com/google/gfbuild-swiftshader/releases/download/github%2Fgoogle%2Fgfbuild-swiftshader%2Ff9f999f5a2eb6dd586a1da03e6b400d044ae6022/gfbuild-swiftshader-f9f999f5a2eb6dd586a1da03e6b400d044ae6022-Linux_x64_Release.zip

unzip -d swiftshader swiftshader.zip

curl -fsSL -o glslang.zip https://github.com/google/gfbuild-glslang/releases/download/github%2Fgoogle%2Fgfbuild-glslang%2Fae59435606fc5bc453cf4e32320e6579ff7ea22e/gfbuild-glslang-ae59435606fc5bc453cf4e32320e6579ff7ea22e-Linux_x64_Release.zip

unzip -d glslang glslang.zip

curl -fsSL -o SPIRV-Tools.zip https://github.com/google/gfbuild-SPIRV-Tools/releases/download/github%2Fgoogle%2Fgfbuild-SPIRV-Tools%2F6c218ec60b5f6b525f1badb60c820cae20bd4df3/gfbuild-SPIRV-Tools-6c218ec60b5f6b525f1badb60c820cae20bd4df3-Linux_x64_Release.zip

unzip -d SPIRV-Tools SPIRV-Tools.zip

```

The following snippet sets up your environment so we can execute the tools.

```bash
export PATH="$(pwd)/glslang/bin:$(pwd)/SPIRV-Tools/bin:$(pwd)/amber/bin:${PATH}"
# Note for the curious: this prebuilt Amber comes with a prebuilt Vulkan loader for convenience, which we add to LD_LIBRARY_PATH.
export LD_LIBRARY_PATH="$(pwd)/amber/lib:${LD_LIBRARY_PATH}"
export VK_ICD_FILENAMES="$(pwd)/swiftshader/lib/vk_swiftshader_icd.json"
```

You should now be able to run Amber.

```bash
amber -d -V
```

It should output something like the following, indicating that
the SwiftShader Vulkan device was found.

```
Amber        : d8acae6
SPIRV-Tools  : 97f1d485
SPIRV-Headers: dc77030
GLSLang      : 07a55839
Shaderc      : 821d564

Physical device properties:
  apiVersion: 1.1.0
  driverVersion: 20971520
  vendorID: 6880
  deviceID: 49374
  deviceType: cpu
  deviceName: SwiftShader Device (LLVM 7.0.1)
  driverName: SwiftShader driver
  driverInfo:
End of physical device properties.

Summary: 0 pass, 0 fail
```

## Creating a SPIR-V fragment shader

We start by writing a simple Vulkan application (using AmberScript)
that will render a red square.
To do that, we first need to write a fragment shader.
We will write the shader in GLSL (a high-level language)
and compile it to SPIR-V (a low-level, binary, intermediate representation for Vulkan).

The fragment shader is essentially a program that will run on the graphics device
(normally your GPU, but in this case SwiftShader)
for every pixel that is rendered,
and the output from the program is the pixel color.
Here is our fragment shader written in GLSL:

```glsl
#version 310 es
precision highp float;

layout(location = 0) out vec4 color;

void main()
{
  color = vec4(1.0, 0.0, 0.0, 1.0);
}
```

You do not need to understand all the details, but in brief:
the `main` function will be executed for every pixel that is rendered
and the output is a vector of 4 elements `(1.0, 0.0, 0.0, 1.0)`, where each element represents
the red, green, blue, and alpha color components respectively (each in the range `0.0` to `1.0`).
In this case, the output from `main` is always the color red.

The following snippet writes our shader to `shader.frag`.

```bash
cat >shader.frag <<HERE
#version 310 es
precision highp float;

layout(location = 0) out vec4 color;

void main()
{
  color = vec4(1.0, 0.0, 0.0, 1.0);
}
HERE
```

We can compile the shader using `glslangValidator` to get `shader.spv`.

```bash
glslangValidator -V shader.frag -o shader.spv
```

SPIR-V is a binary format, so `shader.spv` is not designed to be human-readable.
However, we can use `spirv-dis` to disassemble the SPIR-V to human-readable
assembly text.

```bash
spirv-dis shader.spv --raw-id
```

The output should be:

```spirv
; SPIR-V
; Version: 1.0
; Generator: Khronos Glslang Reference Front End; 8
; Bound: 13
; Schema: 0
               OpCapability Shader
          %1 = OpExtInstImport "GLSL.std.450"
               OpMemoryModel Logical GLSL450
               OpEntryPoint Fragment %4 "main" %9
               OpExecutionMode %4 OriginUpperLeft
               OpSource ESSL 310
               OpName %4 "main"
               OpName %9 "color"
               OpDecorate %9 Location 0
          %2 = OpTypeVoid
          %3 = OpTypeFunction %2
          %6 = OpTypeFloat 32
          %7 = OpTypeVector %6 4
          %8 = OpTypePointer Output %7
          %9 = OpVariable %8 Output
         %10 = OpConstant %6 1
         %11 = OpConstant %6 0
         %12 = OpConstantComposite %7 %10 %11 %11 %10
          %4 = OpFunction %2 None %3
          %5 = OpLabel
               OpStore %9 %12
               OpReturn
               OpFunctionEnd
```

You do not need to try to understand all of the SPIR-V assembly,
but you can get an idea of the low-level nature of SPIR-V; for example:

* The `%4 = OpFunction %2 None %3` instruction is the `main` function entry point.
* The `OpStore %9 %12` instruction writes the color red (`%12`) to the output variable (`%9`).

## Drawing a square

We could now write a C/C++ application that uses the Vulkan API
and loads `shader.spv` to render a red square.
However, this would typically require ~1000 lines of code.
Instead, we will use AmberScript,
which lets us do the same thing more concisely.
One thing to note is that AmberScript files
are designed to be self-contained
so that they can be used as
self-contained tests for graphics APIs.
Thus,
we cannot load the `shader.spv` file in AmberScript.
Instead, we embed the shader in the AmberScript file.

The following snippet creates our AmberScript file, `simple.amber`,
that contains our shader (represented as SPIR-V assembly)
followed by the AmberScript commands needed to render
a square using the fragment shader.

```bash
cat >simple.amber <<HERE
#!amber

SHADER vertex vertex_shader PASSTHROUGH

SHADER fragment fragment_shader SPIRV-ASM
; SPIR-V
; Version: 1.0
; Generator: Khronos Glslang Reference Front End; 8
; Bound: 13
; Schema: 0
               OpCapability Shader
          %1 = OpExtInstImport "GLSL.std.450"
               OpMemoryModel Logical GLSL450
               OpEntryPoint Fragment %4 "main" %9
               OpExecutionMode %4 OriginUpperLeft
               OpSource ESSL 310
               OpName %4 "main"
               OpName %9 "color"
               OpDecorate %9 Location 0
          %2 = OpTypeVoid
          %3 = OpTypeFunction %2
          %6 = OpTypeFloat 32
          %7 = OpTypeVector %6 4
          %8 = OpTypePointer Output %7
          %9 = OpVariable %8 Output
         %10 = OpConstant %6 1
         %11 = OpConstant %6 0
         %12 = OpConstantComposite %7 %10 %11 %11 %10
          %4 = OpFunction %2 None %3
          %5 = OpLabel
               OpStore %9 %12
               OpReturn
               OpFunctionEnd
END

BUFFER framebuffer FORMAT B8G8R8A8_UNORM

PIPELINE graphics pipeline
  ATTACH vertex_shader
  ATTACH fragment_shader
  FRAMEBUFFER_SIZE 256 256
  BIND BUFFER framebuffer AS color LOCATION 0
END
CLEAR_COLOR pipeline 0 0 0 255

CLEAR pipeline
RUN pipeline DRAW_RECT POS 0 0 SIZE 256 256

HERE
```

We can now run our AmberScript file:

```bash
amber -d simple.amber -i output.png

# If you are running this walkthrough in your browser using Jupyter, the
# following will display the image.
# Otherwise, open output.png in your image viewer of choice.
echo bash_kernel: saved image data to: output.png
```

You should see the following output:

```

Summary: 1 pass, 0 fail
```

Amber renders graphics off-screen (you won't see anything),
which is perhaps slightly anti-climactic.
However, the `-i` option is used to write out the rendered image to
`output.png`.
It should contain a red square. Success!

## Shader runner

We want to be able to run a SPIR-V fragment shader like `shader.spv`
without having to manually write an AmberScript file each time.

The following snippet creates a Bash script that
takes a SPIR-V fragment shader file as its only command line argument,
writes out an AmberScript file that embeds the shader assembly,
and runs Amber on the AmberScript file,
writing the rendered image to `output.png`.


```bash
cat >run_shader.sh <<RUN_SHADER_END
#!/usr/bin/env bash

set -x
set -e
set -u

SHADER="\${1}"

cat >simple.amber <<HERE
#!amber

SHADER vertex vertex_shader PASSTHROUGH

SHADER fragment fragment_shader SPIRV-ASM
HERE

spirv-dis --raw-id "\${SHADER}" >>simple.amber

cat >>simple.amber <<HERE

END

BUFFER framebuffer FORMAT B8G8R8A8_UNORM

PIPELINE graphics pipeline
  ATTACH vertex_shader
  ATTACH fragment_shader
  FRAMEBUFFER_SIZE 256 256
  BIND BUFFER framebuffer AS color LOCATION 0
END
CLEAR_COLOR pipeline 0 0 0 255

CLEAR pipeline
RUN pipeline DRAW_RECT POS 0 0 SIZE 256 256

HERE

amber -d simple.amber -i output.png
RUN_SHADER_END

```

We can now "execute" a SPIR-V fragment shader (after making the script executable):


```bash
chmod +x run_shader.sh

./run_shader.sh shader.spv

echo bash_kernel: saved image data to: output.png
```

You should again see a red square in `output.png`.

Let's try changing the shader to render green, just to make sure it really works:

```bash
cat >shader.frag <<HERE
#version 310 es
precision highp float;

layout(location = 0) out vec4 color;

void main()
{
  color = vec4(0.0, 1.0, 0.0, 1.0);
}
HERE

glslangValidator -V shader.frag -o shader.spv
./run_shader.sh shader.spv
echo bash_kernel: saved image data to: output.png
```

The `output.png` file should now be a green square.

> Exercise: try changing the shader to render a gradient from red to green.
You can get the x- and y- pixel coordinate using `gl_FragCoord.x` and `gl_FragCoord.y`.
The coordinates will be between `0.0` and `255.0`.
You can modify and re-execute the snippet above.


## Running `spirv-fuzz` on our shader

The `spirv-fuzz` tool takes
as input a SPIR-V file,
applies many *semantics preserving transformations*,
and outputs a transformed SPIR-V file
that should be more complex than (but otherwise have the same semantics
as) the original input SPIR-V file.

> An example of one (very) simple transformation that could be applied is: add the following code (expressed as GLSL):
> * `if(false) { return; }`.

Thus,
using the transformed shader in place of the original should not change
the rendered image.
If the image *does* change, or the Vulkan driver crashes,
then we have found a bug in the Vulkan driver.

> The fact that the transformed shader has the same semantics as the original
is the key novelty of the *metamorphic testing* fuzzing approach.
You can read more about the general approach on the
[GraphicsFuzz](https://github.com/google/graphicsfuzz) page,
which includes links to our [papers](https://github.com/google/graphicsfuzz#academic-publications)
and a ["how it works" page](https://github.com/google/graphicsfuzz/blob/master/docs/glsl-fuzz-intro.md).
In short,
because the transformed shader is both valid and semantically equivalent,
we know when the driver does the wrong thing because the rendered image will change
or the driver will crash.
In contrast,
if we used a traditional fuzzer to generate an arbitrary input (array of bytes),
we would not know if the driver was rendering the wrong image
because we don't know what the expected image is.
We also don't know what a crash means because the arbitrary SPIR-V file
might be invalid, and Vulkan drivers are allowed to crash when given
invalid SPIR-V.

The `spirv-fuzz` tool also takes a list of *donor* SPIR-V files;
it can use chunks of code from the donors to make the transformed output file
more interesting.
You can also provide a _facts file_ alongside the input SPIR-V file
to inform `spirv-fuzz` about certain facts that will hold at runtime,
which can also make the output SPIR-V file more interesting.
However, we will not use any donors or facts in this walkthough.


```bash
# Create an empty donors list.
touch donors.txt

# Run spirv-fuzz to generate a transformed shader "fuzzed.spv".
spirv-fuzz shader.spv -o fuzzed.spv --donors=donors.txt
```

There should be some output files:

```bash
ls fuzzed.*
```

The output files are:

  * `fuzzed.spv`: the transformed SPIR-V file.
  * `fuzzed.transformations`: the list of semantics preserving transformations that were applied to `shader.spv` to get to `fuzzed.spv`. The file is in a binary (protobuf) format.
  * `fuzzed.transformations_json`: the same as `fuzzed.transformations` but in a human-readable JSON format.

You can see the transformations that were applied:

```bash
cat fuzzed.transformations_json
```

You do not need to understand the encoding of the transformations,
but know that the list of transformations
was applied in order to get to `shader.spv`.

You can run the transformed shader using our "run shader" script:

```bash
# Run the shader.
./run_shader.sh fuzzed.spv

echo bash_kernel: saved image data to: output.png
```

You will most likely see the same output as before,
and `output.png` will continue to be a green square
(or whatever you changed `shader.spv` to render).
You can keep trying to see if you get a crash or a different image
by repeatedly running the following snippet:

```bash
spirv-fuzz shader.spv -o fuzzed.spv --donors=donors.txt
./run_shader.sh fuzzed.spv
echo bash_kernel: saved image data to: output.png
```

The outputs from `spirv-fuzz` will be different every time.

## Finding a Vulkan driver bug using `spirv-fuzz`

Unfortunately for this walkthrough (but fortunately for the Vulkan ecosystem),
finding a bug in our Vulkan driver (SwiftShader) is non-trivial.
Furthermore,
because our input shader is very simple
and we are not using donors nor shader facts,
we are very unlikely to be able to find a bug in *any* Vulkan driver.
Instead,
here is a slightly more interesting shader we made earlier.
Execute the following snippet to create the shader `almost_interesting.spv`.


```bash
spirv-as --preserve-numeric-ids --target-env spv1.0 -o almost_interesting.spv <<HERE
; SPIR-V
; Version: 1.0
; Generator: Khronos Glslang Reference Front End; 7
; Bound: 54
; Schema: 0
               OpCapability Shader
          %1 = OpExtInstImport "GLSL.std.450"
               OpMemoryModel Logical GLSL450
               OpEntryPoint Fragment %4 "main" %15
               OpExecutionMode %4 OriginUpperLeft
               OpSource ESSL 310
               OpName %4 "main"
               OpName %11 "brick(vf2;"
               OpName %10 "uv"
               OpName %15 "_GLF_color"
               OpName %23 "buf0"
               OpMemberName %23 0 "injectionSwitch"
               OpName %51 "param"
               OpDecorate %15 Location 0
               OpMemberDecorate %23 0 Offset 0
               OpDecorate %23 Block
          %2 = OpTypeVoid
          %3 = OpTypeFunction %2
          %6 = OpTypeFloat 32
          %7 = OpTypeVector %6 2
          %8 = OpTypePointer Function %7
          %9 = OpTypeFunction %7 %8
         %13 = OpTypeVector %6 4
         %14 = OpTypePointer Output %13
         %15 = OpVariable %14 Output
         %16 = OpConstant %6 1
         %17 = OpConstant %6 0
         %18 = OpConstantComposite %13 %16 %17 %17 %16
         %23 = OpTypeStruct %7
         %24 = OpTypePointer Uniform %23
         %26 = OpTypeInt 32 1
         %27 = OpConstant %26 0
         %28 = OpTypeInt 32 0
         %29 = OpConstant %28 1
         %30 = OpTypePointer Uniform %6
         %33 = OpTypeBool
         %37 = OpConstantComposite %7 %16 %16
         %39 = OpTypePointer Function %6
         %44 = OpConstantFalse %33
          %4 = OpFunction %2 None %3
          %5 = OpLabel
         %51 = OpVariable %8 Function
               OpStore %51 %37
         %52 = OpFunctionCall %7 %11 %51
               OpReturn
               OpFunctionEnd
         %11 = OpFunction %7 None %9
         %10 = OpFunctionParameter %8
         %12 = OpLabel
               OpStore %15 %18
               OpBranch %19
         %19 = OpLabel
               OpLoopMerge %21 %36 None
               OpBranch %20
         %20 = OpLabel
         %31 = OpAccessChain %39 %10 %29
         %32 = OpLoad %6 %31
         %34 = OpFOrdLessThan %33 %16 %17
               OpSelectionMerge %53 None
               OpBranchConditional %34 %35 %36
         %35 = OpLabel
               OpReturnValue %37
         %53 = OpLabel
               OpBranch %36
         %36 = OpLabel
         %40 = OpAccessChain %39 %10 %29
         %41 = OpLoad %6 %40
         %42 = OpFSub %6 %41 %16
               OpStore %40 %42
               OpBranchConditional %44 %19 %21
         %21 = OpLabel
         %46 = OpLoad %6 %40
         %47 = OpFSub %6 %46 %16
               OpStore %40 %47
               OpReturnValue %37
               OpFunctionEnd
HERE
```

If you run the shader, you will see that it successfully renders a red square:

```bash
./run_shader.sh almost_interesting.spv
echo bash_kernel: saved image data to: output.png
```

So the shader does not trigger a bug in our Vulkan driver.
However, the shader is named `almost_interesting.spv`
because it is in fact very close to triggering a crash in our Vulkan driver.
Execute the following snippet to try fuzzing using the shader.

```bash
spirv-fuzz almost_interesting.spv -o fuzzed.spv --donors=donors.txt
./run_shader.sh fuzzed.spv
```

Even if you execute the above snippet hundreds of times, you may not find the bug,
or you may find a different bug to the one assumed by this walkthrough.
We can set the random number generator seed to get a deterministic
result, which will cause `spirv-fuzz` to generate
a specific shader that exposes the bug.

First, let's remove `output.png`.

```bash
rm output.png
```

And now execute the following snippet to find the bug-inducing shader and trigger the bug:

```bash
spirv-fuzz almost_interesting.spv -o fuzzed.spv --donors=donors.txt --seed=211
./run_shader.sh fuzzed.spv
```

You should see output similar to the following:

```
../src/Pipeline/SpirvShader.hpp:991 WARNING: ASSERT(obj.kind == SpirvShader::Object::Kind::Constant)

./run_shader.sh: line 38: 252516 Segmentation fault      amber -d simple.amber -i output.png
```

Amber segfaulted due to a bug in our Vulkan driver (SwiftShader).
The `output.png` file was not produced.
Congratulations! You just found a bug in a Vulkan driver!

...probably. The shader *might* violate the requirements of the Vulkan or SPIR-V specification,
in which case the issue is with our shader and not the Vulkan driver.
This can happen if the original shader violates the spec or if there is a bug in `spirv-fuzz`.
The `spirv-val` tool can validate certain properties of our SPIR-V shader;
if validation fails, then the shader is definitely the problem (modulo bugs in `spirv-val`).

```bash
spirv-val fuzzed.spv
echo $?
```

You should see an output of `0`, the exit status of `spirv-val`,
indicating that validation succeeded.
Thus,
our shader still might be triggering a driver bug.

> Spoiler: the bug really *is* in SwiftShader but our point is that,
in general,
we try to be careful. We do not assume that our tools are bug-free.

## Shrinking

In this walkthrough,
`fuzzed.spv` is a fairly small shader.
In practice,
our fuzzed shaders can be very large
due to the large number of transformations applied,
nearly all of which add code.
Trying to investigate/debug the fuzzed shaders
would be difficult.
More importantly,
in most cases,
only a tiny subset of the transformations
are needed to get the bug-inducing shader.
Thus,
we should "shrink" the list of transformations applied to get a much simpler
shader that still induces the bug.
We should do this before doing further investigation or reporting the bug
to the driver developers.

> We (and others) normally use the term *reducing* or *reduction* instead of shrinking.
However, with `spirv-fuzz`, we use the term shrinking
to refer to reducing the list of transformations
and *reduction* to refer to
reducing SPIR-V by just removing chunks of code (which we will cover
in the next section).


> Note that there are 44 transformations in `fuzzed.transformations_json`;
we will see how many are left after shrinking.

To use the shrink mode of `spirv-fuzz`,
we need a script that can be used to execute a shader
and that
gives an exit status of 0 if and only if the shader is still "interesting"; i.e.
if and only if the shader causes a segfault.
Unfortunately,
our shader runner script is close to the opposite of this:

```bash
./run_shader.sh fuzzed.spv
echo $?
```

The output is `139`,
which is the exit status for a segfault.
The following snippet creates a modified shader runner, `run_shader_expect_segfault.sh`,
that exits with a status of `0` if and only if the shader causes a segfault:


```bash
cat >run_shader_expect_segfault.sh <<RUN_SHADER_END
#!/usr/bin/env bash

set -x
set -e
set -u

SHADER="\${1}"

cat >simple.amber <<HERE
#!amber

SHADER vertex vertex_shader PASSTHROUGH

SHADER fragment fragment_shader SPIRV-ASM
HERE

spirv-dis --raw-id "\${SHADER}" >>simple.amber

cat >>simple.amber <<HERE

END

BUFFER framebuffer FORMAT B8G8R8A8_UNORM

PIPELINE graphics pipeline
  ATTACH vertex_shader
  ATTACH fragment_shader
  FRAMEBUFFER_SIZE 256 256
  BIND BUFFER framebuffer AS color LOCATION 0
END
CLEAR_COLOR pipeline 0 0 0 255

CLEAR pipeline
RUN pipeline DRAW_RECT POS 0 0 SIZE 256 256

HERE

# Allow non-zero exit status.
set +e

amber -d simple.amber -i output.png
AMBER_EXIT_STATUS="\${?}"

set -e

# This line will exit with status 1 if Amber's exit status was anything other
# than 139 (segfault).
test "\${AMBER_EXIT_STATUS}" -eq 139
RUN_SHADER_END

```

Let's see if it works on the bug-inducing shader:

```bash
chmod +x run_shader_expect_segfault.sh

./run_shader_expect_segfault.sh fuzzed.spv
echo $?
```

The output should be `0`. Now let's try the original shader
that does not trigger a segfault:

```bash
./run_shader_expect_segfault.sh almost_interesting.spv
echo $?
```

The output should be `1`.

We can now run `spirv-fuzz` in its shrink mode.
The inputs are:

* `almost_interesting.spv`: the original (non-transformed) shader.
* `fuzzed.transformations`: the list of transformations that produces the bug-inducing shader.
* `./run_shader_expect_segfault.sh`: the "interestingness test" script that will be invoked by `spirv-fuzz` to determine whether a shader is still "interesting"; i.e. still crashes SwiftShader.


```bash
spirv-fuzz almost_interesting.spv -o shrunk.spv --shrink=fuzzed.transformations -- ./run_shader_expect_segfault.sh
```

The shrink mode of `spirv-fuzz` repeatedly tries applying a *subset* of the transformations `fuzzed.transformations`
to `almost_interesting.spv`.
For the `i`th attempt,
the transformed shader is written to `temp_i.spv` and the interestingness test is invoked (`./run_shader_expect_segfault.sh temp_i.spv`) to see if the shader still triggers the bug.

After 33 attempts,
no further transformations could be removed (without failing the interestingness test),
so the final shrunk shader is written to `shrunk.spv`.

You should see the temporary shaders:

```bash
ls temp*
```

```
temp_0000.spv  temp_0005.spv  temp_0010.spv  temp_0015.spv  temp_0020.spv  temp_0025.spv  temp_0030.spv
temp_0001.spv  temp_0006.spv  temp_0011.spv  temp_0016.spv  temp_0021.spv  temp_0026.spv  temp_0031.spv
temp_0002.spv  temp_0007.spv  temp_0012.spv  temp_0017.spv  temp_0022.spv  temp_0027.spv  temp_0032.spv
temp_0003.spv  temp_0008.spv  temp_0013.spv  temp_0018.spv  temp_0023.spv  temp_0028.spv  temp_0033.spv
temp_0004.spv  temp_0009.spv  temp_0014.spv  temp_0019.spv  temp_0024.spv  temp_0029.spv
```

As with the fuzz mode of `spirv-fuzz`,
the transformations that were applied to the final shader are also output:

```bash
ls shrunk.*
```

```
shrunk.spv  shrunk.transformations  shrunk.transformations_json
```

`shrunk.transformations_json` contains just 3 transformations,
down from 44 transformations before shrinking. Success!

We can also compare the number of lines of SPIR-V assembly:

```bash
spirv-dis fuzzed.spv --raw-id | wc -l
spirv-dis shrunk.spv --raw-id | wc -l
```


Output:

```
116
84
```

Not a huge difference for this small example, but still worthwhile.
In practice, the difference could be much larger.

## Reducing

Consider the pair of shaders:

* `almost_interesting.spv`: the original, untransformed shader that should render
red on any correct Vulkan implementation.
* `shrunk.spv`: a shader that is the same as `almost_interesting.spv` except it has
three small changes that should not change the semantics of the shader.
It should render red on
any correct Vulkan implementation.

Imagine if, instead of crashing,
the `shrunk.spv` shader rendered green when using SwiftShader
(due to a bug in SwiftShader).
In that case,
the *pair* of shaders would form a valuable bug report and debugging aid:
the shaders are almost identical
except for three small changes that should not change the color
that is rendered.
Both shaders should render the same image
and it is easy for a developer see this,
even if it is not obvious that the shaders should render red.
A developer could investigate the three small changes
and try to understand why they cause the shaders to render different images
(red vs. green).

In reality,
our `shrunk.spv` shader causes a crash;
the fact that it should render the same image as `almost_interesting.spv`
is not particularly helpful.
The most useful bug report will typically be the smallest possible shader
that crashes SwiftShader.
Thus,
we can simplify `shrunk.spv` further by removing
chunks of code,
_even if this changes the semantics of the shader_ (i.e the shader might
be changed to no longer render red).
We just have to ensure the shader remains valid
and still crashes SwiftShader.

To do this,
we can use `spirv-reduce`,
which repeatedly removes instructions from a SPIR-V file,
as long as the shader remains valid and still passes the supplied interesting test.
We can use the same interestingness test as before.

The following snippet executes `spirv-reduce` on `shrunk.spv`:

```bash
spirv-reduce shrunk.spv -o reduced.spv -- ./run_shader_expect_segfault.sh
```

Similar to with the shrinking process,
you should be able to see the temporary shaders:

```bash
ls temp*
```

```
temp_0000.spv  temp_0008.spv  temp_0016.spv  temp_0024.spv  temp_0032.spv  temp_0040.spv  temp_0048.spv  temp_0056.spv
temp_0001.spv  temp_0009.spv  temp_0017.spv  temp_0025.spv  temp_0033.spv  temp_0041.spv  temp_0049.spv
temp_0002.spv  temp_0010.spv  temp_0018.spv  temp_0026.spv  temp_0034.spv  temp_0042.spv  temp_0050.spv
temp_0003.spv  temp_0011.spv  temp_0019.spv  temp_0027.spv  temp_0035.spv  temp_0043.spv  temp_0051.spv
temp_0004.spv  temp_0012.spv  temp_0020.spv  temp_0028.spv  temp_0036.spv  temp_0044.spv  temp_0052.spv
temp_0005.spv  temp_0013.spv  temp_0021.spv  temp_0029.spv  temp_0037.spv  temp_0045.spv  temp_0053.spv
temp_0006.spv  temp_0014.spv  temp_0022.spv  temp_0030.spv  temp_0038.spv  temp_0046.spv  temp_0054.spv
temp_0007.spv  temp_0015.spv  temp_0023.spv  temp_0031.spv  temp_0039.spv  temp_0047.spv  temp_0055.spv
```

The final output shader is `reduced.spv`,
which still passes our interestingness test (i.e. causes a segfault):

```bash
./run_shader_expect_segfault.sh reduced.spv
echo $?
```

Output:

```
../src/Pipeline/SpirvShader.hpp:991 WARNING: ASSERT(obj.kind == SpirvShader::Object::Kind::Constant)

./run_shader.sh: line 38: 252516 Segmentation fault      amber -d simple.amber -i output.png

0
```

We can compare how the shrinking and reduction have affected the
number of lines of SPIR-V assembly:

```bash
spirv-dis fuzzed.spv --raw-id  | wc -l
spirv-dis shrunk.spv --raw-id  | wc -l
spirv-dis reduced.spv --raw-id | wc -l
```

Output:

```
116
84
53
```

Again, with this small example the difference is ~30 lines each time,
which is a useful reduction.
With larger shaders, the difference could be much greater
and so even more useful.

## Conclusion

In this walkthrough,
we used `spirv-fuzz` to find a bug in a Vulkan driver (SwiftShader)
and used the shrink mode of `spirv-fuzz`,
plus `spirv-reduce`, to reduce
the bug-inducing input to
a much simpler input that still triggers the bug
and is suitable for reporting to the driver developers.

We do _not_ wish to imply that SwiftShader's shader compiler
is full of bugs; on the contrary,
we are happy to report that it is
hard to find crash bugs in the latest builds of SwiftShader using our fuzzers.
This is one of the reasons why the walkthrough uses a shader that we prepared in advance;
the other reason is it avoids having to go into details about
donor shaders and shader facts,
which _were_ used to find the example bug originally.

As hinted earlier,
if you wish to try fuzzing some Vulkan drivers,
the best way is to
use [gfauto](https://github.com/google/graphicsfuzz/tree/master/gfauto#gfauto),
which uses both [glsl-fuzz](https://github.com/google/graphicsfuzz#glsl-fuzz) and `spirv-fuzz` (and other tools) to do continuous fuzzing of
desktop and Android Vulkan drivers.

