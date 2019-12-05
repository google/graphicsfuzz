# Coverage

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

gfauto_cov_from_gcov -h
# Check help text.
# You may need to add --gcov_uses_json depending on your version of GCC.
# This flag has not been tested yet.

gfauto_cov_from_gcov --out deqp.cov $BUILD_DIR $COV_ROOT/prefix_deqp/PROC_ID --num_threads 32

gfauto_cov_from_gcov --out gfauto.cov $BUILD_DIR $COV_ROOT/prefix_gfauto/PROC_ID --num_threads 32

gfauto_cov_new deqp.cov gfauto.cov new.cov

gfauto_cov_to_source --coverage_out new_cov --zero_coverage_out new_cov_zero --cov new.cov $BUILD_DIR

# Note: Install meld if needed.

meld new_cov_zero/ new_cov/

```
