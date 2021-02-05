# Coverage tools

`gfauto` includes a set of tools for generating differential
code coverage.

## A simple example

```sh

# Make sure gfauto_* is available. Check the README.md file above this
# directory for setup instructions.
#
# E.g.
# source /path/to/gfauto/.venv/activate

# Run your program once.

export GCOV_PREFIX=/data/gcda_files/run1
./program 1 2 3
unset GCOV_PREFIX

# Run your program a second time with a different input.

export GCOV_PREFIX=/data/gcda_files/run2
./program 4 5 6
unset GCOV_PREFIX

# The --gcov_uses_json flag below is needed for GCC 9+.

# Process the .gcda files from run1.
gfauto_cov_from_gcov --gcov_uses_json --out run1.cov $BUILD_DIR --gcov_prefix_dir /data/gcda_files/run1 --num_threads 32

# Process the .gcda files from run2.
gfauto_cov_from_gcov --gcov_uses_json --out run2.cov $BUILD_DIR --gcov_prefix_dir /data/gcda_files/run2 --num_threads 32

# Output just the *newly-covered* lines from run2 into "new.cov".
gfauto_cov_new run1.cov run2.cov new.cov

# Output two parallel source directories, "new_cov_zero/" and "new_cov/".
# Each directory contains the original project source tree with each line
# prefixed with an execution count.
# The parallel source directories can be compared with any existing diff
# viewer.
gfauto_cov_to_source --coverage_out new_cov --zero_coverage_out new_cov_zero --cov new.cov $BUILD_DIR

# E.g. We can use the meld diff viewer.
# Note: Install meld if needed.

meld new_cov_zero/ new_cov/

# The lines that are "different" are the newly-covered lines. The execution
# count on the right (in "new_cov/") is the execution count for the line from
# run2. The execution count from the run1 is zero (because only newly-covered
# lines are highlighted in the diff viewer).
```

## dEQP and gfauto example

The example below demonstrates how to generate and view
differential code coverage on the SwiftShader Vulkan driver,
comparing a run of `gfauto_fuzz` vs. a run of dEQP.

The main coverage tools are executed near the end of the example script.

```sh
# Make sure gfauto_* is available.
# E.g.
# source /path/to/gfauto/.venv/activate
# gfauto_cov_from_gcov -h

COV_ROOT=$(pwd)


### Build SwiftShader ###

git clone https://swiftshader.googlesource.com/SwiftShader
cd SwiftShader

mkdir -p out/build
cd out/build

BUILD_DIR=$(pwd)

export CFLAGS=--coverage
export CXXFLAGS=--coverage
export LDFLAGS=--coverage

cmake -G Ninja ../.. -DCMAKE_BUILD_TYPE=Debug
cmake --build . --config Debug

unset CFLAGS
unset CXXFLAGS
unset LDFLAGS

cd $COV_ROOT

export VK_ICD_FILENAMES=$BUILD_DIR/Linux/vk_swiftshader_icd.json


### Build dEQP ###
DEQP_USERNAME=paulthomson
git clone ssh://$DEQP_USERNAME@gerrit.khronos.org:29418/vk-gl-cts && scp -p -P 29418 $DEQP_USERNAME@gerrit.khronos.org:hooks/commit-msg vk-gl-cts/.git/hooks/

cd vk-gl-cts
python external/fetch_sources.py
cd ..

mkdir vk-gl-cts-build
cd vk-gl-cts-build
cmake -G Ninja ../vk-gl-cts -DCMAKE_BUILD_TYPE=Release DCMAKE_C_FLAGS=-m64 -DCMAKE_CXX_FLAGS=-m64
cmake --build . --config Release --target deqp-vk

cd $COV_ROOT


### Run gfauto ###

export GCOV_PREFIX=$COV_ROOT/prefix_gfauto/PROC_ID

mkdir gfauto_run
cd gfauto_run

# Run gfauto. See ../README.md.
# In settings.json, set `active_devices` to `host`.
# Of course, `host` will actually be your built version of SwiftShader
# because of VK_ICD_FILENAMES.
# In settings.json, set `reduce_*` to false. E.g. "reduce_bad_images": false.
# Once `gfauto_fuzz` seems to work, you can try running e.g. 32 instances in parallel:

parallel -j 32 -i gfauto_fuzz -- $(seq 100)
# Output coverage files are in: $COV_ROOT/prefix_gfauto/*/

cd $COV_ROOT


### Run dEQP ###

export GCOV_PREFIX=$COV_ROOT/prefix_deqp/PROC_ID

# TODO: where do we find rundeqp.go?

go run rundeqpvk.go -deqp-vk $COV_ROOT/vk-gl-cts-build/external/vulkancts/modules/vulkan/deqp-vk -num-threads 32

cd $COV_ROOT


### Process coverage info ###

# The --gcov_uses_json flag below is needed for GCC 9+.

# Process the .gcda files from the dEQP run into "deqp.cov".
# Do not replace "PROC_ID" with a number; gfauto understands this special
# directory name.
gfauto_cov_from_gcov --gcov_uses_json --out deqp.cov $BUILD_DIR --gcov_prefix_dir $COV_ROOT/prefix_deqp/PROC_ID --num_threads 32

# Process the .gcda files from the gfauto run into "gfauto.cov".
# Do not replace "PROC_ID" with a number; gfauto understands this special
# directory name.
gfauto_cov_from_gcov --gcov_uses_json --out gfauto.cov $BUILD_DIR --gcov_prefix_dir $COV_ROOT/prefix_gfauto/PROC_ID --num_threads 32

# Output just the *newly-covered* lines from the gfauto run into "new.cov".
gfauto_cov_new deqp.cov gfauto.cov new.cov

# Output two parallel source directories, "new_cov_zero/" and "new_cov/".
# Each directory contains the original project source tree with each line
# prefixed with an execution count.
# The parallel source directories can be compared with any existing diff
# viewer.
gfauto_cov_to_source --coverage_out new_cov --zero_coverage_out new_cov_zero --cov new.cov $BUILD_DIR

# E.g. We can use the meld diff viewer.
# Note: Install meld if needed.

meld new_cov_zero/ new_cov/

# The lines that are "different" are the newly-covered lines. The execution
# count on the right (in "new_cov/") is the execution count for the line from
# the gfauto run. The execution count from the dEQP run is zero (because only
# newly-covered lines are highlighted in the diff viewer).

```
