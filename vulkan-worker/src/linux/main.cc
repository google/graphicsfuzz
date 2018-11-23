// Copyright 2018 The GraphicsFuzz Project Authors
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

// Entry point on Linux

// For GLFW details, see: https://www.glfw.org/docs/latest/vulkan_guide.html

#define GLFW_INCLUDE_VULKAN // GLFW will include vulkan
#include <GLFW/glfw3.h>

#include <assert.h> // assert()
#include <stdlib.h> // exit()
#include <stdio.h> // printf(), fopen()

#include <gflags/gflags.h> // DEFINE_*, FLAGS_*

#include "vulkan_worker.h"

const int WIDTH = 256;
const int HEIGHT = 256;

int main(int argc, char **argv) {

  gflags::SetUsageMessage("GraphicsFuzz Vulkan worker http://github.com/google/graphicsfuzz");
  gflags::ParseCommandLineFlags(&argc, &argv, true);

  if (FLAGS_info) {
    VulkanWorker::DumpWorkerInfo("worker_info.json");
    exit(EXIT_SUCCESS);
  }

  if (argc != 4) {
    printf("Error: need exactly 3 arguments\n");
    printf("Usage: %s shader.vert.spv shader.frag.spv shader.json\n", argv[0]);
    exit(EXIT_FAILURE);
  }

  FILE *vertex_file = nullptr;
  vertex_file = fopen(argv[1], "r");
  assert(vertex_file != nullptr);

  FILE *fragment_file = nullptr;
  fragment_file = fopen(argv[2], "r");
  assert(fragment_file != nullptr);

  FILE *uniform_file = nullptr;
  uniform_file = fopen(argv[3], "r");
  assert(uniform_file != nullptr);

  glfwInit();
  glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);

  PlatformData platform_data = {};
  platform_data.window = glfwCreateWindow(WIDTH, HEIGHT, "VulkanWorker", nullptr, nullptr);

  VulkanWorker* vulkan_worker = new VulkanWorker(&platform_data);
  vulkan_worker->RunTest(vertex_file, fragment_file, uniform_file, FLAGS_skip_render);
  delete vulkan_worker;

  fclose(vertex_file);
  fclose(fragment_file);
  fclose(uniform_file);

  // while(!glfwWindowShouldClose(window)) {
  //   glfwPollEvents();
  // }

  glfwDestroyWindow(platform_data.window);

  glfwTerminate();

  log("\nLINUX TERMINATE OK\n");

  exit(EXIT_SUCCESS);
}
