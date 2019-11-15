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
#include <vector>

#include "source/fuzz/fuzzer.h"
#include "source/fuzz/protobufs/spirvfuzz_protobufs.h"
#include "source/opt/build_module.h"
#include "source/opt/ir_context.h"
#include "spirv-tools/libspirv.hpp"
#include "tools/io.h"

namespace graphicsfuzz_shader_fuzzer {

const char kPathSeparator =
#ifdef _WIN32
    '\\';
#else
    '/';
#endif

// Environment variable specifying where fuzzed shaders will be saved to.
const char *kWorkDirEnvironmentVariable = "GRAPHICSFUZZ_SHADER_FUZZER_WORK_DIR";

// Counter used as a source of shader module ids.
std::atomic_uint shader_module_counter(0);

// Returns an empty vector if fuzzing was not possible.  Otherwise, returns a
// vector representing the fuzzed version of the shader referred to by
// |pCreateInfo->pCode|.
std::vector<uint32_t>
TryFuzzingShader(VkShaderModuleCreateInfo const *pCreateInfo) {

  // Grab a new id for this shader module.
  uint32_t shader_module_id = shader_module_counter++;

  // Check whether the work directory environment variable is set.
  const char *work_dir = std::getenv(kWorkDirEnvironmentVariable);
  if (!work_dir) {
    std::cerr << "Environment variable " << kWorkDirEnvironmentVariable
              << " is not set; shaders will not be fuzzed." << std::endl;
    return std::vector<uint32_t>();
  }

  // TODO(afd): The required target environment should be queried.
  const spv_target_env target_env = SPV_ENV_UNIVERSAL_1_3;

  // |pCreateInfo->codeSize| gives the size in bytes; convert it to words.
  const uint32_t code_size_in_words =
      static_cast<uint32_t>(pCreateInfo->codeSize) / 4;

  spvtools::SpirvTools tools(target_env);
  if (!tools.IsValid()) {
    std::cerr << "Did not manage to create a SPIRV-Tools instance; shaders "
                 "will not be fuzzed."
              << std::endl;
    return std::vector<uint32_t>();
  }

  // Create a fuzzer and the various parameters required for fuzzing.
  spvtools::fuzz::Fuzzer fuzzer(target_env);
  std::vector<uint32_t> binary_in(pCreateInfo->pCode,
                                  pCreateInfo->pCode + code_size_in_words);
  std::vector<uint32_t> result;
  spvtools::fuzz::protobufs::FactSequence no_facts;
  spvtools::fuzz::protobufs::TransformationSequence transformation_sequence;
  spvtools::FuzzerOptions fuzzer_options;
  fuzzer_options.set_random_seed(shader_module_id);

  // Fuzz the shader into |result|.
  auto fuzzer_result_status = fuzzer.Run(binary_in, no_facts, fuzzer_options,
                                         &result, &transformation_sequence);

  if (fuzzer_result_status !=
      spvtools::fuzz::Fuzzer::FuzzerResultStatus::kComplete) {
    std::cerr << "Fuzzing failed." << std::endl;
    return std::vector<uint32_t>();
  }

  // Write out the original shader module
  {
    std::stringstream original_shader_binary_name;
    original_shader_binary_name << std::string(work_dir) << kPathSeparator
                                << "_original_" << shader_module_id
                                << ""
                                   ".spv";
    WriteFile<uint32_t>(original_shader_binary_name.str().c_str(), "wb",
                        pCreateInfo->pCode, code_size_in_words);
  }

  // Write out the fuzzed shader module
  {
    std::stringstream fuzzed_shader_binary_name;
    fuzzed_shader_binary_name << std::string(work_dir) << kPathSeparator
                              << "_fuzzed_" << shader_module_id
                              << ""
                                 ".spv";
    WriteFile<uint32_t>(fuzzed_shader_binary_name.str().c_str(), "wb",
                        result.data(), result.size());
  }

  // Write out the transformations
  {
    std::stringstream transformations_name;
    transformations_name << std::string(work_dir) << kPathSeparator << "_"
                         << shader_module_id << ".transformations";
    std::ofstream transformations_file;
    transformations_file.open(transformations_name.str(),
                              std::ios::out | std::ios::binary);
    transformation_sequence.SerializeToOstream(&transformations_file);
    transformations_file.close();
  }

  // Write out the transformations in JSON format
  {
    std::stringstream transformations_json_name;
    transformations_json_name << std::string(work_dir) << kPathSeparator << "_"
                              << shader_module_id << ".transformations_json";
    std::string json_string;
    auto json_options = google::protobuf::util::JsonOptions();
    json_options.add_whitespace = true;
    auto json_generation_status = google::protobuf::util::MessageToJsonString(
        transformation_sequence, &json_string, json_options);
    if (json_generation_status == google::protobuf::util::Status::OK) {
      std::ofstream transformations_json_file(transformations_json_name.str());
      transformations_json_file << json_string;
      transformations_json_file.close();
    }
  }

  return result;
}

VkResult vkCreateShaderModule(PFN_vkCreateShaderModule next, VkDevice device,
                              VkShaderModuleCreateInfo const *pCreateInfo,
                              AllocationCallbacks pAllocator,
                              VkShaderModule *pShaderModule) {
  // Fuzzing the provided shader will either yield an empty vector - if
  // something went wrong - or a vector whose contents is the fuzzed shader
  // binary.
  std::vector<uint32_t> fuzzed = TryFuzzingShader(pCreateInfo);

  VkShaderModuleCreateInfo fuzzed_shader_module_create_info;
  VkShaderModuleCreateInfo const *fuzzed_shader_module_create_info_pointer;
  if (!fuzzed.empty()) {
    // We succeeded in fuzzing the shader, so pass on a pointer to a new
    // VkShaderModuleCreateInfo object identical to the original, except with
    // the fuzzed shader data.
    fuzzed_shader_module_create_info_pointer =
        &fuzzed_shader_module_create_info;
    fuzzed_shader_module_create_info.sType = pCreateInfo->sType;
    fuzzed_shader_module_create_info.pNext = pCreateInfo->pNext;
    fuzzed_shader_module_create_info.flags = pCreateInfo->flags;
    fuzzed_shader_module_create_info.codeSize = fuzzed.size() * 4;
    fuzzed_shader_module_create_info.pCode = fuzzed.data();
  } else {
    // We did not succeed in fuzzing the shader, pass on the original
    // VkShaderModuleCreateInfo pointer.
    fuzzed_shader_module_create_info_pointer = pCreateInfo;
  }
  return next(device, fuzzed_shader_module_create_info_pointer, pAllocator,
              pShaderModule);
}

} // namespace graphicsfuzz_shader_fuzzer
