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
