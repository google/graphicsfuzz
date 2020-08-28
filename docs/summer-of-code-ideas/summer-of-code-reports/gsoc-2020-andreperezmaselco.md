# Google Summer of Code 2020 Report

##### Project:
##### [Fuzzing Graphics Shaders](https://summerofcode.withgoogle.com/projects/#6594591471435776)

##### Student:
##### [André Perez Maselco](https://github.com/andreperezmaselco)

##### Organization:
##### [Android Graphics Tools Team](https://summerofcode.withgoogle.com/organizations/6102438181863424/)

##### Mentors:
##### [Alastair Donaldson](https://github.com/afd)
##### [Paul Thomson](https://github.com/paulthomson)

## Abstract

During the *Google Summer of Code 2020* program, I worked on the development of new *semantics-preserving transformations*, fuzzer passes and enhancements for the [*spirv-fuzz*](https://github.com/google/graphicsfuzz/blob/master/docs/finding-a-vulkan-driver-bug-using-spirv-fuzz.md) [3] tool.
Also, I developed [Amber Fuzz](https://github.com/andreperezmaselco/amber-fuzz), a simple graphics tool that takes an [Amber](https://github.com/google/amber) script as input and outputs a new script by fuzzing the reference shaders.

## Introduction
Graphics shaders are programs to run on a Graphics Processing Unit. Shaders are written in some shading language such as  *OpenGL Shading Language* and *SPIR-V* (a simple binary intermediate language). Since shaders are programs, they need to be compiled into specific GPU instructions. A question that arises is what if the shader compiler has bugs? Some tools have been developed to deal with this problem.

[SPIRV-Tools](https://github.com/KhronosGroup/SPIRV-Tools) is a collection of tools to work on *SPIR-V* modules.
One of these tools is *spirv-fuzz*. It applies *semantics-preserving transformations* to a *SPIR-V* module, that we call the *reference* module, to obtain a modified *variant* module. Theoretically, a *semantics-preserving transformation* should not change the behaviour of the code. In this way, the *reference* and *variant* modules can be run, and the outputs compared. The output is usually an image. If the images differ, this probably indicates a shader compiler bug. This is a specialised form of *fuzzing* [1] that uses a technique called *metamorphic testing* [2].

This project involved using the metamorphic testing approach to extend *spirv-fuzz* by adding new transformations to cover instructions and patterns of *SPIR-V* that were not previously covered by existing *spirv-fuzz* transformations. Also, the project used tools such as [CLSmith](https://github.com/ChrisLidbury/CLSmith) and [clspv](https://github.com/google/clspv) to improve *spirv-fuzz*.

## Development

Transformations and fuzzer passes work together to modify the shader source code.
The fuzzer pass iterates over the *SPIR-V* module and decides whether a transformation will be applied.
Below are the transformations, fuzzer passes and improvements that I developed during the program.

### Transformations and Fuzzer Passes

- [#3205](https://github.com/KhronosGroup/SPIRV-Tools/pull/3205): Swap Commutable Operands

- [#3211](https://github.com/KhronosGroup/SPIRV-Tools/pull/3211): Toggle Access Chain Instruction

- [#3336](https://github.com/KhronosGroup/SPIRV-Tools/pull/3336): Adjust Branch Weights

- [#3359](https://github.com/KhronosGroup/SPIRV-Tools/pull/3359): Push Ids Through Variable

- [#3402](https://github.com/KhronosGroup/SPIRV-Tools/pull/3402): Replace Linear Algebra Instruction

- [#3412](https://github.com/KhronosGroup/SPIRV-Tools/pull/3412): Add Vector Shuffle Instruction

- [#3439](https://github.com/KhronosGroup/SPIRV-Tools/pull/3439): Add Image Sample Unused Components

- [#3597](https://github.com/KhronosGroup/SPIRV-Tools/pull/3597): Make Vector Operations Dynamic

- [#3517](https://github.com/KhronosGroup/SPIRV-Tools/pull/3517): Inline Function

The following are examples of *SPIR-V* code to demonstrate some transformations I proposed.

- __Replace Linear Algebra Instruction:__ It replaces linear algebra instructions, such as `OpTranspose`, `OpVectorTimesScalar`, `OpMatrixTimesScalar`, `OpVectorTimesMatrix`, `OpMatrixTimesVector`, `OpMatrixTimesMatrix`, `OpOuterProduct` and `OpDot`,
with their mathematical definitions.

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
 %1 = OpExtInstImport "GLSL.std.450"
      OpMemoryModel Logical GLSL450
      OpEntryPoint Vertex %10 "main"
        <br>
; Types
 %2 = OpTypeFloat 32
 %3 = OpTypeVoid
 %4 = OpTypeFunction %3
 %5 = OpTypeVector %2 2
        <br>
; Constants
 %6 = OpConstant %2 1
 %7 = OpConstant %2 2
 %8 = OpConstant %2 3
 %9 = OpConstantComposite %5 %6 %7
        <br>
; main function
%10 = OpFunction %3 None %4
%11 = OpLabel
<strong>%12 = OpVectorTimesScalar %5 %9 %8</strong>
      OpReturn
      OpFunctionEnd
        </code>
      </pre>
    </td>
    <td>
      <pre>
        <code>
      OpCapability Shader
 %1 = OpExtInstImport "GLSL.std.450"
      OpMemoryModel Logical GLSL450
      OpEntryPoint Vertex %10 "main"
        <br>
; Types
 %2 = OpTypeFloat 32
 %3 = OpTypeVoid
 %4 = OpTypeFunction %3
 %5 = OpTypeVector %2 2
        <br>
; Constants
 %6 = OpConstant %2 1
 %7 = OpConstant %2 2
 %8 = OpConstant %2 3
 %9 = OpConstantComposite %5 %6 %7
        <br>
; main function
%10 = OpFunction %3 None %4
%11 = OpLabel
<strong>%13 = OpCompositeExtract %2 %9 0
%14 = OpFMul %2 %13 %8
%15 = OpCompositeExtract %2 %9 1
%16 = OpFMul %2 %15 %8
%12 = OpCompositeConstruct %5 %14 %16</strong>
      OpReturn
      OpFunctionEnd
        </code>
      </pre>
    </td>
  </tr>
</table>

- __Add Bit Instruction Synonym:__ It does a bitwise evaluation for the given bit instruction, constructs the result and states that the original and new instructions are synonymous.
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
      OpEntryPoint Vertex %7 "main"
        <br>
 ; Types
 %2 = OpTypeInt 8 0
 %3 = OpTypeVoid
 %4 = OpTypeFunction %3
        <br>
 ; Constants
 %5 = OpConstant %2 0
 %6 = OpConstant %2 1
        <br>
 ; main function
 %7 = OpFunction %3 None %4
 %8 = OpLabel
 <strong>%9 = OpBitwiseOr %2 %5 %6</strong>
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
      OpEntryPoint Vertex %7 "main"
        <br>
; Types
 %2 = OpTypeInt 8 0
 %3 = OpTypeVoid
 %4 = OpTypeFunction %3
        <br>
; Constants
 %5 = OpConstant %2 0
 %6 = OpConstant %2 1
<strong>%10 = OpConstant %2 2
%11 = OpConstant %2 3
%12 = OpConstant %2 4
%13 = OpConstant %2 5
%14 = OpConstant %2 6
%15 = OpConstant %2 7</strong>
        <br>
; main function
 %7 = OpFunction %3 None %4
 %8 = OpLabel
        <br>
<strong>%16 = OpBitFieldUExtract %2 %5 %5 %6 ; extracts bit 0 from %5
%17 = OpBitFieldUExtract %2 %6 %5 %6 ; extracts bit 0 from %6
%18 = OpBitwiseOr %2 %16 %17
        <br>
%19 = OpBitFieldUExtract %2 %5 %6 %6 ; extracts bit 1 from %5
%20 = OpBitFieldUExtract %2 %6 %6 %6 ; extracts bit 1 from %6
%21 = OpBitwiseOr %2 %19 %20
        <br>
%22 = OpBitFieldUExtract %2 %5 %10 %6 ; extracts bit 2 from %5
%23 = OpBitFieldUExtract %2 %6 %10 %6 ; extracts bit 2 from %6
%24 = OpBitwiseOr %2 %22 %23
        <br>
%25 = OpBitFieldUExtract %2 %5 %11 %6 ; extracts bit 3 from %5
%26 = OpBitFieldUExtract %2 %6 %11 %6 ; extracts bit 3 from %6
%27 = OpBitwiseOr %2 %25 %26
        <br>
%28 = OpBitFieldUExtract %2 %5 %12 %6 ; extracts bit 4 from %5
%29 = OpBitFieldUExtract %2 %6 %12 %6 ; extracts bit 4 from %6
%30 = OpBitwiseOr %2 %28 %29
        <br>
%31 = OpBitFieldUExtract %2 %5 %13 %6 ; extracts bit 5 from %5
%32 = OpBitFieldUExtract %2 %6 %13 %6 ; extracts bit 5 from %6
%33 = OpBitwiseOr %2 %31 %32
        <br>
%34 = OpBitFieldUExtract %2 %5 %14 %6 ; extracts bit 6 from %5
%35 = OpBitFieldUExtract %2 %6 %14 %6 ; extracts bit 6 from %6
%36 = OpBitwiseOr %2 %34 %35
        <br>
%37 = OpBitFieldUExtract %2 %5 %15 %6 ; extracts bit 7 from %5
%38 = OpBitFieldUExtract %2 %6 %15 %6 ; extracts bit 7 from %6
%39 = OpBitwiseOr %2 %37 %38
        <br>
%40 = OpBitFieldInsert %2 %5 %18 %5 %6 ; inserts bit 0
%41 = OpBitFieldInsert %2 %40 %21 %6 %6 ; inserts bit 1
%42 = OpBitFieldInsert %2 %41 %24 %10 %6 ; inserts bit 2
%43 = OpBitFieldInsert %2 %42 %27 %11 %6 ; inserts bit 3
%44 = OpBitFieldInsert %2 %43 %30 %12 %6 ; inserts bit 4
%45 = OpBitFieldInsert %2 %44 %33 %13 %6 ; inserts bit 5
%46 = OpBitFieldInsert %2 %45 %36 %14 %6 ; inserts bit 6
 %9 = OpBitFieldInsert %2 %46 %39 %15 %6 ; inserts bit 7</strong>
      OpReturn
      OpFunctionEnd
        </code>
      </pre>
    </td>
  </tr>
</table>

### Improvements

*CLSmith* is a random generator of *OpenCL C* programs and *clspv* is a compiler for *OpenCL C* to *Vulkan* compute shaders.
I used these tools together to generate random *SPIR-V* code. Running that code in *spirv-fuzz*, I was able to detect improvements to be made to it. All the improvements that have been made are listed below.

- [#3206](https://github.com/KhronosGroup/SPIRV-Tools/pull/3206): Refactor `FuzzerPass::ApplyTransformation` code duplication

- [#3221](https://github.com/KhronosGroup/SPIRV-Tools/pull/3221): Allow `OpPhi` operand to be replaced with a composite synonym

- [#3372](https://github.com/KhronosGroup/SPIRV-Tools/pull/3372): Fix function use

- [#3378](https://github.com/KhronosGroup/SPIRV-Tools/pull/3378): Support bit width argument for int and float types

- [#3385](https://github.com/KhronosGroup/SPIRV-Tools/pull/3385): Fix assertion failure related to transformation applicability

- [#3390](https://github.com/KhronosGroup/SPIRV-Tools/pull/3390): Fix instruction function use

- [#3450](https://github.com/KhronosGroup/SPIRV-Tools/pull/3450): Implement the `OpMatrixTimesScalar` linear algebra case

- [#3485](https://github.com/KhronosGroup/SPIRV-Tools/pull/3485): Add variables with workgroup storage class

- [#3489](https://github.com/KhronosGroup/SPIRV-Tools/pull/3489): Implement the `OpVectorTimesMatrix` linear algebra case

- [#3500](https://github.com/KhronosGroup/SPIRV-Tools/pull/3500): Implement the `OpMatrixTimesVector` linear algebra case

- [#3518](https://github.com/KhronosGroup/SPIRV-Tools/pull/3518): Support `OpPhi` when replacing boolean constant operand

- [#3519](https://github.com/KhronosGroup/SPIRV-Tools/pull/3519): Support adding dead break from back-edge block

- [#3521](https://github.com/KhronosGroup/SPIRV-Tools/pull/3521): Fix instruction insertion issue

- [#3527](https://github.com/KhronosGroup/SPIRV-Tools/pull/3527): Implement the `OpMatrixTimesMatrix` linear algebra case

- [#3587](https://github.com/KhronosGroup/SPIRV-Tools/pull/3587): Add condition to make functions livesafe

- [#3589](https://github.com/KhronosGroup/SPIRV-Tools/pull/3589): Implement the `OpTranspose` linear algebra case

- [#3610](https://github.com/KhronosGroup/SPIRV-Tools/pull/3610): Improve the code of the `Instruction` class

- [#3617](https://github.com/KhronosGroup/SPIRV-Tools/pull/3617): Implement the `OpOuterProduct` linear algebra case

- [#3654](https://github.com/KhronosGroup/SPIRV-Tools/pull/3654): Iterate over blocks in replace linear algebra pass

- [#3664](https://github.com/KhronosGroup/SPIRV-Tools/pull/3664): Ignore specialization constants

- [#3666](https://github.com/KhronosGroup/SPIRV-Tools/pull/3666): Fix in operand type assertion

- [#3670](https://github.com/KhronosGroup/SPIRV-Tools/pull/3670): Check integer and float width capabilities

- [#3672](https://github.com/KhronosGroup/SPIRV-Tools/pull/3672): Consider additional access chain instructions

- [#3682](https://github.com/KhronosGroup/SPIRV-Tools/pull/3682): Add `spvOpcodeIsAccessChain` function

- [#3694](https://github.com/KhronosGroup/SPIRV-Tools/pull/3694): Check header dominance when adding dead block

- [#3710](https://github.com/KhronosGroup/SPIRV-Tools/pull/3710): Check termination instructions when donating modules

- [#3711](https://github.com/KhronosGroup/SPIRV-Tools/pull/3711): Implement `opt::Function::HasEarlyReturn` function

- [#3728](https://github.com/KhronosGroup/SPIRV-Tools/pull/3728): Add words instead of logical operands

### Issues

- [#3354](https://github.com/KhronosGroup/SPIRV-Tools/issues/3354): Replace linear algebra instruction with its mathematical definition

- [#3484](https://github.com/KhronosGroup/SPIRV-Tools/issues/3484): Replace `OpSwitch` with blocks containing an `OpBranchConditional` instruction

- [#3557](https://github.com/KhronosGroup/SPIRV-Tools/issues/3557): Split bit instructions into most fundamental operations

- [#3588](https://github.com/KhronosGroup/SPIRV-Tools/issues/3588): Dynamically extract and insert vector components

- [#3646](https://github.com/KhronosGroup/SPIRV-Tools/issues/3646): Replace decimal arithmetic with binary arithmetic

## Conclusion
It was great to participate in the *Google Summer of Code 2020* program. I was able to learn more about computer graphics, improve code writing and project collaboration. Finally, I would like to thank my mentors and coworkers, Alastair Donaldson, Paul Thomson, Vasyl Teliman, Antoni Karpiński and Stefano Milizia, for the opportunity.

## References
[1] Donaldson, A.; Thomson, P.; Evrard, H.; Lascu, A. [Automated Testing of Graphics Shader Compilers](https://www.doc.ic.ac.uk/~afd/homepages/papers/pdfs/2017/OOPSLA.pdf).

[2] Donaldson, A.; Lascu, A. [Metamorphic Testing for (Graphics) Compilers. 2016](http://www.doc.ic.ac.uk/~afd/homepages/papers/pdfs/2016/MET.pdf).

[3] Thomson, P. [Finding a Vulkan driver bug using spirv-fuzz](https://github.com/google/graphicsfuzz/blob/master/docs/finding-a-vulkan-driver-bug-using-spirv-fuzz.md).
