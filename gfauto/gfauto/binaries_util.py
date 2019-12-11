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

"""Binary utilities module.

Defines the latest binaries (name and version) that will be used by default for new fuzzing sessions.
Defines the recipes (see recipe.proto) for all built-in binaries, including old versions of binaries.
Defines BinaryManager; see below.
"""

import abc
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import attr
import requests

from gfauto import artifact_util, recipe_wrap, test_util, util
from gfauto.common_pb2 import Archive, ArchiveSet, Binary
from gfauto.gflogging import log
from gfauto.recipe_pb2 import Recipe, RecipeDownloadAndExtractArchiveSet
from gfauto.settings_pb2 import Settings
from gfauto.util import check

BINARY_RECIPES_PREFIX = "//binaries"

LATEST_GRAPHICSFUZZ_ARTIFACT = f"{BINARY_RECIPES_PREFIX}/graphicsfuzz_v1.2.1"

GLSLANG_VALIDATOR_NAME = "glslangValidator"
SPIRV_OPT_NAME = "spirv-opt"
SPIRV_VAL_NAME = "spirv-val"
SPIRV_DIS_NAME = "spirv-dis"
SWIFT_SHADER_NAME = "swift_shader_icd"
AMBER_NAME = "amber"

SPIRV_OPT_NO_VALIDATE_AFTER_ALL_TAG = "no-validate-after-all"

BUILT_IN_BINARY_RECIPES_PATH_PREFIX = f"{BINARY_RECIPES_PREFIX}/built_in"

CUSTOM_BINARY_RECIPES_PATH_PREFIX = f"{BINARY_RECIPES_PREFIX}/custom"

PLATFORMS = ["Linux", "Mac", "Windows"]

PLATFORMS_SET = set(PLATFORMS)

CONFIGS = ["Release", "Debug"]

CONFIGS_SET = set(CONFIGS)

PLATFORM_SUFFIXES_DEBUG = ["Linux_x64_Debug", "Windows_x64_Debug", "Mac_x64_Debug"]
PLATFORM_SUFFIXES_RELEASE = [
    "Linux_x64_Release",
    "Windows_x64_Release",
    "Mac_x64_Release",
]
PLATFORM_SUFFIXES_RELWITHDEBINFO = [
    "Linux_x64_RelWithDebInfo",
    "Windows_x64_RelWithDebInfo",
    "Mac_x64_RelWithDebInfo",
]

DEFAULT_SPIRV_TOOLS_VERSION = "983b5b4fccea17cab053de24d51403efb4829158"

DEFAULT_BINARIES = [
    Binary(
        name="glslangValidator",
        tags=["Debug"],
        version="1afa2b8cc57b92c6b769eb44a6854510b6921a0b",
    ),
    Binary(name="spirv-opt", tags=["Debug"], version=DEFAULT_SPIRV_TOOLS_VERSION),
    Binary(name="spirv-dis", tags=["Debug"], version=DEFAULT_SPIRV_TOOLS_VERSION),
    Binary(name="spirv-as", tags=["Debug"], version=DEFAULT_SPIRV_TOOLS_VERSION),
    Binary(name="spirv-val", tags=["Debug"], version=DEFAULT_SPIRV_TOOLS_VERSION),
    Binary(name="spirv-fuzz", tags=["Debug"], version=DEFAULT_SPIRV_TOOLS_VERSION),
    Binary(name="spirv-reduce", tags=["Debug"], version=DEFAULT_SPIRV_TOOLS_VERSION),
    Binary(
        name="swift_shader_icd",
        tags=["Debug"],
        version="cf79a622ec5c993fa48f8557c28e23b8407d1efd",
    ),
    Binary(
        name="amber", tags=["Debug"], version="f231728f60cb3b0f21d7423aed24fd3b317f38c9"
    ),
    Binary(
        name="amber_apk",
        tags=["Debug"],
        version="f231728f60cb3b0f21d7423aed24fd3b317f38c9",
    ),
    Binary(
        name="amber_apk_test",
        tags=["Debug"],
        version="f231728f60cb3b0f21d7423aed24fd3b317f38c9",
    ),
    Binary(
        name="graphicsfuzz-tool",
        tags=[],
        version="7b143bcb3ad38b64ddc17d132886636b229b6684",
    ),
    Binary(
        name="amdllpc",
        tags=["Debug"],
        version="c21d76dceaf26361f9b6b3838a955ec3301506b5",
    ),
]


@attr.dataclass
class BinaryPathAndInfo:
    path: Path
    binary: Binary


class BinaryGetter(abc.ABC):
    def get_binary_path_by_name(self, name: str) -> BinaryPathAndInfo:
        pass


class BinaryNotFound(Exception):
    pass


class BinaryPathNotFound(Exception):
    def __init__(self, binary: Binary):
        super().__init__(f"Could not find binary path for binary: \n{binary}")


@attr.dataclass
class ToolNameAndPath:
    name: str
    subpath: str
    add_exe_on_windows: bool = True


def get_platform_from_platform_suffix(platform_suffix: str) -> str:
    platforms = ("Linux", "Mac", "Windows")
    for platform in platforms:
        if platform in platform_suffix:
            return platform
    raise AssertionError(f"Could not guess platform of {platform_suffix}")


def add_common_tags_from_platform_suffix(tags: List[str], platform_suffix: str) -> None:
    platform = get_platform_from_platform_suffix(platform_suffix)
    tags.append(platform)
    common_tags = ["Release", "Debug", "RelWithDebInfo", "x64"]
    for common_tag in common_tags:
        if common_tag in platform_suffix:
            tags.append(common_tag)


def _get_built_in_binary_recipe_from_build_github_repo(
    project_name: str,
    version_hash: str,
    build_version_hash: str,
    platform_suffixes: List[str],
    tools: List[ToolNameAndPath],
) -> List[recipe_wrap.RecipeWrap]:

    result: List[recipe_wrap.RecipeWrap] = []

    for platform_suffix in platform_suffixes:
        tags: List[str] = []
        add_common_tags_from_platform_suffix(tags, platform_suffix)
        binaries = [
            Binary(
                name=binary.name,
                tags=tags,
                path=(
                    f"{project_name}/{(binary.subpath + '.exe') if 'Windows' in tags and binary.add_exe_on_windows else binary.subpath}"
                ),
                version=version_hash,
            )
            for binary in tools
        ]

        result.append(
            recipe_wrap.RecipeWrap(
                f"{BUILT_IN_BINARY_RECIPES_PATH_PREFIX}/{project_name}_{version_hash}_{platform_suffix}",
                Recipe(
                    download_and_extract_archive_set=RecipeDownloadAndExtractArchiveSet(
                        archive_set=ArchiveSet(
                            archives=[
                                Archive(
                                    url=f"https://github.com/paulthomson/build-{project_name}/releases/download/github/paulthomson/build-{project_name}/{build_version_hash}/build-{project_name}-{build_version_hash}-{platform_suffix}.zip",
                                    output_file=f"{project_name}.zip",
                                    output_directory=project_name,
                                )
                            ],
                            binaries=binaries,
                        )
                    )
                ),
            )
        )

    return result


def _get_built_in_swift_shader_version(
    version_hash: str, build_version_hash: str
) -> List[recipe_wrap.RecipeWrap]:
    return _get_built_in_binary_recipe_from_build_github_repo(
        project_name="swiftshader",
        version_hash=version_hash,
        build_version_hash=build_version_hash,
        platform_suffixes=PLATFORM_SUFFIXES_RELEASE
        + PLATFORM_SUFFIXES_DEBUG
        + PLATFORM_SUFFIXES_RELWITHDEBINFO,
        tools=[
            ToolNameAndPath(
                name="swift_shader_icd",
                subpath="lib/vk_swiftshader_icd.json",
                add_exe_on_windows=False,
            )
        ],
    )


def _get_built_in_spirv_tools_version(
    version_hash: str, build_version_hash: str, includes_spirv_fuzz: bool = True
) -> List[recipe_wrap.RecipeWrap]:
    return _get_built_in_binary_recipe_from_build_github_repo(
        project_name="SPIRV-Tools",
        version_hash=version_hash,
        build_version_hash=build_version_hash,
        platform_suffixes=PLATFORM_SUFFIXES_RELEASE + PLATFORM_SUFFIXES_DEBUG,
        tools=[
            ToolNameAndPath(name="spirv-as", subpath="bin/spirv-as"),
            ToolNameAndPath(name="spirv-dis", subpath="bin/spirv-dis"),
            ToolNameAndPath(name="spirv-opt", subpath="bin/spirv-opt"),
            ToolNameAndPath(name="spirv-val", subpath="bin/spirv-val"),
        ]
        + (
            [ToolNameAndPath(name="spirv-fuzz", subpath="bin/spirv-fuzz")]
            if includes_spirv_fuzz
            else []
        ),
    )


def _get_built_in_glslang_version(
    version_hash: str, build_version_hash: str
) -> List[recipe_wrap.RecipeWrap]:
    return _get_built_in_binary_recipe_from_build_github_repo(
        project_name="glslang",
        version_hash=version_hash,
        build_version_hash=build_version_hash,
        platform_suffixes=PLATFORM_SUFFIXES_RELEASE + PLATFORM_SUFFIXES_DEBUG,
        tools=[
            ToolNameAndPath(name="glslangValidator", subpath="bin/glslangValidator")
        ],
    )


def get_graphics_fuzz_121() -> List[recipe_wrap.RecipeWrap]:
    return [
        recipe_wrap.RecipeWrap(
            f"{BUILT_IN_BINARY_RECIPES_PATH_PREFIX}/graphicsfuzz_v1.2.1",
            Recipe(
                download_and_extract_archive_set=RecipeDownloadAndExtractArchiveSet(
                    archive_set=ArchiveSet(
                        archives=[
                            Archive(
                                url="https://github.com/google/graphicsfuzz/releases/download/v1.2.1/graphicsfuzz.zip",
                                output_file="graphicsfuzz.zip",
                                output_directory="graphicsfuzz",
                            )
                        ],
                        binaries=[
                            #
                            # glslangValidator
                            Binary(
                                name="glslangValidator",
                                tags=["Linux", "x64", "Release"],
                                path="graphicsfuzz/bin/Linux/glslangValidator",
                                version="40c16ec0b3ad03fc170f1369a58e7bbe662d82cd",
                            ),
                            Binary(
                                name="glslangValidator",
                                tags=["Windows", "x64", "Release"],
                                path="graphicsfuzz/bin/Windows/glslangValidator.exe",
                                version="40c16ec0b3ad03fc170f1369a58e7bbe662d82cd",
                            ),
                            Binary(
                                name="glslangValidator",
                                tags=["Mac", "x64", "Release"],
                                path="graphicsfuzz/bin/Mac/glslangValidator",
                                version="40c16ec0b3ad03fc170f1369a58e7bbe662d82cd",
                            ),
                            #
                            # spirv-opt
                            Binary(
                                name="spirv-opt",
                                tags=[
                                    "Linux",
                                    "x64",
                                    "Release",
                                    SPIRV_OPT_NO_VALIDATE_AFTER_ALL_TAG,
                                ],
                                path="graphicsfuzz/bin/Linux/spirv-opt",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            Binary(
                                name="spirv-opt",
                                tags=[
                                    "Windows",
                                    "x64",
                                    "Release",
                                    SPIRV_OPT_NO_VALIDATE_AFTER_ALL_TAG,
                                ],
                                path="graphicsfuzz/bin/Windows/spirv-opt.exe",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            Binary(
                                name="spirv-opt",
                                tags=[
                                    "Mac",
                                    "x64",
                                    "Release",
                                    SPIRV_OPT_NO_VALIDATE_AFTER_ALL_TAG,
                                ],
                                path="graphicsfuzz/bin/Mac/spirv-opt",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            #
                            # spirv-dis
                            Binary(
                                name="spirv-dis",
                                tags=["Linux", "x64", "Release"],
                                path="graphicsfuzz/bin/Linux/spirv-dis",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            Binary(
                                name="spirv-dis",
                                tags=["Windows", "x64", "Release"],
                                path="graphicsfuzz/bin/Windows/spirv-dis.exe",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            Binary(
                                name="spirv-dis",
                                tags=["Mac", "x64", "Release"],
                                path="graphicsfuzz/bin/Mac/spirv-dis",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            #
                            # spirv-as
                            Binary(
                                name="spirv-as",
                                tags=["Linux", "x64", "Release"],
                                path="graphicsfuzz/bin/Linux/spirv-as",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            Binary(
                                name="spirv-as",
                                tags=["Windows", "x64", "Release"],
                                path="graphicsfuzz/bin/Windows/spirv-as.exe",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            Binary(
                                name="spirv-as",
                                tags=["Mac", "x64", "Release"],
                                path="graphicsfuzz/bin/Mac/spirv-as",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            #
                            # spirv-val
                            Binary(
                                name="spirv-val",
                                tags=["Linux", "x64", "Release"],
                                path="graphicsfuzz/bin/Linux/spirv-val",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            Binary(
                                name="spirv-val",
                                tags=["Windows", "x64", "Release"],
                                path="graphicsfuzz/bin/Windows/spirv-val.exe",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                            Binary(
                                name="spirv-val",
                                tags=["Mac", "x64", "Release"],
                                path="graphicsfuzz/bin/Mac/spirv-val",
                                version="a2ef7be242bcacaa9127a3ce011602ec54b2c9ed",
                            ),
                        ],
                    )
                )
            ),
        )
    ]


BUILT_IN_BINARY_RECIPES: List[recipe_wrap.RecipeWrap] = (
    _get_built_in_spirv_tools_version(
        version_hash="4a00a80c40484a6f6f72f48c9d34943cf8f180d4",
        build_version_hash="422f2fe0f0f32494fa687a12ba343d24863b330a",
        includes_spirv_fuzz=False,
    )
    + _get_built_in_glslang_version(
        version_hash="9866ad9195cec8f266f16191fb4ec2ce4896e5c0",
        build_version_hash="1586e566f4949b1957e7c32454cbf27e501ed632",
    )
    + _get_built_in_swift_shader_version(
        version_hash="a0b3a02601da8c48012a4259d335be04d00818da",
        build_version_hash="08fb8d429272ef8eedb4d610943b9fe59d336dc6",
    )
    + get_graphics_fuzz_121()
    + _get_built_in_spirv_tools_version(
        version_hash="1c1e749f0b51603032ed573acb5ee4cd6fee8d01",
        build_version_hash="7663d620a7fbdccb330d2baec138d0e3e096457c",
        includes_spirv_fuzz=False,
    )
    + _get_built_in_spirv_tools_version(
        version_hash="55adf4cf707bb12c29fc12f784ebeaa29a819e9b",
        build_version_hash="f2170cc791d0eaa5789ec7528862ae00b984b3b8",
        includes_spirv_fuzz=False,
    )
    + _get_built_in_glslang_version(
        version_hash="e383c5f55defdb884a77820483d3360617391d78",
        build_version_hash="f3df04d4f582af6b54989d7da86f58f8f38423ba",
    )
    + _get_built_in_spirv_tools_version(
        version_hash="230c9e437146e48ec58adb4433890403c23c98fa",
        build_version_hash="288b0f57443e221df530b705085df59f2da93843",
        includes_spirv_fuzz=False,
    )
    + _get_built_in_spirv_tools_version(
        version_hash="76b75c40a1e27939957e6a598292e9f32b4e98d4",
        build_version_hash="9debf645007ef2807ba68f4497d50638c4c57878",
        includes_spirv_fuzz=False,
    )
    + _get_built_in_spirv_tools_version(
        version_hash="9559cdbdf011c487f67f89e2d694bd4a18d5c1e0",
        build_version_hash="693b9805d162d5a49592912f6b9bb2d0b4868ec8",
    )
    + _get_built_in_glslang_version(
        version_hash="f04f1f93a70f4608ffa9903b20bfb95f20a063f5",
        build_version_hash="211afd921a2b354ee579cd4b60f761bfe27c1003",
    )
    + _get_built_in_swift_shader_version(
        version_hash="fa0175c0988dd542f008257232207a8b87ad6c63",
        build_version_hash="ea3b929604da6873ace48988b8d4651bbcd2e573",
    )
    + _get_built_in_swift_shader_version(
        version_hash="f25a1c68473b868ce61e97fe5b830c0cdd7e8181",
        build_version_hash="ad0a59319c4a3e23db2688c593a1e0459a99340d",
    )
    + _get_built_in_spirv_tools_version(
        version_hash="06407250a169c6a03b3765e86619075af1a8c187",
        build_version_hash="04b2b8e2543b4643c533b20ca1a9d88c72fea370",
    )
    + _get_built_in_glslang_version(
        version_hash="fe0b2bd694bb07004a2db859c5714c321c26b751",
        build_version_hash="0f167ce7125795df62ae5893f553e5608c9652f4",
    )
    + _get_built_in_spirv_tools_version(
        version_hash="ad7f2c5c4c7f51360e9e079109a9217aa5ba5cc0",
        build_version_hash="b97215064186d731eac68adcc5ade4c7b96b265b",
    )
    + _get_built_in_spirv_tools_version(
        version_hash="6b072126595dd8c2448eb1fda616251c5e6d7079",
        build_version_hash="74886e02e26453ee1dcba4290157e9c8a5e8d07e",
    )
    + _get_built_in_swift_shader_version(
        version_hash="b6fa949c45397bd1fbfda769a104b9e8884f343e",
        build_version_hash="70e8d53b94227fed094975771d96f240f7d00911",
    )
)

BUILT_IN_BINARY_RECIPES_MAP: Dict[str, Recipe] = {
    recipe_wrap.path: recipe_wrap.recipe for recipe_wrap in BUILT_IN_BINARY_RECIPES
}


def get_platform_from_binary(binary: Binary) -> str:
    tags = list(binary.tags)
    platforms = [p for p in tags if p in PLATFORMS_SET]
    if platforms:
        check(
            len(platforms) == 1, AssertionError(f"More than one platform in: {binary}")
        )
        platform = platforms[0]
    else:
        platform = util.get_platform()
    return platform


def get_config_from_binary(binary: Binary) -> str:
    tags = list(binary.tags)
    configs = [c for c in tags if c in CONFIGS_SET]
    if not configs:
        raise AssertionError(f"Could not find a config in tags: {tags}")
    check(len(configs) == 1, AssertionError(f"More than one config in: {binary}"))
    config = configs[0]
    return config


def binary_name_to_project_name(binary_name: str) -> str:
    if binary_name == "glslangValidator":
        project_name = "glslang"
    elif binary_name in (
        "spirv-opt",
        "spirv-as",
        "spirv-dis",
        "spirv-val",
        "spirv-fuzz",
        "spirv-reduce",
    ):
        project_name = "SPIRV-Tools"
    elif binary_name == "swift_shader_icd":
        project_name = "swiftshader"
    elif binary_name in ("amber", "amber_apk", "amber_apk_test"):
        project_name = "amber"
    elif binary_name == "graphicsfuzz-tool":
        project_name = "graphicsfuzz"
    elif binary_name == "amdllpc":
        project_name = "llpc"
    else:
        raise AssertionError(
            f"Could not find {binary_name}. Could not map {binary_name} to a gfbuild- repo."
        )

    return project_name


def get_github_release_recipe(  # pylint: disable=too-many-branches;
    binary: Binary,
) -> recipe_wrap.RecipeWrap:

    project_name = binary_name_to_project_name(binary.name)

    if project_name == "graphicsfuzz":
        # Special case:
        platform = util.get_platform()  # Not used.
        tags = PLATFORMS[:]  # All host platforms.
        repo_name = f"gfbuild-{project_name}"
        version = binary.version
        artifact_name = f"gfbuild-{project_name}-{version}"
    elif project_name == "amber" and binary.name in ("amber_apk", "amber_apk_test"):
        # Special case:
        platform = util.get_platform()  # Not used.
        tags = PLATFORMS[:]  # All host platforms.
        tags.append("Debug")
        repo_name = f"gfbuild-{project_name}"
        version = binary.version
        artifact_name = f"gfbuild-{project_name}-{version}-android_apk"
    else:
        # Normal case:
        platform = get_platform_from_binary(binary)
        config = get_config_from_binary(binary)
        arch = "x64"

        tags = [platform, config, arch]

        repo_name = f"gfbuild-{project_name}"
        version = binary.version
        artifact_name = f"gfbuild-{project_name}-{version}-{platform}_{arch}_{config}"

    recipe = recipe_wrap.RecipeWrap(
        path=f"{BUILT_IN_BINARY_RECIPES_PATH_PREFIX}/{artifact_name}",
        recipe=Recipe(
            download_and_extract_archive_set=RecipeDownloadAndExtractArchiveSet(
                archive_set=ArchiveSet(
                    archives=[
                        Archive(
                            url=f"https://github.com/google/{repo_name}/releases/download/github/google/{repo_name}/{version}/{artifact_name}.zip",
                            output_file=f"{project_name}.zip",
                            output_directory=f"{project_name}",
                        )
                    ],
                    binaries=[],
                )
            )
        ),
    )

    executable_suffix = ".exe" if platform == "Windows" else ""

    if project_name == "glslang":
        binaries = [
            Binary(
                name="glslangValidator",
                tags=tags,
                path=f"{project_name}/bin/glslangValidator{executable_suffix}",
                version=version,
            )
        ]
    elif project_name == "SPIRV-Tools":
        binaries = [
            Binary(
                name="spirv-opt",
                tags=tags,
                path=f"{project_name}/bin/spirv-opt{executable_suffix}",
                version=version,
            ),
            Binary(
                name="spirv-as",
                tags=tags,
                path=f"{project_name}/bin/spirv-as{executable_suffix}",
                version=version,
            ),
            Binary(
                name="spirv-dis",
                tags=tags,
                path=f"{project_name}/bin/spirv-dis{executable_suffix}",
                version=version,
            ),
            Binary(
                name="spirv-val",
                tags=tags,
                path=f"{project_name}/bin/spirv-val{executable_suffix}",
                version=version,
            ),
            Binary(
                name="spirv-fuzz",
                tags=tags,
                path=f"{project_name}/bin/spirv-fuzz{executable_suffix}",
                version=version,
            ),
            Binary(
                name="spirv-reduce",
                tags=tags,
                path=f"{project_name}/bin/spirv-reduce{executable_suffix}",
                version=version,
            ),
        ]
    elif project_name == "swiftshader":
        binaries = [
            Binary(
                name="swift_shader_icd",
                tags=tags,
                path=f"{project_name}/lib/vk_swiftshader_icd.json",
                version=version,
            )
        ]
    elif project_name == "amber":
        if binary.name in ("amber_apk", "amber_apk_test"):
            binaries = [
                Binary(
                    name="amber_apk",
                    tags=tags,
                    path=f"{project_name}/amber.apk",
                    version=version,
                ),
                Binary(
                    name="amber_apk_test",
                    tags=tags,
                    path=f"{project_name}/amber-test.apk",
                    version=version,
                ),
            ]
        else:
            binaries = [
                Binary(
                    name="amber",
                    tags=tags,
                    path=f"{project_name}/bin/amber{executable_suffix}",
                    version=version,
                )
            ]
    elif project_name == "graphicsfuzz":
        binaries = [
            Binary(
                name="graphicsfuzz-tool",
                tags=tags,
                path=f"{project_name}/python/drivers/graphicsfuzz-tool",
                version=version,
            )
        ]
    elif project_name == "llpc":
        if platform != "Linux":
            raise AssertionError("amdllpc is only available on Linux")
        binaries = [
            Binary(
                name="amdllpc",
                tags=tags,
                path=f"{project_name}/bin/amdllpc{executable_suffix}",
                version=version,
            )
        ]
    else:
        raise AssertionError(f"Unknown project name: {project_name}")

    recipe.recipe.download_and_extract_archive_set.archive_set.binaries.extend(binaries)
    return recipe


class BinaryManager(BinaryGetter):
    """
    Implements BinaryGetter.

    An instance of BinaryManager is the main way that code accesses binaries. BinaryManger allows certain tests and/or
    devices to override binaries by passing a list of binary versions that take priority, so the correct versions are
    always used. Plus, the current platform will be used when deciding which binary to download and return.

    See the Binary proto.

    _binary_list: A list of Binary with name, version, configuration. This is used to map a binary name to a Binary.
    _resolved_paths: A map containing: Binary (serialized bytes) -> Path
    _binary_artifacts: A list of all available binary artifacts/recipes stored as tuples: (ArchiveSet, artifact_path).
    _built_in_binary_recipes: This is needed to pass to artifact_util.artifact_execute_recipe_if_needed() so that the
    recipe file can be written on-demand from our in-memory list of built-in recipes.
    """

    _binary_list: List[Binary]
    _resolved_paths: Dict[bytes, Path]
    _binary_artifacts: List[Tuple[ArchiveSet, str]]
    _built_in_binary_recipes: artifact_util.RecipeMap

    def __init__(
        self,
        binary_list: Optional[List[Binary]] = None,
        platform: Optional[str] = None,
        built_in_binary_recipes: Optional[Dict[str, Recipe]] = None,
    ):
        self._binary_list = binary_list or DEFAULT_BINARIES
        self._resolved_paths = {}
        self._platform = platform or util.get_platform()
        self._binary_artifacts = []
        self._built_in_binary_recipes = {}

        self._binary_artifacts.extend(
            artifact_util.binary_artifacts_find(BINARY_RECIPES_PREFIX)
        )

        # When changing this constructor, check self.get_child_binary_manager().

        if built_in_binary_recipes:
            self._built_in_binary_recipes = built_in_binary_recipes
            # For each recipe, add a tuple (ArchiveSet, artifact_path) to self._binary_artifacts.
            for (artifact_path, recipe) in self._built_in_binary_recipes.items():
                check(
                    recipe.HasField("download_and_extract_archive_set"),
                    AssertionError(f"Bad built-in recipe: {recipe}"),
                )
                archive_set: RecipeDownloadAndExtractArchiveSet = recipe.download_and_extract_archive_set
                self._binary_artifacts.append((archive_set.archive_set, artifact_path))

    @staticmethod
    def get_binary_list_from_test_metadata(test_json_path: Path) -> List[Binary]:
        test_metadata = test_util.metadata_read_from_path(test_json_path)
        result: List[Binary] = []
        if test_metadata.device:
            result.extend(test_metadata.device.binaries)
        result.extend(test_metadata.binaries)
        return result

    def _get_binary_path_from_binary_artifacts(self, binary: Binary) -> Optional[Path]:
        binary_tags = set(binary.tags)
        binary_tags.add(self._platform)
        for (archive_set, artifact_path) in self._binary_artifacts:
            for artifact_binary in archive_set.binaries:  # type: Binary
                if artifact_binary.name != binary.name:
                    continue
                if artifact_binary.version != binary.version:
                    continue
                recipe_binary_tags = set(artifact_binary.tags)
                if not binary_tags.issubset(recipe_binary_tags):
                    continue
                artifact_util.artifact_execute_recipe_if_needed(
                    artifact_path, self._built_in_binary_recipes
                )
                result = artifact_util.artifact_get_inner_file_path(
                    artifact_binary.path, artifact_path
                )
                self._resolved_paths[binary.SerializePartialToString()] = result
                return result
        return None

    def get_binary_path(self, binary: Binary) -> Path:
        # Try resolved cache first.
        result = self._resolved_paths.get(binary.SerializePartialToString())
        if result:
            return result
        log(f"Finding path of binary:\n{binary}")

        # Try list (cache) of binary artifacts on disk.
        result = self._get_binary_path_from_binary_artifacts(binary)
        if result:
            return result

        # Try online.
        wrapped_recipe = get_github_release_recipe(binary)
        # Execute the recipe to download the binaries.
        artifact_util.artifact_execute_recipe_if_needed(
            wrapped_recipe.path, {wrapped_recipe.path: wrapped_recipe.recipe}
        )
        # Add to binary artifacts list (cache).
        self._binary_artifacts.append(
            (
                wrapped_recipe.recipe.download_and_extract_archive_set.archive_set,
                wrapped_recipe.path,
            )
        )
        # Now we should be able to find it in the binary artifacts list.
        result = self._get_binary_path_from_binary_artifacts(binary)
        check(
            bool(result),
            AssertionError(
                f"Could not find:\n{binary} even though we just added it:\n{wrapped_recipe}"
            ),
        )
        assert result  # noqa
        return result

    @staticmethod
    def get_binary_by_name_from_list(name: str, binary_list: List[Binary]) -> Binary:
        for binary in binary_list:
            if binary.name == name:
                return binary
        raise BinaryNotFound(
            f"Could not find binary named {name} in list:\n{binary_list}"
        )

    def get_binary_path_by_name(self, name: str) -> BinaryPathAndInfo:
        binary = self.get_binary_by_name(name)
        return BinaryPathAndInfo(self.get_binary_path(binary), binary)

    def get_binary_by_name(self, name: str) -> Binary:
        return self.get_binary_by_name_from_list(name, self._binary_list)

    def get_child_binary_manager(
        self, binary_list: List[Binary], prepend: bool = False
    ) -> "BinaryManager":
        child_binary_list = binary_list
        if prepend:
            child_binary_list += self._binary_list
        result = BinaryManager(child_binary_list, self._platform)
        # pylint: disable=protected-access; This is fine since |result| is a BinaryManager.
        result._resolved_paths = self._resolved_paths
        # pylint: disable=protected-access; This is fine since |result| is a BinaryManager.
        result._binary_artifacts = self._binary_artifacts
        # pylint: disable=protected-access; This is fine since |result| is a BinaryManager.
        result._built_in_binary_recipes = self._built_in_binary_recipes
        return result


def get_default_binary_manager(settings: Settings) -> BinaryManager:
    """
    Gets the default binary manager.

    :param settings: Passing just "Settings()" will use the hardcoded (slightly out-of-date) default binary_list, which
    may be fine, especially if you plan to use specific versions anyway by immediately overriding the binary_list using
    get_child_binary_manager().
    :return:
    """
    return BinaryManager(
        binary_list=list(settings.latest_binary_versions) or DEFAULT_BINARIES,
        built_in_binary_recipes=BUILT_IN_BINARY_RECIPES_MAP,
    )


class DownloadVersionError(Exception):
    pass


def _download_latest_version_number(project_name: str) -> str:

    url = f"https://api.github.com/repos/google/gfbuild-{project_name}/releases"
    log(f"Checking: {url}")
    response = requests.get(url)
    if not response:
        raise DownloadVersionError(f"Failed to find version of {project_name}")

    result = response.json()

    expected_num_assets_map = {
        "amber": 19,
        "glslang": 15,
        "SPIRV-Tools": 15,
        "swiftshader": 15,
        "graphicsfuzz": 5,
        "llpc": 7,
    }

    expected_num_assets = expected_num_assets_map[project_name]

    for release in result:
        assets = release["assets"]
        if len(assets) != expected_num_assets:
            log(
                f"SKIPPING a release of {project_name} with {len(assets)} assets (expected {expected_num_assets})"
            )
            continue

        tag_name: str = release["tag_name"]
        last_slash = tag_name.rfind("/")
        if last_slash == -1:
            raise DownloadVersionError(
                f"Failed to find version of {project_name}; tag name: {tag_name}"
            )
        version = tag_name[last_slash + 1 :]
        log(f"Found {project_name} version {version}")
        return version

    raise DownloadVersionError(
        f"Failed to find version of {project_name} with {expected_num_assets} assets"
    )


def download_latest_binary_version_numbers() -> List[Binary]:
    log("Downloading the latest binary version numbers...")

    # Deep copy of DEFAULT_BINARIES.
    binaries: List[Binary] = []
    for binary in DEFAULT_BINARIES:
        new_binary = Binary()
        new_binary.CopyFrom(binary)
        binaries.append(new_binary)

    # Update version numbers.
    for binary in binaries:
        project_name = binary_name_to_project_name(binary.name)
        binary.version = _download_latest_version_number(project_name)

    return binaries
