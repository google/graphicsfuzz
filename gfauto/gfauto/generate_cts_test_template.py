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

import sys
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
        source_dir=bug_dir / "reduced_manual",
        output_amber=bug_dir / "name-of-test-TODO.amber",
        work_dir=bug_dir / "work",
        # One sentence, 58 characters max., no period, no line breaks.
        short_description="A fragment shader with TODO",
        comment_text="""The test passes because TODO""",
        copyright_year="2019",
        extra_commands=tool.AMBER_COMMAND_EXPECT_RED,
    )


if __name__ == "__main__":
    main()
    sys.exit(0)
