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

#ifndef __VULKAN_WORKER__
#define __VULKAN_WORKER__

#include <vulkan/vulkan.h>
#include <vector>

#include "platform.h"
#include "gflags/gflags.h"

DECLARE_bool(info);
DECLARE_bool(skip_render);
DECLARE_string(coherence_before);
DECLARE_string(coherence_after);
DECLARE_int32(num_render);
DECLARE_string(png_template);

typedef struct Vertex {
  float x, y, z, w; // position
  float r, g, b, a; // color
} Vertex;

typedef struct UniformEntry {
  size_t size;
  void *value;
} UniformEntry;

class VulkanWorker {
  private:

  // Platform-specific data
  PlatformData *platform_data_;

  // Dimensions
  uint32_t width_;
  uint32_t height_;

  // Shader binaries
  std::vector<uint32_t> vertex_shader_spv_;
  std::vector<uint32_t> fragment_shader_spv_;
  std::vector<uint32_t> coherence_vertex_shader_spv_;
  std::vector<uint32_t> coherence_fragment_shader_spv_;


  // Vulkan specific

  VkInstance instance_;
  std::vector<VkPhysicalDevice> physical_devices_;
  VkPhysicalDeviceMemoryProperties physical_device_memory_properties_;
  VkPhysicalDeviceProperties physical_device_properties_;
  VkPhysicalDevice physical_device_;
  std::vector<VkQueueFamilyProperties> queue_family_properties_;
  uint32_t queue_family_index_;
  VkQueue queue_;
  VkDevice device_;
  VkCommandPool command_pool_;
  VkCommandBuffer command_buffer_;
  std::vector<VkCommandBuffer> export_command_buffers_;
  VkSurfaceKHR surface_;
  VkFormat format_;
  VkSwapchainKHR swapchain_;
  std::vector<VkImage> images_;
  std::vector<VkImageView> image_views_;
  VkImage depth_image_;
  VkDeviceMemory depth_memory_;
  VkImageView depth_image_view_;
  std::vector<VkBuffer> uniform_buffers_;
  std::vector<VkDeviceMemory> uniform_memories_;
  std::vector<UniformEntry> uniform_entries_;
  VkDescriptorSetLayout descriptor_set_layout_;
  VkPipelineLayout pipeline_layout_;
  VkDescriptorPool descriptor_pool_;
  VkDescriptorSet descriptor_set_;
  std::vector<VkDescriptorBufferInfo> descriptor_buffer_infos_;
  VkRenderPass render_pass_;
  VkShaderModule vertex_shader_module_;
  VkShaderModule fragment_shader_module_;
  VkPipelineShaderStageCreateInfo shader_stages_[2];
  std::vector<VkFramebuffer> framebuffers_;
  VkBuffer vertex_buffer_;
  VkDeviceMemory vertex_memory_;
  VkVertexInputBindingDescription vertex_input_binding_description_;
  VkVertexInputAttributeDescription vertex_input_attribute_description_[2];
  VkPipeline graphics_pipeline_;
  VkSemaphore semaphore_;
  uint32_t swapchain_image_index_;
  VkFence fence_;
  VkImage export_image_;
  VkDeviceMemory export_image_memory_;
  VkMemoryRequirements export_image_memory_requirements_;

  void CreateInstance();
  void DestroyInstance();
  void EnumeratePhysicalDevices();
  void PreparePhysicalDevice();
  void GetPhysicalDeviceQueueFamilyProperties();
  void FindGraphicsAndPresentQueueFamily();
  void CreateDevice();
  void DestroyDevice();
  void CreateCommandPool();
  void DestroyCommandPool();
  void AllocateCommandBuffer();
  void FreeCommandBuffers();
  void CreateSurface();
  void FindFormat();
  void CreateSwapchain();
  void DestroySwapchain();
  void GetSwapchainImages();
  void CreateSwapchainImageViews();
  void DestroySwapchainImageViews();
  void CreateDepthImage();
  void AllocateDepthMemory();
  void BindDepthImageMemory();
  void CreateDepthImageView();
  void DestroyDepthResources();
  void PrepareUniformBuffer();
  void DestroyUniformResources();
  void CreateDescriptorSetLayout();
  void DestroyDescriptorSetLayout();
  void CreatePipelineLayout();
  void DestroyPipelineLayout();
  void CreateDescriptorPool();
  void DestroyDescriptorPool();
  void AllocateDescriptorSet();
  void FreeDescriptorSet();
  void UpdateDescriptorSet();
  void CreateRenderPass();
  void DestroyRenderPass();
  void CreateShaderModules();
  void DestroyShaderModules();
  void PrepareShaderStages();
  void CreateFramebuffers();
  void DestroyFramebuffers();
  void PrepareVertexBufferObject();
  void CleanVertexBufferObject();
  void CreateGraphicsPipeline();
  void DestroyGraphicsPipeline();
  void CreateSemaphore();
  void DestroySemaphore();
  void AcquireNextImage();
  void PrepareCommandBuffer();
  void CreateFence();
  void DestroyFence();
  void SubmitCommandBuffer();
  void PresentToDisplay();
  void LoadUniforms(const char *uniforms_string);
  void PrepareExport();
  void CleanExport();
  void ExportPNG(const char *png_filename);
  void UpdateImageLayout(VkCommandBuffer command_buffer, VkImage image, VkImageLayout old_image_layout, VkImageLayout new_image_layout, VkPipelineStageFlags src_stage_mask, VkPipelineStageFlags dest_stage_mask);
  void PrepareTest(std::vector<uint32_t> &vertex_spv, std::vector<uint32_t> &fragment_spv, const char *uniforms_string);
  void CleanTest();
  void DrawTest(const char *png_filename, bool skip_render);

  uint32_t GetMemoryTypeIndex(uint32_t memory_requirements_type_bits, VkMemoryPropertyFlags required_properties);
  char *GetFileContent(FILE *file);
  void LoadSpirvFromFile(FILE *source, std::vector<uint32_t> &spv);
  void LoadSpirvFromArray(unsigned char *array, unsigned int len, std::vector<uint32_t> &spv);

  public:
  VulkanWorker(PlatformData *platform_data);
  ~VulkanWorker();
  void RunTest(FILE *vertex_file, FILE *fragment_file, FILE *uniforms_file, bool skip_render);
  static void DumpWorkerInfo(const char *worker_info_filename);
};

#endif
