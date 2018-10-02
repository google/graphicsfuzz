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
import subprocess
import shutil

path = os.path.join


def go():
    os.environ["GRADLE_OPTS"] = "-Dorg.gradle.daemon=false"

    os.mkdir("out")

    subprocess.check_call(["mvn", "package", "-Dmaven.test.skip=true"])
    subprocess.check_call(["mvn", "clean"])
    subprocess.check_call(["mvn", "package"])  # TODO: Enable image tests.

    shutil.copy2(
        path("assembly", "target", "assembly-1.0.zip"),
        path("out", "server.zip")
    )

    # install libgdx client dependencies
    subprocess.check_call(["mvn", "-am", "-pl", "repos/gf-private/android-client-dep", "install"])

    source_root = os.path.abspath(".")

    os.chdir(path("platforms", "libgdx", "OGLTesting"))

    # build desktop worker
    subprocess.call(
        ["./gradlew", "desktop:dist"])

    shutil.copy2(
        path("desktop", "build", "libs", "desktop-1.0.jar"),
        path(source_root, "out", "desktop-worker.jar")
    )

    


if __name__ == "__main__":
    go()

