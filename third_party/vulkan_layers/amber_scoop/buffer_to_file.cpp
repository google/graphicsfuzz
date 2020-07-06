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

#include "buffer_to_file.h"

namespace graphicsfuzz_amber_scoop {

BufferToFile::BufferToFile(const std::string& file_path)
    : file_path_(file_path) {
  file_stream_.open(file_path,
                    std::ios::out | std::ios::binary | std::ios::trunc);
}

std::fstream& BufferToFile::GetFileStream() { return file_stream_; }

void BufferToFile::WriteComponents(const uint8_t* data,
                                   vkf::VulkanFormat format) {
  file_stream_.write(reinterpret_cast<const char*>(data),
                     format.width_bits_ / 8);

  // Vec3 formats needs to be aligned to be size of vec4;
  if (!format.is_packed_ && format.component_count_ == 3) {
    static const uint64_t zero = 0UL;
    file_stream_.write(reinterpret_cast<const char*>(&zero),
                       format.components[0].num_bits / 8);
  }
}

void BufferToFile::WriteBytes(const uint8_t* data, VkDeviceSize byte_count) {
  file_stream_.write(reinterpret_cast<const char*>(data), byte_count);
}

void BufferToFile::WriteBuffer() {}

BufferToFile::~BufferToFile() { file_stream_.close(); }

}  // namespace graphicsfuzz_amber_scoop
