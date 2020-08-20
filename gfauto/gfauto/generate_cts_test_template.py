#!/usr/bin/env python3
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

"""Generate a CTS test.

This module/script is copied next to a specific test in your repository of bugs
to generate an Amber script test suitable for adding to the CTS.
In particular, the Amber script test is suitable for use with |add_amber_tests_to_cts.py|.
"""

from pathlib import Path

from gfauto import tool, util


def main() -> None:

    # Checklist:
    # - check output_amber
    # - check short_description
    # - check comment_text
    # - check copyright_year
    # - check extra_commands

    bug_dir = util.norm_path(Path(__file__).absolute()).parent

    tool.glsl_shader_job_crash_to_amber_script_for_google_cts(
        source_dir=bug_dir / "reduced_1",
        # Look at the "derived_from" field in `reduced_1/test.json` to find the original shader name.
        # If the shader was stable (e.g. "stable_quicksort") then you must prefix the .amber file name
        # with the name of the shader (using dashes instead of underscores).
        # The rest of the test name should describe the DIFFERENCE between the reference and variant shaders.
        # E.g. "stable-quicksort-composite-insert-with-constant.amber".
        # If the original shader was not stable then you should probably not add this test (with a few exceptions).
        # If you still want to add the test then the test name should approximately describe the contents of the variant
        # shader. E.g. "loop-with-early-return-and-function-call.amber".
        output_amber=bug_dir / "test-name-TODO.amber",
        work_dir=bug_dir / "work",
        # One sentence, 58 characters max., no period, no line breaks.
        # Describe the difference between the shaders, as described above for the .amber file name.
        # E.g. Two shaders with diff: composite insert with constant
        short_description="Two shaders with diff: TODO",
        comment_text="""
The test renders two images using semantically equivalent shaders, and then
checks that the images are similar.
The test passes because the shaders have the same semantics and so the images
should be the same.""",
        copyright_year="2020",
        # Pass |tool.AMBER_COMMAND_EXPECT_RED| to check that the shader renders red.
        extra_commands="",
        is_coverage_gap=False,
    )


if __name__ == "__main__":
    main()
