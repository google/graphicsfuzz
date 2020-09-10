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

import os
import shutil
from pathlib import Path
from typing import Callable, List, Optional

from gfauto import binaries_util, fuzz, settings_util, util
from gfauto.common_pb2 import Binary
from gfauto.device_pb2 import (
    Device,
    DeviceList,
    DeviceShaderCompiler,
    DeviceSwiftShader,
)
from gfauto.gflogging import log
from gfauto.settings_pb2 import Settings

PREPROCESSOR_CACHE_HIT_STRING = "Preprocessor cache: hit"


def check_common_summary_files(summary_dir: Path) -> None:
    reduced_dir = summary_dir / "reduced_1"
    assert reduced_dir.is_dir()
    stage_one_reduced_result = summary_dir / "reduced_1_result" / "STATUS"
    assert stage_one_reduced_result.is_file()


def test_fuzz_and_reduce_llpc_bug_no_opt() -> None:
    def check_result() -> None:
        # no_signature because we use a Release build.
        bucket = Path() / "reports" / "crashes" / "no_signature"
        assert bucket.is_dir()
        test_dirs = list(bucket.iterdir())
        assert len(test_dirs) == 1
        assert "no_opt" in test_dirs[0].name
        log_path = test_dirs[0] / "results" / "amdllpc" / "result" / "log.txt"
        assert (
            util.file_read_text(log_path).count(PREPROCESSOR_CACHE_HIT_STRING) == 1
        ), f"{log_path}"
        summary_dir = test_dirs[0] / "summary"
        check_common_summary_files(summary_dir)

    fuzz_and_reduce_bug(
        active_device="amdllpc",
        seed=113850571166867052450985985234950340261823086165340926462238356578814719718087,
        check_result=check_result,
    )


def test_fuzz_and_reduce_llpc_bug_opt() -> None:
    def check_result() -> None:
        # no_signature because we use a Release build.
        bucket = Path() / "reports" / "crashes" / "no_signature"
        assert bucket.is_dir()
        test_dirs = list(bucket.iterdir())
        assert len(test_dirs) == 1
        assert "opt_O" in test_dirs[0].name
        log_path = test_dirs[0] / "results" / "amdllpc" / "result" / "log.txt"
        assert (
            util.file_read_text(log_path).count(PREPROCESSOR_CACHE_HIT_STRING) == 2
        ), f"{log_path}"
        summary_dir = test_dirs[0] / "summary"
        check_common_summary_files(summary_dir)

    fuzz_and_reduce_bug(
        active_device="amdllpc",
        seed=99929706048223329039220273236588800138087812135322921806045517555377829572971,
        check_result=check_result,
    )


def test_fuzz_and_reduce_swift_shader_bug_no_opt() -> None:
    def check_result() -> None:
        bucket = Path() / "reports" / "crashes" / "UNIMPLEMENTED_extensionFeaturessType"
        assert bucket.is_dir()
        test_dirs = list(bucket.iterdir())
        assert len(test_dirs) == 1
        assert "no_opt" in test_dirs[0].name
        log_path = test_dirs[0] / "results" / "swift_shader" / "result" / "log.txt"
        assert (
            util.file_read_text(log_path).count(PREPROCESSOR_CACHE_HIT_STRING) == 1
        ), f"{log_path}"
        summary_dir = test_dirs[0] / "summary"
        check_common_summary_files(summary_dir)

    fuzz_and_reduce_bug(
        active_device="swift_shader",
        seed=35570251875691207436044799625964330634240739187615728715101271237388241843323,
        check_result=check_result,
    )


def test_fuzz_and_reduce_swift_shader_bug_no_opt_regex() -> None:
    def check_result() -> None:
        bucket = Path() / "reports" / "crashes" / "UNIMPLEMENTED_extensionFeaturessType"
        assert bucket.is_dir()
        test_dirs = list(bucket.iterdir())
        assert len(test_dirs) == 1
        assert "no_opt" in test_dirs[0].name
        summary_dir = test_dirs[0] / "summary"
        log_path = test_dirs[0] / "results" / "swift_shader" / "result" / "log.txt"
        assert (
            util.file_read_text(log_path).count(PREPROCESSOR_CACHE_HIT_STRING) == 1
        ), f"{log_path}"
        summary_dir = test_dirs[0] / "summary"
        check_common_summary_files(summary_dir)

    settings = Settings()
    settings.CopyFrom(settings_util.DEFAULT_SETTINGS)
    settings.only_reduce_signature_regex = "UNIMPLEMENTED_extensionFeaturessType"

    fuzz_and_reduce_bug(
        active_device="swift_shader",
        seed=35570251875691207436044799625964330634240739187615728715101271237388241843323,
        check_result=check_result,
        settings=settings,
    )


def test_fuzz_and_reduce_swift_shader_bug_no_opt_regex_miss() -> None:
    def check_result() -> None:
        bucket = Path() / "reports" / "crashes" / "UNIMPLEMENTED_extensionFeaturessType"
        assert bucket.is_dir()
        test_dirs = list(bucket.iterdir())
        assert len(test_dirs) == 1
        assert "no_opt" in test_dirs[0].name
        log_path = test_dirs[0] / "results" / "swift_shader" / "result" / "log.txt"
        assert (
            util.file_read_text(log_path).count(PREPROCESSOR_CACHE_HIT_STRING) == 1
        ), f"{log_path}"
        summary_dir = test_dirs[0] / "summary"
        reduced_dir = summary_dir / "reduced_1"
        assert not reduced_dir.is_dir()  # No reduction because of regex below.

    settings = Settings()
    settings.CopyFrom(settings_util.DEFAULT_SETTINGS)
    settings.only_reduce_signature_regex = "extension"  # Does not match.

    fuzz_and_reduce_bug(
        active_device="swift_shader",
        seed=35570251875691207436044799625964330634240739187615728715101271237388241843323,
        check_result=check_result,
        settings=settings,
    )


def test_fuzz_and_reduce_swift_shader_bug_ignored_signature() -> None:
    def check_result() -> None:
        bucket = Path() / "reports" / "crashes" / "vkGetInstanceProcAddr"
        assert not bucket.is_dir()  # No report because signature is ignored below.

    fuzz_and_reduce_bug(
        active_device="swift_shader",
        seed=35570251875691207436044799625964330634240739187615728715101271237388241843323,
        check_result=check_result,
        ignored_signatures=["vkGetInstanceProcAddr"],
    )


def fuzz_and_reduce_bug(
    active_device: str,
    seed: int,
    check_result: Callable[[], None],
    settings: Optional[Settings] = None,
    ignored_signatures: Optional[List[str]] = None,
) -> None:
    """
    Fuzz, find a bug, reduce it.

    Linux only.
    """
    # Test only works on Linux.
    if util.get_platform() != "Linux":
        return

    here = util.norm_path(Path(__file__).absolute()).parent
    temp_dir: Path = here.parent / "temp"

    assert temp_dir.is_dir()

    os.chdir(temp_dir)

    # Create ROOT file in temp/ if needed.
    fuzz.try_get_root_file()

    work_dir = temp_dir / fuzz.get_random_name()[:8]
    util.mkdir_p_new(work_dir)
    os.chdir(work_dir)

    log(f"Changed to {str(work_dir)}")

    if settings is None:
        settings = Settings()
        settings.CopyFrom(settings_util.DEFAULT_SETTINGS)

    settings.device_list.CopyFrom(
        DeviceList(
            active_device_names=[active_device],
            devices=[
                Device(
                    name="amdllpc",
                    shader_compiler=DeviceShaderCompiler(
                        binary="amdllpc",
                        args=["-gfxip=9.0.0", "-verify-ir", "-auto-layout-desc"],
                    ),
                    binaries=[
                        Binary(
                            name="amdllpc",
                            tags=["Release"],
                            version="c21d76dceaf26361f9b6b3838a955ec3301506b5",
                        ),
                    ],
                ),
                Device(
                    name="swift_shader",
                    swift_shader=DeviceSwiftShader(),
                    binaries=[
                        Binary(
                            name="swift_shader_icd",
                            tags=["Release"],
                            version="6d69aae0e1ab49190ea46cd1c999fd3d02e016b9",
                        ),
                    ],
                    ignored_crash_signatures=ignored_signatures,
                ),
            ],
        )
    )

    spirv_tools_version = "983b5b4fccea17cab053de24d51403efb4829158"

    settings.latest_binary_versions.extend(
        [
            Binary(
                name="glslangValidator",
                tags=["Release"],
                version="1afa2b8cc57b92c6b769eb44a6854510b6921a0b",
            ),
            Binary(name="spirv-opt", tags=["Release"], version=spirv_tools_version),
            Binary(name="spirv-dis", tags=["Release"], version=spirv_tools_version),
            Binary(name="spirv-as", tags=["Release"], version=spirv_tools_version),
            Binary(name="spirv-val", tags=["Release"], version=spirv_tools_version),
            Binary(name="spirv-fuzz", tags=["Release"], version=spirv_tools_version),
            Binary(name="spirv-reduce", tags=["Release"], version=spirv_tools_version),
            Binary(
                name="graphicsfuzz-tool",
                tags=[],
                version="7b143bcb3ad38b64ddc17d132886636b229b6684",
            ),
        ]
    )
    # Add default binaries; the ones above have priority.
    settings.latest_binary_versions.extend(binaries_util.DEFAULT_BINARIES)

    settings.extra_graphics_fuzz_generate_args.append("--small")
    settings.extra_graphics_fuzz_generate_args.append("--single-pass")

    settings.extra_graphics_fuzz_reduce_args.append("--max-steps")
    settings.extra_graphics_fuzz_reduce_args.append("2")

    # We use an old version of GraphicsFuzz.
    settings.legacy_graphics_fuzz_vulkan_arg = True

    settings_util.write(settings, settings_util.DEFAULT_SETTINGS_FILE_PATH)

    # Add shaders.
    binary_manager = binaries_util.get_default_binary_manager(settings)
    graphicsfuzz_tool = binary_manager.get_binary_path_by_name("graphicsfuzz-tool")
    sample_shaders_path: Path = graphicsfuzz_tool.path.parent.parent.parent / "shaders" / "samples" / "310es"
    util.copy_dir(sample_shaders_path, Path() / fuzz.REFERENCES_DIR)
    util.copy_dir(sample_shaders_path, Path() / fuzz.DONORS_DIR)

    fuzz.main_helper(
        settings_path=settings_util.DEFAULT_SETTINGS_FILE_PATH,
        iteration_seed_override=seed,
        override_sigint=False,
        use_amber_vulkan_loader=True,
    )

    check_result()

    os.chdir(here)
    shutil.rmtree(work_dir)
