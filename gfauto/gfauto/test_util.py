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

from gfauto import proto_util, util
from gfauto.test_pb2 import Test

TEST_METADATA = "test.json"
REFERENCE_DIR = "reference"
VARIANT_DIR = "variant"
SHADER_JOB = "shader.json"
SHADER_JOB_RESULT = "shader.info.json"


def get_source_dir(test_dir: Path) -> Path:
    return test_dir / "source"


def get_metadata_path(test_dir: Path) -> Path:
    return get_source_dir(test_dir) / TEST_METADATA


def metadata_write(metadata: Test, test_dir: Path) -> Path:
    metadata_write_to_path(metadata, get_metadata_path(test_dir))
    return test_dir


def metadata_read(test_dir: Path) -> Test:
    return metadata_read_from_path(get_metadata_path(test_dir))


def metadata_read_from_path(test_metadata_path: Path) -> Test:
    text = util.file_read_text(test_metadata_path)
    result = Test()
    proto_util.json_to_message(text, result)
    return result


def metadata_write_to_path(metadata: Test, test_metadata_path: Path) -> Path:
    text = proto_util.message_to_json(metadata)
    util.file_write_text(test_metadata_path, text)
    return test_metadata_path


def get_shader_job_path(test_dir: Path, is_variant: bool = True) -> Path:
    return (
        test_dir
        / "source"
        / (VARIANT_DIR if is_variant else REFERENCE_DIR)
        / SHADER_JOB
    )


def get_device_directory(test_dir: Path, device_name: str) -> Path:
    return test_dir / "results" / device_name


def get_results_directory(
    test_dir: Path, device_name: str, is_variant: bool = True
) -> Path:
    return get_device_directory(test_dir, device_name) / (
        VARIANT_DIR if is_variant else REFERENCE_DIR
    )


def get_reduced_test_dir(test_dir: Path, device_name: str, reduction_name: str) -> Path:
    return get_device_directory(test_dir, device_name) / "reductions" / reduction_name


def get_reduction_work_directory(
    reduced_test_dir: Path, is_variant: bool = True
) -> Path:
    return (
        reduced_test_dir
        / "reduction_work"
        / (VARIANT_DIR if is_variant else REFERENCE_DIR)
    )
