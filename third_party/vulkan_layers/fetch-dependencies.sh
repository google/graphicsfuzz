#!/bin/bash

# Get SPIRV-Tools and pin to a known-good revision
git clone https://github.com/KhronosGroup/SPIRV-Tools.git external/SPIRV-Tools
pushd external/SPIRV-Tools
git checkout 3622769785086d4ace466cbc4ffc5263f6ffcf50
popd

# Get SPIRV-Headers and pin to a known-good revision
git clone https://github.com/KhronosGroup/SPIRV-Headers external/SPIRV-Tools/external/spirv-headers
pushd external/SPIRV-Tools/external/spirv-headers
git checkout 308bd07424350a6000f35a77b5f85cd4f3da319e
popd

# Get protobufs and pin to a known-good revision
git clone --depth=1 https://github.com/protocolbuffers/protobuf external/SPIRV-Tools/external/protobuf
pushd external/SPIRV-Tools/external/protobuf
git fetch --all --tags --prune
git checkout v3.7.1
popd

# Get glslang and pin to a known-good revision
git clone https://github.com/KhronosGroup/glslang external/glslang
pushd external/glslang
git checkout 9c3204a1fde09ba7b98b1779047bf8d3491244a5
popd

# Get stb and pin to a known-good revision
git clone https://github.com/nothings/stb external/stb
pushd external/stb
git checkout f54acd4e13430c5122cab4ca657705c84aa61b08
popd
