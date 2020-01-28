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
#include <iostream>
#include <memory>
#include <sstream>

#include "source/opt/build_module.h"
#include "source/opt/ir_context.h"
#include "spirv-tools/libspirv.hpp"
#include "tools/io.h"

namespace graphicsfuzz_shader_scraper {

const char kPathSeparator =
#ifdef _WIN32
    '\\';
#else
    '/';
#endif

// Environment variable specifying where shaders will be scraped to.
const char *kWorkDirEnvironmentVariable =
    "GRAPHICSFUZZ_SHADER_SCRAPER_WORK_DIR";

// Counter used as a source of shader module ids.
std::atomic_uint shader_module_counter(0);

// Attempts to save out the shader module stored in |pCreateInfo->pCode|,
// giving it an execution model-specific extension if it contains a single entry
// point.
void TryScrapingShader(VkShaderModuleCreateInfo const *pCreateInfo) {

  // Grab an id for the shader module.
  uint32_t shader_module_id = shader_module_counter++;

  // Check whether the work directory environment variable is set.
  const char *work_dir = std::getenv(kWorkDirEnvironmentVariable);
  if (!work_dir) {
    std::cerr << "Environment variable " << kWorkDirEnvironmentVariable
              << " is not set; shaders will not be scraped." << std::endl;
    return;
  }

  // TODO(afd): The required target environment should be queried.
  const spv_target_env target_env = SPV_ENV_UNIVERSAL_1_3;

  // |pCreateInfo->codeSize| gives the size in bytes; convert it to words.
  const uint32_t code_size_in_words =
      static_cast<uint32_t>(pCreateInfo->codeSize) / 4;

  spvtools::SpirvTools tools(target_env);
  if (!tools.IsValid()) {
    std::cerr << "Did not manage to create a SPIRV-Tools instance; shaders "
                 "will not be scraped."
              << std::endl;
    return;
  }

  // Try to figure out a reasonable extension for the shader module, based on
  // the entry point(s) it contains.
  std::string extension_prefix = "none";
  if (!tools.Validate(pCreateInfo->pCode, code_size_in_words)) {
    // Save out the shader even if it is invalid, but indicate invalidity in
    // the extension.
    extension_prefix = "invalid";
  } else {
    // Parse the shader module so that we can inspect its entry point(s).
    std::unique_ptr<spvtools::opt::IRContext> ir_context =
        spvtools::BuildModule(target_env, nullptr, pCreateInfo->pCode,
                              code_size_in_words);

    // Inspect at most two entry points in the shader module.
    for (auto &entry_point : ir_context->module()->entry_points()) {
      if (extension_prefix != "none") {
        // We have seen an entry point already, so there must be multiple of
        // them.  Record this in the extension, and stop looking at entry
        // points.
        extension_prefix = "many";
        break;
      }
      // Choose an extension based on the execution model of the entry point.
      switch (
          ir_context->module()->entry_points().begin()->GetSingleWordInOperand(
              0)) {
      case SpvExecutionModelFragment:
        extension_prefix = "frag";
        break;
      case SpvExecutionModelGeometry:
        extension_prefix = "geom";
        break;
      case SpvExecutionModelGLCompute:
        extension_prefix = "comp";
        break;
      case SpvExecutionModelVertex:
        extension_prefix = "vert";
        break;
      case SpvExecutionModelTessellationControl:
        extension_prefix = "tesc";
        break;
      case SpvExecutionModelTessellationEvaluation:
        extension_prefix = "tese";
        break;
      default:
        extension_prefix = "other";
        break;
      }
    }
  }
  // Write out the scraped shader module
  std::stringstream shader_module_name;
  shader_module_name << std::string(work_dir) << kPathSeparator
                     << "_scraped_shader_" << shader_module_id << "."
                     << extension_prefix << ".spv";
  WriteFile<uint32_t>(shader_module_name.str().c_str(), "wb",
                      pCreateInfo->pCode, code_size_in_words);
}

VkResult vkCreateShaderModule(PFN_vkCreateShaderModule next, VkDevice device,
                              VkShaderModuleCreateInfo const *pCreateInfo,
                              AllocationCallbacks pAllocator,
                              VkShaderModule *pShaderModule) {
  TryScrapingShader(pCreateInfo);
  return next(device, pCreateInfo, pAllocator, pShaderModule);
}

} // namespace graphicsfuzz_shader_scraper
