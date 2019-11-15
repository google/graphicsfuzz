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

std::atomic_uint shader_counter(0);

std::vector<uint32_t>
TryFuzzingShader(VkShaderModuleCreateInfo const *pCreateInfo) {

  uint32_t shader_id = shader_counter++;

  std::string work_dir_environment_variable =
      "GRAPHICSFUZZ_SHADER_FUZZER_WORK_DIR";

  const char *work_dir = std::getenv(work_dir_environment_variable.c_str());
  if (!work_dir) {
    std::cerr << "Environment variable " << work_dir_environment_variable
              << " is not set; shaders will not be fuzzed." << std::endl;
    return std::vector<uint32_t>();
  }
  const spv_target_env target_env = SPV_ENV_UNIVERSAL_1_3;
  const uint32_t code_size_in_words =
      static_cast<uint32_t>(pCreateInfo->codeSize) / 4;
  spvtools::SpirvTools tools(target_env);
  if (!tools.IsValid()) {
    std::cerr << "Did not manage to create a SPIRV-Tools instance; shaders "
                 "will not be fuzzed."
              << std::endl;
    return std::vector<uint32_t>();
  }
  spvtools::fuzz::Fuzzer fuzzer(target_env);
  std::vector<uint32_t> binary_in(pCreateInfo->pCode,
                                  pCreateInfo->pCode + code_size_in_words);
  std::vector<uint32_t> result;
  spvtools::fuzz::protobufs::FactSequence no_facts;
  spvtools::fuzz::protobufs::TransformationSequence transformation_sequence;

  spvtools::FuzzerOptions fuzzer_options;
  fuzzer_options.set_random_seed(shader_id);

  auto fuzzer_result_status = fuzzer.Run(binary_in, no_facts, fuzzer_options,
                                         &result, &transformation_sequence);
  if (fuzzer_result_status !=
      spvtools::fuzz::Fuzzer::FuzzerResultStatus::kComplete) {
    std::cerr << "Fuzzing failed." << std::endl;
    return std::vector<uint32_t>();
  }

  // Write out the binary
  {
    std::stringstream shader_binary_name;
    shader_binary_name << "_fuzzed_" << shader_id << ".spv";
    WriteFile<uint32_t>(shader_binary_name.str().c_str(), "wb", result.data(),
                        result.size());
  }

  // Write out the transformations
  {
    std::stringstream transformations_name;
    transformations_name << "_fuzzed_" << shader_id << ".transformations";
    std::ofstream transformations_file;
    transformations_file.open(transformations_name.str(),
                              std::ios::out | std::ios::binary);
    transformation_sequence.SerializeToOstream(&transformations_file);
    transformations_file.close();
  }

  // Write out the transformations in JSON format
  {
    std::stringstream transformations_json_name;
    transformations_json_name << "_fuzzed_" << shader_id
                              << ".transformations_json";
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
  std::vector<uint32_t> fuzzed = TryFuzzingShader(pCreateInfo);
  VkShaderModuleCreateInfo fuzzed_shader_module_create_info;
  VkShaderModuleCreateInfo const *fuzzed_shader_module_create_info_pointer;
  if (!fuzzed.empty()) {
    fuzzed_shader_module_create_info_pointer =
        &fuzzed_shader_module_create_info;
    fuzzed_shader_module_create_info.sType = pCreateInfo->sType;
    fuzzed_shader_module_create_info.pNext = pCreateInfo->pNext;
    fuzzed_shader_module_create_info.flags = pCreateInfo->flags;
    fuzzed_shader_module_create_info.codeSize = fuzzed.size() * 4;
    fuzzed_shader_module_create_info.pCode = fuzzed.data();
  } else {
    fuzzed_shader_module_create_info_pointer = pCreateInfo;
  }
  return next(device, fuzzed_shader_module_create_info_pointer, pAllocator,
              pShaderModule);
}

} // namespace graphicsfuzz_shader_fuzzer
