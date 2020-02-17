# `spirv-fuzz` standalone walkthrough

`spirv-fuzz`


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

```bash
export PATH="$(pwd)/glslang/bin:$(pwd)/SPIRV-Tools/bin:$(pwd)/amber/bin:${PATH}"
export LD_LIBRARY_PATH="$(pwd)/amber/lib/libvulkan.so:${LD_LIBRARY_PATH}"
export VK_ICD_FILENAMES="$(pwd)/swiftshader/lib/vk_swiftshader_icd.json"
```

```bash
amber -d -V
```

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


```glsl
#version 310 es
precision highp float;

layout(location = 0) out vec4 color;

void main()
{
  color = vec4(1.0, 0.0, 0.0, 1.0);
}
```

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

```bash
glslangValidator -V shader.frag -o shader.spv
```

```bash
spirv-dis shader.spv --raw-id
```

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

```bash
amber -d simple.amber -i output.png

# If you are running this walk-through in Jupyter, the following will display
# the image.
# Otherwise, open output.png in your editor of choice.
echo bash_kernel: saved image data to: output.png
```


```

Summary: 1 pass, 0 fail
```


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

```bash
chmod +x run_shader.sh

./run_shader.sh shader.spv

echo bash_kernel: saved image data to: output.png
```

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

```bash
# Create an empty donors list.
touch donors.txt

# Run spirv-fuzz to generate a mutated shader "fuzzed.spv".
spirv-fuzz shader.spv -o fuzzed.spv --donors=donors.txt

# Run the shader.
./run_shader.sh fuzzed.spv

echo bash_kernel: saved image data to: output.png
```



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


```bash
spirv-fuzz almost_interesting.spv -o fuzzed.spv --donors=donors.txt
./run_shader.sh fuzzed.spv
```


```bash
spirv-fuzz almost_interesting.spv -o fuzzed.spv --donors=donors.txt --seed=211
./run_shader.sh fuzzed.spv
```

```bash
spirv-val fuzzed.spv
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



