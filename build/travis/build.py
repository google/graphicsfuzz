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
import sys
import subprocess
import shutil
import licenses
import check_headers

path = os.path.join


def check_call_filter(cmd):
    """
    This is like subprocess.check_call but filters out common, boring messages so we don't have
    such a large stdout when executing mvn. This was necessary to prevent travis from
    terminating our build.

    :param cmd:
    :return:
    """
    print(" ".join(cmd))
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
    )
    for line in iter(proc.stdout.readline, b""):
        line = line.decode("utf-8")
        # note: line includes newline
        if line.startswith("Progress "):
            continue
        if line.find("SimplePlan - Looking for opportunities of kind") != -1:
            continue
        print(line, end="")
    return_code = proc.wait()
    print("")
    sys.stdout.flush()
    assert return_code == 0


def go():
    os.environ["GRADLE_OPTS"] = "-Dorg.gradle.daemon=false"

    os.mkdir("out")

    # Check licenses
    check_headers.go()

    # Build with no tests.
    check_call_filter(["mvn", "package", "-Dmaven.test.skip=true"])

    # Generate third party licenses file.
    licenses.go()

    # Copy license files.

    shutil.copy2(
        "OPEN_SOURCE_LICENSES.TXT",
        path("out", "OPEN_SOURCE_LICENSES.TXT")
    )

    shutil.copy2(
        "OPEN_SOURCE_LICENSES.TXT",
        path("assembly", "src", "main", "scripts", "OPEN_SOURCE_LICENSES.TXT")
    )

    shutil.copy2(
        "LICENSE",
        path("assembly", "src", "main", "scripts", "LICENSE.TXT")
    )

    # Rebuild with tests.
    check_call_filter(["mvn", "clean"])
    check_call_filter(["mvn", "package"])  # TODO: Enable image tests.

    # Copy output.
    shutil.copy2(
        path("assembly", "target", "assembly-1.0.zip"),
        path("out", "server.zip")
    )

    source_root = os.path.abspath(".")

    os.chdir(path("platforms", "libgdx", "OGLTesting"))

    # Build desktop worker.
    subprocess.check_call(
        ["./gradlew", "desktop:dist"])

    # Copy desktop worker.
    shutil.copy2(
        path("desktop", "build", "libs", "desktop-1.0.jar"),
        path(source_root, "out", "desktop-gles-worker.jar")
    )

    # Build Android worker
    subprocess.check_call(
        ["./gradlew", "android:assembleDebug"])

    subprocess.check_call("find", shell=True)

    # Copy Android worker.
    shutil.copy2(
        path("android", "build", "outputs", "apk", "android-debug.apk"),
        path(source_root, "out", "android-gles-worker.apk")
    )

    os.chdir(source_root)


if __name__ == "__main__":
    go()

