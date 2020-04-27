# -*- coding: utf-8 -*-

# Copyright 2020 The GraphicsFuzz Project Authors
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

from collections import Counter

from gfauto import cov_merge, cov_util


def test_cov_merge() -> None:

    output_line_counts: cov_util.LineCounts = {
        "a": Counter({1: 500, 2: 300}),
        "b": Counter({1: 400, 2: 100}),
    }

    input_line_counts: cov_util.LineCounts = {
        "a": Counter({1: 100, 3: 200}),
        "c": Counter({1: 50}),
    }

    cov_merge.add_line_counts_unsafe(input_line_counts, output_line_counts)

    assert input_line_counts == {
        "a": Counter({1: 100, 3: 200}),
        "c": Counter({1: 50}),
    }, "input should be unchanged"

    assert output_line_counts == {
        "a": Counter({1: 600, 2: 300, 3: 200}),  # Merged.
        "b": Counter({1: 400, 2: 100}),  # Unchanged from output.
        "c": Counter({1: 50}),  # Unchanged from input.
    }, "output line counts should have the input merged"
