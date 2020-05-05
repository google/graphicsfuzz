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

#include "buffer_copy.h"

namespace graphicsfuzz_amber_scoop {

void BufferCopy::copyBuffer(
    VkQueue queue, uint32_t queueFamilyIndex,
    const std::vector<std::shared_ptr<CmdPipelineBarrier>> &pipelineBarriers,
    const VkBuffer &buffer, VkDeviceSize bufferSize, void **mappedMemory) {
  device = GetGlobalContext().GetVkQueueData(queue)->device;

  // Create a buffer where the data will be copied to.
  VkBufferCreateInfo vkBufferCreateInfo = {};
  vkBufferCreateInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
  vkBufferCreateInfo.size = bufferSize;
  vkBufferCreateInfo.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
  vkBufferCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
  if (vkCreateBuffer(device, &vkBufferCreateInfo, nullptr, &bufferCopy) !=
      VK_SUCCESS) {
    throw std::runtime_error("Failed to create buffer for the copy data.");
  }
  VkMemoryRequirements bufferMemoryRequirements;
  vkGetBufferMemoryRequirements(device, bufferCopy, &bufferMemoryRequirements);
  {
    VkMemoryAllocateInfo vkMemoryAllocateInfo = {};
    vkMemoryAllocateInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    vkMemoryAllocateInfo.allocationSize = bufferMemoryRequirements.size;
    vkMemoryAllocateInfo.memoryTypeIndex =
        findMemoryType(bufferMemoryRequirements.memoryTypeBits,
                       VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, device);
    if (vkAllocateMemory(device, &vkMemoryAllocateInfo, nullptr,
                         &bufferCopyMemory) != VK_SUCCESS) {
      throw std::runtime_error("Failed to allocate memory for buffer copy.");
    }
  }
  if (vkBindBufferMemory(device, bufferCopy, bufferCopyMemory, 0) !=
      VK_SUCCESS) {
    throw std::runtime_error("Failed binding memory for buffer copy.");
  }

  // Create command pool
  VkCommandPoolCreateInfo commandPoolCreateInfo = {};
  commandPoolCreateInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
  commandPoolCreateInfo.queueFamilyIndex = queueFamilyIndex;
  if (vkCreateCommandPool(device, &commandPoolCreateInfo, nullptr,
                          &commandPool) != VK_SUCCESS) {
    throw std::runtime_error("Failed to create command pool.");
  }

  VkCommandBufferAllocateInfo vkCommandBufferAllocateInfo = {};
  vkCommandBufferAllocateInfo.sType =
      VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
  vkCommandBufferAllocateInfo.commandPool = commandPool;
  vkCommandBufferAllocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  vkCommandBufferAllocateInfo.commandBufferCount = 1;
  if (vkAllocateCommandBuffers(device, &vkCommandBufferAllocateInfo,
                               &commandBuffer) != VK_SUCCESS) {
    throw std::runtime_error("Failed to allocate command buffers.");
  }

  // Record a command buffer for the copy operation.
  {
    VkCommandBufferBeginInfo beginInfo = {};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    if (vkBeginCommandBuffer(commandBuffer, &beginInfo) != VK_SUCCESS) {
      throw std::runtime_error("Failed to begin recording command buffer.");
    }

    for (const auto &pipelineBarrier : pipelineBarriers) {

      // Copy all global and buffer memory barriers
      auto bufferMemoryBarriers =
          CopyArray(pipelineBarrier->pBufferMemoryBarriers_,
                    pipelineBarrier->bufferMemoryBarrierCount_);
      auto memoryBarriers = CopyArray(pipelineBarrier->pMemoryBarriers_,
                                      pipelineBarrier->memoryBarrierCount_);

      // Set dstAccessMask(s) to VK_ACCESS_HOST_READ_BIT
      for (uint32_t i = 0; i < pipelineBarrier->bufferMemoryBarrierCount_;
           i++) {
        bufferMemoryBarriers[i].dstAccessMask = VK_ACCESS_HOST_READ_BIT;
      }
      for (uint32_t i = 0; i < pipelineBarrier->memoryBarrierCount_; i++) {
        memoryBarriers[i].dstAccessMask = VK_ACCESS_HOST_READ_BIT;
      }

      // clang-format off
      vkCmdPipelineBarrier(
          commandBuffer,
          pipelineBarrier->srcStageMask_ | VK_PIPELINE_STAGE_TRANSFER_BIT,
          VK_PIPELINE_STAGE_HOST_BIT,
          0,
          pipelineBarrier->memoryBarrierCount_,
          memoryBarriers,
          pipelineBarrier->bufferMemoryBarrierCount_,
          bufferMemoryBarriers,
          0,
          nullptr
      );
      // clang-format on

      delete[] bufferMemoryBarriers;
      delete[] memoryBarriers;
    }
    VkBufferCopy copyRegion{0, 0, bufferSize};
    vkCmdCopyBuffer(commandBuffer, buffer, bufferCopy, 1, &copyRegion);

    if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
      throw std::runtime_error("Failed to record command buffer.");
    }
  }

  VkSubmitInfo submitInfo = {};
  submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
  submitInfo.commandBufferCount = 1;
  submitInfo.pCommandBuffers = &commandBuffer;
  vkQueueSubmit(queue, 1, &submitInfo, nullptr);

  vkDeviceWaitIdle(device); // Maybe use vkQueueWaitIdle() instead?

  // Invalidate memory to make it visible to host.
  {
    vkMapMemory(device, bufferCopyMemory, 0, bufferSize, 0, mappedMemory);
    VkMappedMemoryRange rangeToInvalidate = {};
    rangeToInvalidate.sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
    rangeToInvalidate.memory = bufferCopyMemory;
    rangeToInvalidate.offset = 0;
    rangeToInvalidate.size = VK_WHOLE_SIZE;
    vkInvalidateMappedMemoryRanges(device, 1, &rangeToInvalidate);
  }
}

void BufferCopy::freeResources() {
  // Unmap memory
  vkUnmapMemory(device, bufferCopyMemory);

  // Free resources
  vkFreeCommandBuffers(device, commandPool, 1, &commandBuffer);
  vkDestroyCommandPool(device, commandPool, nullptr);
  vkDestroyBuffer(device, bufferCopy, nullptr);
}

uint32_t BufferCopy::findMemoryType(uint32_t typeFilter,
                                    VkMemoryPropertyFlags properties,
                                    VkDevice device) {
  VkPhysicalDevice physicalDevice =
      GetGlobalContext().GetVkDeviceData(device)->physical_device;

  VkPhysicalDeviceMemoryProperties memProperties;

  PFN_vkGetPhysicalDeviceMemoryProperties fn =
      GetGlobalContext()
          .GetVkPhysicalDeviceData(physicalDevice)
          ->functions->vkGetPhysicalDeviceMemoryProperties;
  graphicsfuzz_amber_scoop::vkGetPhysicalDeviceMemoryProperties(
      fn, physicalDevice, &memProperties);

  for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++) {
    if ((typeFilter & (1U << i)) &&
        (memProperties.memoryTypes[i].propertyFlags & properties) ==
            properties) {
      return i;
    }
  }
  throw std::runtime_error("Failed to find suitable memory type.");
}

} // namespace graphicsfuzz_amber_scoop