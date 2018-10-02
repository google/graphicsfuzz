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
import sys

path = os.path.join


def go():
    repo_name = "google/graphicsfuzz"
    log_depth = 3

    if "COMMIT_HASH" not in os.environ:
        print("Please define COMMIT_HASH")
        sys.exit(1)
    commit_hash = os.environ["COMMIT_HASH"]

    if not os.path.isdir("out"):
        print("Failing release because 'out' directory was not found.")

    # Upload release.
    description = "Automated build."
    git_log = subprocess.check_output([
        "git", "log", "--graph", "-n", str(log_depth),
        "--abbrev-commit",
        "--pretty=format:%h - %s <%an>"]).decode()

    subprocess.check_call([
        "github-release",
        repo_name,
        "v-" + commit_hash,
        commit_hash,
        description + "\n" + git_log,
        "out/*"])


if __name__ == "__main__":
    go()

