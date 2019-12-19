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

if [ -z ${VIRTUAL_ENV+x} ]; then
  source .venv/bin/activate
fi

mypy --strict --show-absolute-path gfauto gfautotests
pylint gfauto gfautotests
# Flake checks formatting via black.
flake8 .
# Run tests, but run test_large.py last and in parallel workers.
pytest gfautotests --ignore=gfautotests/test_large.py
pytest -n 8 gfautotests/test_large.py
