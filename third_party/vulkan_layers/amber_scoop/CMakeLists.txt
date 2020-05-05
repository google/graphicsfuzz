# Copyright 2020 The GraphicsFuzz Project Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

cmake_minimum_required(VERSION 3.0)

find_package(Vulkan REQUIRED)

set(AMBER_SCOOP_LAYER_SOURCES
        buffer_copy.cpp
        buffer_copy.h
        layer.cpp
        layer.h
        layer_impl.cpp
        layer_impl.h
        vk_deep_copy.cpp
        vk_deep_copy.h
        vulkan_commands.h
        vulkan_formats.cpp
        vulkan_formats.h
        )

add_library(VkLayer_graphicsfuzz_amber_scoop SHARED ${AMBER_SCOOP_LAYER_SOURCES})

target_include_directories(VkLayer_graphicsfuzz_amber_scoop
        PRIVATE ../external/SPIRV-Tools
        PRIVATE ${CMAKE_BINARY_DIR}/external/SPIRV-Tools
        PRIVATE ../agi
        PRIVATE ..
        )
#target_link_libraries(simple PUBLIC vulkan glslang SPIRV)
target_link_libraries(VkLayer_graphicsfuzz_amber_scoop
        PUBLIC SPIRV-Tools-opt
        PUBLIC vulkan_layers_common
        PUBLIC vulkan)
