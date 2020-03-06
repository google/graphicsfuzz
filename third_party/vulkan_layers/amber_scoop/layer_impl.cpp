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

#include "amber_scoop/layer.h"

#include <cassert>
#include <iostream>
#include <memory>
#include <sstream>
#include <tuple>
#include <unordered_map>
#include <vector>

#include "amber_scoop/vk_deep_copy.h"
#include "common/spirv_util.h"
#include "spirv-tools/libspirv.hpp"

namespace graphicsfuzz_amber_scoop {

#define DEBUG_AMBER_SCOOP 0

#if DEBUG_AMBER_SCOOP
#define DEBUG_LAYER(F) std::cout << "In " << #F << std::endl
#else
#define DEBUG_LAYER(F)
#endif

struct CmdBeginRenderPass;
struct CmdBindDescriptorSets;
struct CmdBindPipeline;
struct CmdDraw;
struct CmdDrawIndexed;

struct Cmd {

  enum Kind {
    kBeginRenderPass,
    kBindDescriptorSets,
    kBindPipeline,
    kDraw,
    kDrawIndexed
  };

  explicit Cmd(Kind kind) : kind_(kind) {}

// A bunch of methods for casting this type to a given type. Returns this if the
// cast can be done, nullptr otherwise.
// clang-format off
#define DeclareCastMethod(target)                                              \
  virtual Cmd##target *As##target() { return nullptr; }                        \
  virtual const Cmd##target *As##target() const { return nullptr; }
  DeclareCastMethod(BeginRenderPass) DeclareCastMethod(BindDescriptorSets)
  DeclareCastMethod(BindPipeline) DeclareCastMethod(Draw)
  DeclareCastMethod(DrawIndexed)
#undef DeclareCastMethod

  Kind kind_;
  // clang-format on
};

struct CmdBeginRenderPass : public Cmd {
  CmdBeginRenderPass(VkRenderPassBeginInfo const *pRenderPassBegin,
                     VkSubpassContents contents)
      : Cmd(kBeginRenderPass), pRenderPassBegin_(DeepCopy(pRenderPassBegin)),
        contents_(contents) {}

  CmdBeginRenderPass *AsBeginRenderPass() override { return this; }
  const CmdBeginRenderPass *AsBeginRenderPass() const override { return this; }

  VkRenderPassBeginInfo *pRenderPassBegin_;
  VkSubpassContents contents_;
};

struct CmdBindDescriptorSets : public Cmd {

  CmdBindDescriptorSets(VkPipelineBindPoint pipelineBindPoint,
                        VkPipelineLayout layout, uint32_t firstSet,
                        uint32_t descriptorSetCount,
                        VkDescriptorSet const *pDescriptorSets,
                        uint32_t dynamicOffsetCount,
                        uint32_t const *pDynamicOffsets)
      : Cmd(kBindDescriptorSets), pipelineBindPoint_(pipelineBindPoint),
        layout_(layout), firstSet_(firstSet),
        descriptorSetCount_(descriptorSetCount),
        pDescriptorSets_(CopyArray(pDescriptorSets, descriptorSetCount)),
        dynamicOffsetCount_(dynamicOffsetCount),
        pDynamicOffsets_(CopyArray(pDynamicOffsets, dynamicOffsetCount)) {}

  CmdBindDescriptorSets *AsBindDescriptorSets() override { return this; }
  const CmdBindDescriptorSets *AsBindDescriptorSets() const override {
    return this;
  }

  VkPipelineBindPoint pipelineBindPoint_;
  VkPipelineLayout layout_;
  uint32_t firstSet_;
  uint32_t descriptorSetCount_;
  VkDescriptorSet *pDescriptorSets_;
  uint32_t dynamicOffsetCount_;
  uint32_t *pDynamicOffsets_;
};

struct CmdBindPipeline : public Cmd {
  CmdBindPipeline(VkPipelineBindPoint pipelineBindPoint, VkPipeline pipeline)
      : Cmd(kBindPipeline), pipelineBindPoint_(pipelineBindPoint),
        pipeline_(pipeline) {}

  CmdBindPipeline *AsBindPipeline() override { return this; }
  const CmdBindPipeline *AsBindPipeline() const override { return this; }

  VkPipelineBindPoint pipelineBindPoint_;
  VkPipeline pipeline_;
};

struct CmdDraw : public Cmd {
  CmdDraw(uint32_t vertexCount, uint32_t instanceCount, uint32_t firstVertex,
          uint32_t firstInstance)
      : Cmd(kDraw), vertexCount_(vertexCount), instanceCount_(instanceCount),
        firstVertex_(firstVertex), firstInstance_(firstInstance) {}

  CmdDraw *AsDraw() override { return this; }
  const CmdDraw *AsDraw() const override { return this; }

  uint32_t vertexCount_;
  uint32_t instanceCount_;
  uint32_t firstVertex_;
  uint32_t firstInstance_;
};

struct CmdDrawIndexed : public Cmd {
  CmdDrawIndexed(uint32_t indexCount, uint32_t instanceCount,
                 uint32_t firstIndex, int32_t vertexOffset,
                 uint32_t firstInstance)
      : Cmd(kDrawIndexed), indexCount_(indexCount),
        instanceCount_(instanceCount), firstIndex_(firstIndex),
        vertexOffset_(vertexOffset), firstInstance_(firstInstance) {}

  CmdDrawIndexed *AsDrawIndexed() override { return this; }
  const CmdDrawIndexed *AsDrawIndexed() const override { return this; }

  uint32_t indexCount_;
  uint32_t instanceCount_;
  uint32_t firstIndex_;
  int32_t vertexOffset_;
  uint32_t firstInstance_;
};

std::unordered_map<VkCommandBuffer, std::vector<std::unique_ptr<Cmd>>>
    command_buffers;

void AddCommand(VkCommandBuffer command_buffer, std::unique_ptr<Cmd> command) {
  if (command_buffers.count(command_buffer) == 0) {
    std::vector<std::unique_ptr<Cmd>> empty_cmds;
    command_buffers.insert({command_buffer, std::move(empty_cmds)});
  }
  command_buffers.at(command_buffer).push_back(std::move(command));
}

std::unordered_map<VkDeviceMemory, std::tuple<VkDeviceSize, VkDeviceSize,
                                              VkMemoryMapFlags, void *>>
    mappedMemory;
std::unordered_map<VkBuffer, std::pair<VkDeviceMemory, VkDeviceSize>>
    bufferToMemory;
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

VkResult
vkAllocateDescriptorSets(PFN_vkAllocateDescriptorSets next, VkDevice device,
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

VkResult vkBindBufferMemory(PFN_vkBindBufferMemory next, VkDevice device,
                            VkBuffer buffer, VkDeviceMemory memory,
                            VkDeviceSize memoryOffset) {
  DEBUG_LAYER(vkBindBufferMemory);
  auto result = next(device, buffer, memory, memoryOffset);
  if (result == VK_SUCCESS) {
    bufferToMemory.insert({buffer, {memory, memoryOffset}});
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

void vkCmdBindPipeline(PFN_vkCmdBindPipeline next,
                       VkCommandBuffer commandBuffer,
                       VkPipelineBindPoint pipelineBindPoint,
                       VkPipeline pipeline) {
  DEBUG_LAYER(vkCmdBindPipeline);
  next(commandBuffer, pipelineBindPoint, pipeline);
  AddCommand(commandBuffer,
             std::make_unique<CmdBindPipeline>(pipelineBindPoint, pipeline));
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

VkResult vkCreateBuffer(PFN_vkCreateBuffer next, VkDevice device,
                        VkBufferCreateInfo const *pCreateInfo,
                        AllocationCallbacks pAllocator, VkBuffer *pBuffer) {
  DEBUG_LAYER(vkCreateBuffer);
  auto result = next(device, pCreateInfo, pAllocator, pBuffer);
  if (result == VK_SUCCESS) {
    buffers.insert({*pBuffer, DeepCopy(*pCreateInfo)});
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

VkResult vkMapMemory(PFN_vkMapMemory next, VkDevice device,
                     VkDeviceMemory memory, VkDeviceSize offset,
                     VkDeviceSize size, VkMemoryMapFlags flags, void **ppData) {
  DEBUG_LAYER(vkMapMemory);
  auto result = next(device, memory, offset, size, flags, ppData);
  if (result == VK_SUCCESS) {
    mappedMemory.insert({memory, {offset, size, flags, *ppData}});
  }
  return result;
}

struct DrawCallStateTracker {
  bool graphicsPipelineIsBound = false;
  VkPipeline boundGraphicsPipeline = nullptr;
  VkRenderPassBeginInfo *currentRenderPass = nullptr;
  uint32_t currentSubpass = 0;
  std::unordered_map<uint32_t, VkDescriptorSet> boundGraphicsDescriptorSets;
};

void HandleDrawCall(const DrawCallStateTracker &drawCallStateTracker);

VkResult vkQueueSubmit(PFN_vkQueueSubmit next, VkQueue queue,
                       uint32_t submitCount, VkSubmitInfo const *pSubmits,
                       VkFence fence) {
  DEBUG_LAYER(vkQueueSubmit);

  for (uint32_t submitIndex = 0; submitIndex < submitCount; submitIndex++) {
    for (uint32_t commandBufferIndex = 0;
         commandBufferIndex < pSubmits[submitIndex].commandBufferCount;
         commandBufferIndex++) {
      auto command_buffer =
          pSubmits[submitIndex].pCommandBuffers[commandBufferIndex];

      DrawCallStateTracker drawCallStateTracker = {};

      if (!command_buffers.count(command_buffer)) {
        continue;
      }
      for (auto &cmd : command_buffers.at(command_buffer)) {
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
        } else if (auto cmdDraw = cmd->AsDraw()) {
          HandleDrawCall(drawCallStateTracker);
        } else if (auto cmdDrawIndexed = cmd->AsDrawIndexed()) {
          HandleDrawCall(drawCallStateTracker);
        } else {
          assert(false && "Unknown command.");
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
    case VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT:
      // pImageInfo must be a valid pointer to an array of descriptorCount valid
      // VkDescriptorImageInfo structures
      break;
    case VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER:
    case VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER:
      // pTexelBufferView must be a valid pointer to an array of descriptorCount
      // valid VkBufferView handles
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
      assert(false && "Should be unreachable.");
      break;
    }
  }
}

void HandleDrawCall(const DrawCallStateTracker &drawCallStateTracker) {
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
      assert(false && "Not handled.");
    }
  }
  assert(vertexShader && "Vertex shader required for graphics pipeline.");
  assert(fragmentShader && "Vertex shader required for graphics pipeline.");

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

  for (auto descriptorSet : drawCallStateTracker.boundGraphicsDescriptorSets) {
    uint32_t descriptorSetNumber = descriptorSet.first;
    VkDescriptorSetLayoutCreateInfo layoutCreateInfo =
        descriptorSetLayouts.at(descriptorSets.at(descriptorSet.second));
    for (auto bindingAndBuffer :
         descriptorSetToBindingBuffer.at(descriptorSet.second)) {
      uint32_t bindingNumber = bindingAndBuffer.first;
      VkDescriptorBufferInfo bufferInfo = bindingAndBuffer.second;

      std::stringstream strstr;
      strstr << "buf_" << descriptorSetNumber << "_" << bindingNumber;
      std::string bufferName = strstr.str();

      VkBufferCreateInfo bufferCreateInfo = buffers.at(bufferInfo.buffer);
      assert(bufferInfo.offset == 0);

      bufferDeclarationStringStream << "BUFFER " << bufferName << " DATA_TYPE "
                                    << "uint32"
                                    << " DATA" << std::endl;
      bufferDeclarationStringStream << "  ";

      if (bufferToMemory.count(bufferInfo.buffer)) {
        VkDeviceMemory deviceMemory =
            bufferToMemory.at(bufferInfo.buffer).first;
        if (mappedMemory.count(deviceMemory)) {
          std::tuple<VkDeviceSize, VkDeviceSize, VkMemoryMapFlags, void *>
              &memoryMappingEntry = mappedMemory.at(deviceMemory);
          VkDeviceSize range = bufferInfo.range == VK_WHOLE_SIZE
                                   ? bufferCreateInfo.size
                                   : bufferInfo.range;
          assert(std::get<0>(memoryMappingEntry) == 0);
          assert(std::get<1>(memoryMappingEntry) >= range);
          uint32_t *thePtr = (uint32_t *)std::get<3>(memoryMappingEntry);
          for (int bidx = 0; bidx < range / sizeof(uint32_t); bidx++) {
            if (bidx > 0) {
              bufferDeclarationStringStream << " ";
            }
            bufferDeclarationStringStream << thePtr[bidx];
          }
        }
      } else {
        bufferDeclarationStringStream << "...";
      }
      bufferDeclarationStringStream << std::endl;
      bufferDeclarationStringStream << "END" << std::endl << std::endl;

      VkDescriptorSetLayoutBinding layoutBinding =
          layoutCreateInfo.pBindings[bindingNumber];
      VkDescriptorType descriptorType = layoutBinding.descriptorType;
      descriptorSetBindingStringStream
          << "  BIND BUFFER " << bufferName << " AS "
          << (descriptorType == VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER ? "uniform"
                                                                  : "...")
          << " DESCRIPTOR_SET " << descriptorSetNumber << " BINDING "
          << bindingNumber << std::endl;
    }
  }

  VkRenderPassCreateInfo renderPassCreateInfo =
      renderPasses.at(drawCallStateTracker.currentRenderPass->renderPass);
  for (uint colorAttachment = 0;
       colorAttachment <
       renderPassCreateInfo.pSubpasses[drawCallStateTracker.currentSubpass]
           .colorAttachmentCount;
       colorAttachment++) {
    bufferDeclarationStringStream << "BUFFER framebuffer_" << colorAttachment
                                  << " FORMAT B8G8R8A8_UNORM" << std::endl
                                  << std::endl;
    framebufferAttachmentStringStream
        << "  BIND BUFFER framebuffer_" << colorAttachment
        << " AS color LOCATION " << colorAttachment << std::endl;
  }

  std::cout << bufferDeclarationStringStream.str();

  std::cout << "PIPELINE graphics pipeline" << std::endl;
  std::cout << "  ATTACH vertex_shader" << std::endl;
  std::cout << "  ATTACH fragment_shader" << std::endl;
  VkFramebufferCreateInfo framebufferCreateInfo =
      framebuffers.at(drawCallStateTracker.currentRenderPass->framebuffer);
  std::cout << "  FRAMEBUFFER_SIZE " << framebufferCreateInfo.width << " "
            << framebufferCreateInfo.height << std::endl;
  std::cout << framebufferAttachmentStringStream.str();
  std::cout << descriptorSetBindingStringStream.str();
  std::cout << "END" << std::endl << std::endl;

  std::cout << "CLEAR_COLOR pipeline 0 0 0 255" << std::endl;
  std::cout << std::endl;

  std::cout << "CLEAR pipeline" << std::endl;
  std::cout << "RUN pipeline DRAW_RECT POS 0 0 SIZE 256 256" << std::endl;
}

} // namespace graphicsfuzz_amber_scoop
