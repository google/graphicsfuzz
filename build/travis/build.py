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
import sys
import subprocess
import shutil
import licenses
import check_headers

assert sys.version_info >= (3, 6), \
    "Requires Python 3.6+. Run with `python3.6 build/travis/build.py`."
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

    os.makedirs("out", exist_ok=True)

    # Check licenses
    check_headers.go()

    # Build with no tests.
    check_call_filter(["mvn", "clean"])
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
        path("graphicsfuzz", "src", "main", "scripts", "OPEN_SOURCE_LICENSES.TXT")
    )

    # Rebuild with tests.
    check_call_filter(["mvn", "clean"])
    check_call_filter(["mvn", "package"])  # TODO: Enable image tests.

    # Copy output.
    shutil.copy2(
        path("graphicsfuzz", "target", "graphicsfuzz.zip"),
        path("out", "graphicsfuzz.zip")
    )

    source_root = os.path.abspath(os.curdir)

    os.chdir(path("gles-worker"))

    gradlew = path(os.curdir, "gradlew")

    # Build desktop worker.
    subprocess.check_call(gradlew + " desktop:dist", shell=True)

    # Copy desktop worker.
    shutil.copy2(
        path("desktop", "build", "libs", "gles-worker-desktop.jar"),
        path(source_root, "out", "gles-worker-desktop.jar")
    )

    # Build Android worker
    subprocess.check_call(gradlew + " android:assembleDebug", shell=True)

    # Copy Android worker.
    shutil.copy2(
        path("android", "build", "outputs", "apk", "debug", "gles-worker-android-debug.apk"),
        path(source_root, "out", "gles-worker-android-debug.apk")
    )

    os.chdir(source_root)

    # Check licenses again to ensure that build outputs don't trigger header failures to ensure that
    # we can run this file repeatedly without removing all outputs.
    check_headers.go()


if __name__ == "__main__":
    go()

