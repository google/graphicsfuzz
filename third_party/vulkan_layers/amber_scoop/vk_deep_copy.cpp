/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

namespace graphicsfuzz_amber_scoop {

VkBufferCreateInfo DeepCopy(const VkBufferCreateInfo &createInfo) {
  VkBufferCreateInfo result = createInfo;
  result.pQueueFamilyIndices = CopyArray(createInfo.pQueueFamilyIndices,
                                         createInfo.queueFamilyIndexCount);
  return result;
}

VkDescriptorSetLayoutBinding
DeepCopy(const VkDescriptorSetLayoutBinding &descriptorSetLayoutBinding) {
  VkDescriptorSetLayoutBinding result = descriptorSetLayoutBinding;
  // TODO deep copy immutable samplers if needed.
  return result;
}

VkDescriptorSetLayoutCreateInfo
DeepCopy(const VkDescriptorSetLayoutCreateInfo &createInfo) {
  VkDescriptorSetLayoutCreateInfo result = createInfo;
  auto newBindings = new VkDescriptorSetLayoutBinding[createInfo.bindingCount];
  for (uint32_t i = 0; i < createInfo.bindingCount; i++) {
    newBindings[i] = DeepCopy(createInfo.pBindings[i]);
  }
  result.pBindings = newBindings;
  return result;
}

VkFramebufferCreateInfo DeepCopy(const VkFramebufferCreateInfo &createInfo) {
  VkFramebufferCreateInfo result = createInfo;
  result.pAttachments =
      CopyArray(createInfo.pAttachments, createInfo.attachmentCount);
  return result;
}

VkGraphicsPipelineCreateInfo
DeepCopy(const VkGraphicsPipelineCreateInfo &createInfo) {
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

    // Copy pInputAssemblyState
    {
      auto inputAssemblyStateCreateInfo =
          new VkPipelineInputAssemblyStateCreateInfo();
      *inputAssemblyStateCreateInfo = *createInfo.pInputAssemblyState;
      result.pInputAssemblyState = inputAssemblyStateCreateInfo;
    }

    // Copy pVertexInputState
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

    result.pVertexInputState = vertexInputState;
  }

  // TODO: handle deep copying of other fields.
  return result;
}

VkPipelineLayoutCreateInfo
DeepCopy(const VkPipelineLayoutCreateInfo &createInfo) {
  VkPipelineLayoutCreateInfo result = createInfo;
  result.pSetLayouts =
      CopyArray(createInfo.pSetLayouts, createInfo.setLayoutCount);
  result.pPushConstantRanges = CopyArray(createInfo.pPushConstantRanges,
                                         createInfo.pushConstantRangeCount);
  return result;
}

VkPipelineShaderStageCreateInfo
DeepCopy(const VkPipelineShaderStageCreateInfo &createInfo) {
  VkPipelineShaderStageCreateInfo result = createInfo;
  // TODO: make copy deep.
  return result;
}

VkRenderPassCreateInfo DeepCopy(const VkRenderPassCreateInfo &createInfo) {
  VkRenderPassCreateInfo result = createInfo;
  result.pAttachments =
      CopyArray(createInfo.pAttachments, createInfo.attachmentCount);
  VkSubpassDescription *newSubpasses =
      new VkSubpassDescription[createInfo.subpassCount];
  for (uint32_t i = 0; i < createInfo.subpassCount; i++) {
    newSubpasses[i] = DeepCopy(createInfo.pSubpasses[i]);
  }
  result.pSubpasses = newSubpasses;
  result.pDependencies =
      CopyArray(createInfo.pDependencies, createInfo.dependencyCount);
  return result;
}

VkRenderPassBeginInfo *DeepCopy(VkRenderPassBeginInfo const *pRenderPassBegin) {
  auto result = new VkRenderPassBeginInfo;
  *result = *pRenderPassBegin;
  result->pClearValues = CopyArray(pRenderPassBegin->pClearValues,
                                   pRenderPassBegin->clearValueCount);
  return result;
}

VkShaderModuleCreateInfo DeepCopy(const VkShaderModuleCreateInfo &createInfo) {
  VkShaderModuleCreateInfo result = createInfo;
  auto newCode = new uint32_t[createInfo.codeSize];
  memcpy(newCode, createInfo.pCode, createInfo.codeSize);
  result.pCode = newCode;
  return result;
}

VkSubpassDescription DeepCopy(const VkSubpassDescription &subpassDescription) {
  auto result = subpassDescription;
  result.pInputAttachments = CopyArray(subpassDescription.pInputAttachments,
                                       subpassDescription.inputAttachmentCount);
  result.pColorAttachments = CopyArray(subpassDescription.pColorAttachments,
                                       subpassDescription.colorAttachmentCount);
  if (subpassDescription.pResolveAttachments) {
    result.pResolveAttachments =
        CopyArray(subpassDescription.pResolveAttachments,
                  subpassDescription.colorAttachmentCount);
  }
  // TODO: deep copying of pDepthStencilAttachment not yet handled
  result.pPreserveAttachments =
      CopyArray(subpassDescription.pPreserveAttachments,
                subpassDescription.preserveAttachmentCount);
  return result;
}

} // namespace graphicsfuzz_amber_scoop
