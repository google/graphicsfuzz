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

from pathlib import Path

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


def test_catchsegv_backtrace_nvvm_function_name() -> None:
    def addr2line_mock(module: Path, address: str) -> str:
        assert str(module) == "/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100"
        assert address == "0x4eab81"

        return "_nv004nvvm\n??:?\n"

    # addr2line finds function names that contain "nvvn" in NVIDIA drivers, but these
    # are useless as signatures. Thus, the offset should be used instead.

    log = """
Backtrace:
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x4eab81)[0x7f5f3434eb81]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x690978)[0x7f5f344f4978]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x695cc4)[0x7f5f344f9cc4]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x685fb4)[0x7f5f344e9fb4]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x78648a)[0x7f5f345ea48a]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x4fd52f)[0x7f5f3436152f]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x3cf602)[0x7f5f34233602]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(_nv002nvvm+0xa22)[0x7f5f3422b622]
/lib/x86_64-linux-gnu/libnvidia-glcore.so.440.100(+0x127179e)[0x7f5f3606379e]
"""
    signature = signature_util.get_signature_from_log_contents(
        log, addr2line_mock=addr2line_mock
    )
    assert signature == "libnvidiaglvkspirvso4401000x4eab81"


def test_catchsegv_backtrace_module_no_sig() -> None:
    def addr2line_mock(module: Path, address: str) -> str:
        assert str(module) == "/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100"
        assert address == "0x35fdfd"

        return "??\n??:0\n"

    # In this case, addr2line returns question marks, so the offset of the
    # top stack frame should be used as the signature.

    log = """
Backtrace:
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x35fdfd)[0x7f4440caedfd]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x37ac93)[0x7f4440cc9c93]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x37bbd2)[0x7f4440ccabd2]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x37c1d6)[0x7f4440ccb1d6]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(+0x37c51f)[0x7f4440ccb51f]
/lib/x86_64-linux-gnu/libnvidia-glvkspirv.so.440.100(_nv008nvvm+0xa6)[0x7f4440d10006]
"""
    signature = signature_util.get_signature_from_log_contents(
        log, addr2line_mock=addr2line_mock
    )
    assert signature == "libnvidiaglvkspirvso4401000x35fdfd"


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


def test_mesa_error_exec_list_length() -> None:
    log = """
*** Aborted
Register dump:

 RAX: 0000000000000000   RBX: 00007f86645f9380   RCX: 00007f866463bdb1
 RDX: 0000000000000000   RSI: 00007ffdd626b0e0   RDI: 0000000000000002
 Trap: 00000000   Error: 00000000   OldMask: 00000000   CR2: 00000000

 XMM14: 00000000000000000000000025252525 XMM15: 00000000000000000000000025252525

Backtrace:
/lib/x86_64-linux-gnu/libc.so.6(gsignal+0x141)[0x7f866463bdb1]
/lib/x86_64-linux-gnu/libc.so.6(abort+0x123)[0x7f8664625537]
/data/Mesa/mesa-20.2/lib/x86_64-linux-gnu/libvulkan_intel.so(+0x5aa709)[0x7f86641fa709]
/data/Mesa/mesa-20.2/lib/x86_64-linux-gnu/libvulkan_intel.so(+0x101953)[0x7f8663d51953]
/data/Mesa/mesa-20.2/lib/x86_64-linux-gnu/libvulkan_intel.so(+0x10467e)[0x7f8663d5467e]
STDERR:
INTEL-MESA: warning: Performance support disabled, consider sysctl dev.i915.perf_stream_paranoid=0

NIR validation failed after nir_lower_returns
2 errors:
shader: MESA_SHADER_FRAGMENT
inputs: 0
outputs: 0
uniforms: 0
ubos: 1
error: exec_list_length(&instr->srcs) == state->block->predecessors->entries (../src/compiler/nir/nir_validate.c:766)

    vec1 32 ssa_139 = deref_var &return (function_temp bool)
    vec1 1 ssa_140 = intrinsic load_deref (ssa_139) (0) /* access=0 */
    /* succs: block_18 block_19 */
1 additional errors:
error: state->ssa_srcs->entries == 0 (../src/compiler/nir/nir_validate.c:1207)
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "exec_list_lengthinstrsrcs_stateblockpredecessorsen"


def test_mesa_error_glsl_type_is_struct_or_ifc() -> None:
    log = """
*** Segmentation fault
Register dump:

 RAX: 0000000000000000   RBX: 0000000000000000   RCX: 00007fff214183c0
 RDX: 000000000000001d   RSI: 0000000001d66ce0   RDI: 0000000001e68b20
 Trap: 0000000e   Error: 00000004   OldMask: 00000000   CR2: 00000038

 XMM12: 00000000000000000000000000000000 XMM13: 00000000000000000000000000000000
 XMM14: 00000000000000000000000000000000 XMM15: 00000000000000000000000000000000

Backtrace:
/data/Mesa/mesa-20.2/lib/x86_64-linux-gnu/libvulkan_intel.so(+0x101316)[0x7f09c0dcc316]
/data/Mesa/mesa-20.2/lib/x86_64-linux-gnu/libvulkan_intel.so(+0x10467e)[0x7f09c0dcf67e]
/data/Mesa/mesa-20.2/lib/x86_64-linux-gnu/libvulkan_intel.so(+0x1073fe)[0x7f09c0dd23fe]
/home/runner/work/gfbuild-amber/gfbuild-amber/amber/b_Debug/../samples/amber.cc:605 (discriminator 2)(main)[0xc4c07f]
/lib/x86_64-linux-gnu/libc.so.6(__libc_start_main+0xea)[0x7f09c16a1cca]
/data/binaries/built_in/gfbuild-amber-760076b7c8bf43d6c6cc4928f5129afca16e895e-Linux_x64_Debug/amber/bin/amber(_start+0x29)[0xc49609]

Memory map:

00400000-01704000 r-xp 00000000 fe:01 3802475 /data/binaries/built_in/gfbuild-amber-760076b7c8bf43d6c6cc4928f5129afca16e895e-Linux_x64_Debug/amber/bin/amber
01904000-01953000 r--p 01304000 fe:01 3802475 /data/binaries/built_in/gfbuild-amber-760076b7c8bf43d6c6cc4928f5129afca16e895e-Linux_x64_Debug/amber/bin/amber
01953000-01956000 rw-p 01353000 fe:01 3802475 /data/binaries/built_in/gfbuild-amber-760076b7c8bf43d6c6cc4928f5129afca16e895e-Linux_x64_Debug/amber/bin/amber
7fff21403000-7fff21425000 rw-p 00000000 00:00 0 [stack]
7fff215a3000-7fff215a7000 r--p 00000000 00:00 0 [vvar]
7fff215a7000-7fff215a9000 r-xp 00000000 00:00 0 [vdso]


STDERR:
INTEL-MESA: warning: Performance support disabled, consider sysctl dev.i915.perf_stream_paranoid=0

SPIR-V parsing FAILED:
    In file ../src/compiler/spirv/spirv_to_nir.c:2394
    glsl_type_is_struct_or_ifc(type)
    8636 bytes into the SPIR-V binary

"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "glsl_type_is_struct_or_ifctype"


def test_mali_error() -> None:
    log = """
--------- beginning of kernel
11-23 12:48:29.723  2507  2507 E mali 1e1000.mali: Unhandled Page fault
11-23 12:48:29.723     0     0 W         : [    C0] mali 1e1000.mali: error detected from slot 0, job status 0x00000004 (TERMINATED)
11-23 12:48:29.724   321   321 E mali 1e1000.mali: t1xx: GPU fault 0x04 from job slot 0
11-23 12:48:29.724     0     0 W         : [    C0] mali 1e1000.mali: error detected from slot 0, job status 0x00000042 (JOB_READ_FAULT)
11-23 12:48:29.724   321   321 E mali 1e1000.mali: t1xx: GPU fault 0x42 from job slot 0
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "mali_t1xx_GPU_fault_0x42_from_job_slot_0"


def test_android_hex_backtrace() -> None:
    log = """
05-23 16:56:20.744  5884  5884 F DEBUG   : backtrace:
05-23 16:56:20.745  5884  5884 F DEBUG   :   NOTE: Function names and BuildId information is missing for some frames due
05-23 16:56:20.745  5884  5884 F DEBUG   :   NOTE: to unreadable libraries. For unwinds of apps, only shared libraries
05-23 16:56:20.745  5884  5884 F DEBUG   :   NOTE: found under the lib/ directory are readable.
05-23 16:56:20.745  5884  5884 F DEBUG   :   NOTE: On this device, run setenforce 0 to make the libraries readable.
05-23 16:56:20.745  5884  5884 F DEBUG   :       #00 pc 00000000018213fc  /vendor/lib64/egl/libGLES_mali.so (BuildId: d4fb5800abef5fc4b86c0032cae223d5)
05-23 16:56:20.745  5884  5884 F DEBUG   :       #01 pc 0000000001821584  /vendor/lib64/egl/libGLES_mali.so (BuildId: d4fb5800abef5fc4b86c0032cae223d5)
05-23 16:56:20.745  5884  5884 F DEBUG   :       #02 pc 000000000182456c  /vendor/lib64/egl/libGLES_mali.so (BuildId: d4fb5800abef5fc4b86c0032cae223d5)
05-23 16:56:20.745  5884  5884 F DEBUG   :       #03 pc 0000000001821b2c  /vendor/lib64/egl/libGLES_mali.so (BuildId: d4fb5800abef5fc4b86c0032cae223d5)
05-23 16:56:20.745  5884  5884 F DEBUG   :       #04 pc 0000000000a08cac  /vendor/lib64/egl/libGLES_mali.so (BuildId: d4fb5800abef5fc4b86c0032cae223d5)
05-23 16:56:20.745  5884  5884 F DEBUG   :       #05 pc 00000000016d6ce0  /vendor/lib64/egl/libGLES_mali.so (BuildId: d4fb5800abef5fc4b86c0032cae223d5)
05-23 16:56:20.746  5884  5884 F DEBUG   :       #06 pc 00000000007de018  /vendor/lib64/egl/libGLES_mali.so (BuildId: d4fb5800abef5fc4b86c0032cae223d5)
05-23 16:56:20.746  5884  5884 F DEBUG   :       #07 pc 00000000007dcbc0  /vendor/lib64/egl/libGLES_mali.so (BuildId: d4fb5800abef5fc4b86c0032cae223d5)
05-23 16:56:20.746  5884  5884 F DEBUG   :       #08 pc 00000000000fdb4c  /data/local/tmp/amber_ndk
05-23 16:56:20.746  5884  5884 F DEBUG   :       #09 pc 00000000000d0ca0  /data/local/tmp/amber_ndk
05-23 16:56:20.746  5884  5884 F DEBUG   :       #10 pc 00000000000d1b5c  /data/local/tmp/amber_ndk
05-23 16:56:20.746  5884  5884 F DEBUG   :       #11 pc 00000000000cd8bc  /data/local/tmp/amber_ndk
05-23 16:56:20.746  5884  5884 F DEBUG   :       #12 pc 00000000000aedf0  /data/local/tmp/amber_ndk
05-23 16:56:20.746  5884  5884 F DEBUG   :       #13 pc 00000000000aeb7c  /data/local/tmp/amber_ndk
05-23 16:56:20.746  5884  5884 F DEBUG   :       #14 pc 0000000000083554  /data/local/tmp/amber_ndk
05-23 16:56:20.746  5884  5884 F DEBUG   :       #15 pc 0000000000083464  /data/local/tmp/amber_ndk
05-23 16:56:20.746  5884  5884 F DEBUG   :       #16 pc 00000000000765a8  /data/local/tmp/amber_ndk
"""
    signature = signature_util.get_signature_from_log_contents(log)
    assert signature == "00000000018213fc_egllibGLES_maliso"
