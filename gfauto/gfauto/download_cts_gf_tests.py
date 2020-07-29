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

"""Downloads the latest GraphicsFuzz AmberScript tests from vk-gl-cts.

Downloads the latest tests, including those that are pending.
"""

import argparse
import sys
from pathlib import Path

from gfauto import (
    amber_converter,
    binaries_util,
    fuzz,
    gerrit_util,
    settings_util,
    subprocess_util,
    util,
)


def download_cts_graphicsfuzz_tests(  # pylint: disable=too-many-locals;
    git_tool: Path, cookie: str, output_tests_dir: Path,
) -> Path:
    work_dir = Path() / "temp" / ("cts_" + fuzz.get_random_name())

    latest_change = gerrit_util.get_latest_deqp_change(cookie)
    latest_change_number = latest_change["_number"]
    latest_change_details = gerrit_util.get_gerrit_change_details(
        change_number=latest_change_number, cookie=cookie
    )
    current_revision = latest_change_details["current_revision"]
    cts_archive_path = gerrit_util.download_gerrit_revision(
        output_path=work_dir / "cts.tgz",
        change_number=latest_change_number,
        revision=current_revision,
        download_type=gerrit_util.DownloadType.Archive,
        cookie=cookie,
    )

    cts_dir_name = "cts_temp"
    cts_out = util.extract_archive(cts_archive_path, work_dir / cts_dir_name)

    pending_graphicsfuzz_changes = gerrit_util.get_deqp_graphicsfuzz_pending_changes(
        cookie
    )

    for pending_change in pending_graphicsfuzz_changes:
        change_number = pending_change["_number"]
        change_details = gerrit_util.get_gerrit_change_details(
            change_number=change_number, cookie=cookie
        )
        current_revision = change_details["current_revision"]
        patch_zip = gerrit_util.download_gerrit_revision(
            output_path=work_dir / f"{change_number}.zip",
            change_number=change_number,
            revision=current_revision,
            download_type=gerrit_util.DownloadType.Patch,
            cookie=cookie,
        )
        util.extract_archive(patch_zip, work_dir)

    # Create a dummy git repo in the work directory, otherwise "git apply" can fail silently.
    # --unsafe-paths is possibly supposed to address this, but it doesn't seem to work if we
    # are already in a git repo.
    subprocess_util.run(
        [str(git_tool), "init", "."], verbose=True, working_dir=work_dir
    )

    cmd = [str(git_tool), "apply"]

    patch_names = [p.name for p in work_dir.glob("*.diff")]

    cmd += patch_names

    # Use unix-style path for git.
    cmd += [
        "--verbose",
        "--unsafe-paths",
        f"--directory={cts_dir_name}",
        f"--exclude={cts_dir_name}/external/vulkancts/data/vulkan/amber/graphicsfuzz/index.txt",
        f"--include={cts_dir_name}/external/vulkancts/data/vulkan/amber/graphicsfuzz/*",
    ]

    subprocess_util.run(cmd, verbose=True, working_dir=work_dir)

    util.copy_dir(
        cts_out
        / "external"
        / "vulkancts"
        / "data"
        / "vulkan"
        / "amber"
        / "graphicsfuzz",
        output_tests_dir,
    )

    # Sometimes dEQP contributors add non-GraphicsFuzz AmberScript files to the graphicsfuzz directory.
    # We remove these.
    bad_test_names = ["texel_offset.amber"]

    for bad_test_name in bad_test_names:
        bad_test = output_tests_dir / bad_test_name
        if bad_test.is_file():
            bad_test.unlink()

    return output_tests_dir


GERRIT_COOKIE_INSTRUCTIONS = (
    "Log in to the Khronos Gerrit page in your "
    "browser and paste the following into the JavaScript console (F12) to copy the cookie to your clipboard: "
    "copy( document.cookie.match( /GerritAccount=([^;]*)/ )[1])"
)


def extract_shaders(tests_dir: Path, binaries: binaries_util.BinaryManager) -> None:
    for amber_file in sorted(tests_dir.glob("*.amber")):
        amber_converter.extract_shaders(
            amber_file, output_dir=amber_file.parent, binaries=binaries
        )

        zip_files = [
            util.ZipEntry(f, Path(f.name))
            for f in sorted(tests_dir.glob(f"{amber_file.stem}.*"))
        ]

        util.create_zip(amber_file.with_suffix(".zip"), zip_files)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Downloads the latest GraphicsFuzz AmberScript tests from vk-gl-cts, "
        "including those in pending CLs. "
        "Requires Git. Requires Khronos membership."
    )

    parser.add_argument(
        "gerrit_cookie",
        help="The Gerrit cookie used for authentication. Requires Khronos membership. Obtain this as follows. "
        + GERRIT_COOKIE_INSTRUCTIONS,
    )

    parser.add_argument(
        "--settings",
        help="Path to the settings JSON file for this instance.",
        default=str(settings_util.DEFAULT_SETTINGS_FILE_PATH),
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    cookie: str = parsed_args.gerrit_cookie
    settings_path: Path = Path(parsed_args.settings)

    # Need git.
    git_tool = util.tool_on_path("git")

    settings = settings_util.read_or_create(settings_path)

    binaries = binaries_util.get_default_binary_manager(settings=settings)

    tests_dir = Path() / "graphicsfuzz"
    download_cts_graphicsfuzz_tests(git_tool, cookie, tests_dir)
    extract_shaders(tests_dir, binaries)


if __name__ == "__main__":
    main()
