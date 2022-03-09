#!/usr/bin/env python3

# Copyright 2018 The GraphicsFuzz Project Authors
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
import io
import sys
import re
from os.path import normpath

path = os.path.join


def exclude_dirname(f: str):
    return (
        f.endswith(".egg-info") or
        f in [
        "target",
        ".git",
        ".idea",
        ".gradle",
        "tmp",
        "third_party",
        "venv",
        "__pycache__",
        ".externalNativeBuild",
        ".mvn",
        "cmake-build-debug",
        "cmake-build-release",
        ".pytest_cache",
        ".venv",
        ]
    )


def exclude_dirpath(f: str):
    f = normpath(f)
    return \
        f.startswith(normpath("./vulkan-worker/build")) or \
        f in [normpath(p) for p in [
            "./gles-worker/build",
            "./gles-worker/android/build",
            "./gles-worker/android/libs",
            "./gles-worker/core/build",
            "./gles-worker/desktop/build",
            "./gles-worker/gradle/wrapper",
            "./build/licenses",
            "./vulkan-worker/src/android/build",
            "./temp",
            "./out"
        ]]


def no_ext_or_endswith_bat(f: str):
    if len(os.path.basename(f).split(".")) == 1:
        # No extension
        return True
    if f.endswith(".bat"):
        return True
    return False


def is_command_wrapper(f: str):
    if not no_ext_or_endswith_bat(f):
        return False
    with io.open(f, 'r') as fin:
        if len(fin.readlines()) < 6:
            return True
    return False


def exclude_filepath(f: str):
    f = normpath(f)
    return \
        f in [normpath(p) for p in [
            "./graphicsfuzz/src/main/scripts/server-static/shaders/shader.vert",
            "./server-static-public/src/main/files/server-static/runner_multi_template.html",
            "./gles-worker/build.gradle",
            "./gles-worker/gradle.properties",
            "./gles-worker/settings.gradle",
            "./gles-worker/android/build.gradle",
            "./gles-worker/android/proguard-project.txt",
            "./gles-worker/android/project.properties",
            "./gles-worker/core/build.gradle",
            "./gles-worker/desktop/build.gradle",
            "./gles-worker/ios/build.gradle",
            "./gles-worker/ios/robovm.properties",
            "./mvnw",
            "./mvnw.cmd"

        ]]


def exclude_filename(f: str):
    return \
        f.startswith(".attach_pid") or \
        f.endswith(".iml") or \
        f.endswith(".png") or \
        f.endswith(".md") or \
        f.endswith(".json") or \
        f.endswith(".primitives") or \
        f.endswith(".jar") or \
        f.endswith(".spv") or \
        f.endswith(".dic") or \
        f in [
            ".editorconfig",
            ".gitmodules",
            "settings.gradle",
            ".gitignore",
            ".gitattributes",
            "AUTHORS",
            "CODEOWNERS",
            "CONTRIBUTORS",
            "LICENSE",
            "LICENSE.TXT",
            "OPEN_SOURCE_LICENSES.TXT",
            "HASH",
            "local.properties",
            "gradlew",
            "gradlew.bat",
            "dependency-reduced-pom.xml",
            "gradle-wrapper.properties",
            "Pipfile.lock",
        ]


def go():
    fail = False
    copyright_pattern = re.compile(r"Copyright 20(18|19|20|21|22) The GraphicsFuzz Project Authors")
    generated_pattern = re.compile(r"(g|G)enerated")

    for (dirpath, dirnames, filenames) in os.walk(os.curdir, topdown=True):

        # dirnames[:] = <--- modifies in-place to ignore certain directories

        if exclude_dirpath(dirpath):
            dirnames[:] = []
            continue

        dirnames[:] = [d for d in dirnames if not exclude_dirname(d)]

        for file in [path(dirpath, f) for f in filenames if not exclude_filename(f)]:
            if exclude_filepath(file):
                continue
            try:
                with io.open(file, "r") as fin:
                    contents = fin.read()


                    first_lines = "\n".join(contents.split("\n")[:10])
                    # OK if the generated pattern is found within the first few lines.
                    if generated_pattern.search(first_lines):
                        continue

                    # Must contain a header for any year within the first few lines.
                    if copyright_pattern.search(first_lines) is None:
                        fail = True
                        print("Missing license header " + file)
                        continue

                    # This file is OK. Continue to the next file.
            except Exception as ex:
                print("Failed to check license header of file " + file)
                print(ex)
                fail = True

    if fail:
        sys.exit(1)


if __name__ == "__main__":
    go()
