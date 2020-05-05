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

#include <map>
#include <sstream>

#include "amber_scoop/layer.h"

namespace graphicsfuzz_amber_scoop {

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

}  // namespace graphicsfuzz_amber_scoop

#endif  // GRAPHICSFUZZ_VULKAN_LAYERS_LAYER_IMPL_H
