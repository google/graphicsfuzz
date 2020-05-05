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

#ifndef GRAPHICSFUZZ_VULKAN_LAYERS_BUFFER_COPY_H
#define GRAPHICSFUZZ_VULKAN_LAYERS_BUFFER_COPY_H

#include "layer_impl.h"
#include "vulkan_commands.h"
#include <memory>
#include <vulkan/vulkan.h>

namespace graphicsfuzz_amber_scoop {

class BufferCopy {

public:
  /**
   * Copies buffer contents from the given buffer to a host readable buffer.
   * Creates a new command buffer for the copy commands and submits the command
   * buffer to the given queue. Waits for the copy commands to finish before
   * returning from the function.
   *
   * @param queue Queue where the copy commands will be submitted to.
   * @param queueFamilyIndex Queue family index of the given queue. Used to
   * create a new command pool.
   * @param pipelineBarriers Pipeline barriers that must be waited before
   * copying can be performed.
   * @param buffer Buffer where the data is copied from.
   * @param bufferSize Size of the buffer in bytes.
   * @param mappedMemory Pointer to the host visible copied data.
   */
  void copyBuffer(
      VkQueue queue, uint32_t queueFamilyIndex,
      const std::vector<std::shared_ptr<CmdPipelineBarrier>> &pipelineBarriers,
      const VkBuffer &buffer, VkDeviceSize bufferSize, void **mappedMemory);

  void freeResources();

private:
  VkBuffer bufferCopy;
  VkDeviceMemory bufferCopyMemory;
  VkCommandPool commandPool;
  VkCommandBuffer commandBuffer;
  VkDevice device;

  uint32_t findMemoryType(uint32_t typeFilter,
                                 VkMemoryPropertyFlags properties,
                                 VkDevice physicalDevice);

}; // class BufferCopy

} // namespace graphicsfuzz_amber_scoop

#endif // GRAPHICSFUZZ_VULKAN_LAYERS_BUFFER_COPY_H
