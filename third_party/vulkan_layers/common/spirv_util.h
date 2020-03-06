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

#ifndef GRAPHICSFUZZ_VULKAN_LAYERS_SPIRV_UTIL_H
#define GRAPHICSFUZZ_VULKAN_LAYERS_SPIRV_UTIL_H

#include <utility>

#include "spirv-tools/libspirv.hpp"

namespace graphicsfuzz_vulkan_layers {

// Attempts to deduce a SPIR-V target environment from |version_number|, which
// should be the second word of a SPIR-V module.
//
// Returns (false, _) if no SPIR-V target environment could be deduced from the
// binary.
//
// Otherwise returns (true, env), where env is the deduced target environment.
std::pair<bool, spv_target_env>
GetTargetEnvFromSpirvBinary(uint32_t version_number);

}  // graphicsfuzz_vulkan_layers

#endif //GRAPHICSFUZZ_VULKAN_LAYERS_SPIRV_UTIL_H
