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

#include "amber_scoop/vk_deep_copy.h"

#include <cstring>
#include <iostream>

namespace graphicsfuzz_amber_scoop {

VkBufferCreateInfo DeepCopy(const VkBufferCreateInfo &createInfo) {
  VkBufferCreateInfo result = createInfo;
  result.pQueueFamilyIndices = CopyArray(createInfo.pQueueFamilyIndices,
                                         createInfo.queueFamilyIndexCount);
  return result;
}

void DeepDelete(const VkBufferCreateInfo &create_info) {
  if (create_info.queueFamilyIndexCount)
    delete[] create_info.pQueueFamilyIndices;
}

VkDescriptorSetLayoutBinding DeepCopy(
    const VkDescriptorSetLayoutBinding &descriptor_set_layout_binding) {
  VkDescriptorSetLayoutBinding result = descriptor_set_layout_binding;

  // TODO deep copy immutable samplers if needed.
  if (descriptor_set_layout_binding.pImmutableSamplers != nullptr) {
    std::cout << "Warning: immutable samplers not implemented\n";
  }
  return result;
}

void DeepDelete(const VkDescriptorSetLayoutBinding &binding) {
  // TODO: Implement deleting immutable samplers (currently copying is not
  // implemented)
}

VkDescriptorSetLayoutCreateInfo DeepCopy(
    const VkDescriptorSetLayoutCreateInfo &createInfo) {
  VkDescriptorSetLayoutCreateInfo result = createInfo;
  auto newBindings = new VkDescriptorSetLayoutBinding[createInfo.bindingCount];
  for (uint32_t i = 0; i < createInfo.bindingCount; i++) {
    newBindings[i] = DeepCopy(createInfo.pBindings[i]);
  }
  result.pBindings = newBindings;
  return result;
}

void DeepDelete(const VkDescriptorSetLayoutCreateInfo &create_info) {
  for (uint32_t i = 0; i < create_info.bindingCount; i++) {
    DeepDelete(create_info.pBindings[i]);
  }
  delete[] create_info.pBindings;
}

VkFramebufferCreateInfo DeepCopy(const VkFramebufferCreateInfo &create_info) {
  VkFramebufferCreateInfo result = create_info;
  result.pAttachments =
      CopyArray(create_info.pAttachments, create_info.attachmentCount);
  return result;
}

void DeepDelete(const VkFramebufferCreateInfo &create_info) {
  delete[] create_info.pAttachments;
}

VkGraphicsPipelineCreateInfo DeepCopy(
    const VkGraphicsPipelineCreateInfo &createInfo) {
  VkGraphicsPipelineCreateInfo result = createInfo;
  // Copy pStages
  {
    auto *newStages =
        new VkPipelineShaderStageCreateInfo[createInfo.stageCount];

    for (uint32_t i = 0; i < createInfo.stageCount; i++) {
      newStages[i] = DeepCopy(createInfo.pStages[i]);
    }

    result.pStages = newStages;
  }

  // Copy pVertexInputState
  {
    auto vertexInputState = new VkPipelineVertexInputStateCreateInfo();
    *vertexInputState = *createInfo.pVertexInputState;

    // Copy pVertexBindingDescriptions
    {
      vertexInputState->pVertexBindingDescriptions =
          CopyArray(createInfo.pVertexInputState->pVertexBindingDescriptions,
                    vertexInputState->vertexBindingDescriptionCount);
    }

    // Copy pVertexAttributeDescriptions
    {
      vertexInputState->pVertexAttributeDescriptions = CopyArray(
          createInfo.pVertexInputState->pVertexAttributeDescriptions,
          createInfo.pVertexInputState->vertexAttributeDescriptionCount);
    }

    result.pVertexInputState = vertexInputState;
  }

  // Copy pInputAssemblyState
  {
    auto inputAssemblyStateCreateInfo =
        new VkPipelineInputAssemblyStateCreateInfo();
    *inputAssemblyStateCreateInfo = *createInfo.pInputAssemblyState;
    result.pInputAssemblyState = inputAssemblyStateCreateInfo;
  }

  // Copy pRasterizationState
  {
    auto rasterizationState = new VkPipelineRasterizationStateCreateInfo();
    *rasterizationState = *createInfo.pRasterizationState;
    result.pRasterizationState = rasterizationState;
  }

  // Copy pDepthStencilState
  if (createInfo.pDepthStencilState != nullptr) {
    auto depthStencilState = new VkPipelineDepthStencilStateCreateInfo();
    *depthStencilState = *createInfo.pDepthStencilState;
    result.pDepthStencilState = depthStencilState;
  }

  // TODO: handle deep copying of other fields.
  return result;
}

void DeepDelete(const VkGraphicsPipelineCreateInfo &create_info) {
  delete[] create_info.pStages;

  if (create_info.pVertexInputState) {
    delete[] create_info.pVertexInputState->pVertexBindingDescriptions;
    delete[] create_info.pVertexInputState->pVertexAttributeDescriptions;
    delete create_info.pVertexInputState;
  }

  delete create_info.pInputAssemblyState;
  delete create_info.pRasterizationState;
  delete create_info.pDepthStencilState;
}

VkPipelineLayoutCreateInfo DeepCopy(
    const VkPipelineLayoutCreateInfo &create_info) {
  VkPipelineLayoutCreateInfo result = create_info;
  result.pSetLayouts =
      CopyArray(create_info.pSetLayouts, create_info.setLayoutCount);
  result.pPushConstantRanges = CopyArray(create_info.pPushConstantRanges,
                                         create_info.pushConstantRangeCount);
  return result;
}

void DeepDelete(const VkPipelineLayoutCreateInfo &create_info) {
    delete [] create_info.pSetLayouts;
    delete [] create_info.pPushConstantRanges;
}

VkPipelineShaderStageCreateInfo DeepCopy(
    const VkPipelineShaderStageCreateInfo &create_info) {
  VkPipelineShaderStageCreateInfo result = create_info;
  if (create_info.pSpecializationInfo != nullptr) {
    result.pSpecializationInfo = new VkSpecializationInfo(
        {create_info.pSpecializationInfo->mapEntryCount,
         CopyArray(create_info.pSpecializationInfo->pMapEntries,
                   create_info.pSpecializationInfo->mapEntryCount),
         create_info.pSpecializationInfo->dataSize,
         CopyArray((char *)create_info.pSpecializationInfo->pData,
                   create_info.pSpecializationInfo->dataSize)});
  }
  // TODO: make copy deep.
  return result;
}

void DeepDelete(const VkPipelineShaderStageCreateInfo &create_info) {
  if (create_info.pSpecializationInfo) {
    delete [] create_info.pSpecializationInfo->pMapEntries;
    delete [] (char *)create_info.pSpecializationInfo->pData;
  }
}

VkRenderPassCreateInfo DeepCopy(const VkRenderPassCreateInfo &create_info) {
  VkRenderPassCreateInfo result = create_info;
  result.pAttachments =
      CopyArray(create_info.pAttachments, create_info.attachmentCount);
  VkSubpassDescription *newSubpasses =
      new VkSubpassDescription[create_info.subpassCount];
  for (uint32_t i = 0; i < create_info.subpassCount; i++) {
    newSubpasses[i] = DeepCopy(create_info.pSubpasses[i]);
  }
  result.pSubpasses = newSubpasses;
  result.pDependencies =
      CopyArray(create_info.pDependencies, create_info.dependencyCount);
  return result;
}

void DeepDelete(const VkRenderPassCreateInfo &create_info) {
  delete [] create_info.pAttachments;

  for (uint32_t i = 0; i < create_info.subpassCount; i++) {
    DeepDelete(create_info.pSubpasses[i]);
  }
  delete [] create_info.pDependencies;
  delete [] create_info.pSubpasses;
}

VkRenderPassBeginInfo DeepCopy(VkRenderPassBeginInfo const *p_render_pass_begin_info) {
  VkRenderPassBeginInfo result = {};
  result = *p_render_pass_begin_info;
  result.pClearValues = CopyArray(p_render_pass_begin_info->pClearValues,
                                   p_render_pass_begin_info->clearValueCount);
  return result;
}

void DeepDelete(VkRenderPassBeginInfo const p_render_pass_begin_info) {
  delete [] p_render_pass_begin_info.pClearValues;
}

VkShaderModuleCreateInfo DeepCopy(const VkShaderModuleCreateInfo &create_info) {
  VkShaderModuleCreateInfo result = create_info;
  auto new_code = new uint32_t[create_info.codeSize];
  memcpy(new_code, create_info.pCode, create_info.codeSize);
  result.pCode = new_code;
  return result;
}

void DeepDelete(const VkShaderModuleCreateInfo &create_info) {
  delete [] create_info.pCode;
}

VkSubpassDescription DeepCopy(const VkSubpassDescription &subpass_description) {
  auto result = subpass_description;
  result.pInputAttachments = CopyArray(subpass_description.pInputAttachments,
                subpass_description.inputAttachmentCount);
  result.pColorAttachments = CopyArray(subpass_description.pColorAttachments,
                subpass_description.colorAttachmentCount);
  if (subpass_description.pResolveAttachments) {
    result.pResolveAttachments =
        CopyArray(subpass_description.pResolveAttachments,
                  subpass_description.colorAttachmentCount);
  }

  if (subpass_description.pDepthStencilAttachment) {
    result.pDepthStencilAttachment =
        CopyArray(subpass_description.pDepthStencilAttachment, 1);
  }

  result.pPreserveAttachments =
      CopyArray(subpass_description.pPreserveAttachments,
                subpass_description.preserveAttachmentCount);
  return result;
}

void DeepDelete(const VkSubpassDescription &subpass_description) {
  delete [] subpass_description.pInputAttachments;
  delete [] subpass_description.pColorAttachments;
  delete [] subpass_description.pResolveAttachments;
  delete [] subpass_description.pDepthStencilAttachment;
  delete [] subpass_description.pPreserveAttachments;
}

}  // namespace graphicsfuzz_amber_scoop
