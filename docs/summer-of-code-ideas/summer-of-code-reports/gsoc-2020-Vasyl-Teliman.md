# Google Summer of Code 2020 Report - Vasyl Teliman

###### Personal Links:
###### [Github](https://github.com/Vasniktel) - [Linkedin](https://www.linkedin.com/in/vasyl-teliman-775421153/)

###### Proposal:
###### [Implementation of additional transformations for spirv-fuzz](https://summerofcode.withgoogle.com/projects/#4892481772060672)

###### Mentors: 
###### Alastair Donaldson, Paul Thomson

## High-level feedback
I've decided to put this section first since it's somewhat unlikely that a potential GSoC student will read through all the information in this document :)

I'm glad I've had the opportunity to participate in this project. It was a pleasure working with my mentors, doing code reviews and implementing various
interesting features.

Don't be afraid to apply even if you have very little experience in the field at hand. You will learn everything you need to know along the way.

## Introduction
This document describes what was accomplished during this project, including:
- Project objectives
- Implementation of those objectives
- Potential ideas for the future

## Objectives and their implementation
Project's deliverables were determined during the early days of the project:
- Extend the set of transformations in *spirv-fuzz* by implementing new ones
- Find bugs in existing graphics drivers
- Improve the implementation of the tooling

### Extend the set of transformations in spirv-fuzz
*spirv-fuzz* works by applying a set of transformations to the original (reference) shader to produce a modified one (variant shader) (learn more about metamorphic testing and fuzzing in [1, 7, 8]). Those transformations do not change 
the semantics of the program. Thus, differences in the output of the reference and variant shaders might be caused by bugs in the graphics driver. Hence, it is important to
have a sufficiently rich set of transformations. To that end, the following pull requests were implemented containing new transformations for the tool.

[#3212](https://github.com/KhronosGroup/SPIRV-Tools/pull/3212): Transformation to permute function parameters

[#3391](https://github.com/KhronosGroup/SPIRV-Tools/pull/3391): Introduce `OpCopyMemory` instructions to the module

[#3399](https://github.com/KhronosGroup/SPIRV-Tools/pull/3399): Add new parameters to the function

[#3421](https://github.com/KhronosGroup/SPIRV-Tools/pull/3421): Permute operands in the OpPhi instruction

[#3423](https://github.com/KhronosGroup/SPIRV-Tools/pull/3423): Swap operands in OpBranchConditional instruction

[#3434](https://github.com/KhronosGroup/SPIRV-Tools/pull/3434): Replace function parameters with global variables

[#3447](https://github.com/KhronosGroup/SPIRV-Tools/pull/3447): Add synonyms to the module

[#3455](https://github.com/KhronosGroup/SPIRV-Tools/pull/3455): Replace function parameters with structs

[#3475](https://github.com/KhronosGroup/SPIRV-Tools/pull/3475): Invert comparison operators

[#3477](https://github.com/KhronosGroup/SPIRV-Tools/pull/3477): Permute instructions in the basic block

[#3478](https://github.com/KhronosGroup/SPIRV-Tools/pull/3478): Propagate instruction from the basic block to its predecessors

[#3674](https://github.com/KhronosGroup/SPIRV-Tools/pull/3674): Outline selection constructs

[#3692](https://github.com/KhronosGroup/SPIRV-Tools/pull/3692): Propagate instructions from the basic block into its successors

[#3737](https://github.com/KhronosGroup/SPIRV-Tools/pull/3737): Back up the pointer, write through the pointer, restore the original value

### Bugs in graphics shaders
The implemented transformations have been used to find bugs in graphics drivers and supporting tooling. As a result, several bugs have been found in Mesa open-source project [2] and *spirv-opt* tool [3] for SPIR-V optimization.
Additionally, some inconsistencies in the SPIR-V specification [4] and *spirv-val* [5] tool were found and reported. Links to the corresponding issues are provided below.

Mesa issues: [#3418](https://gitlab.freedesktop.org/mesa/mesa/-/issues/3418), [#3427](https://gitlab.freedesktop.org/mesa/mesa/-/issues/3427),
[#3428](https://gitlab.freedesktop.org/mesa/mesa/-/issues/3428), [#3452](https://gitlab.freedesktop.org/mesa/mesa/-/issues/3452),
[#3453](https://gitlab.freedesktop.org/mesa/mesa/-/issues/3453).

*spirv-opt* issues: [#3643](https://github.com/KhronosGroup/SPIRV-Tools/issues/3643), [#3738](https://github.com/KhronosGroup/SPIRV-Tools/issues/3738),
[#3631](https://github.com/KhronosGroup/SPIRV-Tools/issues/3631),
[#3715](https://github.com/KhronosGroup/SPIRV-Tools/issues/3715), [#3708](https://github.com/KhronosGroup/SPIRV-Tools/issues/3708),
[#3707](https://github.com/KhronosGroup/SPIRV-Tools/issues/3707), [#3706](https://github.com/KhronosGroup/SPIRV-Tools/issues/3706),
[#3635](https://github.com/KhronosGroup/SPIRV-Tools/issues/3635), [#3650](https://github.com/KhronosGroup/SPIRV-Tools/issues/3650),
[#3515](https://github.com/KhronosGroup/SPIRV-Tools/issues/3515).

SPIR-V specification and *spirv-val*: [#3716](https://github.com/KhronosGroup/SPIRV-Tools/issues/3716), [#3650](https://github.com/KhronosGroup/SPIRV-Tools/issues/3650).

#### Example
One of the implemented transformations was very helpful in uncovering a bug in the Mesa open-source library. The transformation propagates an instruction from its
basic block into the block's predecessors. This transformation is a part of a larger idea of being able to permute instructions in the function. Concretely, this transformation:
1. Copies some instruction from its basic block into the block's predecessors
2. Replaces the original instruction with an `OpPhi` instruction that selects one of the copies based on the previously executed block

In SPIR-V-like pseudocode:
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
%1 = OpLabel
     OpBranch %3
<br />
%2 = OpLabel
     OpBranch %3
<br />
%3 = OpLabel
<strong>%4 = OpIAdd %int %a %b</strong>
...
</code>
      </pre>
    </td>
    <td>
      <pre>
        <code>
%1 = OpLabel
<strong>%5 = OpIAdd %int %a %b</strong>
     OpBranch %3
<br />
%2 = OpLabel
<strong>%6 = OpIAdd %int %a %b</strong>
     OpBranch %3
<br />
%3 = OpLabel
<strong>%4 = OpPhi %int %5 %1 %6 %2</strong>
...
</code>
      </pre>
    </td>
  </tr>
</table>
This transformation triggers a bug in Mesa open-source library that causes the shader to generate a wrong image. 

Reference shader output | Variant shader output
--- | ---
![Reference shader output](https://imgur.com/LPQxiRr.png) | ![Variant output shader](https://imgur.com/o383jfp.png)

The bug is triggered when the transformation moves increment and comparison instructions in the loop as follows:
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
...
%493 = OpLabel
       OpBranch %495
<br />
%495 = OpLabel
%527 = OpPhi %6 %522 %493 %508 %500
<strong>%499 = OpSLessThanEqual %31 %527 %416</strong>
       OpLoopMerge %509 %500 None
       OpBranchConditional %499 %500 %509
<br />
%500 = OpLabel
<strong>%508 = OpIAdd %6 %527 %22</strong>
       OpBranch %495
...
</code>
      </pre>
    </td>
    <td>
      <pre>
        <code>
...
%493 = OpLabel
<strong>%761 = OpSLessThanEqual %31 %522 %416</strong>
       OpBranch %495
<br />
%495 = OpLabel
<strong>%499 = OpPhi %31 %761 %493 %762 %500</strong>
%527 = OpPhi %6 %522 %493 %508 %500
<strong>%655 = OpIAdd %6 %527 %22</strong>
<strong>%763 = OpSLessThanEqual %31 %655 %416</strong>
       OpLoopMerge %509 %500 None
       OpBranchConditional %499 %500 %509
<br />
%500 = OpLabel
<strong>%762 = OpPhi %31 %763 %495</strong>
<strong>%508 = OpPhi %6 %655 %495</strong>
       OpBranch %495
...
</code>
      </pre>
    </td>
  </tr>
</table>

In the example above, `OpSLessThanEqual` instruction is propagated from the loop's header block (`%495`) into its predecessors: `%493` and the back-edge block `%500`. This produces ids `%762` and `%761`.
Then, both `OpSLessThanEqual` and `OpIAdd` instructions are propagated from the loop's back-edge block (`%500`) into its header (`%495`) producing `%763` and `%655`.

According to the discussion in [#3428](https://gitlab.freedesktop.org/mesa/mesa/-/issues/3428), the internal optimization in the Mesa library has a bug that skips
the last iteration of the loop.

### Improve the implementation of the tooling
Several contributions to *spirv-fuzz* and *spirv-opt* tools have been made to fulfil this objective. Those contributions mostly fixed existing bugs and implemented additional features.

Contributions to *spirv-opt*:
- [#3435](https://github.com/KhronosGroup/SPIRV-Tools/pull/3435): Fix type of the value, returned by iterators.
- [#3437](https://github.com/KhronosGroup/SPIRV-Tools/pull/3437): Add `RemoveParameter` method.
- [#3516](https://github.com/KhronosGroup/SPIRV-Tools/pull/3516): Support `OpLabel` instructions in dominator analyses.
- [#3680](https://github.com/KhronosGroup/SPIRV-Tools/pull/3680): Fix off-by-1 error in constant folding.
- [#3683](https://github.com/KhronosGroup/SPIRV-Tools/pull/3683): Add support for `OpConstantNull` to loop unroller.

Contributions to *spirv-fuzz*:
- [#3225](https://github.com/KhronosGroup/SPIRV-Tools/pull/3225): Support `OpPhi` instructions when adding dead break and continue blocks.
- [#3220](https://github.com/KhronosGroup/SPIRV-Tools/pull/3220): Remove duplicated functionality from `TransformationFunctionCall`.
- [#3238](https://github.com/KhronosGroup/SPIRV-Tools/pull/3238): Add a test for `OpTypeRuntimeArray` to `FuzzerPassDonateModules`.
- [#3341](https://github.com/KhronosGroup/SPIRV-Tools/pull/3341): Remove `FuzzerPassAddUsefulConstructs`.
- [#3348](https://github.com/KhronosGroup/SPIRV-Tools/pull/3348): Add support for `StorageBuffer` storage class to `FuzzerPassDonateModules`.
- [#3373](https://github.com/KhronosGroup/SPIRV-Tools/pull/3373): Add support for `OpSpecConstant*` instructions to `FuzzerPassDOnateModules`.
- [#3396](https://github.com/KhronosGroup/SPIRV-Tools/pull/3396): Fix regressions in `TransformationPushIdThroughVariable`.
- [#3401](https://github.com/KhronosGroup/SPIRV-Tools/pull/3401): Fix comparison in closure computation in `DataSynonymAndIdEquationFacts`.
- [#3414](https://github.com/KhronosGroup/SPIRV-Tools/pull/3414): Refactor variable creation.
- [#3427](https://github.com/KhronosGroup/SPIRV-Tools/pull/3427): Fix memory operand access in `TransformationSetMemoryOperandsMasks`.
- [#3454](https://github.com/KhronosGroup/SPIRV-Tools/pull/3454): Add `GetParameters` function to `fuzzerutil` namespace.
- [#3469](https://github.com/KhronosGroup/SPIRV-Tools/pull/3469): Add a single parameter to the function at a time.
- [#3472](https://github.com/KhronosGroup/SPIRV-Tools/pull/3472): Add support for `OpConvert*` instructions to `TransformationEqationInstruction`.
- [#3479](https://github.com/KhronosGroup/SPIRV-Tools/pull/3479): Create a set of `fuzzerutil::MaybeGetConstant*` functions.
- [#3523](https://github.com/KhronosGroup/SPIRV-Tools/pull/3523): Add support for `OpBitcast` to `TransformationEquationInstruction`.
- [#3531](https://github.com/KhronosGroup/SPIRV-Tools/pull/3531): Remove `TransformationCopyObject`.
- [#3538](https://github.com/KhronosGroup/SPIRV-Tools/pull/3538): Compute corollary facts from equation facts with `OpBitcast` opcode.
- Create a new `IdIsIrrelevant` fact:
  - [#3561](https://github.com/KhronosGroup/SPIRV-Tools/pull/3561): Add `IdIsIrrelevant` fact.
  - [#3563](https://github.com/KhronosGroup/SPIRV-Tools/pull/3563): Use `FindOrCreate*` methods to create irrelevant constants.
  - [#3565](https://github.com/KhronosGroup/SPIRV-Tools/pull/3565): Refactor usages of irrelevant constants.
  - [#3566](https://github.com/KhronosGroup/SPIRV-Tools/pull/3566): Fix existing usages of irrelevant ids.
  - [#3578](https://github.com/KhronosGroup/SPIRV-Tools/pull/3578): Test usages of `IdIsIrrelevant` fact.
  - [#3583](https://github.com/KhronosGroup/SPIRV-Tools/pull/3583): Make `is_irrelevant` parameter non-default.
- [#3572](https://github.com/KhronosGroup/SPIRV-Tools/pull/3572): Create `fuzzerutil::UpdateFunctionType` helper.
- [#3602](https://github.com/KhronosGroup/SPIRV-Tools/pull/3602): Relax type constrains in `DataSynonym` facts.
- [#3608](https://github.com/KhronosGroup/SPIRV-Tools/pull/3608): Fix non-deterministic behaviour when serializing a transformation sequence.
- [#3622](https://github.com/KhronosGroup/SPIRV-Tools/pull/3622): Fix memory-related bugs in various transformations.
- [#3640](https://github.com/KhronosGroup/SPIRV-Tools/pull/3640): Handle `OpPhi` instructions during constant obfuscation.
- [#3642](https://github.com/KhronosGroup/SPIRV-Tools/pull/3642): Handle `OpPhi` instructions in livesafe functions.
- [#3651](https://github.com/KhronosGroup/SPIRV-Tools/pull/3651): Handle capabilities during module donation.
- [#3685](https://github.com/KhronosGroup/SPIRV-Tools/pull/3685): Fix bit width in `FuzzerUtilAddEquationInstructions`.
- [#3689](https://github.com/KhronosGroup/SPIRV-Tools/pull/3689): Support identical predecessors in `TransformationPropagateInstructionUp`.
- Refactor the fact manager. This is not finished (more PRs will be created):
  - [#3699](https://github.com/KhronosGroup/SPIRV-Tools/pull/3699): Split the fact manager into multiple files.
- [#3740](https://github.com/KhronosGroup/SPIRV-Tools/pull/3740): Fix `MaybeGetZeroConstant` function.
- [#3700](https://github.com/KhronosGroup/SPIRV-Tools/pull/3700): Add support for memory instructions to `TransformationMoveInstructionDown`.
- [#3729](https://github.com/KhronosGroup/SPIRV-Tools/pull/3729): Skip unreachable blocks in the fuzzer.
- [#3736](https://github.com/KhronosGroup/SPIRV-Tools/pull/3736): Add support for `BuiltIn` decoration.
- [#3742](https://github.com/KhronosGroup/SPIRV-Tools/pull/3742): Handle non-existent ids in the fact manager.
- [#3630](https://github.com/KhronosGroup/SPIRV-Tools/pull/3630): Remove `OpFunctionCall` operands in correct order.

### Potential ideas for the future
Some interesting ideas might be worthwhile implementing soon. Those include:

[#3695](https://github.com/KhronosGroup/SPIRV-Tools/issues/3695): Transformation to inline pointer parameters.

[#3723](https://github.com/KhronosGroup/SPIRV-Tools/issues/3723): Implement alias analysis.

[#3696](https://github.com/KhronosGroup/SPIRV-Tools/issues/3696): Introduce context-dependent facts.

[#3725](https://github.com/KhronosGroup/SPIRV-Tools/issues/3725): Determine if the function is pure.

### Challenges
Some of the implemented transformations were quite challenging. Most of the challenges came from various rules of the SPIR-V language that
must be satisfied. Specific transformations and various challenging implementation details are mentioned below.

#### [#3477](https://github.com/KhronosGroup/SPIRV-Tools/pull/3477): Permute instructions in the basic block
There are simple algorithms that produce a random permutation of the sequence of elements. However, they can't be used to permute instructions in the basic block.
This is because:
1. Domination rules must be satisfied. Thus, every definition must dominate (read 'precede') all of its users. There are exceptions to this rule but
we won't discuss them here.
2. Swapping some instructions might change the semantics of the program. This is usually the case with instructions that have side-effects: deal with memory, synchronization etc.

Thus, the transformation simply swaps two consecutive instructions instead of permuting a range of them. This approach simplifies the implementation
and makes the code more readable. Moreover, it is still able to produce a random permutation of all instructions since the transformation can be applied
many times to the same basic block.

#### [#3692](https://github.com/KhronosGroup/SPIRV-Tools/pull/3692): Propagate instructions from the basic block into its successors
This transformation is part of the idea of being able to permute instructions in the program.

This transformation has the same challenges as the previous one and more.
1. Domination rules must be satisfied. We must make sure that all dependencies of the original instruction (before the instruction is propagated) dominate all the
propagated copies of the original instruction. In the case of this transformation, `dominates` does not mean `syntactically precedes` if the original instruction is in a loop.
2. We can't always guarantee that the propagated copy of the original instruction dominates all users of the original instruction. Thus, we do not preserve the id of
the original instruction and instead decide if we can replace all usages of that id with propagated ids.
3. Some propagated instructions might have side-effects (e.g. write to memory etc). Thus, we might change the semantics of the program if we try to propagate them.

There seems to be no single simple approach to overcome all these challenges. Thus, the transformation contains a sequence of conditions that must be satisfied to make sure that the program's semantics remain the same. Some of those conditions are:
1. Check that all users of the original instruction are dominated by at least one propagated copy of that instruction.
2. We can't propagate an instruction into its block's successor if the latter has an `OpPhi` that uses the original instruction. This is because the original
instruction will be removed from its block.
3. Check that all dependencies of the original instruction dominate all propagated copies of that instruction.

All these conditions (and some more) must be satisfied to make sure that domination rules are satisfied as well.

#### [#3674](https://github.com/KhronosGroup/SPIRV-Tools/pull/3674): Outline selection constructs
The idea behind this transformation can be simply explained as follows.
Reference shader | Variant shader
--- | ---
`...` | `if (true) { ... }`

However, the implementation is somewhat more complicated in practice than that. Most of the complexity comes from (again) SPIR-V rules for structured control flow [6].

Thus, the transformation does not create any new basic blocks and instead reuses existing ones.
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
%1 = OpLabel
     OpBranch %2
<br />
%2 = OpLabel
     OpBranch %3
<br />
%3 = OpLabel
     ...
        </code>
      </pre>
    </td>
    <td>
      <pre>
        <code>
%1 = OpLabel
     <strong>OpSelectionMerge %3 None
     OpBranchConditional %true %2 %2</strong>
<br />
%2 = OpLabel
     OpBranch %3
<br />
%3 = OpLabel
     ...
        </code>
      </pre>
    </td>
  </tr>
</table>

Some of the conditions, required by this transformation, are:
1. An outlined region of blocks must be single-entry, single-exit (i.e. all blocks in the region are dominated by the entry block and postdominated by the exit block). This particular condition can be relaxed in the future, though.
2. The entry block may not be a header block of any construct.
3. The exit block may not be a merge block of any construct.

## References
1. T.Y. Chen, S.C. Cheung, and S.M. Yiu. 1998. Metamorphic testing: a new approach for generating next test cases. Technical Report HKUST-CS98-01. Hong Kong University of Science and Technology.
2. [Mesa open-source project](https://gitlab.freedesktop.org/mesa/mesa)
3. [spirv-opt](https://github.com/KhronosGroup/SPIRV-Tools#optimizer)
4. [SPIR-V specification](https://www.khronos.org/registry/spir-v/specs/unified1/SPIRV.html)
5. [spirv-val](https://github.com/KhronosGroup/SPIRV-Tools#validator)
6. [Rules of structured control flow](https://www.khronos.org/registry/spir-v/specs/unified1/SPIRV.html#_a_id_structuredcontrolflow_a_structured_control_flow)
7. Alastair F. Donaldson, Hugues Evrard, Andrei Lascu, and Paul Thomson. 2017. Automated testing of graphics shader compilers. Proc. ACM Program. Lang. 1, OOPSLA, Article 93 (October 2017), 29 pages. DOI:https://doi.org/10.1145/3133917
8. Alastair F. Donaldson and Andrei Lascu. 2016. Metamorphic testing for (graphics) compilers. In Proceedings of the 1st International Workshop on Metamorphic Testing (MET '16). Association for Computing Machinery, New York, NY, USA, 44–47. DOI:https://doi.org/10.1145/2896971.2896978

