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
