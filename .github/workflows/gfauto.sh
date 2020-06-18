#!/usr/bin/env bash

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

set -x
set -e
set -u

help | head

uname

case "$(uname)" in
"Linux")
  ACTIVATE_PATH=".venv/bin/activate"
  export PYTHON=python
  ;;

"Darwin")
  ACTIVATE_PATH=".venv/bin/activate"
  export PYTHON=python
  ;;

"MINGW"*|"MSYS_NT"*)
  ACTIVATE_PATH=".venv/Scripts/activate"
  export PYTHON=python.exe
  ;;

*)
  echo "Unknown OS"
  exit 1
  ;;
esac

python build/travis/check_headers.py
cd gfauto
./dev_shell.sh.template
# shellcheck disable=SC1090
source "${ACTIVATE_PATH}"
./check_all.sh
