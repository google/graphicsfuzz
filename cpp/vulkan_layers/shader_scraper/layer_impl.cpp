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

#include "layer.h"

#include <atomic>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <memory>
#include <sstream>

#include "source/opt/build_module.h"
#include "source/opt/ir_context.h"
#include "spirv-tools/libspirv.hpp"

namespace graphicsfuzz_shader_scraper {

const char kPathSeparator =
#ifdef _WIN32
        '\\';
#else
        '/';
#endif

std::atomic_uint shader_counter(0);

void TryScrapingShader(VkShaderModuleCreateInfo const* pCreateInfo) {

  uint32_t shader_id = shader_counter++;

  std::string dump_dir_environment_variable =
          "GRAPHICSFUZZ_SHADER_SCRAPER_DUMP_DIR";

  const char* dump_dir = std::getenv(dump_dir_environment_variable.c_str());
  if (!dump_dir) {
    std::cerr << "Environment variable " << dump_dir_environment_variable <<
              " is not set; shaders will not be scraped." << std::endl;
    return;
  }
  const spv_target_env target_env = SPV_ENV_UNIVERSAL_1_3;
  const uint32_t code_size_in_words =
          static_cast<uint32_t>(pCreateInfo->codeSize) / 4;
  spvtools::SpirvTools tools(target_env);
  if (!tools.IsValid()) {
    std::cerr << "Did not manage to create a SPIRV-Tools instance; shaders will not be scraped." << std::endl;
    return;
  }
  std::string extension_prefix = "none";
  if (!tools.Validate(pCreateInfo->pCode, code_size_in_words)) {
    extension_prefix = "invalid";
  } else {
    std::unique_ptr<spvtools::opt::IRContext> ir_context = spvtools::BuildModule(
            target_env, nullptr, pCreateInfo->pCode,
            code_size_in_words);
    for (auto& entry_point : ir_context->module()->entry_points()) {
      if (extension_prefix != "none") {
        extension_prefix = "many";
        break;
      }
      switch (ir_context->module()->entry_points().begin()
              ->GetSingleWordInOperand(0)) {
        case SpvExecutionModelFragment:
          extension_prefix = "frag";
          break;
        case SpvExecutionModelVertex:
          extension_prefix = "vert";
          break;
        default:
          extension_prefix = "other";
          break;
      }
    }
  }
  std::stringstream strstr;
  strstr << std::string(dump_dir)
         << kPathSeparator
         << "_captured_shader_"
         << shader_id
         << "."
         << extension_prefix
         << ".spv";
  std::ofstream shader;
  shader.open(strstr.str().c_str(), std::ios::out | std::ios::binary);
  shader.write((const char*)pCreateInfo->pCode, pCreateInfo->codeSize);
  shader.close();
}

VkResult vkCreateShaderModule(PFN_vkCreateShaderModule next, VkDevice device, VkShaderModuleCreateInfo const* pCreateInfo, AllocationCallbacks pAllocator, VkShaderModule* pShaderModule) {
  TryScrapingShader(pCreateInfo);
  return next(device, pCreateInfo, pAllocator, pShaderModule);
}

}
