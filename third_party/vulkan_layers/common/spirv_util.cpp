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

#include "common/spirv_util.h"

namespace graphicsfuzz_vulkan_layers {

std::pair<bool, spv_target_env>
GetTargetEnvFromSpirvBinary(uint32_t version_number) {

  auto target_env = static_cast<spv_target_env>(0);

  auto major_version = (version_number >> 16) & 0xff;
  auto minor_version = (version_number >> 8) & 0xff;
  if (major_version != 1) {
    return {false, target_env};
  }

  switch (minor_version) {
    case 0:
      target_env = SPV_ENV_UNIVERSAL_1_0;
      break;
    case 1:
      target_env = SPV_ENV_UNIVERSAL_1_1;
      break;
    case 2:
      target_env = SPV_ENV_UNIVERSAL_1_2;
      break;
    case 3:
      target_env = SPV_ENV_UNIVERSAL_1_3;
      break;
    case 4:
      target_env = SPV_ENV_UNIVERSAL_1_4;
      break;
    case 5:
      target_env = SPV_ENV_UNIVERSAL_1_5;
      break;
    default:
      return {false, target_env};
  }
  return {true, target_env};
}


}  // graphicsfuzz_vulkan_layers