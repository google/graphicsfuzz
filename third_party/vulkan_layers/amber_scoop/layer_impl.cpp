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

#include <cstring>
#include <fstream>
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

const char *GetDescriptorTypeString(VkDescriptorType descriptor_type);

void readComponentsFromBufferAndWriteToStrStream(uint8_t *buffer,
                                                 vkf::VulkanFormat format,
                                                 std::stringstream &bufStr);

const char *GetSamplerAddressModeString(VkSamplerAddressMode address_mode);

const char *GetSamplerBorderColorString(VkBorderColor border_color);

const char *GetSamplerFilterTypeString(VkFilter filter);

std::string CreateSpecializationString(
    const VkSpecializationMapEntry &specialization_map_entry,
    const void *specialization_data);

const char *GetStencilOpString(VkStencilOp stencil_op);

const char *GetCompareOpString(VkCompareOp compare_op);

/**
 * Container for per pipeline layout data.
 */
struct PipelineLayoutData {
  PipelineLayoutData(
      VkPipelineLayoutCreateInfo create_info,
      std::map<uint32_t, VkDescriptorSet> descriptor_set_bindings,
      uint32_t push_constants_size)
      : create_info_(create_info),
        descriptor_set_bindings_(std::move(descriptor_set_bindings)),
        push_constants_size_(push_constants_size) {}

  VkPipelineLayoutCreateInfo create_info_;
  std::map<uint32_t, VkDescriptorSet> descriptor_set_bindings_;
  uint32_t push_constants_size_;
};

struct DescriptorBufferBinding {
  uint32_t binding_number_;
  uint32_t dynamic_offset_;
  VkDescriptorBufferInfo descriptor_buffer_info_;
};

struct DescriptorSetData {
  explicit DescriptorSetData(
      VkDescriptorSetLayout descriptor_set_layout,
      const VkDescriptorSetLayoutCreateInfo descriptor_set_layout_create_info)
      : descriptor_set_layout_(descriptor_set_layout),
        descriptor_set_layout_create_info_(descriptor_set_layout_create_info) {}

  VkDescriptorSetLayout descriptor_set_layout_;
  VkDescriptorSetLayoutCreateInfo descriptor_set_layout_create_info_;
  std::vector<DescriptorBufferBinding> descriptor_buffer_bindings_ = {};
  std::unordered_map<uint32_t, VkDescriptorImageInfo>
      image_and_sampler_bindings_ = {};
};

std::unordered_map<VkCommandBuffer, std::vector<std::shared_ptr<Cmd>>>
    commandBuffers;

void AddCommand(VkCommandBuffer command_buffer, std::unique_ptr<Cmd> command) {
  if (commandBuffers.count(command_buffer) == 0) {
    std::vector<std::shared_ptr<Cmd>> empty_cmds;
    commandBuffers.insert({command_buffer, std::move(empty_cmds)});
  }
  commandBuffers.at(command_buffer).push_back(std::move(command));
}

int32_t capture_draw_call_number = -1;
int32_t current_draw_call_number = 0;

std::unordered_map<VkBuffer, VkBufferCreateInfo> buffers;
std::unordered_map<VkSampler, VkSamplerCreateInfo> samplers_;
std::unordered_map<VkDescriptorSet, DescriptorSetData> descriptor_sets_;
std::unordered_map<VkDescriptorSetLayout, VkDescriptorSetLayoutCreateInfo>
    descriptor_set_layouts_;
std::unordered_map<VkFramebuffer, VkFramebufferCreateInfo> framebuffers;
std::unordered_map<VkPipeline, VkGraphicsPipelineCreateInfo> graphicsPipelines;
std::unordered_map<VkPipelineLayout, PipelineLayoutData> pipeline_layouts;
std::unordered_map<VkRenderPass, VkRenderPassCreateInfo> renderPasses;
std::unordered_map<VkShaderModule, VkShaderModuleCreateInfo> shaderModules;
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
      descriptor_sets_.insert(
          {pDescriptorSets[i],
           DescriptorSetData(
               pAllocateInfo->pSetLayouts[i],
               descriptor_set_layouts_.at(pAllocateInfo->pSetLayouts[i]))});
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
  DEBUG_LAYER(vkCmdBindIndexBuffer);
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

void vkCmdPushConstants(PFN_vkCmdPushConstants next,
                        VkCommandBuffer commandBuffer, VkPipelineLayout layout,
                        VkShaderStageFlags stageFlags, uint32_t offset,
                        uint32_t size, void const *pValues) {
  DEBUG_LAYER(vkCmdPushConstants);
  next(commandBuffer, layout, stageFlags, offset, size, pValues);

  AddCommand(commandBuffer, std::make_unique<CmdPushConstants>(
                                layout, stageFlags, offset, size, pValues));
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

VkResult vkCreateSampler(PFN_vkCreateSampler next, VkDevice device,
                         VkSamplerCreateInfo const *pCreateInfo,
                         AllocationCallbacks pAllocator, VkSampler *pSampler) {
  DEBUG_LAYER(vkCreateSampler);
  auto result = next(device, pCreateInfo, pAllocator, pSampler);
  if (result == VK_SUCCESS) {
    samplers_.insert({*pSampler, *pCreateInfo});
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
    descriptor_set_layouts_.insert({*pSetLayout, DeepCopy(*pCreateInfo)});
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
    uint32_t push_constant_size = 0;
    for (uint32_t i = 0; i < pCreateInfo->pushConstantRangeCount; i++) {
      push_constant_size = std::max(
          push_constant_size, pCreateInfo->pPushConstantRanges[i].size +
                                  pCreateInfo->pPushConstantRanges[i].offset);
    }

    pipeline_layouts.insert(
        {*pPipelineLayout,
         PipelineLayoutData(DeepCopy(*pCreateInfo), {}, push_constant_size)});
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
  VkPipeline graphics_pipeline_ = nullptr;
  VkRenderPassBeginInfo *currentRenderPass = nullptr;
  uint32_t currentSubpass = 0;
  VkCommandBuffer commandBuffer;
  VkQueue queue;
  std::vector<uint8_t> push_constants_;

  std::unordered_map<uint32_t, VkBuffer> bound_vertex_buffers;
  std::unordered_map<uint32_t, VkDeviceSize> vertex_buffer_offsets;
  std::vector<const CmdPipelineBarrier *> pipeline_barriers;
  IndexBufferBinding boundIndexBuffer = {};
};

void HandleDrawCall(const DrawCallStateTracker &draw_call_state_tracker,
                    uint32_t first_index, uint32_t index_count,
                    uint32_t first_vertex, uint32_t vertex_count,
                    uint32_t first_instance, uint32_t instance_count);

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

      // For debugging
      uint32_t draw_commands = 0;
      for (auto &cmd : commandBuffers.at(commandBuffer)) {
        if (cmd->AsDraw() || cmd->AsDrawIndexed()) draw_commands++;
      }
      // std::cout << "Draw command count: " << draw_commands << std::endl;

      for (auto &cmd : commandBuffers.at(commandBuffer)) {
        if (auto cmdBeginRenderPass = cmd->AsBeginRenderPass()) {
          drawCallStateTracker.currentRenderPass =
              cmdBeginRenderPass->pRenderPassBegin_;
          drawCallStateTracker.currentSubpass = 0;
        } else if (auto cmdBindDescriptorSets = cmd->AsBindDescriptorSets()) {
          auto &pipeline_layout_data =
              pipeline_layouts.at(cmdBindDescriptorSets->layout_);
          if (cmdBindDescriptorSets->pipelineBindPoint_ ==
              VK_PIPELINE_BIND_POINT_GRAPHICS) {
            // Check if there are already descriptor bindings for the pipeline
            // layout.
            uint32_t dynamic_offset_idx = 0;

            // Update / create the bindings
            for (uint32_t descriptor_set_idx = 0;
                 descriptor_set_idx <
                 cmdBindDescriptorSets->descriptorSetCount_;
                 descriptor_set_idx++) {
              auto descriptor_set =
                  cmdBindDescriptorSets->pDescriptorSets_[descriptor_set_idx];
              // Check if there's any UNIFORM_BUFFER_DYNAMIC or
              // STORAGE_BUFFER_DYNAMIC descriptors in the set and store the
              // dynamic offsets for them.
              auto &descriptor_set_data = descriptor_sets_.at(descriptor_set);

              for (auto &buffer_binding :
                   descriptor_set_data.descriptor_buffer_bindings_) {
                VkDescriptorSetLayoutBinding layout_binding =
                    descriptor_set_data.descriptor_set_layout_create_info_
                        .pBindings[buffer_binding.binding_number_];
                if (layout_binding.descriptorType ==
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC) {
                  buffer_binding.dynamic_offset_ =
                      cmdBindDescriptorSets
                          ->pDynamicOffsets_[dynamic_offset_idx++];
                  /* // For debug. TODO: remove this
                  std::cout
                      << "dynamic offset: " << std::get<2>(binding_and_buffer)
                      << std::endl;*/
                }
              }
              // TODO: descriptor set bindings should be stored in
              // drawCallStateTracker to enable pipeline layout compatibility:
              // https://www.khronos.org/registry/vulkan/specs/1.1-extensions/html/chap13.html#descriptorsets-compatibility

              // Update the descriptor set bindings
              pipeline_layout_data
                  .descriptor_set_bindings_[cmdBindDescriptorSets->firstSet_ +
                                            descriptor_set_idx] =
                  cmdBindDescriptorSets->pDescriptorSets_[descriptor_set_idx];
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
              drawCallStateTracker.graphics_pipeline_ =
                  cmdBindPipeline->pipeline_;
              break;
            default:
              // Not considering other pipelines now.
              break;
          }
        } else if (auto cmdBindVertexBuffers = cmd->AsBindVertexBuffers()) {
          for (uint32_t bindingIdx = 0;
               bindingIdx < cmdBindVertexBuffers->bindingCount_; bindingIdx++) {
            drawCallStateTracker
                .bound_vertex_buffers[bindingIdx +
                                      cmdBindVertexBuffers->firstBinding_] =
                cmdBindVertexBuffers
                    ->pBuffers_[bindingIdx +
                                cmdBindVertexBuffers->firstBinding_];
            drawCallStateTracker
                .vertex_buffer_offsets[bindingIdx +
                                       cmdBindVertexBuffers->firstBinding_] =
                cmdBindVertexBuffers
                    ->pOffsets_[bindingIdx +
                                cmdBindVertexBuffers->firstBinding_];
          }
        } else if (auto cmdCopyBuffer = cmd->AsCopyBuffer()) {
          // TODO: track buffer copies?
        } else if (auto cmdCopyBufferToImage = cmd->AsCopyBufferToImage()) {
          // TODO: not implemented.
        } else if (auto cmdDraw = cmd->AsDraw()) {
          HandleDrawCall(drawCallStateTracker, 0, 0, cmdDraw->firstVertex_,
                         cmdDraw->vertexCount_, cmdDraw->firstInstance_,
                         cmdDraw->instanceCount_);
        } else if (auto cmdDrawIndexed = cmd->AsDrawIndexed()) {
          HandleDrawCall(drawCallStateTracker, cmdDrawIndexed->firstInstance_,
                         cmdDrawIndexed->indexCount_, 0, 0,
                         cmdDrawIndexed->firstInstance_,
                         cmdDrawIndexed->instanceCount_);
        } else if (auto cmdPipelineBarrier = cmd->AsPipelineBarrier()) {
          drawCallStateTracker.pipeline_barriers.push_back(cmdPipelineBarrier);
        } else if (auto cmdPushConstants = cmd->AsPushConstants()) {
          // Resize (increase) push constant storage size based on the ranges
          // defined in the pipeline layout currently being used.
          if (pipeline_layouts.at(cmdPushConstants->layout_)
                  .push_constants_size_ >
              drawCallStateTracker.push_constants_.size()) {
            drawCallStateTracker.push_constants_.resize(
                pipeline_layouts.at(cmdPushConstants->layout_)
                    .push_constants_size_);
          }
          // Store push constant values
          memcpy(drawCallStateTracker.push_constants_.data() +
                     cmdPushConstants->offset_,
                 cmdPushConstants->pValues_, cmdPushConstants->size_);
        } else {
          throw std::runtime_error("Unknown command.");
        }
      }
      if (draw_commands) exit(1);
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
        descriptor_sets_.at(writeDescriptorSet.dstSet)
            .image_and_sampler_bindings_.insert(
                {writeDescriptorSet.dstBinding,
                 *writeDescriptorSet.pImageInfo});
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
        descriptor_sets_.at(writeDescriptorSet.dstSet)
            .descriptor_buffer_bindings_.emplace_back(DescriptorBufferBinding{
                writeDescriptorSet.dstBinding,
                0,
                *writeDescriptorSet.pBufferInfo,
            });
        break;
      }
      default:
        throw std::runtime_error("Should be unreachable.");
        break;
    }
  }
}

void HandleDrawCall(const DrawCallStateTracker &draw_call_state_tracker,
                    uint32_t first_index, uint32_t index_count,
                    uint32_t first_vertex, uint32_t vertex_count,
                    uint32_t first_instance, uint32_t instance_count) {
  if (!draw_call_state_tracker.graphicsPipelineIsBound) {
    return;
  }
  assert(draw_call_state_tracker.currentRenderPass);

  // if (index_count != 213) return;  // DEBUG TODO: remove

  if (capture_draw_call_number == -1) {
    auto frame_number_str = std::getenv("DRAW_CALL_NUMBER");
    try {
      capture_draw_call_number = std::stoi(frame_number_str);
    } catch (const std::exception &exception) {
      std::cout << "Warning: Unable to parse the number of the draw call to be "
                   "captured. Please set DRAW_CALL_NUMBER environment "
                   "variable. Defaulting to 0.\n\n";
      capture_draw_call_number = 0;
    }
  }

  /*if (current_draw_call_number != capture_draw_call_number) {
    current_draw_call_number++;
    return;
  }*/

  VkPipelineShaderStageCreateInfo *vertexShader = nullptr;
  VkPipelineShaderStageCreateInfo *fragmentShader = nullptr;
  auto graphicsPipelineCreateInfo =
      graphicsPipelines.at(draw_call_state_tracker.graphics_pipeline_);
  for (uint32_t stageIndex = 0;
       stageIndex < graphicsPipelineCreateInfo.stageCount; stageIndex++) {
    auto &stageCreateInfo = graphicsPipelineCreateInfo.pStages[stageIndex];
    if (stageCreateInfo.stage == VK_SHADER_STAGE_VERTEX_BIT) {
      vertexShader =
          const_cast<VkPipelineShaderStageCreateInfo *>(&stageCreateInfo);
    } else if (stageCreateInfo.stage == VK_SHADER_STAGE_FRAGMENT_BIT) {
      fragmentShader =
          const_cast<VkPipelineShaderStageCreateInfo *>(&stageCreateInfo);
    } else {
      throw std::runtime_error("Not handled.");
    }
  }
  // Both vertex and fragment shaders are required.
  if (vertexShader == nullptr || fragmentShader == nullptr) {
    throw std::runtime_error("Missing vertex or fragment shader.");
  }

  std::stringstream bufferDeclarationStringStream;
  std::stringstream descriptorSetBindingStringStream;
  std::stringstream framebufferAttachmentStringStream;
  std::stringstream pipeline_str_stream;

  // Declare index buffer (if used)
  uint32_t max_index_value = 0;
  if (index_count > 0) {
    auto buffer = buffers.at(draw_call_state_tracker.boundIndexBuffer.buffer);
    VkBuffer indexBuffer = draw_call_state_tracker.boundIndexBuffer.buffer;

    auto commandPool =
        GetGlobalContext()
            .GetVkCommandBufferData(draw_call_state_tracker.commandBuffer)
            ->command_pool;
    auto queueFamilyIndex = commandPoolToQueueFamilyIndex.at(commandPool);

    std::vector<const CmdPipelineBarrier *> index_buffer_pipeline_barriers;
    // Check if there is pipeline barriers for index buffer
    for (const auto &barrier : draw_call_state_tracker.pipeline_barriers) {
      if (barrier->dstStageMask_ & VK_PIPELINE_STAGE_VERTEX_INPUT_BIT) {
        index_buffer_pipeline_barriers.push_back(barrier);
        break;
      }
    }

    BufferCopy index_buffer_copy = BufferCopy();
    index_buffer_copy.CopyBuffer(
        draw_call_state_tracker.queue, queueFamilyIndex,
        index_buffer_pipeline_barriers, indexBuffer, buffer.size);

    pipeline_str_stream << "  INDEX_DATA index_buffer" << std::endl;

    // Amber supports only 32-bit indices. 16-bit indices will be used as
    // 32-bit.
    bufferDeclarationStringStream << "BUFFER index_buffer DATA_TYPE uint32 ";
    bufferDeclarationStringStream << "DATA " << std::endl << "  ";

    // 16-bit indices
    if (draw_call_state_tracker.boundIndexBuffer.indexType ==
        VK_INDEX_TYPE_UINT16) {
      auto ptr = (uint16_t *)(index_buffer_copy.copied_data_ +
                              draw_call_state_tracker.boundIndexBuffer.offset);
      for (uint32_t indexIdx = 0; indexIdx < index_count; indexIdx++) {
        max_index_value = std::max((uint32_t)ptr[indexIdx], max_index_value);
        bufferDeclarationStringStream << ptr[indexIdx] << " ";
      }
    }
    // 32-bit indices
    else if (draw_call_state_tracker.boundIndexBuffer.indexType ==
             VK_INDEX_TYPE_UINT32) {
      auto ptr = (uint32_t *)(index_buffer_copy.copied_data_ +
                              draw_call_state_tracker.boundIndexBuffer.offset);
      for (uint32_t indexIdx = 0; indexIdx < index_count; indexIdx++) {
        max_index_value = std::max(ptr[indexIdx], max_index_value);
        bufferDeclarationStringStream << ptr[indexIdx] << " ";
      }
    } else {
      throw std::runtime_error("Invalid indexType");
    }
    bufferDeclarationStringStream << std::endl
                                  << "END" << std::endl
                                  << std::endl;

    // Free vulkan resources related to index buffer copy.
    index_buffer_copy.FreeResources();
  }

  bool vertex_buffer_found = false;
  std::unordered_map<VkBuffer, BufferCopy *> copied_buffers;
  for (uint32_t attr_idx = 0;
       attr_idx < graphicsPipelineCreateInfo.pVertexInputState
                      ->vertexAttributeDescriptionCount;
       attr_idx++) {
    // TODO: Check if this can be removed
    if (!graphicsPipelineCreateInfo.pVertexInputState
             ->vertexBindingDescriptionCount)
      continue;

    // auto location = vertex_buffer_binding.first;
    auto attribute_description = graphicsPipelineCreateInfo.pVertexInputState
                                     ->pVertexAttributeDescriptions[attr_idx];
    auto location = attribute_description.location;
    auto binding_description =
        graphicsPipelineCreateInfo.pVertexInputState
            ->pVertexBindingDescriptions[attribute_description.binding];
    auto vertex_buffer = draw_call_state_tracker.bound_vertex_buffers.at(
        attribute_description.binding);
    auto buffer_create_info = buffers.at(vertex_buffer);
    assert(buffer_create_info.usage & VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);

    if (binding_description.inputRate != VK_VERTEX_INPUT_RATE_VERTEX)
      throw std::runtime_error("VK_VERTEX_INPUT_RATE_INSTANCE not implemented");

    vertex_buffer_found = true;
    BufferCopy *vertex_buffer_copy = nullptr;
    // Don't copy the buffer if it's already copied
    if (!copied_buffers.count(vertex_buffer)) {
      auto commandPool =
          GetGlobalContext()
              .GetVkCommandBufferData(draw_call_state_tracker.commandBuffer)
              ->command_pool;
      auto queueFamilyIndex = commandPoolToQueueFamilyIndex.at(commandPool);
      vertex_buffer_copy = new BufferCopy();

      // Check if there is pipeline barriers for vertex buffer
      std::vector<const CmdPipelineBarrier *> vertex_buffer_pipeline_barriers;
      for (auto barrier : draw_call_state_tracker.pipeline_barriers) {
        if (barrier->dstStageMask_ & VK_PIPELINE_STAGE_VERTEX_INPUT_BIT) {
          vertex_buffer_pipeline_barriers.push_back(barrier);
          break;  // TODO: Should this be removed?
        }
      }

      vertex_buffer_copy->CopyBuffer(draw_call_state_tracker.queue,
                                     queueFamilyIndex,
                                     vertex_buffer_pipeline_barriers,
                                     vertex_buffer, buffer_create_info.size);

      copied_buffers.insert({vertex_buffer, vertex_buffer_copy});
    } else {
      vertex_buffer_copy = copied_buffers.at(vertex_buffer);
    }

    std::stringstream bufferName;
    bufferName << "vert_" << location;

    pipeline_str_stream << "  VERTEX_DATA " << bufferName.str() << " LOCATION "
                        << location << std::endl;

    vkf::VulkanFormat format =
        vkf::VkFormatToVulkanFormat(attribute_description.format);

    std::stringstream buffer_declaration_str;
    buffer_declaration_str << "BUFFER " << bufferName.str() << " DATA_TYPE "
                           << format.name << " DATA\n"
                           << "  ";

    auto buffer_offset = draw_call_state_tracker.vertex_buffer_offsets.at(
        binding_description.binding);

    uint32_t stride =
        binding_description.stride == 0 ? 1 : binding_description.stride;

    uint32_t element_count =
        vertex_count == 0 ? max_index_value + 1 : vertex_count;

    for (uint32_t i = 0; i < element_count; i++) {
      uint32_t offset =
          i * stride + attribute_description.offset + buffer_offset;
      if (offset > buffer_create_info.size) {
        throw std::runtime_error(
            "Offset is greater than the size of the buffer.");
      }
      auto readPtr = vertex_buffer_copy->copied_data_ + offset;

      readComponentsFromBufferAndWriteToStrStream(readPtr, format,
                                                  buffer_declaration_str);
    }

    bufferDeclarationStringStream << buffer_declaration_str.str() << std::endl
                                  << "END" << std::endl
                                  << std::endl;
  }

  // Free copied vertex buffers
  for (auto buffer_copy : copied_buffers) {
    buffer_copy.second->FreeResources();
    delete buffer_copy.second;
  }

  if (!vertex_buffer_found) return;

  auto pipeline_layout =
      graphicsPipelines.at(draw_call_state_tracker.graphics_pipeline_).layout;

  const auto &pipeline_layout_data = pipeline_layouts.at(pipeline_layout);

  if (pipeline_layout_data.create_info_.pushConstantRangeCount) {
    if (pipeline_layout_data.create_info_.pushConstantRangeCount > 1) {
      throw std::runtime_error("Amber supports only one pushConstantRange.");
    }

    bufferDeclarationStringStream
        << "BUFFER push_constants_buffer DATA_TYPE uint8 DATA" << std::endl;

    const auto &push_constants = draw_call_state_tracker.push_constants_;
    bufferDeclarationStringStream << "  ";

    const auto &range =
        pipeline_layout_data.create_info_.pPushConstantRanges[0];

    for (uint32_t idx = 0; idx < range.size; idx++) {
      // bufferDeclarationStringStream << std::hex << "0x" ;
      bufferDeclarationStringStream
          << (uint32_t)push_constants[idx + range.offset] << " ";
      // bufferDeclarationStringStream << std::dec;
    }
    bufferDeclarationStringStream << std::endl
                                  << "END" << std::endl
                                  << std::endl;
    descriptorSetBindingStringStream
        << "  BIND BUFFER push_constants_buffer AS push_constant" << std::endl;
  }

  for (const auto &descriptor_set_binding :
       pipeline_layout_data.descriptor_set_bindings_) {
    uint32_t descriptor_set_number = descriptor_set_binding.first;
    const auto &descriptor_set =
        descriptor_sets_.at(descriptor_set_binding.second);

    uint32_t dynamic_buffer_index = 0;
    for (const auto &buffer_binding :
         descriptor_set.descriptor_buffer_bindings_) {
      std::stringstream strstr;
      strstr << "buf_" << descriptor_set_number << "_"
             << buffer_binding.binding_number_;
      std::string bufferName = strstr.str();

      VkBufferCreateInfo bufferCreateInfo =
          buffers.at(buffer_binding.descriptor_buffer_info_.buffer);

      bufferDeclarationStringStream << "BUFFER " << bufferName << " DATA_TYPE "
                                    << "uint8"
                                    << " DATA" << std::endl;
      bufferDeclarationStringStream << "  ";

      const auto &layout_binding =
          descriptor_set.descriptor_set_layout_create_info_.pBindings;
      descriptorSetBindingStringStream
          << "  BIND BUFFER " << bufferName << " AS "
          << GetDescriptorTypeString(layout_binding->descriptorType)
          << " DESCRIPTOR_SET " << descriptor_set_number << " BINDING "
          << buffer_binding.binding_number_ << std::endl;

      const auto &descriptor_buffer =
          buffer_binding.descriptor_buffer_info_.buffer;

      auto commandPool =
          GetGlobalContext()
              .GetVkCommandBufferData(draw_call_state_tracker.commandBuffer)
              ->command_pool;
      auto queueFamilyIndex = commandPoolToQueueFamilyIndex.at(commandPool);
      BufferCopy descriptorBufferCopy = BufferCopy();

      // Create list of pipeline barriers for the descriptor buffer
      std::vector<const CmdPipelineBarrier *> descriptor_buffer_barriers;
      for (const auto &barrier : draw_call_state_tracker.pipeline_barriers) {
        // Find all barriers where dstStage contains vertex shader.
        if (barrier->dstStageMask_ & VK_PIPELINE_STAGE_VERTEX_SHADER_BIT) {
          // Check if at least one of the buffer memory barriers has
          // VK_ACCESS_UNIFORM_READ_BIT set.
          for (uint32_t bufMemBarrierIdx = 0;
               bufMemBarrierIdx < barrier->bufferMemoryBarrierCount_;
               bufMemBarrierIdx++) {
            descriptor_buffer_barriers.push_back(barrier);
            break;  // TODO: Should this be removed?
          }
        }
      }

      descriptorBufferCopy.CopyBuffer(
          draw_call_state_tracker.queue, queueFamilyIndex,
          descriptor_buffer_barriers, descriptor_buffer, bufferCreateInfo.size);

      VkDeviceSize range =
          buffer_binding.descriptor_buffer_info_.range == VK_WHOLE_SIZE
              ? bufferCreateInfo.size
              : buffer_binding.descriptor_buffer_info_.range;

      auto *thePtr = descriptorBufferCopy.copied_data_;

      for (VkDeviceSize bidx = 0; bidx < range; bidx++) {
        if (bidx > 0) {
          bufferDeclarationStringStream << " ";
        }
        bufferDeclarationStringStream << (uint32_t)
                thePtr[bidx + buffer_binding.descriptor_buffer_info_.offset +
                       buffer_binding.dynamic_offset_];
      }

      bufferDeclarationStringStream << std::endl;
      bufferDeclarationStringStream << "END" << std::endl << std::endl;

      descriptorBufferCopy.FreeResources();
    }

    for (const auto &binding_and_image :
         descriptor_set.image_and_sampler_bindings_) {
      uint32_t binding_number = binding_and_image.first;
      const auto &image_info = binding_and_image.second;

      const auto &layout_binding =
          descriptor_set.descriptor_set_layout_create_info_
              .pBindings[binding_number];
      VkDescriptorType descriptor_type = layout_binding.descriptorType;

      std::stringstream strstr;

      switch (descriptor_type) {
        case VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE:
        case VK_DESCRIPTOR_TYPE_STORAGE_IMAGE:
        case VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER: {
          strstr << "img_" << descriptor_set_number << "_" << binding_number;
          std::string imageName = strstr.str();

          descriptorSetBindingStringStream
              << "  BIND BUFFER " << imageName << " AS "
              << GetDescriptorTypeString(descriptor_type);

          if (descriptor_type == VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER) {
            std::stringstream sampler_str;
            sampler_str << "sampler_" << descriptor_set_number << "_"
                        << binding_number;
            std::string samplerName = sampler_str.str();

            auto sampler_info = samplers_.at(image_info.sampler);
            descriptorSetBindingStringStream << " SAMPLER " << samplerName;
            bufferDeclarationStringStream
                << "SAMPLER " << samplerName << " MAG_FILTER "
                << GetSamplerFilterTypeString(sampler_info.magFilter)
                << " MIN_FILTER "
                << GetSamplerFilterTypeString(sampler_info.minFilter)
                << " ADDRESS_MODE_U "
                << GetSamplerAddressModeString(sampler_info.addressModeU)
                << " ADDRESS_MODE_V "
                << GetSamplerAddressModeString(sampler_info.addressModeV)
                << " ADDRESS_MODE_W "
                << GetSamplerAddressModeString(sampler_info.addressModeW)
                << " BORDER_COLOR "
                << GetSamplerBorderColorString(sampler_info.borderColor)
                << std::scientific << " MIN_LOD " << sampler_info.minLod
                << " MAX_LOD " << sampler_info.maxLod << std::defaultfloat
                << (sampler_info.unnormalizedCoordinates
                        ? " UNNORMALIZED_COORDS"
                        : " NORMALIZED_COORDS")
                << std::endl;
          }

          descriptorSetBindingStringStream
              << " DESCRIPTOR_SET " << descriptor_set_number << " BINDING "
              << binding_number << std::endl;

          // TODO: implement BASE_MIP_LEVEL
          // https://github.com/google/amber/blob/master/docs/amber_script.md#pipeline-buffers

          bufferDeclarationStringStream << "BUFFER " << imageName
                                        << " FORMAT R8G8B8A8_UNORM"
                                        << " FILE texture.png" << std::endl;
          break;
        }
        case VK_DESCRIPTOR_TYPE_SAMPLER: {
          auto sampler_info = samplers_.at(image_info.sampler);
          strstr << "sampler_" << descriptor_set_number << "_"
                 << binding_number;
          std::string samplerName = strstr.str();

          descriptorSetBindingStringStream
              << "  BIND SAMPLER " << samplerName << " DESCRIPTOR_SET "
              << descriptor_set_number << " BINDING " << binding_number;

          bufferDeclarationStringStream
              << "SAMPLER " << samplerName << " MAG_FILTER "
              << GetSamplerFilterTypeString(sampler_info.magFilter)
              << " MIN_FILTER "
              << GetSamplerFilterTypeString(sampler_info.minFilter)
              << " ADDRESS_MODE_U "
              << GetSamplerAddressModeString(sampler_info.addressModeU)
              << " ADDRESS_MODE_V "
              << GetSamplerAddressModeString(sampler_info.addressModeV)
              << " ADDRESS_MODE_W "
              << GetSamplerAddressModeString(sampler_info.addressModeW)
              << " BORDER_COLOR "
              << GetSamplerBorderColorString(sampler_info.borderColor)
              << " MIN_LOD " << sampler_info.minLod << " MAX_LOD "
              << sampler_info.maxLod
              << (sampler_info.unnormalizedCoordinates ? " UNNORMALIZED_COORDS"
                                                       : " NORMALIZED_COORDS")
              << std::endl;
          break;
        }
        default:
          throw std::runtime_error("Unimplemented descriptor type: " +
                                   std::to_string(descriptor_type));
      }
      descriptorSetBindingStringStream << std::endl;
    }
  }

  if (graphicsPipelineCreateInfo.pDepthStencilState != nullptr ||
      graphicsPipelineCreateInfo.pRasterizationState->depthBiasEnable ||
      graphicsPipelineCreateInfo.pRasterizationState->depthClampEnable) {
    // Depth
    pipeline_str_stream << "  DEPTH\n";

    if (graphicsPipelineCreateInfo.pDepthStencilState != nullptr) {
      auto depthState = graphicsPipelineCreateInfo.pDepthStencilState;
      pipeline_str_stream << "    TEST "
                          << (depthState->depthTestEnable ? "on" : "off")
                          << "\n"
                          << "    WRITE "
                          << (depthState->depthWriteEnable ? "on" : "off")
                          << "\n";
      pipeline_str_stream << "    COMPARE_OP "
                          << GetCompareOpString(depthState->depthCompareOp)
                          << "\n";

      // Amber expects the values as float values
      pipeline_str_stream << std::scientific;
      pipeline_str_stream << "    BOUNDS min " << depthState->minDepthBounds
                          << " max " << depthState->maxDepthBounds << "\n";
      pipeline_str_stream << std::defaultfloat;
    }
    if (graphicsPipelineCreateInfo.pRasterizationState->depthClampEnable) {
      pipeline_str_stream << "    CLAMP on\n";
    }
    if (graphicsPipelineCreateInfo.pRasterizationState->depthBiasEnable) {
      pipeline_str_stream
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
    pipeline_str_stream << "  END\n";  // DEPTH

    // Stencil
    if (graphicsPipelineCreateInfo.pDepthStencilState != nullptr &&
        graphicsPipelineCreateInfo.pDepthStencilState->stencilTestEnable) {
      auto create_stencil_block = [](std::stringstream &stringstream,
                                     const VkStencilOpState &state) {
        stringstream << "    TEST on \n"
                     << "    FAIL_OP " << GetStencilOpString(state.failOp)
                     << "\n"
                     << "    PASS_OP " << GetStencilOpString(state.passOp)
                     << "\n"
                     << "    DEPTH_FAIL_OP "
                     << GetStencilOpString(state.depthFailOp) << "\n"
                     << "    COMPARE_OP " << GetCompareOpString(state.compareOp)
                     << "\n"
                     << "    COMPARE_MASK " << state.compareMask << "\n"
                     << "    WRITE_MASK " << state.writeMask << "\n"
                     << "    WRITE_MASK " << state.writeMask << "\n"
                     << "    REFERENCE " << state.reference << "\n"
                     << "  END\n";
      };
      pipeline_str_stream << "  STENCIL front \n";
      create_stencil_block(
          pipeline_str_stream,
          graphicsPipelineCreateInfo.pDepthStencilState->front);
      pipeline_str_stream << "  STENCIL back \n";
      create_stencil_block(pipeline_str_stream,
                           graphicsPipelineCreateInfo.pDepthStencilState->back);
    }
  }

  std::string amber_file_name;
  if (std::getenv("AMBER_FILE_NAME") == nullptr) {
    auto order_string = std::to_string(current_draw_call_number);
    amber_file_name = "draw_call_" +
                      std::string(4 - order_string.length(), '0') +
                      order_string + ".amber";
  } else {
    amber_file_name = std::getenv("AMBER_FILE_NAME");
  }

  // Create buffers for color attachments.
  VkRenderPassCreateInfo renderPassCreateInfo =
      renderPasses.at(draw_call_state_tracker.currentRenderPass->renderPass);
  for (uint colorAttachment = 0;
       colorAttachment <
       renderPassCreateInfo.pSubpasses[draw_call_state_tracker.currentSubpass]
           .colorAttachmentCount;
       colorAttachment++) {
    uint32_t attachmentID =
        renderPassCreateInfo.pSubpasses[draw_call_state_tracker.currentSubpass]
            .pColorAttachments[colorAttachment]
            .attachment;
    vkf::VulkanFormat format = vkf::VkFormatToVulkanFormat(
        renderPassCreateInfo.pAttachments[attachmentID].format);

    bufferDeclarationStringStream << "BUFFER framebuffer_" << colorAttachment
                                  << " FORMAT B8G8R8A8_UNORM" << std::endl;
    // The original format is not used, because the Amber's png output supports
    // only B8G8R8A8_UNORM format. Replace the line above with the following
    // line to use the original format.
    // << format.name << std::endl;

    framebufferAttachmentStringStream
        << "  BIND BUFFER framebuffer_" << colorAttachment
        << " AS color LOCATION " << colorAttachment << std::endl;
  }

  // Create buffer for depth / stencil attachment.
  if (renderPassCreateInfo.pSubpasses[draw_call_state_tracker.currentSubpass]
          .pDepthStencilAttachment) {
    uint32_t attachmentID =
        renderPassCreateInfo.pSubpasses[draw_call_state_tracker.currentSubpass]
            .pDepthStencilAttachment->attachment;
    vkf::VulkanFormat format = vkf::VkFormatToVulkanFormat(
        renderPassCreateInfo.pAttachments[attachmentID].format);

    bufferDeclarationStringStream << "BUFFER depthstencil FORMAT "
                                  << format.name << std::endl;
    framebufferAttachmentStringStream
        << "  BIND BUFFER depthstencil AS depth_stencil" << std::endl;
  }

  std::fstream amber_file;
  amber_file.open(amber_file_name, std::ios::trunc | std::ios::out);

  amber_file << "#!amber" << std::endl << std::endl;

  amber_file << "SHADER vertex vertex_shader SPIRV-ASM" << std::endl;
  amber_file << GetDisassembly(vertexShader->module);
  amber_file << "END" << std::endl << std::endl;

  amber_file << "SHADER fragment fragment_shader SPIRV-ASM" << std::endl;
  amber_file << GetDisassembly(fragmentShader->module);
  amber_file << "END" << std::endl << std::endl;

  amber_file << bufferDeclarationStringStream.str() << std::endl;

  amber_file << "PIPELINE graphics pipeline" << std::endl;
  amber_file << "  ATTACH vertex_shader";
  if (vertexShader->pSpecializationInfo != nullptr) {
    for (uint32_t id = 0; id < vertexShader->pSpecializationInfo->mapEntryCount;
         id++) {
      amber_file << " "
                 << CreateSpecializationString(
                        vertexShader->pSpecializationInfo->pMapEntries[id],
                        vertexShader->pSpecializationInfo->pData);
    }
  }
  amber_file << std::endl;
  amber_file << "  ATTACH fragment_shader";
  if (fragmentShader->pSpecializationInfo != nullptr) {
    for (uint32_t id = 0;
         id < fragmentShader->pSpecializationInfo->mapEntryCount; id++) {
      amber_file << " "
                 << CreateSpecializationString(
                        fragmentShader->pSpecializationInfo->pMapEntries[id],
                        fragmentShader->pSpecializationInfo->pData);
    }
  }
  amber_file << std::endl;

  // Polygon mode
  amber_file << "  POLYGON_MODE ";
  switch (graphicsPipelineCreateInfo.pRasterizationState->polygonMode) {
    case VkPolygonMode::VK_POLYGON_MODE_FILL:
      amber_file << "fill\n";
      break;
    case VkPolygonMode::VK_POLYGON_MODE_LINE:
      amber_file << "line\n";
      break;
    case VkPolygonMode::VK_POLYGON_MODE_POINT:
      amber_file << "point\n";
      break;
    default:
      throw std::runtime_error("Polygon mode not supported by amber.");
  }

  // Add definitions for pipeline
  amber_file << pipeline_str_stream.str();

  VkFramebufferCreateInfo framebufferCreateInfo =
      framebuffers.at(draw_call_state_tracker.currentRenderPass->framebuffer);
  amber_file << "  FRAMEBUFFER_SIZE " << framebufferCreateInfo.width << " "
             << framebufferCreateInfo.height << std::endl;
  amber_file << framebufferAttachmentStringStream.str();
  amber_file << descriptorSetBindingStringStream.str();

  amber_file << "END" << std::endl << std::endl;  // PIPELINE

  /* // This extra pipeline is used to draw over existing "framebuffer"
   // that can be for example an output of the previous draw call.
  amber_file << R"(
SHADER vertex vert_shader_tex GLSL
#version 430
layout(location = 0) in vec4 position;
layout(location = 1) in vec2 texcoords_in;
layout(location = 0) out vec2 texcoords_out;
void main() {
  gl_Position = position;
  texcoords_out = texcoords_in;
}
END

SHADER fragment frag_shader_tex GLSL
#version 430
layout(location = 0) in vec2 texcoords_in;
layout(location = 0) out vec4 color_out;
uniform layout(set=0, binding=0) sampler2D tex_sampler;
void main() {
  color_out = texture(tex_sampler, texcoords_in);
}
END

BUFFER framebuffer_input FORMAT R8G8B8A8_UNORM FILE PNG framebuffer.png
SAMPLER sampler
BUFFER position DATA_TYPE vec2<float> DATA
-1.0 -1.0
 1.0 -1.0
 1.0  1.0
-1.0  1.0
END

BUFFER texcoords DATA_TYPE vec2<float> DATA
0.0 0.0
1.0 0.0
1.0 1.0
0.0 1.0
END

PIPELINE graphics pipeline_input
  ATTACH vert_shader_tex
  ATTACH frag_shader_tex
  BIND BUFFER framebuffer_input AS combined_image_sampler SAMPLER sampler
DESCRIPTOR_SET 0 BINDING 0 VERTEX_DATA position LOCATION 0 VERTEX_DATA texcoords
LOCATION 1 FRAMEBUFFER_SIZE 1280 720 BIND BUFFER framebuffer_0 AS color LOCATION
0 END
)";
  amber_file << "RUN pipeline_input DRAW_ARRAY AS TRIANGLE_FAN START_IDX 0 COUNT
4" << std::endl;
   */

  amber_file << "CLEAR_COLOR pipeline 0 0 0 255" << std::endl;
  amber_file << "CLEAR pipeline" << std::endl;

  const std::string &topology =
      topologies.at(graphicsPipelineCreateInfo.pInputAssemblyState->topology);

  if (index_count > 0) {
    amber_file << "RUN pipeline DRAW_ARRAY AS " << topology
               << " INDEXED START_IDX " << first_index << " COUNT "
               << index_count;
  } else {
    amber_file << "RUN pipeline DRAW_ARRAY AS " << topology;
  }
  if (instance_count) {
    amber_file << " START_INSTANCE " << first_instance << " INSTANCE_COUNT "
               << instance_count;
  }
  amber_file << std::endl;

  amber_file.close();
  amber_file.open(amber_file_name, std::ios::in);

  std::string line;
  while (std::getline(amber_file, line)) {
    std::cout << line << "\n";
  }
  std::cout << std::endl;

  // exit(0);
  current_draw_call_number++;
}

void readComponentsFromBufferAndWriteToStrStream(uint8_t *buffer,
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
        else if (format.components[cIdx].num_bits == 16) {
          throw std::runtime_error("16 bit floats not supported");
        }
      } else if (format.components[cIdx].isUInt()) {
        if (format.components[cIdx].num_bits == 8)
          bufStr << (uint32_t)buffer[cIdx] << " ";
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

const char *GetDescriptorTypeString(VkDescriptorType descriptor_type) {
  switch (descriptor_type) {
    case VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER:
      return "combined_image_sampler";
    case VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE:
      return "sampled_image";
    case VK_DESCRIPTOR_TYPE_STORAGE_IMAGE:
      return "storage_image";
    case VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER:
    case VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC:
      return "uniform";
    case VK_DESCRIPTOR_TYPE_STORAGE_BUFFER:
      return "storage";
    default:
      throw std::runtime_error("Unimplemented descriptor type: " +
                               std::to_string(descriptor_type));
  }
}

const char *GetSamplerAddressModeString(VkSamplerAddressMode address_mode) {
  switch (address_mode) {
    case VK_SAMPLER_ADDRESS_MODE_REPEAT:
      return "repeat";
    case VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT:
      return "mirrored_repeat";
    case VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE:
      return "clamp_to_edge";
    case VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER:
      return "clamp_to_border";
    case VK_SAMPLER_ADDRESS_MODE_MIRROR_CLAMP_TO_EDGE_KHR:
      return "mirrored_clamp_to_edge";
    default:
      throw std::runtime_error("Unsupported sampler address mode.");
  }
}

const char *GetSamplerBorderColorString(VkBorderColor border_color) {
  switch (border_color) {
    case VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK:
      return "float_transparent_black";
    case VK_BORDER_COLOR_INT_TRANSPARENT_BLACK:
      return "int_transparent_black";
    case VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK:
      return "float_opaque_black";
    case VK_BORDER_COLOR_INT_OPAQUE_BLACK:
      return "int_opaque_black";
    case VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE:
      return "float_opaque_white";
    case VK_BORDER_COLOR_INT_OPAQUE_WHITE:
      return "int_opaque_white";
    default:
      throw std::runtime_error("Unsupported sampler border color.");
  }
}

const char *GetSamplerFilterTypeString(VkFilter filter) {
  switch (filter) {
    case VK_FILTER_NEAREST:
      return "nearest";
    case VK_FILTER_LINEAR:
      return "linear";
    default:
      throw std::runtime_error("Unsupported sampler filter.");
  }
}

std::string CreateSpecializationString(
    const VkSpecializationMapEntry &specialization_map_entry,
    const void *specialization_data) {
  if (specialization_map_entry.size != 4) {
    throw std::runtime_error(
        "Amber supports only 4 byte specialization constants.");
  }
  std::string result =
      "SPECIALIZE " + std::to_string(specialization_map_entry.constantID) +
      " AS uint32 " +
      std::to_string(*(const uint32_t *)((const uint8_t *)specialization_data +
                                         specialization_map_entry.offset));
  return result;
}

const char *GetStencilOpString(VkStencilOp stencil_op) {
  switch (stencil_op) {
    case VK_STENCIL_OP_KEEP:
      return "keep";
    case VK_STENCIL_OP_REPLACE:
      return "replace";
    case VK_STENCIL_OP_INCREMENT_AND_CLAMP:
      return "increment_and_clamp";
    case VK_STENCIL_OP_DECREMENT_AND_CLAMP:
      return "decrement_and_clamp";
    case VK_STENCIL_OP_INVERT:
      return "invert";
    case VK_STENCIL_OP_INCREMENT_AND_WRAP:
      return "increment_and_wrap";
    case VK_STENCIL_OP_DECREMENT_AND_WRAP:
      return "decrement_and_wrap";
    default:
      throw std::runtime_error("Stencil operation not supported.");
  }
}

const char *GetCompareOpString(VkCompareOp compare_op) {
  switch (compare_op) {
    case VK_COMPARE_OP_NEVER:
      return "never";
    case VK_COMPARE_OP_LESS:
      return "less";
    case VK_COMPARE_OP_EQUAL:
      return "equal";
    case VK_COMPARE_OP_LESS_OR_EQUAL:
      return "less_or_equal";
    case VK_COMPARE_OP_GREATER:
      return "greater";
    case VK_COMPARE_OP_NOT_EQUAL:
      return "not_equal";
    case VK_COMPARE_OP_GREATER_OR_EQUAL:
      return "greater_or_equal";
    case VK_COMPARE_OP_ALWAYS:
      return "always";
    default:
      throw std::runtime_error("Compare Op not supported.");
  }
}

}  // namespace graphicsfuzz_amber_scoop
