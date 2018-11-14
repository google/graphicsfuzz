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

// Android specifics

#include <android_native_app_glue.h>
#include <vulkan/vulkan.h>
#include <vector>

#include "platform.h"
#include "vkcheck.h"

void PlatformGetInstanceExtensions(std::vector<const char *> &extensions) {
  extensions.resize(0);
  extensions.push_back(VK_KHR_SURFACE_EXTENSION_NAME);
  extensions.push_back(VK_KHR_ANDROID_SURFACE_EXTENSION_NAME);
}

void PlatformGetInstanceLayers(std::vector<const char *> &layers) {
  // Use layers available in Android NDK
  layers.push_back("VK_LAYER_LUNARG_core_validation");
  layers.push_back("VK_LAYER_LUNARG_parameter_validation");
  layers.push_back("VK_LAYER_LUNARG_object_tracker");
  layers.push_back("VK_LAYER_GOOGLE_threading");
}

void PlatformCreateSurface(PlatformData *platform_data, VkInstance instance, VkSurfaceKHR *surface) {
  VkAndroidSurfaceCreateInfoKHR android_surface_create_info;
  android_surface_create_info.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
  android_surface_create_info.pNext = nullptr;
  android_surface_create_info.flags = 0;
  android_surface_create_info.window = platform_data->window;
  VKCHECK(vkCreateAndroidSurfaceKHR(instance, &android_surface_create_info, nullptr, surface));
}

void PlatformGetWidthHeight(PlatformData *platform_data, uint32_t *width, uint32_t *height) {
  uint32_t width_value = ANativeWindow_getWidth(platform_data->window);
  if (width_value > 256) {
    width_value = 256;
  }
  *width = width_value;

  uint32_t height_value = ANativeWindow_getHeight(platform_data->window);
  if (height_value > 256) {
    height_value = 256;
  }
  *height = height_value;
}
