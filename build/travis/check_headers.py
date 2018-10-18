#!/usr/bin/env python

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

path = os.path.join

excluded_dirnames = ["target", ".git", ".idea", ".gradle", "tmp", "third_party", "venv",
                     "__pycache__"]

excluded_dirpaths = ["./platforms/libgdx/OGLTesting/build",
                     "./platforms/libgdx/OGLTesting/android/build",
                     "./platforms/libgdx/OGLTesting/android/libs",
                     "./platforms/libgdx/OGLTesting/core/build",
                     "./platforms/libgdx/OGLTesting/desktop/build",
                     "./platforms/libgdx/OGLTesting/gradle/wrapper",
                     "./build/licenses",
                    ]


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


def exclude_file(f: str):
    return \
        f.startswith("./python/src/main/python/drivers/") and is_command_wrapper(f) or \
        f in [
            "./assembly/src/main/scripts/server-static/shaders/shader.vert",
            "./server-static-public/src/main/files/server-static/runner_multi_template.html",
            "./platforms/libgdx/OGLTesting/build.gradle",
            "./platforms/libgdx/OGLTesting/gradle.properties",
            "./platforms/libgdx/OGLTesting/settings.gradle",
            "./platforms/libgdx/OGLTesting/android/build.gradle",
            "./platforms/libgdx/OGLTesting/android/proguard-project.txt",
            "./platforms/libgdx/OGLTesting/android/project.properties",
            "./platforms/libgdx/OGLTesting/core/build.gradle",
            "./platforms/libgdx/OGLTesting/desktop/build.gradle",
            "./platforms/libgdx/OGLTesting/ios/build.gradle",
            "./platforms/libgdx/OGLTesting/ios/robovm.properties",

        ]


def exclude_filename(f: str):
    return \
        f.endswith(".iml") or \
        f.endswith(".png") or \
        f.endswith(".md") or \
        f.endswith(".json") or \
        f.endswith(".primitives") or \
        f in [
            ".editorconfig",
            ".gitignore",
            ".gitattributes",
            "AUTHORS",
            "CODEOWNERS",
            "CONTRIBUTORS",
            "LICENSE",
            "HASH",
            "local.properties",
            "gradlew",
            "gradlew.bat",
            "dependency-reduced-pom.xml",


        ]


def go():
    fail = False
    for (dirpath, dirnames, filenames) in os.walk(os.curdir, topdown=True):
        # print(dirpath)
        # dirnames[:] = <--- modifies in-place to ignore certain directories

        if dirpath in excluded_dirpaths:
            dirnames[:] = []
            continue

        dirnames[:] = [d for d in dirnames if d not in excluded_dirnames]

        for file in [path(dirpath, f) for f in filenames if not exclude_filename(f)]:
            if exclude_file(file):
                continue
            try:
                with io.open(file, 'r') as fin:
                    contents = fin.read()
                    if contents.find("Copyright 2018 The GraphicsFuzz Project Authors") == -1:
                        fail = True
                        print("Missing license header " + file)
            except:
                print("Failed to check license header of file " + file)
                fail = True

    if fail:
        sys.exit(1)


if __name__ == "__main__":
    go()

