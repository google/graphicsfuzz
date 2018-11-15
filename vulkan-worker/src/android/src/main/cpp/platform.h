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

// Android specifics

#include <vulkan/vulkan.h>

#include <android/log.h> // __android_log_print()
#include <vector> // std::vector<>

#define log(...) __android_log_print(ANDROID_LOG_INFO, "GfzVk", __VA_ARGS__)

typedef struct PlatformData {
  ANativeWindow *window;
} PlatformData;

void PlatformGetInstanceExtensions(std::vector<const char *>& extensions);
void PlatformGetInstanceLayers(std::vector<const char*> &layers);
void PlatformCreateSurface(PlatformData *platform_data, VkInstance instance, VkSurfaceKHR *surface);
void PlatformGetWidthHeight(PlatformData *platform_data, uint32_t *width, uint32_t *height);

#endif
