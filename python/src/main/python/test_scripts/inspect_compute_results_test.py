#!/usr/bin/env python3

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

import os
import pathlib2
import pytest
import sys
from typing import List, Optional

HERE = os.path.abspath(__file__)

sys.path.insert(0, os.path.dirname(os.path.dirname(HERE)) + os.sep + "drivers")
import inspect_compute_results


def test_unknown_command_rejected(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['unknown', '1.json', '2.json'])
    assert 'ValueError: Unknown command' in str(value_error)


def test_show_rejects_multiple_args(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['show', '1.json', '2.json'])
    assert 'ValueError: Command "show" requires exactly 1 input; 2 provided' in str(value_error)


def test_exactdiff_rejects_one_arg(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['exactdiff', '1.json'])
    assert 'ValueError: Command "exactdiff" requires exactly 2 inputs; 1 provided' in str(value_error)


def test_exactdiff_rejects_three_args(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['exactdiff', '1.json', '2.json', '3.json'])
    assert 'ValueError: Command "exactdiff" requires exactly 2 inputs; 3 provided' in str(value_error)


def test_fuzzydiff_rejects_one_arg(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['fuzzydiff', '1.json'])
    assert 'ValueError: Command "fuzzydiff" requires exactly 2 inputs; 1 provided' in str(value_error)


def test_fuzzydiff_rejects_three_args(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['fuzzydiff', '1.json', '2.json', '3.json'])
    assert 'ValueError: Command "fuzzydiff" requires exactly 2 inputs; 3 provided' in str(value_error)


def test_show_handles_file_not_found(tmp_path: pathlib2.Path):
    with pytest.raises(FileNotFoundError) as file_not_found_error:
        inspect_compute_results.main_helper(['show', 'nofile.json'])
    assert 'FileNotFoundError: Input file "nofile.json" not found' in str(file_not_found_error)


def test_exactdiff_handles_first_file_not_found(tmp_path: pathlib2.Path):
    onefile = tmp_path / 'something.json'
    onefile.touch(exist_ok=False)
    with pytest.raises(FileNotFoundError) as file_not_found_error:
        inspect_compute_results.main_helper(['exactdiff', 'nofile.json', str(onefile)])
    assert 'FileNotFoundError: Input file "nofile.json" not found' in str(file_not_found_error)


def test_exactdiff_handles_second_file_not_found(tmp_path: pathlib2.Path):
    onefile = tmp_path / 'something.json'
    onefile.touch(exist_ok=False)
    with pytest.raises(FileNotFoundError) as file_not_found_error:
        inspect_compute_results.main_helper(['exactdiff', str(onefile), 'nofile.json'])
    assert 'FileNotFoundError: Input file "nofile.json" not found' in str(file_not_found_error)


def test_fuzzydiff_handles_first_file_not_found(tmp_path: pathlib2.Path):
    onefile = tmp_path / 'something.json'
    onefile.touch(exist_ok=False)
    with pytest.raises(FileNotFoundError) as file_not_found_error:
        inspect_compute_results.main_helper(['fuzzydiff', 'nofile.json', str(onefile)])
    assert 'FileNotFoundError: Input file "nofile.json" not found' in str(file_not_found_error)


def test_fuzzydiff_handles_second_file_not_found(tmp_path: pathlib2.Path):
    onefile = tmp_path / 'something.json'
    onefile.touch(exist_ok=False)
    with pytest.raises(FileNotFoundError) as file_not_found_error:
        inspect_compute_results.main_helper(['fuzzydiff', str(onefile), 'nofile.json'])
    assert 'FileNotFoundError: Input file "nofile.json" not found' in str(file_not_found_error)


def check_diff(tmp_path: pathlib2.Path, output1: str, output2: str, is_exact: bool,
               extra_args: Optional[List[str]]=None) -> int:
    results1_path = tmp_path / '1.info.json'
    results2_path = tmp_path / '2.info.json'
    with results1_path.open(mode='w') as results1_file:
        results1_file.write(output1)
    with results2_path.open(mode='w') as results2_file:
        results2_file.write(output2)
    args = ['exactdiff' if is_exact else 'fuzzydiff',
             str(results1_path),
             str(results2_path)]
    if extra_args:
        args += extra_args
    return inspect_compute_results.main_helper(args)


def check_exact_diff(tmp_path: pathlib2.Path, output1: str, output2: str) -> int:
    return check_diff(tmp_path, output1, output2, is_exact=True)


def check_fuzzy_diff(tmp_path: pathlib2.Path, output1: str, output2: str,
                     extra_args: Optional[List[str]]=None) -> int:
    return check_diff(tmp_path, output1, output2, is_exact=False, extra_args=extra_args)


def test_exactdiff_pass1(tmp_path: pathlib2.Path):
    assert 0 == check_exact_diff(tmp_path, (
        '{"status": "SUCCESS", "log": "#### Start compute shader", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,18,28,22,24,24,28,26]]}}'), (
        '{"status": "IGNORED_DURING_DIFF", "log": "#### Different stuff", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,18,28,22,24,24,28,26]]}}'))


def test_exactdiff_pass2(tmp_path: pathlib2.Path):
    assert 0 == check_exact_diff(tmp_path, (
        '{"status": "SUCCESS", "log": "#### Start compute shader", "outputs": '
        '{"ssbo":[[2.0]]}}'), (
        '{"status": "IGNORED_DURING_DIFF", "log": "#### Different stuff", "outputs": '
        '{"ssbo": [ [2.0] ] } }'))


def test_exactdiff_pass3(tmp_path: pathlib2.Path):
    assert 0 == check_exact_diff(tmp_path, (
        '{"status": "SUCCESS", "log": "#### Start compute shader", "outputs": '
        '{"ssbo":[[88.0, 12.3],[28,12,14],[1]]}}'), (
        '{"status": "IGNORED_DURING_DIFF", "log": "#### Different stuff", "outputs": '
        '{"ssbo":[[88.0, 12.3],[28,12,14],[1]]}}'))


def test_exactdiff_fail_first_invalid(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        check_exact_diff(tmp_path, (
            'not_json'), (
            '{"status": "IGNORED_DURING_DIFF", "log": "#### Different stuff", "outputs": '
            '{"ssbo": [ [2.0] ] } }'))
    assert 'ValueError: First input file did not contain valid SSBO data' in str(value_error)


def test_exactdiff_fail_second_invalid(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        check_exact_diff(tmp_path, (
            '{"status": "IGNORED_DURING_DIFF", "log": "#### Different stuff", "outputs": '
            '{"ssbo": [ [2.0] ] } }'), (
            'not_json'))
    assert 'ValueError: Second input file did not contain valid SSBO data' in str(value_error)


def test_exactdiff_fail_mismatched_number_of_fields(tmp_path: pathlib2.Path):
    assert 0 != check_exact_diff(tmp_path, (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,18,28,22,24,24,28,26]]}}'), (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88]]}}'))


def test_exactdiff_fail_mismatched_field_length(tmp_path: pathlib2.Path):
    assert 0 != check_exact_diff(tmp_path, (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,18,28,22,24,24,28,26]]}}'), (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,18,28,22,24,24,28]]}}'))


def test_exactdiff_fail_mismatched_field_element(tmp_path: pathlib2.Path):
    assert 0 != check_exact_diff(tmp_path, (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,18,28,22,24,24,28]]}}'), (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,17,28,22,24,24,28]]}}'))


def test_fuzzydiff_pass1(tmp_path: pathlib2.Path):
    float1 = 88.0
    float2 = 1e+6
    float3 = 1.3e-6
    float4 = 0.0

    float1ish = float1 + 0.00000001
    float2ish = float2 + 0.0001
    float3ish = float3 + 1.3e-15
    float4ish = float4 + 1e-20
    assert float1 != float1ish
    assert float2 != float2ish
    assert float3 != float3ish
    assert float4 != float4ish
    output1 = ('{"status": "SUCCESS", "log": "...", "outputs": {"ssbo":[[' + str(float1) + '],['
               + str(float2) + ',' + str(float3) + ',' + str(float4) + ']]}}')
    output2 = ('{"status": "SUCCESS", "log": "...", "outputs": {"ssbo":[[' + str(float1ish) + '],['
               + str(float2ish) + ',' + str(float3ish) + ',' + str(float4ish) + ']]}}')
    assert 0 != check_exact_diff(tmp_path, output1, output2)
    assert 0 == check_fuzzy_diff(tmp_path, output1, output2)


def test_fuzzydiff_pass2(tmp_path: pathlib2.Path):
    float1 = 88.0
    float2 = 1e+6
    float3 = 1.3e-6
    float4 = 0.0

    float1ish = float1 + 0.00009
    float2ish = float2 + 0.00009
    float3ish = float3 + 0.00009
    float4ish = float4 + 0.00009
    assert float1 != float1ish
    assert float2 != float2ish
    assert float3 != float3ish
    assert float4 != float4ish
    output1 = ('{"status": "SUCCESS", "log": "...", "outputs": {"ssbo":[[' + str(float1) + '],['
               + str(float2) + ',' + str(float3) + ',' + str(float4) + ']]}}')
    output2 = ('{"status": "SUCCESS", "log": "...", "outputs": {"ssbo":[[' + str(float1ish) + '],['
               + str(float2ish) + ',' + str(float3ish) + ',' + str(float4ish) + ']]}}')
    assert 0 != check_exact_diff(tmp_path, output1, output2)
    assert 0 == check_fuzzy_diff(tmp_path, output1, output2, extra_args=['--abs_tol=0.0001'])


def test_fuzzydiff_pass3(tmp_path: pathlib2.Path):
    float1 = 88.0
    float2 = 1e+6
    float3 = 1.3e-6
    float4 = 0.0

    float1ish = float1 + 0.0000001
    float2ish = float2 + 1.0
    float3ish = float3 + 1e-12
    float4ish = float4 + 1e-6
    assert float1 != float1ish
    assert float2 != float2ish
    assert float3 != float3ish
    assert float4 != float4ish
    output1 = ('{"status": "SUCCESS", "log": "...", "outputs": {"ssbo":[[' + str(float1) + '],['
               + str(float2) + ',' + str(float3) + ',' + str(float4) + ']]}}')
    output2 = ('{"status": "SUCCESS", "log": "...", "outputs": {"ssbo":[[' + str(float1ish) + '],['
               + str(float2ish) + ',' + str(float3ish) + ',' + str(float4ish) + ']]}}')
    assert 0 != check_exact_diff(tmp_path, output1, output2)
    assert 0 == check_fuzzy_diff(tmp_path, output1, output2,
                                 extra_args=['--rel_tol=1e-06', '--abs_tol=1e-06'])


def test_fuzzydiff_fail_first_invalid(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        check_exact_diff(tmp_path, (
            'not_json'), (
            '{"status": "IGNORED_DURING_DIFF", "log": "#### Different stuff", "outputs": '
            '{"ssbo": [ [2.0] ] } }'))
    assert 'ValueError: First input file did not contain valid SSBO data' in str(value_error)


def test_fuzzydiff_fail_second_invalid(tmp_path: pathlib2.Path):
    with pytest.raises(ValueError) as value_error:
        check_exact_diff(tmp_path, (
            '{"status": "IGNORED_DURING_DIFF", "log": "#### Different stuff", "outputs": '
            '{"ssbo": [ [2.0] ] } }'), (
            'not_json'))
    assert 'ValueError: Second input file did not contain valid SSBO data' in str(value_error)


def test_fuzzydiff_fail_mismatched_number_of_fields(tmp_path: pathlib2.Path):
    assert 0 != check_exact_diff(tmp_path, (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,18,28,22,24,24,28,26]]}}'), (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88]]}}'))


def test_fuzzydiff_fail_mismatched_field_length(tmp_path: pathlib2.Path):
    assert 0 != check_exact_diff(tmp_path, (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,18,28,22,24,24,28,26]]}}'), (
        '{"status": "SUCCESS", "log": "...", "outputs": '
        '{"ssbo":[[88],[28,12,14,14,18,16,18,18,28,22,24,24,28]]}}'))


def test_fuzzydiff_fail_mismatched_field_element(tmp_path: pathlib2.Path):
    float1 = 88.0
    float2 = 1e+6
    float3 = 1.3e-6
    float4 = 0.0

    float1ish = float1 + 0.0000001
    float2ish = float2 + 1.0
    float3ish = float3 + 1e-12
    float4ish = float4 + 1e-4  ## Too big a difference
    assert float1 != float1ish
    assert float2 != float2ish
    assert float3 != float3ish
    assert float4 != float4ish
    output1 = ('{"status": "SUCCESS", "log": "...", "outputs": {"ssbo":[[' + str(float1) + '],['
               + str(float2) + ',' + str(float3) + ',' + str(float4) + ']]}}')
    output2 = ('{"status": "SUCCESS", "log": "...", "outputs": {"ssbo":[[' + str(float1ish) + '],['
               + str(float2ish) + ',' + str(float3ish) + ',' + str(float4ish) + ']]}}')
    assert 0 != check_exact_diff(tmp_path, output1, output2)
    assert 0 != check_fuzzy_diff(tmp_path, output1, output2,
                                 extra_args=['--rel_tol=1e-06', '--abs_tol=1e-06'])


def test_bad_rel_tol():
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['fuzzydiff', '1.json', '2.json', '--rel_tol=notafloat'])
    assert 'ValueError: Positive floating-point value required for --rel_tol argument'\
           in str(value_error)


def test_bad_rel_tol2():
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['fuzzydiff', '1.json', '2.json', '--rel_tol=0.0'])
    assert 'ValueError: Positive floating-point value required for --rel_tol argument'\
           in str(value_error)


def test_bad_rel_tol3():
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['fuzzydiff', '1.json', '2.json', '--rel_tol=-0.1'])
    assert 'ValueError: Positive floating-point value required for --rel_tol argument'\
           in str(value_error)


def test_bad_abs_tol():
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['fuzzydiff', '1.json', '2.json', '--abs_tol=notafloat'])
    assert 'ValueError: Non-negative floating-point value required for --abs_tol argument'\
           in str(value_error)


def test_bad_abs_tol2():
    with pytest.raises(ValueError) as value_error:
        inspect_compute_results.main_helper(['fuzzydiff', '1.json', '2.json', '--abs_tol=-0.1'])
    assert 'ValueError: Non-negative floating-point value required for --abs_tol argument'\
           in str(value_error)
