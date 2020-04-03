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

#include <cassert>
#include <map>
#include <sstream>
#include "amber_scoop/layer.h"

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

enum FormatType {
  tInt8 = 0,
  tInt16 = 1,
  tInt32 = 2,
  tInt64 = 3,
  tUint8 = 4,
  tUint16 = 5,
  tUint32 = 6,
  tUint64 = 7,
  tFloat = 8,
  tDouble = 9
};

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
    {VK_PRIMITIVE_TOPOLOGY_PATCH_LIST, "PATCH_LIST"}
};

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

uint32_t getComponentCount(VkFormat vkFormat) {
  switch (vkFormat) {
  case VK_FORMAT_R32G32B32A32_SFLOAT:
  case VK_FORMAT_R32G32B32A32_UINT:
  case VK_FORMAT_R32G32B32A32_SINT:
    return 4;
  case VK_FORMAT_R32G32B32_SFLOAT:
  case VK_FORMAT_R32G32B32_UINT:
  case VK_FORMAT_R32G32B32_SINT:
    return 3;
  case VK_FORMAT_R32G32_SFLOAT:
  case VK_FORMAT_R32G32_UINT:
  case VK_FORMAT_R32G32_SINT:
    return 2;
  case VK_FORMAT_R32_SFLOAT:
  case VK_FORMAT_R32_UINT:
  case VK_FORMAT_R32_SINT:
    return 1;
  default:
    assert(false && "Unknown format.");
  }
  // TODO: implement other formats
  return 0;
}


uint32_t getComponentWidth(VkFormat vkFormat) {
  switch (vkFormat) {
  case VK_FORMAT_R32G32B32A32_SFLOAT:
  case VK_FORMAT_R32G32B32A32_UINT:
  case VK_FORMAT_R32G32B32A32_SINT:
  case VK_FORMAT_R32G32B32_SFLOAT:
  case VK_FORMAT_R32G32B32_UINT:
  case VK_FORMAT_R32G32B32_SINT:
  case VK_FORMAT_R32G32_SFLOAT:
  case VK_FORMAT_R32G32_UINT:
  case VK_FORMAT_R32G32_SINT:
  case VK_FORMAT_R32_SFLOAT:
  case VK_FORMAT_R32_UINT:
  case VK_FORMAT_R32_SINT:
    return 4;
  default:
    assert(false && "Unknown format.");
  }
  // TODO: implement other formats
  return 0;
}

std::string getFormatTypeName(VkFormat vkFormat) {
  switch (vkFormat) {
  case VK_FORMAT_R32G32B32A32_SFLOAT:
  case VK_FORMAT_R32G32B32_SFLOAT:
  case VK_FORMAT_R32G32_SFLOAT:
  case VK_FORMAT_R32_SFLOAT:
    return "float";
  case VK_FORMAT_R32G32B32A32_UINT:
  case VK_FORMAT_R32G32B32_UINT:
  case VK_FORMAT_R32G32_UINT:
  case VK_FORMAT_R32_UINT:
    return "uint32";
  case VK_FORMAT_R32G32B32A32_SINT:
  case VK_FORMAT_R32G32B32_SINT:
  case VK_FORMAT_R32G32_SINT:
  case VK_FORMAT_R32_SINT:
    return "int32";
  default:
    assert(false && "Unknown format.");
  }
  // TODO: implement other formats
}

std::string getBufferTypeName(VkFormat vkFormat) {
  auto componentCount = getComponentCount(vkFormat);
  if (componentCount == 1) {
    return getFormatTypeName(vkFormat);
  }

  std::stringstream strStream;
  strStream << "vec" << componentCount << "<" << getFormatTypeName(vkFormat)
            << ">";
  return strStream.str();
}

FormatType getFormatTypeCode(VkFormat vkFormat) {
  switch (vkFormat) {
  case VK_FORMAT_R32G32B32A32_SFLOAT:
  case VK_FORMAT_R32G32B32_SFLOAT:
  case VK_FORMAT_R32G32_SFLOAT:
  case VK_FORMAT_R32_SFLOAT:
    return tFloat;
  case VK_FORMAT_R32G32B32A32_UINT:
  case VK_FORMAT_R32G32B32_UINT:
  case VK_FORMAT_R32G32_UINT:
  case VK_FORMAT_R32_UINT:
    return tUint32;
  case VK_FORMAT_R32G32B32A32_SINT:
  case VK_FORMAT_R32G32B32_SINT:
  case VK_FORMAT_R32G32_SINT:
  case VK_FORMAT_R32_SINT:
    return tInt32;
  default:
    assert(false && "Unknown format.");
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

} // namespace graphicsfuzz_amber_scoop

#endif // GRAPHICSFUZZ_VULKAN_LAYERS_LAYER_IMPL_H
