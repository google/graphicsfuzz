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

# Sanity checks.
test -d "temp"
test -f "graphicsfuzz/target/graphicsfuzz.zip"

# Get an empty test directory.
rm -rf temp/cmd_tests
mkdir -p temp/cmd_tests

# Copy in graphicsfuzz.zip.
cp graphicsfuzz/target/graphicsfuzz.zip temp/cmd_tests/

cd temp/cmd_tests

# Unzip it.
if type -P 7z >/dev/null; then
  7z x -ographicsfuzz graphicsfuzz.zip
else
  unzip -q -d graphicsfuzz graphicsfuzz.zip
fi

# Set PATH.
export "PATH=$(pwd)/graphicsfuzz/python/drivers:$PATH"

kernel="$(uname -s)"
case "${kernel}" in
    Linux*)     platform=Linux;;
    Darwin*)    platform=Mac;;
    CYGWIN*)    platform=Windows;;
    MINGW*)     platform=Windows;;
    *)          platform="${kernel}"
esac

export "PATH=$(pwd)/graphicsfuzz/bin/${platform}:$PATH"


### Generate examples.
cp -r graphicsfuzz/shaders/samples .
glsl-generate --seed 0 samples/300es samples/300es 10 family_300es work/shaderfamilies >/dev/null
glsl-generate --seed 0 samples/100 samples/100 10 family_100 work/shaderfamilies >/dev/null
glsl-generate --seed 0 --vulkan --max-uniforms 10 samples/320es samples/320es 10 family_vulkan work/shaderfamilies >/dev/null

test -d "work/shaderfamilies/family_100_stable_bubblesort_flag"
test -d "work/shaderfamilies/family_300es_stable_bubblesort_flag"
test -d "work/shaderfamilies/family_vulkan_stable_bubblesort_flag"


### Reduce examples.
cp -r graphicsfuzz/examples/glsl-reduce-walkthrough .

PATH="$(pwd)/glsl-reduce-walkthrough:${PATH}"
export PATH

# Fake compiler fails.
EXIT_CODE=0
fake_compiler glsl-reduce-walkthrough/colorgrid_modulo.frag || EXIT_CODE=$?
test "${EXIT_CODE}" -eq 2

# Interestingness test succeeds.
interestingness_test glsl-reduce-walkthrough/colorgrid_modulo.json >/dev/null
# Reducer succeeds.
glsl-reduce glsl-reduce-walkthrough/colorgrid_modulo.json --output reduction_results interestingness_test >/dev/null
# Interestingness test still succeeds.
interestingness_test reduction_results/colorgrid_modulo_reduced_final.json >/dev/null
# Fake compiler still fails.
EXIT_CODE=0
fake_compiler reduction_results/colorgrid_modulo_reduced_final.frag || EXIT_CODE=$? >/dev/null
test "${EXIT_CODE}" -eq 2

# Weak interestingness test succeeds.
weak_interestingness_test glsl-reduce-walkthrough/colorgrid_modulo.json >/dev/null
# Reducer succeeds.
glsl-reduce glsl-reduce-walkthrough/colorgrid_modulo.json --output slipped_reduction_results weak_interestingness_test >/dev/null
# Weak interesting test succeeds.
weak_interestingness_test slipped_reduction_results/colorgrid_modulo_reduced_final.json >/dev/null
# Interestingness test (non-weak) fails.
EXIT_CODE=0
interestingness_test slipped_reduction_results/colorgrid_modulo_reduced_final.json || EXIT_CODE=$? >/dev/null
test "${EXIT_CODE}" -eq 1


### Server runs.
EXIT_CODE=0
timeout --preserve-status 5 glsl-server || EXIT_CODE=$?
test "${EXIT_CODE}" -eq 143


### Check some binaries.
SHADER=work/shaderfamilies/family_100_stable_bubblesort_flag/reference.frag

glslangValidator "${SHADER}"
shader_translator "${SHADER}" >/dev/null
spirv-as -h >/dev/null
spirv-dis -h >/dev/null
spirv-val -h >/dev/null
