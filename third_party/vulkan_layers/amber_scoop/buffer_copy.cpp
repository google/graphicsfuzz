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

void BufferCopy::CopyBuffer(
    VkQueue queue, uint32_t queue_family_index,
    const std::vector<std::shared_ptr<CmdPipelineBarrier>> &pipeline_barriers,
    const VkBuffer &buffer, VkDeviceSize buffer_size) {
  device_ = GetGlobalContext().GetVkQueueData(queue)->device;

  // Create a buffer where the data will be copied to.
  VkBufferCreateInfo vk_buffer_create_info = {};
  vk_buffer_create_info.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
  vk_buffer_create_info.size = buffer_size;
  vk_buffer_create_info.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
  vk_buffer_create_info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
  if (vkCreateBuffer(device_, &vk_buffer_create_info, nullptr, &buffer_copy_) !=
      VK_SUCCESS) {
    throw std::runtime_error("Failed to create buffer for the copy data.");
  }
  VkMemoryRequirements memory_requirements;
  vkGetBufferMemoryRequirements(device_, buffer_copy_, &memory_requirements);
  {
    VkMemoryAllocateInfo vkMemoryAllocateInfo = {};
    vkMemoryAllocateInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    vkMemoryAllocateInfo.allocationSize = memory_requirements.size;
    vkMemoryAllocateInfo.memoryTypeIndex =
        FindMemoryType(memory_requirements.memoryTypeBits,
                       VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
    if (vkAllocateMemory(device_, &vkMemoryAllocateInfo, nullptr,
                         &buffer_copy_memory_) != VK_SUCCESS) {
      throw std::runtime_error("Failed to allocate memory for buffer copy.");
    }
  }
  if (vkBindBufferMemory(device_, buffer_copy_, buffer_copy_memory_, 0) !=
      VK_SUCCESS) {
    throw std::runtime_error("Failed binding memory for buffer copy.");
  }

  // Create command pool
  VkCommandPoolCreateInfo command_pool_create_info = {};
  command_pool_create_info.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
  command_pool_create_info.queueFamilyIndex = queue_family_index;
  if (vkCreateCommandPool(device_, &command_pool_create_info, nullptr,
                          &command_pool_) != VK_SUCCESS) {
    throw std::runtime_error("Failed to create command pool.");
  }

  VkCommandBufferAllocateInfo command_buffer_allocate_info = {};
  command_buffer_allocate_info.sType =
      VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
  command_buffer_allocate_info.commandPool = command_pool_;
  command_buffer_allocate_info.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  command_buffer_allocate_info.commandBufferCount = 1;
  if (vkAllocateCommandBuffers(device_, &command_buffer_allocate_info,
                               &command_buffer_) != VK_SUCCESS) {
    throw std::runtime_error("Failed to allocate command buffers.");
  }

  // Record a command buffer for the copy operation.
  {
    VkCommandBufferBeginInfo begin_info = {};
    begin_info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    if (vkBeginCommandBuffer(command_buffer_, &begin_info) != VK_SUCCESS) {
      throw std::runtime_error("Failed to begin recording command buffer.");
    }

    for (const auto &pipeline_barrier : pipeline_barriers) {
      // Copy all global and buffer memory barriers
      auto buffer_memory_barriers =
          CopyArray(pipeline_barrier->pBufferMemoryBarriers_,
                    pipeline_barrier->bufferMemoryBarrierCount_);
      auto memory_barriers = CopyArray(pipeline_barrier->pMemoryBarriers_,
                                       pipeline_barrier->memoryBarrierCount_);

      // Set dstAccessMask(s) to VK_ACCESS_HOST_READ_BIT
      for (uint32_t i = 0; i < pipeline_barrier->bufferMemoryBarrierCount_;
           i++) {
        buffer_memory_barriers[i].dstAccessMask = VK_ACCESS_HOST_READ_BIT;
      }
      for (uint32_t i = 0; i < pipeline_barrier->memoryBarrierCount_; i++) {
        memory_barriers[i].dstAccessMask = VK_ACCESS_HOST_READ_BIT;
      }

      // clang-format off
      vkCmdPipelineBarrier(
          command_buffer_,
          pipeline_barrier->srcStageMask_ | VK_PIPELINE_STAGE_TRANSFER_BIT,
          VK_PIPELINE_STAGE_HOST_BIT,
          0,
          pipeline_barrier->memoryBarrierCount_,
          memory_barriers,
          pipeline_barrier->bufferMemoryBarrierCount_,
          buffer_memory_barriers,
          0,
          nullptr
      );
      // clang-format on

      delete[] buffer_memory_barriers;
      delete[] memory_barriers;
    }
    VkBufferCopy copy_region = {0, 0, buffer_size};
    vkCmdCopyBuffer(command_buffer_, buffer, buffer_copy_, 1, &copy_region);

    if (vkEndCommandBuffer(command_buffer_) != VK_SUCCESS) {
      throw std::runtime_error("Failed to record command buffer.");
    }
  }

  VkSubmitInfo submit_info = {};
  submit_info.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
  submit_info.commandBufferCount = 1;
  submit_info.pCommandBuffers = &command_buffer_;
  vkQueueSubmit(queue, 1, &submit_info, nullptr);

  // TODO: Maybe use vkQueueWaitIdle() or fence instead?
  vkQueueWaitIdle(queue);

  // Invalidate memory to make it visible to host.
  {
    vkMapMemory(device_, buffer_copy_memory_, 0, buffer_size, 0, &copied_data_);
    VkMappedMemoryRange range_to_invalidate = {};
    range_to_invalidate.sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
    range_to_invalidate.memory = buffer_copy_memory_;
    range_to_invalidate.offset = 0;
    range_to_invalidate.size = VK_WHOLE_SIZE;
    vkInvalidateMappedMemoryRanges(device_, 1, &range_to_invalidate);
  }
}

void BufferCopy::FreeResources() {
  // Unmap memory
  vkUnmapMemory(device_, buffer_copy_memory_);

  // Free resources
  vkFreeCommandBuffers(device_, command_pool_, 1, &command_buffer_);
  vkDestroyCommandPool(device_, command_pool_, nullptr);
  vkDestroyBuffer(device_, buffer_copy_, nullptr);
}

uint32_t BufferCopy::FindMemoryType(uint32_t typeFilter,
                                    VkMemoryPropertyFlags properties) {
  VkPhysicalDevice physicalDevice =
      GetGlobalContext().GetVkDeviceData(device_)->physical_device;

  VkPhysicalDeviceMemoryProperties memory_properties;

  PFN_vkGetPhysicalDeviceMemoryProperties fn =
      GetGlobalContext()
          .GetVkPhysicalDeviceData(physicalDevice)
          ->functions->vkGetPhysicalDeviceMemoryProperties;
  graphicsfuzz_amber_scoop::vkGetPhysicalDeviceMemoryProperties(
      fn, physicalDevice, &memory_properties);

  for (uint32_t i = 0; i < memory_properties.memoryTypeCount; i++) {
    if ((typeFilter & (1U << i)) &&
        (memory_properties.memoryTypes[i].propertyFlags & properties) ==
            properties) {
      return i;
    }
  }
  throw std::runtime_error("Failed to find suitable memory type.");
}

}  // namespace graphicsfuzz_amber_scoop