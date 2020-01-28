# Copyright 2019 The GraphicsFuzz Project Authors
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

set(SHADER_SCRAPER_LAYER_SOURCES
    layer.h
    layer.cpp
    layer_impl.cpp
    )

add_library(VkLayer_graphicsfuzz_shader_scraper SHARED ${SHADER_SCRAPER_LAYER_SOURCES})

target_include_directories(VkLayer_graphicsfuzz_shader_scraper
        PRIVATE ../external/SPIRV-Tools
        PRIVATE ${CMAKE_BINARY_DIR}/external/SPIRV-Tools
        PRIVATE ../gapid
)

target_link_libraries(VkLayer_graphicsfuzz_shader_scraper
        PUBLIC SPIRV-Tools-opt)
