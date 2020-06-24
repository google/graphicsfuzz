/*
 * Copyright 2020 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#ifndef GRAPHICSFUZZ_VULKAN_LAYERS_VK_DEEP_COPY_H
#define GRAPHICSFUZZ_VULKAN_LAYERS_VK_DEEP_COPY_H

#include "vulkan/vulkan.h"

namespace graphicsfuzz_amber_scoop {

template <typename T>
T *CopyArray(T const *pData, uint32_t numElements, uint32_t offset = 0) {
  if (pData == nullptr) return nullptr;
  T *result = new T[numElements - offset];
  for (uint32_t i = 0; i < numElements; i++) {
    result[i] = pData[i + offset];
  }
  return result;
}

VkBufferCreateInfo DeepCopy(const VkBufferCreateInfo &createInfo);

void DeepDelete(const VkBufferCreateInfo &create_info);

VkDescriptorSetLayoutBinding DeepCopy(
    const VkDescriptorSetLayoutBinding &descriptor_set_layout_binding);

VkDescriptorSetLayoutCreateInfo DeepCopy(
    const VkDescriptorSetLayoutCreateInfo &createInfo);

void DeepDelete(const VkDescriptorSetLayoutCreateInfo &create_info);

VkFramebufferCreateInfo DeepCopy(const VkFramebufferCreateInfo &createInfo);

void DeepDelete(const VkFramebufferCreateInfo &create_info);

VkGraphicsPipelineCreateInfo DeepCopy(
    const VkGraphicsPipelineCreateInfo &create_info);

void DeepDelete(const VkGraphicsPipelineCreateInfo &create_info);

VkPipelineLayoutCreateInfo DeepCopy(
    const VkPipelineLayoutCreateInfo &create_info);

void DeepDelete(const VkPipelineLayoutCreateInfo &create_info);

VkPipelineShaderStageCreateInfo DeepCopy(
    const VkPipelineShaderStageCreateInfo &create_info);

void DeepDelete(const VkPipelineShaderStageCreateInfo &create_info);

VkRenderPassBeginInfo DeepCopy(VkRenderPassBeginInfo const *p_render_pass_begin_info);

void DeepDelete(const VkRenderPassBeginInfo p_render_pass_begin_info);

VkRenderPassCreateInfo DeepCopy(const VkRenderPassCreateInfo &create_info);

void DeepDelete(const VkRenderPassCreateInfo &create_info);

VkShaderModuleCreateInfo DeepCopy(const VkShaderModuleCreateInfo &create_info);

void DeepDelete(const VkShaderModuleCreateInfo &create_info);

VkSubpassDescription DeepCopy(const VkSubpassDescription &subpass_description);

void DeepDelete(const VkSubpassDescription &subpass_description);

}  // namespace graphicsfuzz_amber_scoop

#endif  // GRAPHICSFUZZ_VULKAN_LAYERS_VK_DEEP_COPY_H
