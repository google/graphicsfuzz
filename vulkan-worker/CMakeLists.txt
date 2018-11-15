# Copyright 2018 The GraphicsFuzz Project Authors
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

project(vkworker)

set(CMAKE_CXX_STANDARD 11)

set(THIRD_PARTY ${CMAKE_SOURCE_DIR}/../third_party)

find_package(glfw3 3.2 REQUIRED)
add_subdirectory(${THIRD_PARTY}/gflags gflags EXCLUDE_FROM_ALL)

add_executable(vkworker
  src/linux/main.cc
  src/linux/platform.cc
  src/common/vulkan_worker.cc
  src/common/vkcheck.cc
  ${THIRD_PARTY}/cJSON/cJSON.c
  ${THIRD_PARTY}/lodepng/lodepng.cpp
  )

if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
    target_compile_options(vkworker PRIVATE "-Werror" "-Wall" "-Wextra")
endif()

target_include_directories(vkworker BEFORE PRIVATE
  ${CMAKE_SOURCE_DIR}/src/common
  ${CMAKE_SOURCE_DIR}/src/linux
  ${THIRD_PARTY}/cJSON
  ${THIRD_PARTY}/lodepng
  $ENV{VULKAN_SDK}/include
  )

link_directories(vkworker BEFORE $ENV{VULKAN_SDK}/lib)
target_link_libraries(vkworker vulkan glfw gflags)

install(TARGETS vkworker DESTINATION bin)
