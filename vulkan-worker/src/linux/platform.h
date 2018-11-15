// Copyright 2018 The GraphicsFuzz Project Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef __VULKAN_WORKER_PLATFORM__
#define __VULKAN_WORKER_PLATFORM__

// Linux specifics

#define GLFW_INCLUDE_VULKAN // GLFW will include vulkan
#include <GLFW/glfw3.h>

#include <stdio.h> // printf()
#include <vector> // std::vector<>

// Add a newline to format string. For details on ", ##__VA_ARGS__", see
// http://gcc.gnu.org/onlinedocs/cpp/Variadic-Macros.html
#define log(fmt, ...) printf(fmt "\n", ##__VA_ARGS__);

typedef struct PlatformData {
  GLFWwindow* window;
} PlatformData;

void PlatformGetInstanceExtensions(std::vector<const char*> &extensions);
void PlatformGetInstanceLayers(std::vector<const char*> &layers);
void PlatformCreateSurface(PlatformData *platform_data, VkInstance instance, VkSurfaceKHR *surface);
void PlatformGetWidthHeight(PlatformData *platform_data, uint32_t *width, uint32_t *height);

#endif
