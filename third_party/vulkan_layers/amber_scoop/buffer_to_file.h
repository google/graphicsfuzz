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

#ifndef GRAPHICSFUZZ_VULKAN_LAYERS_AMBER_SCOOP_BUFFER_TO_FILE_H_
#define GRAPHICSFUZZ_VULKAN_LAYERS_AMBER_SCOOP_BUFFER_TO_FILE_H_

#include <vulkan/vulkan_core.h>

#include <fstream>
#include <string>

#include "vulkan_formats.h"

namespace graphicsfuzz_amber_scoop {

class BufferToFile {
 public:
  BufferToFile(const std::string& file_path);

  std::fstream& GetFileStream();

  void WriteBuffer();

  void WriteComponents(const uint8_t* data, vkf::VulkanFormat format);

  void WriteBytes(const uint8_t* data, VkDeviceSize byte_count);

  ~BufferToFile();

 private:
  const std::string file_path_;
  std::fstream file_stream_;

  // Disabled copy constructor and assign operator
  BufferToFile(BufferToFile&);
  BufferToFile& operator=(BufferToFile&);
};

}  // namespace graphicsfuzz_amber_scoop

#endif  // GRAPHICSFUZZ_VULKAN_LAYERS_AMBER_SCOOP_BUFFER_TO_FILE_H_
