#!/bin/bash
# Build one of the Vulkan demos Repo
# {Sascha Willems, Google Test Applications, Khronos Samples, Imagination Demos}
#
# Run from the top level directory of the repo
# Requires that cmake and the other dependencies 
# for the given repo have to be fulfiled
#
# In particular, for the Sascha Willems demos, run
# the download_assets.py script to download the
# required assets before running this.

mkdir build
cd build
cmake -G Ninja .. -DCMAKE_BUILD_TYPE=Debug
cmake --build . --config Debug

