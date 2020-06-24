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

#ifndef GRAPHICSFUZZ_VULKAN_LAYERS_VULKAN_COMMANDS_H
#define GRAPHICSFUZZ_VULKAN_LAYERS_VULKAN_COMMANDS_H

#include <vulkan/vulkan_core.h>

#include <cstring>

#include "vk_deep_copy.h"

namespace graphicsfuzz_amber_scoop {

struct CmdBeginRenderPass;
struct CmdBindDescriptorSets;
struct CmdBindIndexBuffer;
struct CmdBindPipeline;
struct CmdBindVertexBuffers;
struct CmdCopyBuffer;
struct CmdCopyBufferToImage;
struct CmdDraw;
struct CmdDrawIndexed;
struct CmdPipelineBarrier;
struct CmdPushConstants;

struct Cmd {
  enum Kind {
    kBeginRenderPass,
    kBindDescriptorSets,
    kBindIndexBuffer,
    kBindPipeline,
    kBindVertexBuffers,
    kCopyBuffer,
    kCopyBufferToImage,
    kDraw,
    kDrawIndexed,
    kPipelineBarrier,
    kPushConstants
  };

  explicit Cmd(Kind kind) : kind_(kind) {}

  virtual ~Cmd() = default;

// A bunch of methods for casting this type to a given type. Returns this if the
// cast can be done, nullptr otherwise.
// clang-format off
#define DeclareCastMethod(target)                                              \
  virtual Cmd##target *As##target() { return nullptr; }                        \
  virtual const Cmd##target *As##target() const { return nullptr; }
  DeclareCastMethod(BeginRenderPass)
  DeclareCastMethod(BindDescriptorSets)
  DeclareCastMethod(BindIndexBuffer)
  DeclareCastMethod(BindPipeline)
  DeclareCastMethod(BindVertexBuffers)
  DeclareCastMethod(CopyBuffer)
  DeclareCastMethod(CopyBufferToImage)
  DeclareCastMethod(Draw)
  DeclareCastMethod(DrawIndexed)
  DeclareCastMethod(PipelineBarrier)
  DeclareCastMethod(PushConstants)
#undef DeclareCastMethod

  Kind kind_;
  // clang-format on
};

struct CmdBeginRenderPass : public Cmd {
  CmdBeginRenderPass(VkRenderPassBeginInfo const *pRenderPassBegin,
                     VkSubpassContents contents)
      : Cmd(kBeginRenderPass),
        render_pass_begin_(DeepCopy(pRenderPassBegin)),
        contents_(contents) {}

  CmdBeginRenderPass *AsBeginRenderPass() override { return this; }
  const CmdBeginRenderPass *AsBeginRenderPass() const override { return this; }

  ~CmdBeginRenderPass() override {
    DeepDelete(render_pass_begin_);
  }

  VkRenderPassBeginInfo render_pass_begin_;
  VkSubpassContents contents_;
};

struct CmdBindDescriptorSets : public Cmd {
  CmdBindDescriptorSets(VkPipelineBindPoint pipelineBindPoint,
                        VkPipelineLayout layout, uint32_t firstSet,
                        uint32_t descriptorSetCount,
                        VkDescriptorSet const *pDescriptorSets,
                        uint32_t dynamicOffsetCount,
                        uint32_t const *pDynamicOffsets)
      : Cmd(kBindDescriptorSets),
        pipelineBindPoint_(pipelineBindPoint),
        layout_(layout),
        firstSet_(firstSet) {
    // Copy the whole array including descriptors before the "firstSet".
    if (descriptorSetCount)
      descriptor_sets_.insert(descriptor_sets_.end(), pDescriptorSets,
                              pDescriptorSets + descriptorSetCount + firstSet);
    if (dynamicOffsetCount)
      dynamic_offsets_.insert(dynamic_offsets_.end(), pDynamicOffsets,
                              pDynamicOffsets + dynamicOffsetCount);
  }

  CmdBindDescriptorSets *AsBindDescriptorSets() override { return this; }
  const CmdBindDescriptorSets *AsBindDescriptorSets() const override {
    return this;
  }

  VkPipelineBindPoint pipelineBindPoint_;
  VkPipelineLayout layout_;
  uint32_t firstSet_;
  std::vector<VkDescriptorSet> descriptor_sets_;
  std::vector<uint32_t> dynamic_offsets_;
};

struct CmdBindIndexBuffer : public Cmd {
  CmdBindIndexBuffer(VkBuffer buffer, VkDeviceSize offset,
                     VkIndexType indexType)
      : Cmd(kBindIndexBuffer),
        buffer_(buffer),
        offset_(offset),
        indexType_(indexType) {}

  CmdBindIndexBuffer *AsBindIndexBuffer() override { return this; }
  const CmdBindIndexBuffer *AsBindIndexBuffer() const override { return this; }

  VkBuffer buffer_;
  VkDeviceSize offset_;
  VkIndexType indexType_;
};

struct CmdBindPipeline : public Cmd {
  CmdBindPipeline(VkPipelineBindPoint pipelineBindPoint, VkPipeline pipeline)
      : Cmd(kBindPipeline),
        pipelineBindPoint_(pipelineBindPoint),
        pipeline_(pipeline) {}

  CmdBindPipeline *AsBindPipeline() override { return this; }
  const CmdBindPipeline *AsBindPipeline() const override { return this; }

  VkPipelineBindPoint pipelineBindPoint_;
  VkPipeline pipeline_;
};

struct CmdBindVertexBuffers : public Cmd {
  CmdBindVertexBuffers(uint32_t firstBinding, uint32_t bindingCount,
                       VkBuffer const *pBuffers, VkDeviceSize const *pOffsets)
      : Cmd(kBindVertexBuffers),
        firstBinding_(firstBinding),
        bindingCount_(bindingCount) {
    buffers_.insert(buffers_.end(), pBuffers, pBuffers + firstBinding + bindingCount);
    offsets_.insert(offsets_.end(), pOffsets, pOffsets + firstBinding + bindingCount);
  }

  CmdBindVertexBuffers *AsBindVertexBuffers() override { return this; }
  const CmdBindVertexBuffers *AsBindVertexBuffers() const override {
    return this;
  }

  uint32_t firstBinding_;
  uint32_t bindingCount_;
  std::vector<VkBuffer> buffers_;
  std::vector<VkDeviceSize> offsets_;
};

struct CmdCopyBuffer : public Cmd {
  CmdCopyBuffer(VkBuffer srcBuffer, VkBuffer dstBuffer, uint32_t regionCount,
                VkBufferCopy const *pRegions)
      : Cmd(kCopyBuffer),
        srcBuffer_(srcBuffer),
        dstBuffer_(dstBuffer) {
    regions_.insert(regions_.end(), pRegions, pRegions + regionCount);
  }

  CmdCopyBuffer *AsCopyBuffer() override { return this; }
  const CmdCopyBuffer *AsCopyBuffer() const override { return this; }

  VkBuffer srcBuffer_;
  VkBuffer dstBuffer_;
  std::vector<VkBufferCopy> regions_;
};

struct CmdCopyBufferToImage : public Cmd {
  CmdCopyBufferToImage(VkBuffer srcBuffer, VkImage dstImage,
                       VkImageLayout dstImageLayout, uint32_t regionCount,
                       VkBufferImageCopy const *pRegions)
      : Cmd(kCopyBufferToImage),
        srcBuffer_(srcBuffer),
        dstImage_(dstImage),
        dstImageLayout_(dstImageLayout) {
    regions_.insert(regions_.end(), pRegions, pRegions + regionCount);
  }

  CmdCopyBufferToImage *AsCopyBufferToImage() override { return this; }
  const CmdCopyBufferToImage *AsCopyBufferToImage() const override {
    return this;
  }

  VkBuffer srcBuffer_;
  VkImage dstImage_;
  VkImageLayout dstImageLayout_;
  std::vector<VkBufferImageCopy> regions_;
};

struct CmdDraw : public Cmd {
  CmdDraw(uint32_t vertexCount, uint32_t instanceCount, uint32_t firstVertex,
          uint32_t firstInstance)
      : Cmd(kDraw),
        vertexCount_(vertexCount),
        instanceCount_(instanceCount),
        firstVertex_(firstVertex),
        firstInstance_(firstInstance) {}

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
      : Cmd(kDrawIndexed),
        indexCount_(indexCount),
        instanceCount_(instanceCount),
        firstIndex_(firstIndex),
        vertexOffset_(vertexOffset),
        firstInstance_(firstInstance) {}

  CmdDrawIndexed *AsDrawIndexed() override { return this; }
  const CmdDrawIndexed *AsDrawIndexed() const override { return this; }

  uint32_t indexCount_;
  uint32_t instanceCount_;
  uint32_t firstIndex_;
  int32_t vertexOffset_;
  uint32_t firstInstance_;
};

struct CmdPipelineBarrier : public Cmd {
  CmdPipelineBarrier(VkPipelineStageFlags srcStageMask,
                     VkPipelineStageFlags dstStageMask,
                     VkDependencyFlags dependencyFlags,
                     uint32_t memoryBarrierCount,
                     VkMemoryBarrier const *pMemoryBarriers,
                     uint32_t bufferMemoryBarrierCount,
                     VkBufferMemoryBarrier const *pBufferMemoryBarriers,
                     uint32_t imageMemoryBarrierCount,
                     VkImageMemoryBarrier const *pImageMemoryBarriers)
      : Cmd(kPipelineBarrier),
        srcStageMask_(srcStageMask),
        dstStageMask_(dstStageMask),
        dependencyFlags_(dependencyFlags) {
    if (memoryBarrierCount)
      memory_barriers_.insert(memory_barriers_.end(), pMemoryBarriers,
                              pMemoryBarriers + memoryBarrierCount);
    if (bufferMemoryBarrierCount)
      buffer_memory_barriers_.insert(
          buffer_memory_barriers_.end(), pBufferMemoryBarriers,
          pBufferMemoryBarriers + bufferMemoryBarrierCount);
    if (bufferMemoryBarrierCount)
      image_memory_barriers_.insert(
          image_memory_barriers_.end(), pImageMemoryBarriers,
          pImageMemoryBarriers + imageMemoryBarrierCount);
  }

  CmdPipelineBarrier *AsPipelineBarrier() override { return this; }
  const CmdPipelineBarrier *AsPipelineBarrier() const override { return this; }

  VkPipelineStageFlags srcStageMask_;
  VkPipelineStageFlags dstStageMask_;
  VkDependencyFlags dependencyFlags_;
  std::vector<VkMemoryBarrier> memory_barriers_;
  std::vector<VkBufferMemoryBarrier> buffer_memory_barriers_;
  std::vector<VkImageMemoryBarrier> image_memory_barriers_;
};

struct CmdPushConstants : public Cmd {
  CmdPushConstants(VkPipelineLayout layout, VkShaderStageFlags stageFlags,
                   uint32_t offset, uint32_t size, void const *pValues)
      : Cmd(kPushConstants),
        layout_(layout),
        stageFlags_(stageFlags),
        offset_(offset),
        size_(size) {
    auto push_constant_mem = new uint8_t[size];
    memcpy(push_constant_mem, pValues, size);
    pValues_ = push_constant_mem;
  }

  CmdPushConstants *AsPushConstants() override { return this; }
  const CmdPushConstants *AsPushConstants() const override { return this; }

  VkPipelineLayout layout_;
  VkShaderStageFlags stageFlags_;
  uint32_t offset_;
  uint32_t size_;
  void const *pValues_;
};

}  // namespace graphicsfuzz_amber_scoop

#endif  // GRAPHICSFUZZ_VULKAN_LAYERS_VULKAN_COMMANDS_H
