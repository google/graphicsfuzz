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

import pathlib
from typing import List, Optional, Tuple

from gfauto import (
    built_in_binaries,
    gflogging,
    proto_util,
    recipe_download_and_extract_archive_set,
    util,
)
from gfauto.artifact_pb2 import ArtifactMetadata
from gfauto.common_pb2 import ArchiveSet
from gfauto.recipe_pb2 import Recipe
from gfauto.util import check

ARTIFACT_METADATA_FILE_NAME = "artifact.json"
ARTIFACT_RECIPE_FILE_NAME = "recipe.json"
ARTIFACT_RECIPE_LOG_FILE_NAME = "recipe.log"
ARTIFACT_ROOT_FILE_NAME = "ROOT"


class ArtifactWrap:
    def __init__(self, path: str, metadata: Optional[ArtifactMetadata] = None):
        self.path = path
        self.metadata = (
            metadata if metadata is not None else artifact_read_metadata(path)
        )


def recipes_write_built_in() -> None:
    for recipe_wrap in built_in_binaries.BUILT_IN_BINARY_RECIPES:
        if not artifact_get_metadata_file_path(recipe_wrap.path).exists():
            recipe_wrap.write()


def binary_artifacts_find(artifact_path_prefix: str) -> List[Tuple[ArchiveSet, str]]:
    artifact_paths = artifacts_find(artifact_path_prefix)
    result: List[Tuple[ArchiveSet, str]] = []
    for artifact_path in artifact_paths:
        archive_set = None
        if artifact_get_metadata_file_path(artifact_path).exists():
            metadata = artifact_read_metadata(artifact_path)
            if metadata.data.HasField("extracted_archive_set"):
                archive_set = metadata.data.extracted_archive_set.archive_set
        elif artifact_get_recipe_file_path(artifact_path).exists():
            recipe = artifact_read_recipe(artifact_path)
            if recipe.HasField("download_and_extract_archive_set"):
                archive_set = recipe.download_and_extract_archive_set.archive_set
        if archive_set:
            result.append((archive_set, artifact_path))

    return result


def artifacts_find(artifact_path_prefix: str) -> List[str]:
    path_prefix = artifact_get_directory_path(artifact_path_prefix)

    metadata_files = path_prefix.rglob("*.json")
    metadata_files = (
        file
        for file in metadata_files
        if file.name in (ARTIFACT_METADATA_FILE_NAME, ARTIFACT_RECIPE_FILE_NAME)
    )
    artifact_paths = {path_to_artifact_path(file.parent) for file in metadata_files}
    return sorted(artifact_paths)


def artifact_path_get_root() -> pathlib.Path:
    root_file_suffix = pathlib.Path(ARTIFACT_ROOT_FILE_NAME)
    fake_file = util.norm_path(pathlib.Path("fake").absolute())
    for parent in fake_file.parents:
        if (parent / root_file_suffix).exists():
            return parent
    raise FileNotFoundError(
        "Could not find root file {}".format(ARTIFACT_ROOT_FILE_NAME)
    )


def path_to_artifact_path(path: pathlib.Path) -> str:
    from_root = path.relative_to(artifact_path_get_root()).as_posix()
    return "//" + from_root


def artifact_path_absolute(artifact_path: str) -> str:
    """
    Returns the absolute |artifact_path| starting with '//'.

    Artifact paths should almost always begin with '//', but for convenience it can be useful to
    use relative paths, especially when calling functions from an IPython shell.
    :param artifact_path: An artifact path.
    :return: absolute |artifact_path| starting with '//'.
    """
    if artifact_path.startswith("//"):
        return artifact_path
    path = util.norm_path(pathlib.Path(artifact_path)).absolute()
    return path_to_artifact_path(path)


def artifact_path_to_path(artifact_path: str) -> pathlib.Path:
    """
    Returns |artifact_path| converted to an OS specific path.

    Artifact paths always use '/' to separate paths.
    Artifact paths usually begin with '//' which is the artifact root directory, marked via a ROOT
    file.

    :param artifact_path: an artifact path.
    :return:
    """
    if artifact_path.startswith("//"):
        return util.norm_path(
            artifact_path_get_root() / pathlib.Path(artifact_path[2:])
        )

    return util.norm_path(pathlib.Path(artifact_path))


def artifact_get_directory_path(artifact_path: str = "") -> pathlib.Path:
    return artifact_path_to_path(artifact_path)


def artifact_get_recipe_file_path(artifact_path: str = "") -> pathlib.Path:
    return artifact_get_inner_file_path(ARTIFACT_RECIPE_FILE_NAME, artifact_path)


def artifact_get_metadata_file_path(artifact_path: str = "") -> pathlib.Path:
    return artifact_get_inner_file_path(ARTIFACT_METADATA_FILE_NAME, artifact_path)


def artifact_get_recipe_log_file_path(artifact_path: str) -> pathlib.Path:
    return artifact_get_inner_file_path(ARTIFACT_RECIPE_LOG_FILE_NAME, artifact_path)


def artifact_write_recipe_and_execute(recipe: Recipe, artifact_path: str = "") -> str:
    artifact_path_full = artifact_path_absolute(artifact_path)

    artifact_write_recipe(recipe, artifact_path_full)
    artifact_execute_recipe(artifact_path_full)
    return artifact_path_full


def artifact_write_recipe(
    recipe: Optional[Recipe] = None, artifact_path: str = ""
) -> str:
    if recipe is None:
        recipe = Recipe()

    artifact_path = artifact_path_absolute(artifact_path)

    json_text = proto_util.message_to_json(recipe, including_default_value_fields=True)
    json_file_path = artifact_get_recipe_file_path(artifact_path)
    util.file_write_text(json_file_path, json_text)
    return artifact_path


def artifact_read_recipe(artifact_path: str = "") -> Recipe:
    recipe = Recipe()
    json_file_path = artifact_get_recipe_file_path(artifact_path)
    json_text = util.file_read_text(json_file_path)
    proto_util.json_to_message(json_text, recipe)
    return recipe


def artifact_write_metadata(
    artifact_metadata: ArtifactMetadata, artifact_path: str
) -> str:
    artifact_path = artifact_path_absolute(artifact_path)

    json_text = proto_util.message_to_json(
        artifact_metadata, including_default_value_fields=True
    )
    json_file_path = artifact_get_metadata_file_path(artifact_path)
    util.file_write_text(json_file_path, json_text)
    return artifact_path


def artifact_read_metadata(artifact_path: str = "") -> ArtifactMetadata:
    artifact_metadata = ArtifactMetadata()
    json_file_path = artifact_get_metadata_file_path(artifact_path)
    json_contents = util.file_read_text(json_file_path)
    proto_util.json_to_message(json_contents, artifact_metadata)
    return artifact_metadata


def artifact_execute_recipe_if_needed(artifact_path: str = "") -> None:
    artifact_execute_recipe(artifact_path, only_if_artifact_json_missing=True)


def artifact_execute_recipe(
    artifact_path: str = "", only_if_artifact_json_missing: bool = False
) -> None:

    artifact_path = artifact_path_absolute(artifact_path)

    if (
        only_if_artifact_json_missing
        and artifact_get_metadata_file_path(artifact_path).exists()
    ):
        return

    recipe = artifact_read_recipe(artifact_path)

    with util.file_open_text(
        artifact_get_recipe_log_file_path(artifact_path), "w"
    ) as f:
        gflogging.push_stream_for_logging(f)
        try:
            if recipe.HasField("download_and_extract_archive_set"):
                recipe_download_and_extract_archive_set.recipe_download_and_extract_archive_set(
                    recipe.download_and_extract_archive_set, artifact_path
                )
            else:
                raise NotImplementedError(
                    "Artifact {} has recipe type {} and this is not implemented".format(
                        artifact_path, recipe.WhichOneof("recipe")
                    )
                )
        finally:
            gflogging.pop_stream_for_logging()


def artifact_get_inner_file_path(inner_file: str, artifact_path: str) -> pathlib.Path:
    check(
        not inner_file.startswith("//"),
        AssertionError(
            "bad inner_file argument passed to artifact_get_inner_file_path"
        ),
    )
    # TODO: Consider absolute paths that we might want to support for quick hacks.
    return util.norm_path(
        artifact_get_directory_path(artifact_path) / pathlib.Path(inner_file)
    )
