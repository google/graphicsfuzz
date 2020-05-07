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

#include "amber_scoop/layer_impl.h"

#include <iostream>
#include <memory>
#include <tuple>
#include <unordered_map>
#include <vector>

#include "amber_scoop/buffer_copy.h"
#include "amber_scoop/vk_deep_copy.h"
#include "amber_scoop/vulkan_formats.h"
#include "common/spirv_util.h"
#include "spirv-tools/libspirv.hpp"

namespace graphicsfuzz_amber_scoop {

#define DEBUG_AMBER_SCOOP 0

#if DEBUG_AMBER_SCOOP
#define DEBUG_LAYER(F) std::cout << "In " << #F << std::endl
#else
#define DEBUG_LAYER(F)
#endif

std::string getDescriptorTypeString(VkDescriptorType descriptorType);

void readComponentsFromBufferAndWriteToStrStream(char *buffer,
                                                 vkf::VulkanFormat format,
                                                 std::stringstream &bufStr);

std::unordered_map<VkCommandBuffer, std::vector<std::shared_ptr<Cmd>>>
    commandBuffers;

void AddCommand(VkCommandBuffer command_buffer, std::unique_ptr<Cmd> command) {
  if (commandBuffers.count(command_buffer) == 0) {
    std::vector<std::shared_ptr<Cmd>> empty_cmds;
    commandBuffers.insert({command_buffer, std::move(empty_cmds)});
  }
  commandBuffers.at(command_buffer).push_back(std::move(command));
}

std::unordered_map<VkBuffer, VkBufferCreateInfo> buffers;
std::unordered_map<VkDescriptorSet, VkDescriptorSetLayout> descriptorSets;
std::unordered_map<VkDescriptorSetLayout, VkDescriptorSetLayoutCreateInfo>
    descriptorSetLayouts;
std::unordered_map<VkFramebuffer, VkFramebufferCreateInfo> framebuffers;
std::unordered_map<VkPipeline, VkGraphicsPipelineCreateInfo> graphicsPipelines;
std::unordered_map<VkPipelineLayout, VkPipelineLayoutCreateInfo>
    pipelineLayouts;
std::unordered_map<VkRenderPass, VkRenderPassCreateInfo> renderPasses;
std::unordered_map<VkShaderModule, VkShaderModuleCreateInfo> shaderModules;
std::unordered_map<VkDescriptorSet,
                   std::unordered_map<uint32_t, VkDescriptorBufferInfo>>
    descriptorSetToBindingBuffer;
std::unordered_map<VkDescriptorSet,
                   std::unordered_map<uint32_t, VkDescriptorImageInfo>>
    descriptorSetToBindingImageAndSampler;

std::unordered_map<VkCommandPool, uint32_t> commandPoolToQueueFamilyIndex;

std::string GetDisassembly(VkShaderModule shaderModule) {
  auto createInfo = shaderModules.at(shaderModule);
  auto maybeTargetEnv = graphicsfuzz_vulkan_layers::GetTargetEnvFromSpirvBinary(
      createInfo.pCode[1]);
  assert(maybeTargetEnv.first && "SPIR-V version should be valid.");
  spvtools::SpirvTools tools(maybeTargetEnv.second);
  assert(tools.IsValid() && "Invalid tools object created.");
  // |createInfo.codeSize| gives the size in bytes; convert it to words.
  const uint32_t code_size_in_words =
      static_cast<uint32_t>(createInfo.codeSize) / 4;
  std::vector<uint32_t> binary;
  binary.assign(createInfo.pCode, createInfo.pCode + code_size_in_words);
  std::string disassembly;
  tools.Disassemble(binary, &disassembly, SPV_BINARY_TO_TEXT_OPTION_INDENT);
  return disassembly;
}

VkResult vkAllocateDescriptorSets(
    PFN_vkAllocateDescriptorSets next, VkDevice device,
    VkDescriptorSetAllocateInfo const *pAllocateInfo,
    VkDescriptorSet *pDescriptorSets) {
  DEBUG_LAYER(vkAllocateDescriptorSets);
  auto result = next(device, pAllocateInfo, pDescriptorSets);
  if (result == VK_SUCCESS) {
    for (uint32_t i = 0; i < pAllocateInfo->descriptorSetCount; i++) {
      descriptorSets.insert(
          {pDescriptorSets[i], pAllocateInfo->pSetLayouts[i]});
    }
  }
  return result;
}

VkResult vkCreateCommandPool(PFN_vkCreateCommandPool next, VkDevice device,
                             VkCommandPoolCreateInfo const *pCreateInfo,
                             AllocationCallbacks pAllocator,
                             VkCommandPool *pCommandPool) {
  DEBUG_LAYER(vkCreateCommandPool);
  auto result = next(device, pCreateInfo, pAllocator, pCommandPool);
  if (result == VK_SUCCESS) {
    commandPoolToQueueFamilyIndex.insert(
        {*pCommandPool, pCreateInfo->queueFamilyIndex});
  }
  return result;
}

void vkCmdBeginRenderPass(PFN_vkCmdBeginRenderPass next,
                          VkCommandBuffer commandBuffer,
                          VkRenderPassBeginInfo const *pRenderPassBegin,
                          VkSubpassContents contents) {
  DEBUG_LAYER(vkCmdBeginRenderPass);
  next(commandBuffer, pRenderPassBegin, contents);
  AddCommand(commandBuffer,
             std::make_unique<CmdBeginRenderPass>(pRenderPassBegin, contents));
}

void vkCmdBindDescriptorSets(PFN_vkCmdBindDescriptorSets next,
                             VkCommandBuffer commandBuffer,
                             VkPipelineBindPoint pipelineBindPoint,
                             VkPipelineLayout layout, uint32_t firstSet,
                             uint32_t descriptorSetCount,
                             VkDescriptorSet const *pDescriptorSets,
                             uint32_t dynamicOffsetCount,
                             uint32_t const *pDynamicOffsets) {
  DEBUG_LAYER(vkCmdBindDescriptorSets);
  next(commandBuffer, pipelineBindPoint, layout, firstSet, descriptorSetCount,
       pDescriptorSets, dynamicOffsetCount, pDynamicOffsets);
  AddCommand(commandBuffer,
             std::make_unique<CmdBindDescriptorSets>(
                 pipelineBindPoint, layout, firstSet, descriptorSetCount,
                 pDescriptorSets, dynamicOffsetCount, pDynamicOffsets));
}

void vkCmdBindIndexBuffer(PFN_vkCmdBindIndexBuffer next,
                          VkCommandBuffer commandBuffer, VkBuffer buffer,
                          VkDeviceSize offset, VkIndexType indexType) {
  DEBUG_LAYER(vkCmdBindDescriptorSets);
  next(commandBuffer, buffer, offset, indexType);
  AddCommand(commandBuffer,
             std::make_unique<CmdBindIndexBuffer>(buffer, offset, indexType));
}
void vkCmdBindPipeline(PFN_vkCmdBindPipeline next,
                       VkCommandBuffer commandBuffer,
                       VkPipelineBindPoint pipelineBindPoint,
                       VkPipeline pipeline) {
  DEBUG_LAYER(vkCmdBindPipeline);
  next(commandBuffer, pipelineBindPoint, pipeline);
  AddCommand(commandBuffer,
             std::make_unique<CmdBindPipeline>(pipelineBindPoint, pipeline));
}

void vkCmdBindVertexBuffers(PFN_vkCmdBindVertexBuffers next,
                            VkCommandBuffer commandBuffer,
                            uint32_t firstBinding, uint32_t bindingCount,
                            VkBuffer const *pBuffers,
                            VkDeviceSize const *pOffsets) {
  DEBUG_LAYER(vkCmdBindVertexBuffers);
  next(commandBuffer, firstBinding, bindingCount, pBuffers, pOffsets);
  AddCommand(commandBuffer,
             std::make_unique<CmdBindVertexBuffers>(firstBinding, bindingCount,
                                                    pBuffers, pOffsets));
}

void vkCmdCopyBuffer(PFN_vkCmdCopyBuffer next, VkCommandBuffer commandBuffer,
                     VkBuffer srcBuffer, VkBuffer dstBuffer,
                     uint32_t regionCount, VkBufferCopy const *pRegions) {
  DEBUG_LAYER(vkCmdCopyBuffer);
  next(commandBuffer, srcBuffer, dstBuffer, regionCount, pRegions);
  AddCommand(commandBuffer, std::make_unique<CmdCopyBuffer>(
                                srcBuffer, dstBuffer, regionCount, pRegions));
}

void vkCmdCopyBufferToImage(PFN_vkCmdCopyBufferToImage next,
                            VkCommandBuffer commandBuffer, VkBuffer srcBuffer,
                            VkImage dstImage, VkImageLayout dstImageLayout,
                            uint32_t regionCount,
                            VkBufferImageCopy const *pRegions) {
  DEBUG_LAYER(vkCmdCopyBufferToImage);
  next(commandBuffer, srcBuffer, dstImage, dstImageLayout, regionCount,
       pRegions);
  AddCommand(commandBuffer,
             std::make_unique<CmdCopyBufferToImage>(
                 srcBuffer, dstImage, dstImageLayout, regionCount, pRegions));
}

void vkCmdDraw(PFN_vkCmdDraw next, VkCommandBuffer commandBuffer,
               uint32_t vertexCount, uint32_t instanceCount,
               uint32_t firstVertex, uint32_t firstInstance) {
  DEBUG_LAYER(vkCmdDraw);
  next(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
  AddCommand(commandBuffer,
             std::make_unique<CmdDraw>(vertexCount, instanceCount, firstVertex,
                                       firstInstance));
}

void vkCmdDrawIndexed(PFN_vkCmdDrawIndexed next, VkCommandBuffer commandBuffer,
                      uint32_t indexCount, uint32_t instanceCount,
                      uint32_t firstIndex, int32_t vertexOffset,
                      uint32_t firstInstance) {
  DEBUG_LAYER(vkCmdDrawIndexed);
  next(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset,
       firstInstance);
  AddCommand(commandBuffer, std::make_unique<CmdDrawIndexed>(
                                indexCount, instanceCount, firstIndex,
                                vertexOffset, firstInstance));
}

void vkCmdPipelineBarrier(
    PFN_vkCmdPipelineBarrier next, VkCommandBuffer commandBuffer,
    VkPipelineStageFlags srcStageMask, VkPipelineStageFlags dstStageMask,
    VkDependencyFlags dependencyFlags, uint32_t memoryBarrierCount,
    VkMemoryBarrier const *pMemoryBarriers, uint32_t bufferMemoryBarrierCount,
    VkBufferMemoryBarrier const *pBufferMemoryBarriers,
    uint32_t imageMemoryBarrierCount,
    VkImageMemoryBarrier const *pImageMemoryBarriers) {
  DEBUG_LAYER(vkCmdPipelineBarrier);
  next(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
       memoryBarrierCount, pMemoryBarriers, bufferMemoryBarrierCount,
       pBufferMemoryBarriers, imageMemoryBarrierCount, pImageMemoryBarriers);

  auto memoryBarriers =
      CopyArray<VkMemoryBarrier>(pMemoryBarriers, memoryBarrierCount);

  AddCommand(commandBuffer,
             std::make_unique<CmdPipelineBarrier>(
                 srcStageMask, dstStageMask, dependencyFlags,
                 memoryBarrierCount, memoryBarriers, bufferMemoryBarrierCount,
                 CopyArray<VkBufferMemoryBarrier>(pBufferMemoryBarriers,
                                                  bufferMemoryBarrierCount),
                 imageMemoryBarrierCount,
                 CopyArray<VkImageMemoryBarrier>(pImageMemoryBarriers,
                                                 imageMemoryBarrierCount)));
}

VkResult vkCreateBuffer(PFN_vkCreateBuffer next, VkDevice device,
                        VkBufferCreateInfo const *pCreateInfo,
                        AllocationCallbacks pAllocator, VkBuffer *pBuffer) {
  DEBUG_LAYER(vkCreateBuffer);

  VkBufferCreateInfo createInfo = *pCreateInfo;
  // Allow vertex/index/uniform buffer to be used as transfer source buffer.
  // Required if the buffer data needs to be copied from the buffer.
  if (createInfo.usage & VK_BUFFER_USAGE_VERTEX_BUFFER_BIT ||
      createInfo.usage & VK_BUFFER_USAGE_INDEX_BUFFER_BIT ||
      createInfo.usage & VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT) {
    createInfo.usage |= VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
  }

  auto result = next(device, &createInfo, pAllocator, pBuffer);
  if (result == VK_SUCCESS) {
    buffers.insert({*pBuffer, DeepCopy(createInfo)});
  }
  return result;
}

VkResult vkCreateDescriptorSetLayout(
    PFN_vkCreateDescriptorSetLayout next, VkDevice device,
    VkDescriptorSetLayoutCreateInfo const *pCreateInfo,
    AllocationCallbacks pAllocator, VkDescriptorSetLayout *pSetLayout) {
  DEBUG_LAYER(vkCreateDescriptorSetLayout);
  auto result = next(device, pCreateInfo, pAllocator, pSetLayout);
  if (result == VK_SUCCESS) {
    descriptorSetLayouts.insert({*pSetLayout, DeepCopy(*pCreateInfo)});
  }
  return result;
}

VkResult vkCreateFramebuffer(PFN_vkCreateFramebuffer next, VkDevice device,
                             VkFramebufferCreateInfo const *pCreateInfo,
                             AllocationCallbacks pAllocator,
                             VkFramebuffer *pFramebuffer) {
  DEBUG_LAYER(vkCreateFramebuffer);
  auto result = next(device, pCreateInfo, pAllocator, pFramebuffer);
  if (result == VK_SUCCESS) {
    framebuffers.insert({*pFramebuffer, DeepCopy(*pCreateInfo)});
  }
  return result;
}

VkResult vkCreateGraphicsPipelines(
    PFN_vkCreateGraphicsPipelines next, VkDevice device,
    VkPipelineCache pipelineCache, uint32_t createInfoCount,
    VkGraphicsPipelineCreateInfo const *pCreateInfos,
    AllocationCallbacks pAllocator, VkPipeline *pPipelines) {
  DEBUG_LAYER(vkCreateGraphicsPipelines);
  auto result = next(device, pipelineCache, createInfoCount, pCreateInfos,
                     pAllocator, pPipelines);
  if (result == VK_SUCCESS) {
    for (uint32_t i = 0; i < createInfoCount; i++) {
      graphicsPipelines.insert({pPipelines[i], DeepCopy(pCreateInfos[i])});
    }
  }
  return result;
}

VkResult vkCreateImage(PFN_vkCreateImage next, VkDevice device,
                       VkImageCreateInfo const *pCreateInfo,
                       AllocationCallbacks pAllocator, VkImage *pImage) {
  auto result = next(device, pCreateInfo, pAllocator, pImage);
  return result;
}

VkResult vkCreatePipelineLayout(PFN_vkCreatePipelineLayout next,
                                VkDevice device,
                                VkPipelineLayoutCreateInfo const *pCreateInfo,
                                AllocationCallbacks pAllocator,
                                VkPipelineLayout *pPipelineLayout) {
  auto result = next(device, pCreateInfo, pAllocator, pPipelineLayout);
  if (result == VK_SUCCESS) {
    pipelineLayouts.insert({*pPipelineLayout, DeepCopy(*pCreateInfo)});
  }
  return result;
}

VkResult vkCreateRenderPass(PFN_vkCreateRenderPass next, VkDevice device,
                            VkRenderPassCreateInfo const *pCreateInfo,
                            AllocationCallbacks pAllocator,
                            VkRenderPass *pRenderPass) {
  DEBUG_LAYER(vkCreateRenderPass);
  auto result = next(device, pCreateInfo, pAllocator, pRenderPass);
  if (result == VK_SUCCESS) {
    renderPasses.insert({*pRenderPass, DeepCopy(*pCreateInfo)});
  }
  return result;
}

VkResult vkCreateShaderModule(PFN_vkCreateShaderModule next, VkDevice device,
                              VkShaderModuleCreateInfo const *pCreateInfo,
                              AllocationCallbacks pAllocator,
                              VkShaderModule *pShaderModule) {
  DEBUG_LAYER(vkCreateShaderModule);
  auto result = next(device, pCreateInfo, pAllocator, pShaderModule);
  if (result == VK_SUCCESS) {
    shaderModules.insert({*pShaderModule, DeepCopy(*pCreateInfo)});
  }
  return result;
}
void vkGetPhysicalDeviceMemoryProperties(
    PFN_vkGetPhysicalDeviceMemoryProperties next,
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceMemoryProperties *pMemoryProperties) {
  DEBUG_LAYER(vkGetPhysicalDeviceMemoryProperties);
  next(physicalDevice, pMemoryProperties);
}

struct IndexBufferBinding {
  VkBuffer buffer;
  VkDeviceSize offset;
  VkIndexType indexType;
};

struct DrawCallStateTracker {
  bool graphicsPipelineIsBound = false;
  VkPipeline boundGraphicsPipeline = nullptr;
  VkRenderPassBeginInfo *currentRenderPass = nullptr;
  uint32_t currentSubpass = 0;
  VkCommandBuffer commandBuffer;
  VkQueue queue;
  std::unordered_map<uint32_t, VkDescriptorSet> boundGraphicsDescriptorSets;

  // TODO: also track buffer offsets
  std::unordered_map<uint32_t, VkBuffer> boundVertexBuffers;
  std::vector<std::shared_ptr<CmdPipelineBarrier>> pipelineBarriers;
  IndexBufferBinding boundIndexBuffer = {};
};

void HandleDrawCall(const DrawCallStateTracker &drawCallStateTracker,
                    uint32_t indexCount = 0);

VkResult vkQueueSubmit(PFN_vkQueueSubmit next, VkQueue queue,
                       uint32_t submitCount, VkSubmitInfo const *pSubmits,
                       VkFence fence) {
  DEBUG_LAYER(vkQueueSubmit);

  for (uint32_t submitIndex = 0; submitIndex < submitCount; submitIndex++) {
    for (uint32_t commandBufferIndex = 0;
         commandBufferIndex < pSubmits[submitIndex].commandBufferCount;
         commandBufferIndex++) {
      auto commandBuffer =
          pSubmits[submitIndex].pCommandBuffers[commandBufferIndex];

      DrawCallStateTracker drawCallStateTracker = {};

      if (!commandBuffers.count(commandBuffer)) {
        continue;
      }

      drawCallStateTracker.commandBuffer = commandBuffer;
      drawCallStateTracker.queue = queue;

      for (auto &cmd : commandBuffers.at(commandBuffer)) {
        if (auto cmdBeginRenderPass = cmd->AsBeginRenderPass()) {
          drawCallStateTracker.currentRenderPass =
              cmdBeginRenderPass->pRenderPassBegin_;
          drawCallStateTracker.currentSubpass = 0;
        } else if (auto cmdBindDescriptorSets = cmd->AsBindDescriptorSets()) {
          if (cmdBindDescriptorSets->pipelineBindPoint_ ==
              VK_PIPELINE_BIND_POINT_GRAPHICS) {
            for (uint32_t descriptorSetOffset = 0;
                 descriptorSetOffset <
                 cmdBindDescriptorSets->descriptorSetCount_;
                 descriptorSetOffset++) {
              drawCallStateTracker.boundGraphicsDescriptorSets.insert(
                  {cmdBindDescriptorSets->firstSet_ + descriptorSetOffset,
                   cmdBindDescriptorSets
                       ->pDescriptorSets_[descriptorSetOffset]});
            }
          }
        } else if (auto cmdBindIndexBuffer = cmd->AsBindIndexBuffer()) {
          drawCallStateTracker.boundIndexBuffer.buffer =
              cmdBindIndexBuffer->buffer_;
          drawCallStateTracker.boundIndexBuffer.offset =
              cmdBindIndexBuffer->offset_;
          drawCallStateTracker.boundIndexBuffer.indexType =
              cmdBindIndexBuffer->indexType_;
        } else if (auto cmdBindPipeline = cmd->AsBindPipeline()) {
          switch (cmdBindPipeline->pipelineBindPoint_) {
            case VK_PIPELINE_BIND_POINT_GRAPHICS:
              drawCallStateTracker.graphicsPipelineIsBound = true;
              drawCallStateTracker.boundGraphicsPipeline =
                  cmdBindPipeline->pipeline_;
              break;
            default:
              // Not considering other pipelines now.
              break;
          }
        } else if (auto cmdBindVertexBuffers = cmd->AsBindVertexBuffers()) {
          for (uint32_t bindingIdx = 0;
               bindingIdx < cmdBindVertexBuffers->bindingCount_; bindingIdx++) {
            drawCallStateTracker.boundVertexBuffers.insert(
                {bindingIdx + cmdBindVertexBuffers->firstBinding_,
                 cmdBindVertexBuffers
                     ->pBuffers_[bindingIdx +
                                 cmdBindVertexBuffers->firstBinding_]});
          }
        } else if (auto cmdCopyBuffer = cmd->AsCopyBuffer()) {
          // TODO: track buffer copies?
        } else if (auto cmdCopyBufferToImage = cmd->AsCopyBufferToImage()) {
          // TODO: not implemented.
        } else if (auto cmdDraw = cmd->AsDraw()) {
          HandleDrawCall(drawCallStateTracker);
        } else if (auto cmdDrawIndexed = cmd->AsDrawIndexed()) {
          HandleDrawCall(drawCallStateTracker, cmdDrawIndexed->indexCount_);
        } else if (auto cmdPipelineBarrier = cmd->AsPipelineBarrier()) {
          drawCallStateTracker.pipelineBarriers.push_back(
              std::make_shared<CmdPipelineBarrier>(*cmdPipelineBarrier));
        } else {
          throw std::runtime_error("Unknown command.");
        }
      }
    }
  }
  return next(queue, submitCount, pSubmits, fence);
}

void vkUpdateDescriptorSets(PFN_vkUpdateDescriptorSets next, VkDevice device,
                            uint32_t descriptorWriteCount,
                            VkWriteDescriptorSet const *pDescriptorWrites,
                            uint32_t descriptorCopyCount,
                            VkCopyDescriptorSet const *pDescriptorCopies) {
  DEBUG_LAYER(vkUpdateDescriptorSets);
  next(device, descriptorWriteCount, pDescriptorWrites, descriptorCopyCount,
       pDescriptorCopies);
  assert(descriptorCopyCount == 0 && "Not handling descriptor copies yet.");
  for (uint32_t i = 0; i < descriptorWriteCount; i++) {
    auto writeDescriptorSet = pDescriptorWrites[i];
    assert(writeDescriptorSet.dstArrayElement == 0);
    assert(writeDescriptorSet.descriptorCount == 1);

    switch (writeDescriptorSet.descriptorType) {
      case VK_DESCRIPTOR_TYPE_SAMPLER:
      case VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER:
      case VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE:
      case VK_DESCRIPTOR_TYPE_STORAGE_IMAGE:
      case VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT: {
        // pImageInfo must be a valid pointer to an array of descriptorCount
        // valid VkDescriptorImageInfo structures
        if (!descriptorSetToBindingImageAndSampler.count(
                writeDescriptorSet.dstSet)) {
          descriptorSetToBindingImageAndSampler.insert(
              {writeDescriptorSet.dstSet, {}});
        }
        auto &bindingToImage =
            descriptorSetToBindingImageAndSampler.at(writeDescriptorSet.dstSet);
        bindingToImage.insert(
            {writeDescriptorSet.dstBinding, writeDescriptorSet.pImageInfo[0]});
        break;
      }
      case VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER:
      case VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER:
        // pTexelBufferView must be a valid pointer to an array of
        // descriptorCount valid VkBufferView handles
        break;
      case VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER:
      case VK_DESCRIPTOR_TYPE_STORAGE_BUFFER:
      case VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC:
      case VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC: {
        // pBufferInfo must be a valid pointer to an array of descriptorCount
        // valid VkDescriptorBufferInfo structures
        if (!descriptorSetToBindingBuffer.count(writeDescriptorSet.dstSet)) {
          descriptorSetToBindingBuffer.insert({writeDescriptorSet.dstSet, {}});
        }
        auto &bindingToBuffer =
            descriptorSetToBindingBuffer.at(writeDescriptorSet.dstSet);
        bindingToBuffer.insert(
            {writeDescriptorSet.dstBinding, writeDescriptorSet.pBufferInfo[0]});
        break;
      }
      default:
        throw std::runtime_error("Should be unreachable.");
        break;
    }
  }
}

void HandleDrawCall(const DrawCallStateTracker &drawCallStateTracker,
                    uint32_t indexCount) {
  if (!drawCallStateTracker.graphicsPipelineIsBound) {
    return;
  }

  assert(drawCallStateTracker.currentRenderPass);

  VkShaderModule vertexShader = nullptr;
  VkShaderModule fragmentShader = nullptr;
  auto graphicsPipelineCreateInfo =
      graphicsPipelines.at(drawCallStateTracker.boundGraphicsPipeline);
  for (uint32_t stageIndex = 0;
       stageIndex < graphicsPipelineCreateInfo.stageCount; stageIndex++) {
    auto stageCreateInfo = graphicsPipelineCreateInfo.pStages[stageIndex];
    if (stageCreateInfo.stage == VK_SHADER_STAGE_VERTEX_BIT) {
      vertexShader = stageCreateInfo.module;
    } else if (stageCreateInfo.stage == VK_SHADER_STAGE_FRAGMENT_BIT) {
      fragmentShader = stageCreateInfo.module;
    } else {
      throw std::runtime_error("Not handled.");
    }
  }
  assert(vertexShader && "Vertex shader required for graphics pipeline.");
  assert(fragmentShader && "Fragment shader required for graphics pipeline.");

  std::cout << "#!amber" << std::endl << std::endl;

  std::cout << "SHADER vertex vertex_shader SPIRV-ASM" << std::endl;
  std::cout << GetDisassembly(vertexShader);
  std::cout << "END" << std::endl << std::endl;

  std::cout << "SHADER fragment fragment_shader SPIRV-ASM" << std::endl;
  std::cout << GetDisassembly(fragmentShader);
  std::cout << "END" << std::endl << std::endl;

  std::stringstream bufferDeclarationStringStream;
  std::stringstream descriptorSetBindingStringStream;
  std::stringstream framebufferAttachmentStringStream;
  std::stringstream graphicsPipelineStringStream;

  for (const auto &vertexBufferBinding :
       drawCallStateTracker.boundVertexBuffers) {
    auto buffer = buffers.at(vertexBufferBinding.second);
    assert(buffer.usage & VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);

    auto bindingDescription =
        graphicsPipelineCreateInfo.pVertexInputState
            ->pVertexBindingDescriptions[vertexBufferBinding.first];

    assert(bindingDescription.inputRate == VK_VERTEX_INPUT_RATE_VERTEX &&
           "inputRate not implemented");

    VkBuffer vertexBuffer = vertexBufferBinding.second;

    auto commandPool =
        GetGlobalContext()
            .GetVkCommandBufferData(drawCallStateTracker.commandBuffer)
            ->command_pool;
    auto queueFamilyIndex = commandPoolToQueueFamilyIndex.at(commandPool);
    BufferCopy vertexBufferCopy = BufferCopy();
    void *mappedVertexBufferMemory;

    // Check if there is pipeline barriers for vertex buffer
    std::vector<std::shared_ptr<CmdPipelineBarrier>>
        vertexBufferPipelineBarriers;
    for (const auto &barrier : drawCallStateTracker.pipelineBarriers) {
      if (barrier->dstStageMask_ & VK_PIPELINE_STAGE_VERTEX_INPUT_BIT) {
        vertexBufferPipelineBarriers.push_back(barrier);
        break;
      }
    }

    vertexBufferCopy.copyBuffer(drawCallStateTracker.queue, queueFamilyIndex,
                                vertexBufferPipelineBarriers, vertexBuffer,
                                buffer.size, &mappedVertexBufferMemory);

    // Create string stream for every location
    std::vector<std::shared_ptr<std::stringstream>> bufferDeclStrings;
    for (uint32_t location = 0;
         location < graphicsPipelineCreateInfo.pVertexInputState
                        ->vertexAttributeDescriptionCount;
         location++) {
      auto description = graphicsPipelineCreateInfo.pVertexInputState
                             ->pVertexAttributeDescriptions[location];

      std::stringstream bufferName;
      bufferName << "vert_" << vertexBufferBinding.first << "_" << location;

      graphicsPipelineStringStream << "  VERTEX_DATA " << bufferName.str()
                                   << " LOCATION " << location << std::endl;

      auto strStream = std::make_shared<std::stringstream>();
      *strStream << "BUFFER " << bufferName.str() << " DATA_TYPE "
                 << vkf::VkFormatToVulkanFormat(description.format).name
                 << " DATA\n"
                 << "  ";
      bufferDeclStrings.push_back(strStream);
    }

    // Go through all elements in the buffer
    for (uint32_t bufferOffset = 0; bufferOffset < buffer.size;
         bufferOffset += bindingDescription.stride) {
      auto readPtr = (char *)mappedVertexBufferMemory + bufferOffset;

      for (uint32_t location = 0;
           location < graphicsPipelineCreateInfo.pVertexInputState
                          ->vertexAttributeDescriptionCount;
           location++) {
        auto description = graphicsPipelineCreateInfo.pVertexInputState
                               ->pVertexAttributeDescriptions[location];

        vkf::VulkanFormat format =
            vkf::VkFormatToVulkanFormat(description.format);

        readComponentsFromBufferAndWriteToStrStream(
            readPtr + description.offset, format,
            *bufferDeclStrings.at(location));
      }
    }
    vertexBufferCopy.freeResources();

    // End all buffer declaration streams and combine them to one stream.
    for (const auto &stream : bufferDeclStrings) {
      *stream << std::endl << "END" << std::endl << std::endl;
      bufferDeclarationStringStream << (*stream).str();
    }
  }

  // Declare index buffer (if used)
  if (indexCount > 0) {
    auto buffer = buffers.at(drawCallStateTracker.boundIndexBuffer.buffer);
    VkBuffer indexBuffer = drawCallStateTracker.boundIndexBuffer.buffer;

    auto commandPool =
        GetGlobalContext()
            .GetVkCommandBufferData(drawCallStateTracker.commandBuffer)
            ->command_pool;
    auto queueFamilyIndex = commandPoolToQueueFamilyIndex.at(commandPool);
    void *mappedIndexBufferMemory;

    std::vector<std::shared_ptr<CmdPipelineBarrier>>
        vertexBufferPipelineBarriers;
    // Check if there is pipeline barriers for index buffer
    for (const auto &barrier : drawCallStateTracker.pipelineBarriers) {
      if (barrier->dstStageMask_ & VK_PIPELINE_STAGE_VERTEX_INPUT_BIT) {
        vertexBufferPipelineBarriers.push_back(barrier);
        break;
      }
    }

    BufferCopy indexBufferCopy = BufferCopy();
    indexBufferCopy.copyBuffer(drawCallStateTracker.queue, queueFamilyIndex,
                               vertexBufferPipelineBarriers, indexBuffer,
                               buffer.size, &mappedIndexBufferMemory);

    graphicsPipelineStringStream << "  INDEX_DATA index_buffer" << std::endl;

    // Amber supports only 32-bit indices. 16-bit indices will be used as
    // 32-bit.
    bufferDeclarationStringStream << "BUFFER index_buffer DATA_TYPE uint32 ";
    bufferDeclarationStringStream << "DATA " << std::endl << "  ";

    // 16-bit indices
    if (drawCallStateTracker.boundIndexBuffer.indexType ==
        VK_INDEX_TYPE_UINT16) {
      auto ptr = (uint16_t *)mappedIndexBufferMemory;
      for (uint32_t indexIdx = 0; indexIdx < indexCount; indexIdx++) {
        bufferDeclarationStringStream << ptr[indexIdx] << " ";
      }
    }
    // 32-bit indices
    else if (drawCallStateTracker.boundIndexBuffer.indexType ==
             VK_INDEX_TYPE_UINT32) {
      auto ptr = (uint32_t *)mappedIndexBufferMemory;
      for (uint32_t indexIdx = 0; indexIdx < indexCount; indexIdx++) {
        bufferDeclarationStringStream << ptr[indexIdx] << " ";
      }
    } else {
      throw std::runtime_error("Invalid indexType");
    }
    bufferDeclarationStringStream << std::endl
                                  << "END" << std::endl
                                  << std::endl;
  }

  for (const auto &descriptorSet :
       drawCallStateTracker.boundGraphicsDescriptorSets) {
    uint32_t descriptorSetNumber = descriptorSet.first;
    VkDescriptorSetLayoutCreateInfo layoutCreateInfo =
        descriptorSetLayouts.at(descriptorSets.at(descriptorSet.second));

    std::unordered_map<uint32_t, VkDescriptorBufferInfo> bindingAndBuffers;
    if (descriptorSetToBindingBuffer.count(descriptorSet.second)) {
      bindingAndBuffers = descriptorSetToBindingBuffer.at(descriptorSet.second);
    }
    for (const auto &bindingAndBuffer : bindingAndBuffers) {
      uint32_t bindingNumber = bindingAndBuffer.first;
      VkDescriptorBufferInfo bufferInfo = bindingAndBuffer.second;

      std::stringstream strstr;
      strstr << "buf_" << descriptorSetNumber << "_" << bindingNumber;
      std::string bufferName = strstr.str();

      VkBufferCreateInfo bufferCreateInfo = buffers.at(bufferInfo.buffer);

      bufferDeclarationStringStream << "BUFFER " << bufferName << " DATA_TYPE "
                                    << "uint8"
                                    << " DATA" << std::endl;
      bufferDeclarationStringStream << "  ";

      // TODO: implement buffer copying if vkCmdUpdateBuffer is used.

      const VkBuffer &descriptorBuffer = bufferInfo.buffer;

      auto commandPool =
          GetGlobalContext()
              .GetVkCommandBufferData(drawCallStateTracker.commandBuffer)
              ->command_pool;
      auto queueFamilyIndex = commandPoolToQueueFamilyIndex.at(commandPool);
      BufferCopy descriptorBufferCopy = BufferCopy();
      void *mappedDescriptorMemory;

      // Check if there is pipeline barriers for descriptor buffer
      std::vector<std::shared_ptr<CmdPipelineBarrier>> descriptorBufferBarriers;
      for (const auto &barrier : drawCallStateTracker.pipelineBarriers) {
        // Find all barriers where dstStage contains vertex shader.
        if (barrier->dstStageMask_ & VK_PIPELINE_STAGE_VERTEX_SHADER_BIT) {
          // Check if at least one of the buffer memory barriers has
          // VK_ACCESS_UNIFORM_READ_BIT set.
          for (uint32_t bufMemBarrierIdx = 0;
               bufMemBarrierIdx < barrier->bufferMemoryBarrierCount_;
               bufMemBarrierIdx++) {
            descriptorBufferBarriers.push_back(barrier);
            break;
          }
        }
      }

      descriptorBufferCopy.copyBuffer(
          drawCallStateTracker.queue, queueFamilyIndex,
          descriptorBufferBarriers, descriptorBuffer, bufferCreateInfo.size,
          &mappedDescriptorMemory);

      VkDeviceSize range = bufferInfo.range == VK_WHOLE_SIZE
                               ? bufferCreateInfo.size
                               : bufferInfo.range;

      auto *thePtr = (uint8_t *)mappedDescriptorMemory;
      for (int bidx = bufferInfo.offset; bidx < range; bidx++) {
        if (bidx > 0) {
          bufferDeclarationStringStream << " ";
        }
        bufferDeclarationStringStream << (uint32_t)thePtr[bidx];
      }

      bufferDeclarationStringStream << std::endl;
      bufferDeclarationStringStream << "END" << std::endl << std::endl;

      VkDescriptorSetLayoutBinding layoutBinding =
          layoutCreateInfo.pBindings[bindingNumber];
      VkDescriptorType descriptorType = layoutBinding.descriptorType;
      descriptorSetBindingStringStream
          << "  BIND BUFFER " << bufferName << " AS "
          << getDescriptorTypeString(descriptorType) << " DESCRIPTOR_SET "
          << descriptorSetNumber << " BINDING " << bindingNumber << std::endl;
    }

    if (descriptorSetToBindingImageAndSampler.count(descriptorSet.second)) {
      auto bindingAndImages =
          descriptorSetToBindingImageAndSampler.at(descriptorSet.second);
      for (auto bindingAndImage : bindingAndImages) {
        uint32_t bindingNumber = bindingAndImage.first;
        auto imageInfo = bindingAndImage.second;

        VkDescriptorSetLayoutBinding layoutBinding =
            layoutCreateInfo.pBindings[bindingNumber];
        VkDescriptorType descriptorType = layoutBinding.descriptorType;

        std::stringstream strstr;

        switch (descriptorType) {
          case VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE:
          case VK_DESCRIPTOR_TYPE_STORAGE_IMAGE:
          case VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER: {
            strstr << "img_" << descriptorSetNumber << "_" << bindingNumber;
            std::string imageName = strstr.str();

            descriptorSetBindingStringStream
                << "  BIND BUFFER " << imageName << " AS "
                << getDescriptorTypeString(descriptorType) << " DESCRIPTOR_SET "
                << descriptorSetNumber << " BINDING " << bindingNumber;

            if (descriptorType == VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
              descriptorSetBindingStringStream << " SAMPLER "
                                               << " TBD: sampler name";
            // TODO: implement BASE_MIP_LEVEL
            // https://github.com/google/amber/blob/master/docs/amber_script.md#pipeline-buffers

            bufferDeclarationStringStream << "BUFFER " << imageName
                                          << " FORMAT R8G8B8A8_UNORM"
                                          << " FILE texture.png" << std::endl;
            break;
          }
          case VK_DESCRIPTOR_TYPE_SAMPLER: {
            strstr << "sampler_" << descriptorSetNumber << "_" << bindingNumber;
            std::string samplerName = strstr.str();

            descriptorSetBindingStringStream
                << "  BIND SAMPLER " << samplerName << " DESCRIPTOR_SET "
                << descriptorSetNumber << " BINDING " << bindingNumber;

            bufferDeclarationStringStream << "SAMPLER " << samplerName
                                          << std::endl;
            break;
          }
          default:
            throw std::runtime_error("Unimplemented descriptor type");
        }
        descriptorSetBindingStringStream << std::endl;
      }
    }
  }

  // Depth
  if (graphicsPipelineCreateInfo.pDepthStencilState != nullptr ||
      graphicsPipelineCreateInfo.pRasterizationState->depthBiasEnable ||
      graphicsPipelineCreateInfo.pRasterizationState->depthClampEnable) {
    graphicsPipelineStringStream << "  DEPTH\n";

    if (graphicsPipelineCreateInfo.pDepthStencilState != nullptr) {
      auto depthState = graphicsPipelineCreateInfo.pDepthStencilState;
      graphicsPipelineStringStream
          << "    TEST " << (depthState->depthTestEnable ? "on" : "off") << "\n"
          << "    WRITE " << (depthState->depthWriteEnable ? "on" : "off")
          << "\n";
      graphicsPipelineStringStream << "    COMPARE_OP ";
      switch (depthState->depthCompareOp) {
        case VK_COMPARE_OP_NEVER:
          graphicsPipelineStringStream << "never";
          break;
        case VK_COMPARE_OP_LESS:
          graphicsPipelineStringStream << "less";
          break;
        case VK_COMPARE_OP_EQUAL:
          graphicsPipelineStringStream << "equal";
          break;
        case VK_COMPARE_OP_LESS_OR_EQUAL:
          graphicsPipelineStringStream << "less_or_equal";
          break;
        case VK_COMPARE_OP_GREATER:
          graphicsPipelineStringStream << "greater";
          break;
        case VK_COMPARE_OP_NOT_EQUAL:
          graphicsPipelineStringStream << "not_equal";
          break;
        case VK_COMPARE_OP_GREATER_OR_EQUAL:
          graphicsPipelineStringStream << "greater_or_equal";
          break;
        case VK_COMPARE_OP_ALWAYS:
          graphicsPipelineStringStream << "always";
          break;
        default:
          throw std::runtime_error("Invalid VK_COMPARE_OP");
      }
      graphicsPipelineStringStream << "\n";

      // Amber expects the values as float values
      graphicsPipelineStringStream << std::scientific;
      graphicsPipelineStringStream << "    BOUNDS min " << 1.0 << " max "
                                   << depthState->maxDepthBounds << "\n";
      graphicsPipelineStringStream << std::defaultfloat;
    }

    if (graphicsPipelineCreateInfo.pRasterizationState->depthClampEnable) {
      graphicsPipelineStringStream << "    CLAMP on\n";
    }

    if (graphicsPipelineCreateInfo.pRasterizationState->depthBiasEnable) {
      graphicsPipelineStringStream
          << "    BIAS constant "
          << graphicsPipelineCreateInfo.pRasterizationState
                 ->depthBiasConstantFactor
          << " clamp "
          << graphicsPipelineCreateInfo.pRasterizationState->depthBiasClamp
          << " slope "
          << graphicsPipelineCreateInfo.pRasterizationState
                 ->depthBiasSlopeFactor
          << "\n";
    }

    graphicsPipelineStringStream << "  END\n";  // DEPTH
  }

  // Create buffers for color attachments.
  VkRenderPassCreateInfo renderPassCreateInfo =
      renderPasses.at(drawCallStateTracker.currentRenderPass->renderPass);
  for (uint colorAttachment = 0;
       colorAttachment <
       renderPassCreateInfo.pSubpasses[drawCallStateTracker.currentSubpass]
           .colorAttachmentCount;
       colorAttachment++) {
    uint32_t attachmentID =
        renderPassCreateInfo.pSubpasses[drawCallStateTracker.currentSubpass]
            .pColorAttachments[colorAttachment]
            .attachment;
    vkf::VulkanFormat format = vkf::VkFormatToVulkanFormat(
        renderPassCreateInfo.pAttachments[attachmentID].format);

    bufferDeclarationStringStream << "BUFFER framebuffer_" << colorAttachment
                                  << " FORMAT B8G8R8A8_UNORM" << std::endl;
    // << format.name << std::endl;
    framebufferAttachmentStringStream
        << "  BIND BUFFER framebuffer_" << colorAttachment
        << " AS color LOCATION " << colorAttachment << std::endl;
  }

  // Create buffer for depth / stencil attachment.
  if (renderPassCreateInfo.pSubpasses[drawCallStateTracker.currentSubpass]
          .pDepthStencilAttachment) {
    uint32_t attachmentID =
        renderPassCreateInfo.pSubpasses[drawCallStateTracker.currentSubpass]
            .pDepthStencilAttachment->attachment;
    vkf::VulkanFormat format = vkf::VkFormatToVulkanFormat(
        renderPassCreateInfo.pAttachments[attachmentID].format);

    bufferDeclarationStringStream << "BUFFER depthstencil FORMAT "
                                  << format.name << std::endl;
    framebufferAttachmentStringStream
        << "  BIND BUFFER depthstencil AS depth_stencil" << std::endl;
  }

  std::cout << bufferDeclarationStringStream.str() << std::endl;

  std::cout << "PIPELINE graphics pipeline" << std::endl;
  std::cout << "  ATTACH vertex_shader" << std::endl;
  std::cout << "  ATTACH fragment_shader" << std::endl;

  // Polygon mode
  std::cout << "  POLYGON_MODE ";
  switch (graphicsPipelineCreateInfo.pRasterizationState->polygonMode) {
    case VkPolygonMode::VK_POLYGON_MODE_FILL:
      std::cout << "fill\n";
      break;
    case VkPolygonMode::VK_POLYGON_MODE_LINE:
      std::cout << "line\n";
      break;
    case VkPolygonMode::VK_POLYGON_MODE_POINT:
      std::cout << "point\n";
      break;
    default:
      throw std::runtime_error("Polygon mode not supported by amber.");
  }

  // Add definitions for pipeline
  std::cout << graphicsPipelineStringStream.str();

  VkFramebufferCreateInfo framebufferCreateInfo =
      framebuffers.at(drawCallStateTracker.currentRenderPass->framebuffer);
  std::cout << "  FRAMEBUFFER_SIZE " << framebufferCreateInfo.width << " "
            << framebufferCreateInfo.height << std::endl;
  std::cout << framebufferAttachmentStringStream.str();
  std::cout << descriptorSetBindingStringStream.str();

  std::cout << "END" << std::endl << std::endl;  // PIPELINE

  std::cout << "CLEAR_COLOR pipeline 0 0 0 255" << std::endl;
  std::cout << "CLEAR pipeline" << std::endl;

  const std::string &topology =
      topologies.at(graphicsPipelineCreateInfo.pInputAssemblyState->topology);

  if (indexCount > 0) {
    std::cout << "RUN pipeline DRAW_ARRAY AS " << topology << " INDEXED"
              << std::endl;
  } else {
    std::cout << "RUN pipeline DRAW_ARRAY AS " << topology << std::endl;
  }

  exit(0);
}

void readComponentsFromBufferAndWriteToStrStream(char *buffer,
                                                 vkf::VulkanFormat format,
                                                 std::stringstream &bufStr) {
  if (format.isPacked) {
    // Packed formats are 16 or 32 bits wide.
    if (format.width_bits == 16)
      bufStr << (uint32_t) * (uint16_t *)buffer << " ";
    else  // 32-bit
      bufStr << *(uint32_t *)buffer << " ";
  } else {
    for (uint8_t cIdx = 0; cIdx < format.component_count; cIdx++) {
      if (format.components[cIdx].isFloat()) {
        // TODO: implement 16-bit floats
        if (format.components[cIdx].num_bits == 32)
          bufStr << ((float *)buffer)[cIdx] << " ";
        else if (format.components[cIdx].num_bits == 64)
          bufStr << ((double *)buffer)[cIdx] << " ";
      } else if (format.components[cIdx].isUInt()) {
        if (format.components[cIdx].num_bits == 8)
          bufStr << (uint32_t)((uint8_t *)buffer)[cIdx] << " ";
        else if (format.components[cIdx].num_bits == 16)
          bufStr << (uint32_t)((uint16_t *)buffer)[cIdx] << " ";
        else if (format.components[cIdx].num_bits == 32)
          bufStr << ((uint32_t *)buffer)[cIdx] << " ";
        else if (format.components[cIdx].num_bits == 64)
          bufStr << ((uint64_t *)buffer)[cIdx] << " ";
        else
          throw std::runtime_error("Unsupported width.");
      } else if (format.components[cIdx].isSInt()) {
        if (format.components[cIdx].num_bits == 8)
          bufStr << (int32_t)((int8_t *)buffer)[cIdx] << " ";
        else if (format.components[cIdx].num_bits == 16)
          bufStr << (int32_t)((int16_t *)buffer)[cIdx] << " ";
        else if (format.components[cIdx].num_bits == 32)
          bufStr << ((int32_t *)buffer)[cIdx] << " ";
        else if (format.components[cIdx].num_bits == 64)
          bufStr << ((int64_t *)buffer)[cIdx] << " ";
        else
          throw std::runtime_error("Unsupported width.");
      } else {
        throw std::runtime_error("Unsupported format");
      }
    }
  }
}

std::string getDescriptorTypeString(VkDescriptorType descriptorType) {
  switch (descriptorType) {
    case VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER:
      return "combined_image_sampler";
    case VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE:
      return "sampled_image";
    case VK_DESCRIPTOR_TYPE_STORAGE_IMAGE:
      return "storage_image";
    case VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER:
      return "uniform";
    case VK_DESCRIPTOR_TYPE_STORAGE_BUFFER:
      return "storage";
    default:
      assert(false && "Unimplemented descriptor type");
      return "...";
  }
}

}  // namespace graphicsfuzz_amber_scoop
