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

set(THIRD_PARTY ${CMAKE_SOURCE_DIR}/../../../third_party)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DVK_USE_PLATFORM_ANDROID_KHR")

# See: https://github.com/android-ndk/ndk/issues/381
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -u ANativeActivity_onCreate")

# Place to find Vulkan validation layers
link_directories(${ANDROID_NDK}/sources/third_party/vulkan/src/build-android/jniLibs/${ANDROID_ABI})

# native_app_glue enables to get Android app without Java source
add_library(native_app_glue STATIC ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c)

add_subdirectory(${THIRD_PARTY}/gflags gflags EXCLUDE_FROM_ALL)

add_library(vkworker SHARED
        ${CMAKE_SOURCE_DIR}/src/main/cpp/main.cc
        ${CMAKE_SOURCE_DIR}/src/main/cpp/platform.cc
        ${CMAKE_SOURCE_DIR}/../common/vulkan_worker.cc
        ${CMAKE_SOURCE_DIR}/../common/vkcheck.cc
        ${THIRD_PARTY}/cJSON/cJSON.c
        ${THIRD_PARTY}/lodepng/lodepng.cpp
        )

target_include_directories(vkworker PRIVATE
        ${CMAKE_SOURCE_DIR}/src/main/cpp
        ${CMAKE_SOURCE_DIR}/../common
        ${THIRD_PARTY}/cJSON
        ${THIRD_PARTY}/lodepng
        ${ANDROID_NDK}/sources/android/native_app_glue
        )

target_link_libraries(vkworker
        native_app_glue
        android
        log
        gflags
        vulkan
        VkLayer_core_validation
        VkLayer_parameter_validation
        VkLayer_object_tracker
        VkLayer_threading
        )
