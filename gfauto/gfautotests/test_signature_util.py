# -*- coding: utf-8 -*-

# Copyright 2019 The GraphicsFuzz Project Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from gfauto import signature_util

# Disable spell-checking for this file.
# flake8: noqa: SC100


def test_glslang_assertion() -> None:
    log = """
glslangValidator: ../glslang/MachineIndependent/ParseHelper.cpp:2212: void glslang::TParseContext::nonOpBuiltInCheck(const glslang::TSourceLoc&, const glslang::TFunction&, glslang::TIntermAggregate&): Assertion `PureOperatorBuiltins == false' failed.
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "void_glslangTParseContextnonOpBuiltInCheckconst_gl"


def test_glslang_error_1() -> None:
    log = """
ERROR: temp/.../variant/shader.frag:549: 'variable indexing fragment shader output array' : not supported with this profile: es
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "variable_indexing_fragment_shader_output_array_not"


def test_glslang_error_2() -> None:
    log = """
ERROR: reports/.../part_1_preserve_semantics/reduction_work/variant/shader_reduced_0173/0_glsl/shader_reduced_0173.frag:456: '=' :  cannot convert from ' const 3-component vector of bool' to ' temp bool'
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "cannot_convert_from_const_component_vector_of_bool"


def test_crash_offset_in_apk_lib() -> None:
    log = """
11-09 13:36:16.577 18975 18975 F DEBUG   : backtrace:
11-09 13:36:16.578 18975 18975 F DEBUG   :       #00 pc 00000000001a1234  /data/app/a.b.c-AAaa11==/base.apk!a-b.so (offset 0x123000) (!!!0000!1234abcd1234abcd!123abc!+1234) (BuildId: 1234abcde123abcd)
11-09 13:36:16.578 18975 18975 F DEBUG   :       #01 pc 00000000004cb460  /data/app/a.b.c-AAaa11==/base.apk!a-b.so (offset 0x111000) (!!!0000!aa11abc1111!11aaa!+122) (BuildId: 1234abcde123abcd)
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "abso_0x123000_00001234abcd1234abcd123abc1234"


def test_catchsegv_backtrace_with_symbols() -> None:
    log = """

Backtrace:
/data/binaries/built_in/gfbuild-SPIRV-Tools-18b3b94567a9251a6f8491a6d07c4422abadd22c-Linux_x64_Debug/SPIRV-Tools/bin/spirv-opt(_ZNK8spvtools5utils17IntrusiveNodeBaseINS_3opt11InstructionEE8NextNodeEv+0x10)[0x9fa588]
/data/binaries/built_in/gfbuild-SPIRV-Tools-18b3b94567a9251a6f8491a6d07c4422abadd22c-Linux_x64_Debug/SPIRV-Tools/bin/spirv-opt(_ZNK8spvtools5utils13IntrusiveListINS_3opt11InstructionEE5emptyEv+0x1c)[0x9fa4ca]
/data/binaries/built_in/gfbuild-SPIRV-Tools-18b3b94567a9251a6f8491a6d07c4422abadd22c-Linux_x64_Debug/SPIRV-Tools/bin/spirv-opt(_ZN8spvtools3opt10BasicBlock4tailEv+0x2f)[0xa5d8ab]
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "spirvopt_ZNKspvtoolsutilsIntrusiveNodeBaseINS_optI"


def test_catchsegv_backtrace_module_not_found() -> None:
    log = """
Backtrace:
/made/up/path/install/lib64/libvulkan_intel.so(+0x76c9a4)[0x7fc67c0369a4]
/made/up/path/install/lib64/libvulkan_intel.so(+0x8ac1ef)[0x7fc67c1761ef]
    """
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "libvulkan_intelso0x76c9a4"


def test_catchsegv_backtrace_skip_libc() -> None:
    log = """
Backtrace:
/lib64/libc.so.6(abort+0x12b)[0x7f529082d8d9]
/made/up/path/install/lib64/libvulkan_intel.so(+0x8ac1ef)[0x7fc67c1761ef]
    """
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "libvulkan_intelso0x8ac1ef"


def test_catchsegv_backtrace_with_cpp_file_and_line() -> None:
    log = """

Backtrace:
/home/runner/work/gfbuild-llpc/gfbuild-llpc/vulkandriver/drivers/llvm-project/llvm/lib/CodeGen/LiveInterval.cpp:758(_ZN4llvm9LiveRange20MergeValueNumberIntoEPNS_6VNInfoES2_)[0x1202f80]
/home/runner/work/gfbuild-llpc/gfbuild-llpc/vulkandriver/drivers/llvm-project/llvm/lib/CodeGen/RegisterCoalescer.cpp:1861 (discriminator 3)(_ZN12_GLOBAL__N_117RegisterCoalescer8joinCopyEPN4llvm12MachineInstrERb)[0x13946b3]
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "LiveIntervalcpp_ZNllvmLiveRangeMergeValueNumberInt"


def test_llvm_fatal_error_1() -> None:
    log = """
STDOUT:
ERROR: LLVM FATAL ERROR: Broken function found, compilation aborted!


STDERR:
PHINode should have one entry for each predecessor of its parent basic block!
  %__llpc_output_proxy_.0.0 = phi float [ %.6, %163 ]
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "PHINode_should_have_one_entry_for_each_predecessor"


def test_llvm_fatal_error_2() -> None:
    log = """
RETURNCODE: 1
STDOUT:
ERROR: LLVM FATAL ERROR:Broken function found, compilation aborted!


STDERR:
Instruction does not dominate all uses!
  %97 = insertelement <3 x float> %96, float %.0.2, i32 2
  %92 = extractelement <3 x float> %97, i32 2
Instruction does not dominate all uses!
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "Instruction_does_not_dominate_all_uses"


def test_llvm_machine_code_error() -> None:
    log = """
RETURNCODE: 1
STDOUT:
ERROR: LLVM FATAL ERROR:Found 1 machine code errors.


STDERR:

# Machine code for function _amdgpu_ps_main: NoPHIs, TracksLiveness
Frame Objects:
  fi#0: size=256, align=16, at location [SP]
Function Live Ins: $sgpr2 in %37, $vgpr2 in %39, $vgpr3 in %40

bb.0..entry:
  successors: %bb.1(0x40000000), %bb.3(0x40000000); %bb.1(50.00%), %bb.3(50.00%)

# End machine code for function _amdgpu_ps_main.

*** Bad machine code: Using an undefined physical register ***
- function:    _amdgpu_ps_main
- basic block: %bb.17  (0x6f52380)
- instruction: $vcc = S_AND_B64 $exec, $vcc, implicit-def dead $scc
- operand 2:   $vcc
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "Using_an_undefined_physical_register"


def test_llpc_assertion_failure() -> None:
    log = """
amdllpc: /home/runner/work/gfbuild-llpc/gfbuild-llpc/vulkandriver/drivers/llvm-project/llvm/include/llvm/Support/Alignment.h:77: llvm::Align::Align(uint64_t): Assertion `Value > 0 && "Value must not be 0"' failed.
PLEASE submit a bug report to https://bugs.llvm.org/ and include the crash backtrace.
Stack dump:
0.	Program arguments: /data/binaries/built_in/gfbuild-llpc-c9c9a410565ece5c5a8735ddef7c8b24a8446db6-Linux_x64_Debug/llpc/bin/amdllpc -gfxip=9.0.0 -verify-ir -auto-layout-desc llvmAlignAlignuint_t_Assertion/9aa1fdd9_no_opt_test_amdllpc/results/amdllpc/result/variant/1_spirv/shader.frag.spv
1.	Running pass 'CallGraph Pass Manager' on module 'lgcPipeline'.
2.	Running pass 'AMDGPU DAG->DAG Pattern Instruction Selection' on function '@_amdgpu_ps_main'
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "llvmAlignAlignuint_t_Assertion"


def test_asan_error() -> None:
    log = """
=================================================================
==2244339==ERROR: AddressSanitizer: use-after-poison on address 0x62500192def0 at pc 0x000006da0c70 bp 0x7ffd691028c0 sp 0x7ffd691028b8
READ of size 8 at 0x62500192def0 thread T0
    #0 0x6da0c6f in getParent /vulkandriver/drivers/llvm-project/llvm/include/llvm/CodeGen/MachineInstr.h:281:43
    #1 0x6da0c6f in llvm::LiveVariables::VarInfo::findKill(llvm::MachineBasicBlock const*) const /vulkandriver/drivers/llvm-project/llvm/lib/CodeGen/LiveVariables.cpp:62:19

0x62500192def0 is located 3568 bytes inside of 8192-byte region [0x62500192d100,0x62500192f100)
allocated by thread T0 here:
    #0 0x7f01fb89331d in operator new(unsigned long) /build/llvm-toolchain-9-uSl4bC/llvm-toolchain-9-9/projects/compiler-rt/lib/asan/asan_new_delete.cc:99:3
    #1 0x54dc395 in Allocate /vulkandriver/drivers/llvm-project/llvm/include/llvm/Support/AllocatorBase.h:85:12

SUMMARY: AddressSanitizer: use-after-poison /vulkandriver/drivers/llvm-project/llvm/include/llvm/CodeGen/MachineInstr.h:281:43 in getParent
Shadow bytes around the buggy address:
  0x0c4a8031db80: f7 00 00 00 00 00 00 00 00 f7 00 00 00 00 00 00
  0x0c4a8031db90: 00 00 f7 00 00 00 00 00 00 00 00 f7 00 00 00 00
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "useafterpoison_getParent"
