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

#ifndef GRAPHICSFUZZ_VULKAN_LAYERS_LAYER_IMPL_H
#define GRAPHICSFUZZ_VULKAN_LAYERS_LAYER_IMPL_H

#include "amber_scoop/layer.h"
#include "vulkan_formats.h"
#include <cassert>
#include <map>
#include <sstream>

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
struct BufferCopy;

const std::map<VkPrimitiveTopology, std::string> topologies = {
    {VK_PRIMITIVE_TOPOLOGY_POINT_LIST, "POINT_LIST"},
    {VK_PRIMITIVE_TOPOLOGY_LINE_LIST, "LINE_LIST"},
    {VK_PRIMITIVE_TOPOLOGY_LINE_STRIP, "LINE_STRIP"},
    {VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, "TRIANGLE_LIST"},
    {VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP, "TRIANGLE_STRIP"},
    {VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN, "TRIANGLE_FAN"},
    {VK_PRIMITIVE_TOPOLOGY_LINE_LIST_WITH_ADJACENCY,
     "LINE_LIST_WITH_ADJACENCY"},
    {VK_PRIMITIVE_TOPOLOGY_LINE_STRIP_WITH_ADJACENCY,
     "LINE_STRIP_WITH_ADJACENCY"},
    {VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST_WITH_ADJACENCY,
     "TRIANGLE_LIST_WITH_ADJACENCY"},
    {VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP_WITH_ADJACENCY,
     "TRIANGLE_STRIP_WITH_ADJACENCY"},
    {VK_PRIMITIVE_TOPOLOGY_PATCH_LIST, "PATCH_LIST"}};

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
    kDrawIndexed
  };

  explicit Cmd(Kind kind) : kind_(kind) {}

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
#undef DeclareCastMethod

  Kind kind_;
  // clang-format on
};

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

void readComponentsFromBufferAndWriteToStrStream(char *buffer,
                                                 vkf::VulkanFormat format,
                                                 std::stringstream &bufStr);

} // namespace graphicsfuzz_amber_scoop

#endif // GRAPHICSFUZZ_VULKAN_LAYERS_LAYER_IMPL_H
