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

python -m grpc.tools.protoc --python_out=. --proto_path=. --mypy_out=. gfauto/*.proto

# protoc gfauto/artifact.proto gfauto/recipe.proto --python_out=. --plugin=protoc-gen-mypy=github/mypy-protobuf/python/protoc-gen-mypy --mypy_out=. --proto_path=.
