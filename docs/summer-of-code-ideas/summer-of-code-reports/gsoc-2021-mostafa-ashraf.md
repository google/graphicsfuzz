# Google Summer of Code 2021 Report - Mostafa Ashraf

###### Personal Links:
###### [Github](https://github.com/Mostafa-ashraf19) - [Linkedin](https://www.linkedin.com/in/mostafa-ashraf-a62807142/)

###### Proposal:
###### [Improving support for compute shaders in spirv-fuzz](https://summerofcode.withgoogle.com/projects/#6354411477008384)

###### Mentors: 
###### [Alastair Donaldson](https://github.com/afd), [Paul Thomson](https://github.com/paulthomson), [Vasyl Teliman](https://github.com/Vasniktel)

## Abstract

During the *Google Summer of Code 2021* program, I worked on the development of new *semantics-preserving transformations*, fuzzer passes and enhancements for the [*spirv-fuzz*](https://github.com/KhronosGroup/SPIRV-Tools) tool.
Also, wrote some shaders in [shadertrap](https://github.com/google/shadertrap) and improve coverage of spirv-fuzz test suite in case of transformation code is not fully covered.

## Introduction
Graphics drivers are the software components that allow operating systems and programs to use computer graphics hardware and communicate. The problem is there are cases when drivers do not work well due to driver bugs. These can lead to graphical glitches and application crashes. These bugs lead to a bad user experience and can be security-critical.

So our target is to eliminate or decrease these errors. How do this? Using testing, but when we think testing ideas is hard we need some things like the output we expected to compare with current output before rendering to help with this challenge, the [spirv-fuzz](https://github.com/KhronosGroup/SPIRV-Tools) tool employs a concept called metamorphic testing.

The metamorphic concept trying to make programs equivalent to the original program and moving them through the same operations on the original program then compares the output to check it passes or fails depends they have the same output or not.

In a `Spirv-fuzz` it works directly on the SPIR-V binary module and it automatically finds bugs in Vulkan drivers. Specifically the SPIR-V shader compiler component of the driver. The result is an input that when it runs on the Vulkan driver causes the driver to crash or more generally, "do the wrong thing"; e.g. render an incorrect image.

A `Spirv-fuzz` has limited support for compute shaders, it important because feature concurrent computation with multiple workgroups, the use of atomic operations to modify shared memory, barrier operations to synchronize threads, and operations to synchronize memory.

The metamorphic testing approach used by spirv-fuzz involves applying a series of semantics-preserving transformations to the shader to make it very different from the shader compiler's perspective, but to preserve the result that it produces. This means that bugs can be found by comparing the image rendered by an original shader and a transformed variant of that shader. And adding new transformations to `Spir-fuzz` tool to improve their support for compute shaders because the tool have limited support for compute shaders.

My objective is adding tranformations and fuzzer passes for atomic and barrier instructions, improve the implementation of the tooling and find bugs in existing graphics drivers.

### Improvements

- [#4295](https://github.com/KhronosGroup/SPIRV-Tools/pull/4295): Enhancing permute function variables and it's testing.
- [#4289](https://github.com/KhronosGroup/SPIRV-Tools/pull/4289): Enhance test to improve lines covered in TransformationAddConstantScalar
- [#4312](https://github.com/KhronosGroup/SPIRV-Tools/pull/4312): Improve TransformationAddBitInstructionSynonym to check integer signedness
- [#4306](https://github.com/KhronosGroup/SPIRV-Tools/pull/4306): Achieve coverage of TransformationAddDeadBlock test
- [#4349](https://github.com/KhronosGroup/SPIRV-Tools/pull/4349): Don't allow to use of non-constant operands in atomic operations
- [#4348](https://github.com/KhronosGroup/SPIRV-Tools/pull/4348): Support atomic operations opcode and checks it's in operands signedness neutrality
- [#4330](https://github.com/KhronosGroup/SPIRV-Tools/pull/4330): Support AtomicLoad
- [#4440](https://github.com/KhronosGroup/SPIRV-Tools/pull/4440): Support AtomicStore

### Transformations and Fuzzer Passes
- [#4248](https://github.com/KhronosGroup/SPIRV-Tools/pull/4248): Permute the order of variables at function scope
- [#4455](https://github.com/KhronosGroup/SPIRV-Tools/pull/4455): Transformation and fuzzer pass for changing memory semantics 
- [#4483](https://github.com/KhronosGroup/SPIRV-Tools/pull/4483): Transformation and fuzzer pass for changing memory scope

- WIP transformations and fuzzer passes
  - [#4470](https://github.com/KhronosGroup/SPIRV-Tools/pull/4470): Transformation and fuzzer pass for adding atomic RMW instruction
  
- WIP issues
  - [#4484](https://github.com/KhronosGroup/SPIRV-Tools/issues/4484): Adding memory barrier instructions
  - [#4485](https://github.com/KhronosGroup/SPIRV-Tools/issues/4485): Adding control barrier instructions

The following are examples of SPIR-V code to demonstrate some transformations I proposed.
- **Transformation and fuzzer pass for changing memory semantics:** its changes memory semantics for atomic or barrier instruction to stronger order
<table>
  <tr>
    <th>
      Reference shader
    </th>
    <th>
      Variant shader
    </th>
  </tr>
  <tr>
    <td>
      <pre>
        <code>
       OpCapability Shader
       OpCapability Int8
  %1 = OpExtInstImport "GLSL.std.450"
       OpMemoryModel Logical GLSL450
       OpEntryPoint Fragment %4 "main"
       OpExecutionMode %4 OriginUpperLeft
       OpSource ESSL 320
  %2 = OpTypeVoid
  %3 = OpTypeFunction %2
  %6 = OpTypeInt 32 1
  %7 = OpTypeInt 8 1
  %9 = OpTypeInt 32 0
  %8 = OpTypeStruct %6
 %10 = OpTypePointer StorageBuffer %8
 %11 = OpVariable %10 StorageBuffer
 %18 = OpConstant %9 1
 %12 = OpConstant %6 0
 %13 = OpTypePointer StorageBuffer %6
 %15 = OpConstant %6 4
 %16 = OpConstant %6 7
 %17 = OpConstant %7 4
%250 = OpConstant %6 66  ; Acquire | UniformMemory
 %26 = OpConstant %6 258 ; Acquire | WorkgroupMemory
 %27 = OpConstant %6 68  ; Release | UniformMemory
 %28 = OpConstant %6 80  ; SequentiallyConsistent | UniformMemory
 %29 = OpConstant %6 64  ; None | UniformMemory
 %30 = OpConstant %6 256 ; None | WorkgroupMemory
 %31 = OpConstant %6 72 ; AcquireRelease | UniformMemory
  %4 = OpFunction %2 None %3
  %5 = OpLabel
 %14 = OpAccessChain %13 %11 %12
<strong>
 %21 = OpAtomicLoad %6 %14 %15 %250
 %22 = OpAtomicLoad %6 %14 %15 %29
</strong>
 %23 = OpAtomicLoad %6 %14 %15 %27
<strong>
 %32 = OpAtomicExchange %6 %14 %15 %31 %16
 %33 = OpAtomicCompareExchange %6 %14 %15 %28 %29 %16 %15
</strong>
 %24 = OpAccessChain %13 %11 %12
       <strong>OpAtomicStore %14 %15 %27 %16</strong>
       OpReturn
       OpFunctionEnd
</code>
      </pre>
    </td>
    <td>
      <pre>
        <code>               
       OpCapability Shader
       OpCapability Int8
  %1 = OpExtInstImport "GLSL.std.450"
       OpMemoryModel Logical GLSL450
       OpEntryPoint Fragment %4 "main"
       OpExecutionMode %4 OriginUpperLeft
       OpSource ESSL 320
  %2 = OpTypeVoid
  %3 = OpTypeFunction %2
  %6 = OpTypeInt 32 1
  %7 = OpTypeInt 8 1
  %9 = OpTypeInt 32 0
  %8 = OpTypeStruct %6
 %10 = OpTypePointer StorageBuffer %8
 %11 = OpVariable %10 StorageBuffer
 %18 = OpConstant %9 1
 %12 = OpConstant %6 0
 %13 = OpTypePointer StorageBuffer %6
 %15 = OpConstant %6 4
 %16 = OpConstant %6 7
 %17 = OpConstant %7 4
%250 = OpConstant %6 66  ; Acquire | UniformMemory
 %26 = OpConstant %6 258 ; Acquire | WorkgroupMemory
 %27 = OpConstant %6 68  ; Release | UniformMemory
 %28 = OpConstant %6 80  ; SequentiallyConsistent | UniformMemory
 %29 = OpConstant %6 64  ; None | UniformMemory
 %30 = OpConstant %6 256 ; None | WorkgroupMemory
 %31 = OpConstant %6 72 ; AcquireRelease | UniformMemory
  %4 = OpFunction %2 None %3
  %5 = OpLabel
 %14 = OpAccessChain %13 %11 %12
<strong>
 %21 = OpAtomicLoad %6 %14 %15 %28
 %22 = OpAtomicLoad %6 %14 %15 %250</strong>
 %23 = OpAtomicLoad %6 %14 %15 %27
<strong>
 %32 = OpAtomicExchange %6 %14 %15 %28 %16
 %33 = OpAtomicCompareExchange %6 %14 %15 %28 %28 %16 %15</strong>
 %24 = OpAccessChain %13 %11 %12
<strong>
       OpAtomicStore %14 %15 %28 %16</strong>
       OpReturn
       OpFunctionEnd
     </code>
      </pre>
    </td>
  </tr>
</table>

- **Support AtomicLoad:** Improve transformation(and fuzzer pass)load to support adding `OpAtomicLoad`  

<table>
  <tr>
    <th>
      Reference shader
    </th>
    <th>
      Variant shader
    </th>
  </tr>
  <tr>
    <td>
      <pre>
        <code>
       OpCapability Shader
       OpCapability Int8
  %1 = OpExtInstImport "GLSL.std.450"
       OpMemoryModel Logical GLSL450
       OpEntryPoint Fragment %4 "main"
       OpExecutionMode %4 OriginUpperLeft
       OpSource ESSL 310
  %2 = OpTypeVoid
  %3 = OpTypeFunction %2
  %6 = OpTypeInt 32 1
 %26 = OpTypeFloat 32
 %27 = OpTypeInt 8 1
  %7 = OpTypeInt 32 0 ; 0 means unsigned
  %8 = OpConstant %7 0
 %17 = OpConstant %27 4
 %19 = OpConstant %26 0
  %9 = OpTypePointer Function %6
 %13 = OpTypeStruct %6
 %12 = OpTypePointer Workgroup %13
 %11 = OpVariable %12 Workgroup
 %14 = OpConstant %6 0
 %15 = OpTypePointer Function %6
 %51 = OpTypePointer Private %6
 %21 = OpConstant %6 4
 %23 = OpConstant %6 256
 %25 = OpTypePointer Function %7
 %50 = OpTypePointer Workgroup %6
 %34 = OpTypeBool
 %35 = OpConstantFalse %34
 %53 = OpVariable %51 Private
  %4 = OpFunction %2 None %3
  %5 = OpLabel
       OpSelectionMerge %37 None
       OpBranchConditional %35 %36 %37
 %36 = OpLabel
 %38 = OpAccessChain %50 %11 %14
 %40 = OpAccessChain %50 %11 %14
       OpBranch %37
 %37 = OpLabel
       OpReturn
       OpFunctionEnd
</code>
      </pre>
    </td>
    <td>
      <pre>
<code>               
       OpCapability Shader
       OpCapability Int8
  %1 = OpExtInstImport "GLSL.std.450"
       OpMemoryModel Logical GLSL450
       OpEntryPoint Fragment %4 "main"
       OpExecutionMode %4 OriginUpperLeft
       OpSource ESSL 310
  %2 = OpTypeVoid
  %3 = OpTypeFunction %2
  %6 = OpTypeInt 32 1
 %26 = OpTypeFloat 32
 %27 = OpTypeInt 8 1
  %7 = OpTypeInt 32 0 ; 0 means unsigned
  %8 = OpConstant %7 0
 %17 = OpConstant %27 4
 %19 = OpConstant %26 0
  %9 = OpTypePointer Function %6
 %13 = OpTypeStruct %6
 %12 = OpTypePointer Workgroup %13
 %11 = OpVariable %12 Workgroup
 %14 = OpConstant %6 0
 %15 = OpTypePointer Function %6
 %51 = OpTypePointer Private %6
 %21 = OpConstant %6 4
 %23 = OpConstant %6 256
 %25 = OpTypePointer Function %7
 %50 = OpTypePointer Workgroup %6
 %34 = OpTypeBool
 %35 = OpConstantFalse %34
 %53 = OpVariable %51 Private
  %4 = OpFunction %2 None %3
  %5 = OpLabel
       OpSelectionMerge %37 None
       OpBranchConditional %35 %36 %37
 %36 = OpLabel
 %38 = OpAccessChain %50 %11 %14
 <strong>
 %60 = OpAtomicLoad %6 %38 %21 %23
 </strong>
 %40 = OpAccessChain %50 %11 %14
       OpBranch %37
 %37 = OpLabel
       OpReturn
       OpFunctionEnd
     </code>
      </pre>
    </td>
  </tr>
</table>


### Shadertrap Contributions
  - [#68](https://github.com/google/shadertrap/pull/68): Examples of computing a histogram of values

### Bug finding

  - Using gfauto, I re-discovered a known bug in SwiftShader: [UNIMPLEMENTED_b_Function_block_instruction_Return](https://issuetracker.google.com/issues/141246700)
  - Using gfauto, I found a shader that crashes spirv-opt: OpPhis_incoming_basic_block_id_is_not_a_predecessor. This might be a known bug, as there are already a few similar, open GitHub issues in the SPIRV-Tools project.

### Conclusion
It was a pleasure to participate in the Google Summer of Code 2021 program. I learned more about new tools, computer graphics, improved my coding skills, enhanced my git skills, and I'm looking for more. The plan is to continue contributing to the project after GSoC for finishing my role and I'm thinking to make a `Shadertrap formatting tool` and other works from my mentors' suggestions. Finally, I would like to thank my mentors, Alastair Donaldson, Paul Thomson, Vasyl Teliman, for the opportunity. GSoC gave me a high impact for continuously learning and some points of weakness I have and I will do my best to overcome them.

