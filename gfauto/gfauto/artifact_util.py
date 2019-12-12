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

"""Artifacts utility module.

See artifact.proto for information about artifacts.
"""

import pathlib
import time
from typing import Dict, List, Optional, Tuple

from gfauto import gflogging, proto_util, recipe_download_and_extract_archive_set, util
from gfauto.artifact_pb2 import ArtifactMetadata
from gfauto.common_pb2 import ArchiveSet
from gfauto.gflogging import log
from gfauto.recipe_pb2 import Recipe
from gfauto.util import check

ARTIFACT_METADATA_FILE_NAME = "artifact.json"
ARTIFACT_RECIPE_FILE_NAME = "recipe.json"
ARTIFACT_RECIPE_LOG_FILE_NAME = "recipe.log"
ARTIFACT_ROOT_FILE_NAME = "ROOT"
ARTIFACT_EXECUTING_LOCK_FILE_NAME = "EXECUTING_LOCK"

BUSY_WAIT_IN_SECONDS = 1

RecipeMap = Dict[str, Recipe]


class ArtifactWrap:
    def __init__(self, path: str, metadata: Optional[ArtifactMetadata] = None):
        self.path = path
        self.metadata = (
            metadata if metadata is not None else artifact_read_metadata(path)
        )


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
    util.file_write_text_atomic(json_file_path, json_text)
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
    util.file_write_text_atomic(json_file_path, json_text)
    return artifact_path


def artifact_read_metadata(artifact_path: str = "") -> ArtifactMetadata:
    artifact_metadata = ArtifactMetadata()
    json_file_path = artifact_get_metadata_file_path(artifact_path)
    json_contents = util.file_read_text(json_file_path)
    proto_util.json_to_message(json_contents, artifact_metadata)
    return artifact_metadata


def artifact_execute_recipe_if_needed(
    artifact_path: str = "", built_in_recipes: Optional[RecipeMap] = None
) -> None:
    artifact_execute_recipe(
        artifact_path,
        only_if_artifact_json_missing=True,
        built_in_recipes=built_in_recipes,
    )


def artifact_execute_recipe(  # pylint: disable=too-many-branches;
    artifact_path: str = "",
    only_if_artifact_json_missing: bool = False,
    built_in_recipes: Optional[RecipeMap] = None,
) -> None:
    artifact_path = artifact_path_absolute(artifact_path)
    executing_lock_file_path = artifact_get_inner_file_path(
        ARTIFACT_EXECUTING_LOCK_FILE_NAME, artifact_path
    )

    busy_waiting = False
    first_wait = True

    # We may have to retry if another process appears to be executing this recipe.
    while True:
        if busy_waiting:
            time.sleep(BUSY_WAIT_IN_SECONDS)
            if first_wait:
                log(
                    f"Waiting for {artifact_path} due to lock file {executing_lock_file_path}"
                )
                first_wait = False

        if executing_lock_file_path.exists():
            # Retry.
            busy_waiting = True
            continue

        # Several processes can still execute here concurrently; the above check is just an optimization.

        # The metadata file should be written atomically once the artifact is ready for use, so if it exists, we can
        # just return.
        if (
            only_if_artifact_json_missing
            and artifact_get_metadata_file_path(artifact_path).exists()
        ):
            return

        # The recipe file should be written atomically. If it exists, we are fine to continue. If not and if we have
        # the recipe in |built_in_recipes|, more than one process might write it, but the final rename from TEMP_FILE ->
        # RECIPE.json is atomic, so *some* process will succeed and the contents will be valid. Thus, we should be fine
        # to continue once we have written the recipe.
        if (
            not artifact_get_recipe_file_path(artifact_path).exists()
            and built_in_recipes
        ):
            built_in_recipe = built_in_recipes[artifact_path]
            if not built_in_recipe:
                raise FileNotFoundError(
                    str(artifact_get_recipe_file_path(artifact_path))
                )
            # This is atomic; should not fail.
            artifact_write_recipe(built_in_recipe, artifact_path)

        recipe = artifact_read_recipe(artifact_path)

        # Create EXECUTING_LOCK file. The "x" means exclusive creation. This will fail if the file already exists;
        # i.e. another process won the race and is executing the recipe; if so, we retry from the beginning of this
        # function (and will return early). Otherwise, we can continue. We don't need to keep the file open; the file
        # is not opened with exclusive access, just created exclusively.
        try:
            with util.file_open_text(executing_lock_file_path, "x") as lock_file:
                lock_file.write("locked")
        except FileExistsError:
            # Retry.
            busy_waiting = True
            continue

        # If we fail here (e.g. KeyboardInterrupt), we won't remove the lock file. But any alternative will either have
        # the same problem (interrupts can happen at almost any time) or could end up accidentally removing the lock
        # file made by another process, so this is the safest approach. Users can manually delete lock files if needed;
        # the log output indicates the file on which we are blocked.

        try:
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
        finally:
            # Delete the lock file when we have finished. Ignore errors.
            try:
                executing_lock_file_path.unlink()
            except OSError:
                log(f"WARNING: failed to delete: {str(executing_lock_file_path)}")


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
