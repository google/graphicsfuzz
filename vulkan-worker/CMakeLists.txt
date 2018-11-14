# Copyright (C) 2018 The GraphicsFuzz Project Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_CXX_FLAGS "$ENV{CMAKE_CXX_FLAGS} -g -Werror -std=c++11")
set(CJSON "cJSON-1.7.8")

find_package(glfw3 3.2 REQUIRED)
add_subdirectory(${CMAKE_SOURCE_DIR}/third_party/gflags)

add_executable(vkworker
  src/linux/main.cc
  src/linux/platform.cc
  src/common/vulkan_worker.cc
  src/common/vkcheck.cc
  third_party/${CJSON}/cJSON.c
  third_party/lodepng/lodepng.cpp
  )

target_include_directories(vkworker BEFORE PRIVATE
  ${CMAKE_SOURCE_DIR}/src/common
  ${CMAKE_SOURCE_DIR}/src/linux
  ${CMAKE_SOURCE_DIR}/third_party/${CJSON}
  ${CMAKE_SOURCE_DIR}/third_party/lodepng
  $ENV{VULKAN_SDK}/include
  )

link_directories(vkworker BEFORE $ENV{VULKAN_SDK}/lib)
target_link_libraries(vkworker vulkan glfw gflags)
