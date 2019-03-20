// Copyright 2019 The GraphicsFuzz Project Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <fstream>
#include <iostream>
#include <string>

void CHECK(bool condition, const char* message) {
  if (condition) return;
  perror(message);
  exit(EXIT_FAILURE);
}

static int sock;

void ReadBuffer(uint8_t* data, size_t size) {
  size_t bytes_read = 0;
  while (bytes_read < size) {
    void* buf = data + bytes_read;
    size_t count = size - bytes_read;
    ssize_t result = TEMP_FAILURE_RETRY(read(sock, buf, count));
    CHECK(result >= 1, "short read");
    bytes_read += static_cast<size_t>(result);
  }
}

void Discard(size_t total_bytes_to_discard) {
  if (!total_bytes_to_discard) return;

  static const size_t discard_buffer_size = 1024;
  static uint8_t discard_buffer[discard_buffer_size];

  size_t num_discarded = 0;
  while (num_discarded < total_bytes_to_discard) {
    size_t num_to_discard =
        std::min(total_bytes_to_discard - num_discarded, discard_buffer_size);
    ReadBuffer(discard_buffer, num_to_discard);
    num_discarded += num_to_discard;
  }
}

struct __attribute__((packed)) MutateRequestHeader {
  uint64_t size;
  uint32_t seed;
  uint8_t is_fragment;
};

extern "C" size_t LLVMFuzzerCustomMutator(uint8_t* data, size_t size,
                                          size_t max_size, unsigned int seed) {
  if (size <= 1) {
    // Handle common invalid testcases gracefully.
    static const std::string basic_shader = "void main(void) { }";
    if (basic_shader.size() < max_size) {
      memcpy(reinterpret_cast<char*>(data), basic_shader.c_str(),
             basic_shader.size());
      return basic_shader.size();
    }
  }

  // Open a connection to the CustomMutatorServer.
  if (!sock) {
    sock = socket(AF_INET, SOCK_STREAM, 0);
    CHECK(sock >= 0, "Could not create socket");
    static struct sockaddr_in serv_addr;
    serv_addr.sin_family = AF_INET;
    static const int port = 8666;
    serv_addr.sin_port = htons(port);
    CHECK(inet_pton(AF_INET, "0.0.0.0", &serv_addr.sin_addr) != -1,
          "invalid address");

    CHECK(connect(sock, reinterpret_cast<const struct sockaddr*>(&serv_addr),
                  sizeof(serv_addr)) >= 0,
          "connection failed");
  }

  static MutateRequestHeader request_header;
  request_header.size = size;
  request_header.seed = seed;
  // In this example we only start with a fragment shader, so every shader must
  // be a fragment shader.
  request_header.is_fragment = true;
  static int flags = 0;
  // Send the mutation request.
  ssize_t num_bytes_sent =
      send(sock, reinterpret_cast<const void*>(&request_header),
           sizeof(request_header), flags);
  CHECK(num_bytes_sent == sizeof(request_header), "short write");
  // Send the shader.
  num_bytes_sent = send(sock, reinterpret_cast<const void*>(data), size, flags);
  CHECK(static_cast<size_t>(num_bytes_sent) == size, "short write");

  // Read the response shader size and contents.
  uint8_t mutated_size_bytes[sizeof(size_t)];
  ReadBuffer(mutated_size_bytes, sizeof(size_t));
  size_t mutated_buf_length = *(reinterpret_cast<size_t*>(mutated_size_bytes));
  if (mutated_buf_length > max_size) {
    Discard(mutated_buf_length);
    std::cout << "discard" << std::endl;
    return size;
  }
  size_t read_size = std::min(max_size, mutated_buf_length);
  ReadBuffer(data, read_size);
  return read_size;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  std::string shader(reinterpret_cast<const char*>(data), size);
  // Give the user some feedback since the coverage won't grow much in an empty
  // fuzzer.
  std::cout << shader << std::endl;
  return 0;
}
