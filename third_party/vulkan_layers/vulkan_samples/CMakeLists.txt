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

add_executable(simple simple.cpp)
target_include_directories(simple PRIVATE ../external/stb)
target_link_libraries(simple PUBLIC Vulkan::Vulkan glslang SPIRV)
set_property(TARGET simple PROPERTY CXX_STANDARD 17)

add_executable(instance instance.cpp)
target_include_directories(instance PRIVATE ../external/stb)
target_link_libraries(instance PUBLIC Vulkan::Vulkan glslang SPIRV)
set_property(TARGET instance PROPERTY CXX_STANDARD 17)

add_executable(depth depth.cpp)
target_include_directories(depth PRIVATE ../external/stb)
target_link_libraries(depth PUBLIC Vulkan::Vulkan glslang SPIRV)
set_property(TARGET depth PROPERTY CXX_STANDARD 17)

add_executable(vertex_buffer vertex_buffer.cpp)
target_include_directories(vertex_buffer PRIVATE ../external/stb)
target_include_directories(vertex_buffer PRIVATE ../external/glm)
target_link_libraries(vertex_buffer PUBLIC Vulkan::Vulkan glslang SPIRV)
set_property(TARGET vertex_buffer PROPERTY CXX_STANDARD 17)

add_executable(stencil stencil.cpp)
target_include_directories(stencil PRIVATE ../external/stb)
target_include_directories(stencil PRIVATE ../external/glm)
target_link_libraries(stencil PUBLIC Vulkan::Vulkan glslang SPIRV)
set_property(TARGET stencil PROPERTY CXX_STANDARD 17)

add_executable(push_constants push_constants.cpp)
target_include_directories(push_constants PRIVATE ../external/stb)
target_include_directories(push_constants PRIVATE ../external/glm)
target_link_libraries(push_constants PUBLIC Vulkan::Vulkan glslang SPIRV)
set_property(TARGET push_constants PROPERTY CXX_STANDARD 17)

add_executable(uniform uniform.cpp)
target_include_directories(uniform PRIVATE ../external/stb)
target_include_directories(uniform PRIVATE ../external/glm)
target_link_libraries(uniform PUBLIC Vulkan::Vulkan glslang SPIRV)
set_property(TARGET uniform PROPERTY CXX_STANDARD 17)

add_executable(uniform_dynamic_offset uniform_dynamic_offset.cpp)
target_include_directories(uniform_dynamic_offset PRIVATE ../external/stb)
target_include_directories(uniform_dynamic_offset PRIVATE ../external/glm)
target_link_libraries(uniform_dynamic_offset PUBLIC Vulkan::Vulkan glslang SPIRV)
set_property(TARGET uniform_dynamic_offset PROPERTY CXX_STANDARD 17)

add_executable(compute_shader_manipulating_vertex_buffer compute_shader_manipulating_vertex_buffer.cpp)
target_include_directories(compute_shader_manipulating_vertex_buffer PRIVATE ../external/stb)
target_include_directories(compute_shader_manipulating_vertex_buffer PRIVATE ../external/glm)
target_link_libraries(compute_shader_manipulating_vertex_buffer PUBLIC Vulkan::Vulkan glslang SPIRV)
set_property(TARGET compute_shader_manipulating_vertex_buffer PROPERTY CXX_STANDARD 17)
