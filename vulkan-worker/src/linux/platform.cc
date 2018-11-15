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

// Linux specifics

#include "platform.h" // includes GLFW, vulkan

#include <assert.h> // assert()
#include <stdlib.h> // exit()
#include <vector> // std::vector<>

#include "vkcheck.h" // VKCHECK()

void PlatformGetInstanceExtensions(std::vector<const char *>& extensions) {
  uint32_t count = 0;
  const char **glfw_extensions = glfwGetRequiredInstanceExtensions(&count);
  extensions.resize(0);
  for (uint32_t i = 0; i < count; i++) {
    extensions.push_back(glfw_extensions[i]);
  }
}

void PlatformGetInstanceLayers(std::vector<const char*> &layers) {
  // Load validation layer on Linux
  layers.push_back("VK_LAYER_LUNARG_standard_validation");
  layers.push_back("VK_LAYER_LUNARG_assistant_layer");
  layers.push_back("VK_LAYER_LUNARG_object_tracker");
  layers.push_back("VK_LAYER_LUNARG_parameter_validation");
  layers.push_back("VK_LAYER_GOOGLE_threading");
  //layers.push_back("VK_LAYER_LUNARG_screenshot");
}

void PlatformCreateSurface(PlatformData *platform_data, VkInstance instance, VkSurfaceKHR *surface) {
  VKCHECK(glfwCreateWindowSurface(instance, platform_data->window, nullptr, surface));
}

void PlatformGetWidthHeight(PlatformData *platform_data, uint32_t *width, uint32_t *height) {
  int w, h;
  glfwGetWindowSize(platform_data->window, &w, &h);
  *width = (uint32_t)w;
  *height = (uint32_t)h;
}
