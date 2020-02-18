# `spirv-fuzz` standalone walkthrough

`spirv-fuzz` is a tool that automatically finds bugs
in Vulkan drivers, specifically the SPIR-V shader compiler component of the driver.
The result is an input that, when run on the Vulkan driver,
causes the driver to crash or (more generally) "do the wrong thing";
e.g. render an incorrect image.

If you just want to find bugs in Vulkan drivers
then your best bet is probably to run [gfauto](),
which can use `spirv-fuzz` (as well as other tools) to do continuous fuzzing of
desktop and Android Vulkan drivers.
However, `spirv-fuzz` can also be used as a standalone command line tool
or integrated into other workflows.

In this walkthrough,
we will write a simple application (in AmberScript)
that uses the Vulkan API
to render a red square.
We will then run `spirv-fuzz` to find a bug in a Vulkan driver
and then use the "shrink" mode of `spirv-fuzz` to reduce
the bug-inducing input.
We will end up with a much simpler input that still triggers the bug
and is suitable for reporting to the driver developers.

## Getting the tools

This walkthrough can be run interactively in your browser by
clicking [here](); you can use Shift+Enter to execute Bash snippets.
Alternatively, you can copy and paste the Bash snippets
into your terminal on a Linux x86 64-bit machine.
You can also just read it,
but that might be less fun!

The following snippet downloads and extracts
prebuilt versions of the following tools:

* Amber: a tool that executes AmberScript files. An
AmberScript file (written in AmberScript) allows you to
concisely provide
a list of graphics commands that can be executed on graphics APIs,
like Vulkan.
We will use AmberScript to write a simple "Vulkan program"
that draws a square,
without having to write thousands of lines of C++.

* SwiftShader: a Vulkan driver that uses your CPU to render
graphics (no GPU required!).

* `glslangValidator`: a tool that compiles shaders
written in GLSL (the OpenGL Shading Language).
Shaders are essentially programs that are compiled and run
on the GPU (or CPU in the case of SwiftShader)
to render hardware-accelerated graphics.
We will use `glslangValidator` to compile a GLSL shader into SPIR-V
(the binary intermediate representation used by Vulkan)
suitable for use in our Vulkan program.

* SPIRV-Tools: a suite of tools for SPIR-V files. We will use:
  * `spirv-fuzz`: the fuzzer itself.
  * `spirv-val`: a validator for SPIR-V that finds issues with your SPIR-V file.
  * `spirv-dis`: a SPIR-V disassembler that converts a SPIR-V file (which is a binary format) to human-readable assembly text.
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
and compile it to SPIR-V (a binary intermediate representation for Vulkan).

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

You do not need to understand all the details.
The `main` function will be executed for every pixel that is rendered
and the output is a vector of 4 elements `(1.0, 0.0, 0.0, 1.0)`, where each element represents
the red, green, blue, and alpha color components respectively (within the range `0.0` to `1.0`).
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
but you can get an idea of the low-level nature of SPIR-V:

* The `%4 = OpFunction %2 None %3` instruction is the `main` function entry point.
* The `OpStore %9 %12` instruction writes the color red (`%12`) to the output variable (`%9`).

## Drawing a square

We could now write a C/C++ application that uses the Vulkan API
and loads `shader.spv` to render a red square.
However, this would typically require about 1000 lines of code.
Instead, we will write an AmberScript file that renders a red square
using our shader.
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
# Otherwise, open output.png in your editor of choice.
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
takes a SPIR-V fragment shader as its only command line argument,
writes out an AmberScript file that embeds the disassembled shader,
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
as input a SPIR-V file
and outputs a transformed SPIR-V file
that should be more complex but otherwise have the same semantics
as the original file.
Thus,
using the transformed shader in place of the original should not change
the rendered image.
If the image _does_ change, or the Vulkan driver crashes,
then we have found a bug in the Vulkan driver.

> The fact that the transformed shader has the same behavior as the original
is the key novelty of the _metamorphic testing_ fuzzing approach.
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

The `spirv-fuzz` tool also takes a list of _donor_ SPIR-V files;
it can use chunks of code from the donors to make the transformed output file
more interesting.
You can also provide a _facts file_ alongside the input SPIR-V file
to inform `spirv-fuzz` about certain facts that will hold at runtime,
which can also make the output SPIR-V file more interesting.
However, we will not use any donors or facts in this walkthough.


```bash
# Create an empty donors list.
touch donors.txt

# Run spirv-fuzz to generate a mutated shader "fuzzed.spv".
spirv-fuzz shader.spv -o fuzzed.spv --donors=donors.txt
```

The output `fuzzed.spv` SPIR-V file will be different each time.
You can run the shader using our "run shader" script:

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

## Finding a Vulkan driver bug using `spirv-fuzz`

Unfortunately for this walkthrough (but fortunately for the Vulkan ecosystem),
finding a bug in our Vulkan driver (SwiftShader) is non-trivial.
Furthermore,
because our input shader is very simple
and we are not using donors nor shader facts,
we are very unlikely to be able to find a bug in _any_ Vulkan driver.
Instead,
here is a slightly more interesting shader I made earlier.
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
result, which will cause always expose the bug.


```bash
spirv-fuzz almost_interesting.spv -o fuzzed.spv --donors=donors.txt --seed=211
./run_shader.sh fuzzed.spv
```

```bash
spirv-val fuzzed.spv
echo $?
```

There are 44 transformations in `fuzzed.transformations_json`.


```bash
./run_shader.sh fuzzed.spv
echo $?
```



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

```bash
chmod +x run_shader_expect_segfault.sh
```

```bash
./run_shader_expect_segfault.sh fuzzed.spv
echo $?
```

```bash
./run_shader_expect_segfault.sh almost_interesting.spv
echo $?
```

```bash
spirv-fuzz almost_interesting.spv -o reduced.spv --shrink=fuzzed.transformations -- ./run_shader_expect_segfault.sh
```

33 attempts. Only 3 transformations were necessary.



