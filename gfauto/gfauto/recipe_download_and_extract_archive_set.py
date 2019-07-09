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
import stat
import urllib.request
from zipfile import ZipFile, ZipInfo

from gfauto import artifacts, util
from gfauto.artifact_pb2 import ArtifactMetadata
from gfauto.common_pb2 import Archive
from gfauto.gflogging import log
from gfauto.recipe_pb2 import RecipeDownloadAndExtractArchiveSet

ALL_EXECUTABLE_PERMISSION_BITS = stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH


def recipe_download_and_extract_archive_set(
    recipe: RecipeDownloadAndExtractArchiveSet, output_artifact_path: str
) -> None:

    for archive in recipe.archive_set.archives:  # type: Archive
        util.check_field_truthy(archive.url, "url")
        util.check_field_truthy(archive.output_file, "output_file")
        util.check_field_truthy(archive.output_directory, "output_directory")

        output_file_path = artifacts.artifact_get_inner_file_path(
            archive.output_file, output_artifact_path
        )

        output_directory_path = artifacts.artifact_get_inner_file_path(
            archive.output_directory, output_artifact_path
        )

        log(f"Downloading {archive.url} to {str(output_file_path)}")
        urllib.request.urlretrieve(archive.url, str(output_file_path))

        if output_file_path.name.lower().endswith(".zip"):
            with ZipFile(str(output_file_path), "r") as zip_file:
                for info in zip_file.infolist():  # type: ZipInfo
                    extracted_file = zip_file.extract(info, str(output_directory_path))
                    # If the file was created on a UNIX-y system:
                    if info.create_system == 3:
                        # Shift away first 2 bytes to get permission bits.
                        zip_file_exec_bits = info.external_attr >> 16
                        # Just consider the executable bits.
                        zip_file_exec_bits = (
                            zip_file_exec_bits & ALL_EXECUTABLE_PERMISSION_BITS
                        )
                        current_attribute_bits = os.stat(extracted_file).st_mode
                        if (
                            current_attribute_bits | zip_file_exec_bits
                        ) != current_attribute_bits:
                            os.chmod(
                                extracted_file,
                                current_attribute_bits | zip_file_exec_bits,
                            )
        else:
            shutil.unpack_archive(str(output_file_path), str(output_directory_path))

    output_metadata = ArtifactMetadata()
    output_metadata.data.extracted_archive_set.archive_set.CopyFrom(recipe.archive_set)

    artifacts.artifact_write_metadata(output_metadata, output_artifact_path)
