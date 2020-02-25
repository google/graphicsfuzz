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

"""Runs a binary from the given binary name and settings file."""

import argparse
import subprocess
import sys
from pathlib import Path
from typing import List

from gfauto import binaries_util, settings_util
from gfauto.gflogging import log


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Runs a binary given the binary name and settings.json file. "
        "Use -- to separate args to run_bin and your binary. "
    )

    parser.add_argument(
        "--settings",
        help="Path to the settings JSON file for this instance.",
        default=str(settings_util.DEFAULT_SETTINGS_FILE_PATH),
    )

    parser.add_argument(
        "binary_name",
        help="The name of the binary to run. E.g. spirv-opt, glslangValidator",
        type=str,
    )

    parser.add_argument(
        "arguments",
        metavar="arguments",
        type=str,
        nargs="*",
        help="The arguments to pass to the binary",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    # Args.
    settings_path: Path = Path(parsed_args.settings)
    binary_name: str = parsed_args.binary_name
    arguments: List[str] = parsed_args.arguments

    try:
        settings = settings_util.read_or_create(settings_path)
    except settings_util.NoSettingsFile:
        log(f"Settings file {str(settings_path)} was created for you; using this.")
        settings = settings_util.read_or_create(settings_path)

    binary_manager = binaries_util.get_default_binary_manager(settings=settings)

    cmd = [str(binary_manager.get_binary_path_by_name(binary_name).path)]
    cmd.extend(arguments)
    return subprocess.run(cmd, check=False).returncode


if __name__ == "__main__":
    sys.exit(main())
