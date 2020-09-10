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

#include "platform.h" // included first because it includes vulkan headers, that's required for Linux GLFW

#include <assert.h> // assert()
#include <stdlib.h> // malloc()
#include <string.h> // memcpy(), strcmp()
#include <string> // std::string for == comparison
#include <iostream>
#include <fstream>

#include "cJSON.h"
#include "lodepng.h" // lodepng_encode32()
#include "vulkan_worker.h"
#include "vkcheck.h"

// Command line options
DEFINE_bool(info, false, "Dump worker information and exit");
DEFINE_bool(skip_render, false, "Prepare graphics pipeline but skip rendering");
DEFINE_string(coherence_before, "coherence_before.png", "Path to save coherence image recorded before test");
DEFINE_string(coherence_after, "coherence_after.png", "Path to save coherence image recorded after test");
DEFINE_int32(num_render, 3, "Number of times to render");
DEFINE_string(png_template, "image", "Path template to image output, '_<#id>.png' will be added");

// Constants
static const VkSampleCountFlagBits num_samples_ = VK_SAMPLE_COUNT_1_BIT;
// FIXME: depth format may be platform-dependent, but how to decide which one is good? VK_FORMAT_D24_UNORM_S8_UINT is OK on Android and Linux.
static const VkFormat depth_format_ = VK_FORMAT_D24_UNORM_S8_UINT;
static const uint64_t fence_timeout_nanoseconds_ = 100000000;
// Clear with opaque black
static const float clear_color_[4] = {0.0f, 0.0f, 0.0f, 0.0f};
// Coherence
const char *coherence_uniforms_string = "{}";

// Hardcoded quad
#define RED2D(_x, _y)  (_x), (_y), 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f
#define BLUE2D(_x, _y) (_x), (_y), 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f

static const Vertex vertex_input_data[] = {

    // first triangle
    {RED2D(-1.0f,  1.0f)},
    {RED2D(-1.0f, -1.0f)},
    {RED2D( 1.0f, -1.0f)},

    // second triangle
    {BLUE2D(-1.0f,  1.0f)},
    {BLUE2D( 1.0f, -1.0f)},
    {BLUE2D( 1.0f,  1.0f)},
};

// Coherence shader binaries are stored as byte arrays, see coherence/coherence.sh
#include "coherence/coherence_vert.inc"
#include "coherence/coherence_frag.inc"

VulkanWorker::VulkanWorker(PlatformData *platform_data) {
  platform_data_ = platform_data;
  PlatformGetWidthHeight(platform_data_, &width_, &height_);

  LoadSpirvFromArray(coherence_vert_spv, coherence_vert_spv_len, coherence_vertex_shader_spv_);
  LoadSpirvFromArray(coherence_frag_spv, coherence_frag_spv_len, coherence_fragment_shader_spv_);

  CreateInstance();
  EnumeratePhysicalDevices();
  PreparePhysicalDevice();
  GetPhysicalDeviceQueueFamilyProperties();
  CreateDevice();
  CreateSurface();
  FindGraphicsAndPresentQueueFamily();
  CreateCommandPool();
  AllocateCommandBuffer();
  FindFormat();
  CreateSwapchain();
  GetSwapchainImages();
  CreateSwapchainImageViews();
  CreateDepthImage();
  AllocateDepthMemory();
  BindDepthImageMemory();
  CreateDepthImageView();
  PrepareVertexBufferObject();
  PrepareExport();
}

VulkanWorker::~VulkanWorker() {
  CleanExport();
  CleanVertexBufferObject();
  DestroyDepthResources();
  DestroySwapchainImageViews();
  DestroySwapchain();
  FreeCommandBuffers();
  DestroyCommandPool();
  DestroyDevice();
  DestroyInstance();

  log("GFZVK DONE");
}

void VulkanWorker::CreateInstance() {
  VkApplicationInfo application_info = {};
  application_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
  application_info.pNext = nullptr;
  application_info.pApplicationName = "VulkanWorker";
  application_info.applicationVersion = 0;
  application_info.pEngineName = "GraphicsFuzz";
  application_info.engineVersion = 0;
  application_info.apiVersion = VK_MAKE_VERSION(1,0,0);

  std::vector<const char *> enabled_extension_names;
  PlatformGetInstanceExtensions(enabled_extension_names);

  // List extensions from instance properties, add debug report/utils extensions
  uint32_t num_properties = 0;

  VKCHECK(vkEnumerateInstanceExtensionProperties(nullptr, &num_properties, nullptr));
  log("Num instance properties: %d", num_properties);
  VkExtensionProperties *properties = (VkExtensionProperties *)calloc(num_properties, sizeof(VkExtensionProperties));
  assert(properties != nullptr);
  VKCHECK(vkEnumerateInstanceExtensionProperties(nullptr, &num_properties, properties));
  const char *debug_report = "VK_EXT_debug_report";
  const char *debug_utils  = "VK_EXT_debug_utils";
  bool found_debug_report = false;
  bool found_debug_utils = false;
  for (uint32_t i = 0; i < num_properties; i++) {
    log("Extension #%d: %s", i, properties[i].extensionName);
    if (strcmp(properties[i].extensionName, debug_report) == 0) {
      found_debug_report = true;
    }
    if (strcmp(properties[i].extensionName, debug_utils) == 0) {
      found_debug_utils = true;
    }
  }
  // debug_utils should be preferred, but there is no guarantee any is available
  if (found_debug_utils) {
    log("Enable extension debug_utils");
    enabled_extension_names.push_back(debug_utils);
  } else if (found_debug_report) {
    log("Enable extension debug_report");
    enabled_extension_names.push_back(debug_report);
  }
  free(properties);

  // Validation layers
  std::vector<const char *> enabled_layer_names;
  PlatformGetInstanceLayers(enabled_layer_names);

  VkInstanceCreateInfo instance_create_info = {};
  instance_create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
  instance_create_info.pNext = nullptr;
  instance_create_info.flags = 0;
  instance_create_info.pApplicationInfo = &application_info;
  instance_create_info.enabledLayerCount = enabled_layer_names.size();
  instance_create_info.ppEnabledLayerNames = enabled_layer_names.size() == 0 ? nullptr : enabled_layer_names.data();
  instance_create_info.enabledExtensionCount = enabled_extension_names.size();
  instance_create_info.ppEnabledExtensionNames = enabled_extension_names.data();

  VKCHECK(vkCreateInstance(&instance_create_info, nullptr, &instance_));
}

void VulkanWorker::DestroyInstance() {
  VKLOG(vkDestroyInstance(instance_, nullptr));
}

void VulkanWorker::EnumeratePhysicalDevices() {
  uint32_t num_physical_devices = 0;
  VKCHECK(vkEnumeratePhysicalDevices(instance_, &num_physical_devices, nullptr));
  assert(num_physical_devices > 0 || "Cannot find any physical device");
  physical_devices_.resize(num_physical_devices);
  VKCHECK(vkEnumeratePhysicalDevices(instance_, &num_physical_devices, physical_devices_.data()));
  log("Number of physical devices (i.e., actual GPU chips): %d", num_physical_devices);
}

void VulkanWorker::PreparePhysicalDevice() {
  if (physical_devices_.size() > 1) {
    log("Warning: more than one GPU detected, the worker always targets the first one listed");
  }
  physical_device_ = physical_devices_[0];
  VKLOG(vkGetPhysicalDeviceMemoryProperties(physical_device_, &physical_device_memory_properties_));
  VKLOG(vkGetPhysicalDeviceProperties(physical_device_, &physical_device_properties_));
  log("Physical device properties:");
  uint32_t api_version = physical_device_properties_.apiVersion;
  log("apiVersion: %d.%d.%d", VK_VERSION_MAJOR(api_version), VK_VERSION_MINOR(api_version), VK_VERSION_PATCH(api_version));
  log("driverVersion: %u", physical_device_properties_.driverVersion);
  log("vendorID: %u", physical_device_properties_.vendorID);
  log("deviceID: %u", physical_device_properties_.deviceID);
  log("deviceName: %s", physical_device_properties_.deviceName);
}

void VulkanWorker::GetPhysicalDeviceQueueFamilyProperties() {
  uint32_t num_queue_family_properties = 0;
  VKLOG(vkGetPhysicalDeviceQueueFamilyProperties(physical_device_, &num_queue_family_properties, nullptr));
  assert(num_queue_family_properties > 0 || "Cannot find any queue family property");
  queue_family_properties_.resize(num_queue_family_properties);
  VKLOG(vkGetPhysicalDeviceQueueFamilyProperties(physical_device_, &num_queue_family_properties, queue_family_properties_.data()));
}

void VulkanWorker::FindGraphicsAndPresentQueueFamily() {
  bool found = false;
  for (uint32_t i = 0; i < queue_family_properties_.size(); i++) {
    if (queue_family_properties_[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
      VkBool32 supports_present;
      vkGetPhysicalDeviceSurfaceSupportKHR(physical_device_, i, surface_, &supports_present);
      if (supports_present == VK_TRUE) {
        found = true;
        queue_family_index_ = i;
      }
    }
  }
  assert(found || "Cannot find a queue with both VK_QUEUE_GRAPHICS_BIT and supporting 'present'");

  VKLOG(vkGetDeviceQueue(device_, queue_family_index_, 0, &queue_));
}

void VulkanWorker::CreateDevice() {
  float queue_priorities[1] = {0.0};
  VkDeviceQueueCreateInfo device_queue_create_info = {};
  device_queue_create_info.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
  device_queue_create_info.pNext = nullptr;
  device_queue_create_info.flags = 0;
  device_queue_create_info.queueFamilyIndex = queue_family_index_;
  device_queue_create_info.queueCount = 1;
  device_queue_create_info.pQueuePriorities = queue_priorities;

  std::vector<const char *> device_extension_names;
  device_extension_names.push_back(VK_KHR_SWAPCHAIN_EXTENSION_NAME);

  VkDeviceCreateInfo device_create_info = {};
  device_create_info.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
  device_create_info.pNext = nullptr;
  device_create_info.flags = 0;
  device_create_info.queueCreateInfoCount = 1;
  device_create_info.pQueueCreateInfos = &device_queue_create_info;
  device_create_info.enabledExtensionCount = device_extension_names.size();
  device_create_info.ppEnabledExtensionNames = device_extension_names.data();
  device_create_info.enabledLayerCount = 0;
  device_create_info.ppEnabledLayerNames = nullptr;
  device_create_info.pEnabledFeatures = nullptr;

  VKCHECK(vkCreateDevice(physical_device_, &device_create_info, nullptr, &device_));
}

void VulkanWorker::DestroyDevice() {
  VKLOG(vkDestroyDevice(device_, nullptr));
}

void VulkanWorker::CreateCommandPool() {
  VkCommandPoolCreateInfo command_pool_create_info = {};
  command_pool_create_info.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
  command_pool_create_info.pNext = nullptr;
  command_pool_create_info.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
  command_pool_create_info.queueFamilyIndex = queue_family_index_;
  VKCHECK(vkCreateCommandPool(device_, &command_pool_create_info, nullptr, &command_pool_));
}

void VulkanWorker::DestroyCommandPool() {
  VKLOG(vkDestroyCommandPool(device_, command_pool_, nullptr));
}

void VulkanWorker::AllocateCommandBuffer() {
  VkCommandBufferAllocateInfo command_buffer_allocate_info = {};
  command_buffer_allocate_info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
  command_buffer_allocate_info.pNext = nullptr;
  command_buffer_allocate_info.commandPool = command_pool_;
  command_buffer_allocate_info.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  command_buffer_allocate_info.commandBufferCount = 1;
  VKCHECK(vkAllocateCommandBuffers(device_, &command_buffer_allocate_info, &command_buffer_));
}

void VulkanWorker::FreeCommandBuffers() {
  VKLOG(vkFreeCommandBuffers(device_, command_pool_, 1, &command_buffer_));
}

void VulkanWorker::CreateSurface() {
  PlatformCreateSurface(platform_data_, instance_, &surface_);
}

void VulkanWorker::FindFormat() {
  uint32_t num_surface_formats = 0;
  VKCHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical_device_, surface_, &num_surface_formats, nullptr));
  std::vector<VkSurfaceFormatKHR> surface_formats;
  surface_formats.resize(num_surface_formats);
  VKCHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical_device_, surface_, &num_surface_formats, surface_formats.data()));
  if (surface_formats.size() == 1 && surface_formats[0].format == VK_FORMAT_UNDEFINED) {
    // Use default format
    format_ = VK_FORMAT_B8G8R8A8_UNORM;
  } else {
    // Pick up the first format
    assert(surface_formats.size() > 0);
    format_ = surface_formats[0].format;
  }
}

void VulkanWorker::CreateSwapchain() {

  VkSurfaceCapabilitiesKHR surface_capabilities;
  VKCHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical_device_, surface_, &surface_capabilities));

  VkExtent2D extent2D = {};
  // For details on 0xFFFFFFFF values, look for comment on 'currentExtent' at:
  // https://www.khronos.org/registry/vulkan/specs/1.0-extensions/html/vkspec.html#_surface_queries
  if (surface_capabilities.currentExtent.height == 0xFFFFFFFF) {
    assert(surface_capabilities.currentExtent.width == 0xFFFFFFFF);
    extent2D.width = width_;
    extent2D.height = height_;

    // Bound by the surface capabilities
    if (extent2D.width < surface_capabilities.minImageExtent.width) {
      extent2D.width = surface_capabilities.minImageExtent.width;
    } else if (extent2D.width > surface_capabilities.maxImageExtent.width) {
      extent2D.width = surface_capabilities.maxImageExtent.width;
    }
    if (extent2D.height < surface_capabilities.minImageExtent.height) {
      extent2D.height = surface_capabilities.minImageExtent.height;
    } else if (extent2D.height > surface_capabilities.maxImageExtent.height) {
      extent2D.height = surface_capabilities.maxImageExtent.height;
    }
  } else {
    extent2D = surface_capabilities.currentExtent;
  }

  VkSurfaceTransformFlagBitsKHR pre_transform;
  if (surface_capabilities.supportedTransforms & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR) {
    pre_transform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
  } else {
    pre_transform = surface_capabilities.currentTransform;
  }

  // Composite Alpha setting is inspired by the Google/LunarG Vulkan Samples
  VkCompositeAlphaFlagBitsKHR composite_alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
  VkCompositeAlphaFlagBitsKHR composite_alpha_flags[4] = {
      VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
      VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
      VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
      VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
  };
  for (uint32_t i = 0; i < sizeof(composite_alpha_flags); i++) {
    if (surface_capabilities.supportedCompositeAlpha & composite_alpha_flags[i]) {
      composite_alpha = composite_alpha_flags[i];
      break;
    }
  }

  uint32_t num_present_modes;
  VKCHECK(vkGetPhysicalDeviceSurfacePresentModesKHR(physical_device_, surface_, &num_present_modes, nullptr));
  std::vector<VkPresentModeKHR> present_modes;
  present_modes.resize(num_present_modes);
  VKCHECK(vkGetPhysicalDeviceSurfacePresentModesKHR(physical_device_, surface_, &num_present_modes, present_modes.data()));

  // Exclusive as we only support a single queue for both graphics and present
  VkSharingMode image_sharing_mode = VK_SHARING_MODE_EXCLUSIVE;
  uint32_t queue_family_index_count = 0;
  const uint32_t* queue_family_indices = nullptr;

  VkSwapchainCreateInfoKHR swapchain_create_info = {};
  swapchain_create_info.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
  swapchain_create_info.pNext = nullptr;
  swapchain_create_info.flags = 0;
  swapchain_create_info.surface = surface_;
  swapchain_create_info.imageFormat = format_;
  swapchain_create_info.minImageCount = surface_capabilities.minImageCount;
  swapchain_create_info.imageExtent.width = extent2D.width;
  swapchain_create_info.imageExtent.height = extent2D.height;
  swapchain_create_info.presentMode = VK_PRESENT_MODE_FIFO_KHR; // only mode that is required to be supported
  swapchain_create_info.preTransform = pre_transform;
  swapchain_create_info.compositeAlpha = composite_alpha;
  swapchain_create_info.imageColorSpace = VK_COLORSPACE_SRGB_NONLINEAR_KHR;
  swapchain_create_info.imageArrayLayers = 1; // Spec says: "For non-stereoscopic-3D applications, this value is 1."
  swapchain_create_info.clipped = VK_FALSE; // always render all pixels, even if they are not visible
  swapchain_create_info.oldSwapchain = VK_NULL_HANDLE;
  swapchain_create_info.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT; // from VulkanSamples
  swapchain_create_info.imageSharingMode = image_sharing_mode;
  swapchain_create_info.queueFamilyIndexCount = queue_family_index_count;
  swapchain_create_info.pQueueFamilyIndices = queue_family_indices;
  VKCHECK(vkCreateSwapchainKHR(device_, &swapchain_create_info, nullptr, &swapchain_));
}

void VulkanWorker::DestroySwapchain() {
  VKLOG(vkDestroySwapchainKHR(device_, swapchain_, nullptr));
}

void VulkanWorker::GetSwapchainImages() {
  uint32_t num_images = 0;
  VKCHECK(vkGetSwapchainImagesKHR(device_, swapchain_, &num_images, nullptr));
  assert(num_images > 0);
  images_.resize(num_images);
  VKCHECK(vkGetSwapchainImagesKHR(device_, swapchain_, &num_images, images_.data()));
}

void VulkanWorker::CreateSwapchainImageViews() {
  VkImageViewCreateInfo image_view_create_info = {};
  image_view_create_info.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
  image_view_create_info.pNext = nullptr;
  image_view_create_info.flags = 0;
  image_view_create_info.viewType = VK_IMAGE_VIEW_TYPE_2D;
  image_view_create_info.format = format_;
  image_view_create_info.components.r = VK_COMPONENT_SWIZZLE_R;
  image_view_create_info.components.g = VK_COMPONENT_SWIZZLE_G;
  image_view_create_info.components.b = VK_COMPONENT_SWIZZLE_B;
  image_view_create_info.components.a = VK_COMPONENT_SWIZZLE_A;
  image_view_create_info.subresourceRange.baseMipLevel = 0;
  image_view_create_info.subresourceRange.levelCount = 1;
  image_view_create_info.subresourceRange.baseArrayLayer = 0;
  image_view_create_info.subresourceRange.layerCount = 1;
  image_view_create_info.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;

  image_views_.resize(images_.size());

  for (size_t i = 0; i < images_.size(); i++) {
    image_view_create_info.image = images_[i];
    VKCHECK(vkCreateImageView(device_, &image_view_create_info, nullptr, &(image_views_[i])));
  }
}

void VulkanWorker::DestroySwapchainImageViews() {
  for (VkImageView view: image_views_) {
    VKLOG(vkDestroyImageView(device_, view, nullptr));
  }
}

void VulkanWorker::CreateDepthImage() {
  VkImageCreateInfo image_create_info = {};
  image_create_info.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
  image_create_info.pNext = nullptr;
  image_create_info.flags = 0;
  image_create_info.imageType = VK_IMAGE_TYPE_2D;
  image_create_info.extent.width = width_;
  image_create_info.extent.height = height_;
  image_create_info.extent.depth = 1;
  image_create_info.mipLevels = 1;
  image_create_info.arrayLayers = 1;
  image_create_info.samples = num_samples_;
  image_create_info.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
  image_create_info.queueFamilyIndexCount = 0;
  image_create_info.pQueueFamilyIndices = nullptr;
  image_create_info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
  image_create_info.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
  image_create_info.format = depth_format_;

  VkFormatProperties format_properties = {};
  VKLOG(vkGetPhysicalDeviceFormatProperties(physical_device_, image_create_info.format, &format_properties));
  if (format_properties.linearTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) {
    image_create_info.tiling = VK_IMAGE_TILING_LINEAR;
  } else if (format_properties.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) {
    image_create_info.tiling = VK_IMAGE_TILING_OPTIMAL;
  } else {
    assert(false && "Not sure how to set tiling for depth buffer");
  }

  VKCHECK(vkCreateImage(device_, &image_create_info, nullptr, &depth_image_));
}

void VulkanWorker::AllocateDepthMemory() {
  VkMemoryRequirements depth_memory_requirements = {};
  VKLOG(vkGetImageMemoryRequirements(device_, depth_image_, &depth_memory_requirements));

  VkMemoryAllocateInfo depth_memory_allocate_info = {};
  depth_memory_allocate_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
  depth_memory_allocate_info.pNext = nullptr;
  depth_memory_allocate_info.allocationSize = depth_memory_requirements.size;
  depth_memory_allocate_info.memoryTypeIndex = GetMemoryTypeIndex(depth_memory_requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
  VKCHECK(vkAllocateMemory(device_, &depth_memory_allocate_info, nullptr, &depth_memory_));
}

void VulkanWorker::BindDepthImageMemory() {
  VKCHECK(vkBindImageMemory(device_, depth_image_, depth_memory_, /* memory_offset */ 0));
}

void VulkanWorker::CreateDepthImageView() {
  VkImageViewCreateInfo depth_image_view_create_info = {};
  depth_image_view_create_info.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
  depth_image_view_create_info.pNext = nullptr;
  depth_image_view_create_info.flags = 0;
  depth_image_view_create_info.image = depth_image_;
  depth_image_view_create_info.format = depth_format_;
  depth_image_view_create_info.components.r = VK_COMPONENT_SWIZZLE_R;
  depth_image_view_create_info.components.g = VK_COMPONENT_SWIZZLE_G;
  depth_image_view_create_info.components.b = VK_COMPONENT_SWIZZLE_B;
  depth_image_view_create_info.components.a = VK_COMPONENT_SWIZZLE_A;
  depth_image_view_create_info.subresourceRange.baseMipLevel = 0;
  depth_image_view_create_info.subresourceRange.levelCount = 1;
  depth_image_view_create_info.subresourceRange.baseArrayLayer = 0;
  depth_image_view_create_info.subresourceRange.layerCount = 1;
  depth_image_view_create_info.viewType = VK_IMAGE_VIEW_TYPE_2D;

  depth_image_view_create_info.subresourceRange.aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
  // The following is inspired by VulkanSamples
  if (depth_format_ == VK_FORMAT_D16_UNORM_S8_UINT || depth_format_ == VK_FORMAT_D24_UNORM_S8_UINT ||
      depth_format_ == VK_FORMAT_D32_SFLOAT_S8_UINT) {
    depth_image_view_create_info.subresourceRange.aspectMask |= VK_IMAGE_ASPECT_STENCIL_BIT;
  }

  VKCHECK(vkCreateImageView(device_, &depth_image_view_create_info, nullptr, &depth_image_view_));
}

void VulkanWorker::DestroyDepthResources() {
  VKLOG(vkDestroyImageView(device_, depth_image_view_, nullptr));
  VKLOG(vkFreeMemory(device_, depth_memory_, nullptr));
  VKLOG(vkDestroyImage(device_, depth_image_, nullptr));
}

uint32_t VulkanWorker::GetMemoryTypeIndex(uint32_t memory_requirements_type_bits, VkMemoryPropertyFlags required_properties) {
  // See Vulkan spec, 10.2 "Device Memory"
  for (uint32_t index = 0; index < physical_device_memory_properties_.memoryTypeCount; index++) {
    uint32_t memory_type_bits = 1 << index;
    bool is_required_memory_type = memory_requirements_type_bits & memory_type_bits;
    VkMemoryPropertyFlags available_properties = physical_device_memory_properties_.memoryTypes[index].propertyFlags;
    bool has_required_properties = (available_properties & required_properties) == required_properties;
    if (is_required_memory_type && has_required_properties) {
      return index;
    }
  }
  assert(false && "Cannot find relevant memory type index");
  return UINT32_MAX; // unreachable
}

void VulkanWorker::PrepareUniformBuffer() {
  uniform_buffers_.resize(uniform_entries_.size());
  uniform_memories_.resize(uniform_entries_.size());
  descriptor_buffer_infos_.resize(uniform_entries_.size());

  // Buffer create info is same for any uniform, except for size
  VkBufferCreateInfo uniform_buffer_create_info = {};
  uniform_buffer_create_info.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
  uniform_buffer_create_info.pNext = nullptr;
  uniform_buffer_create_info.usage = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
  uniform_buffer_create_info.queueFamilyIndexCount = 0;
  uniform_buffer_create_info.pQueueFamilyIndices = nullptr;
  uniform_buffer_create_info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
  uniform_buffer_create_info.flags = 0;

  for (size_t i = 0; i < uniform_entries_.size(); i++) {
    UniformEntry uniform_entry = uniform_entries_[i];

    uniform_buffer_create_info.size = uniform_entry.size;
    VKCHECK(vkCreateBuffer(device_, &uniform_buffer_create_info, nullptr, &(uniform_buffers_[i])));

    VkMemoryRequirements uniform_memory_requirements = {};
    VKLOG(vkGetBufferMemoryRequirements(device_, uniform_buffers_[i], &uniform_memory_requirements));

    VkMemoryPropertyFlags uniform_memory_property_flags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    VkMemoryAllocateInfo uniform_memory_allocate_info = {};
    uniform_memory_allocate_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    uniform_memory_allocate_info.pNext = nullptr;
    uniform_memory_allocate_info.allocationSize = uniform_memory_requirements.size;
    uniform_memory_allocate_info.memoryTypeIndex = GetMemoryTypeIndex(uniform_memory_requirements.memoryTypeBits, uniform_memory_property_flags);
    VKCHECK(vkAllocateMemory(device_, &uniform_memory_allocate_info, nullptr, &(uniform_memories_[i])));

    void *uniform_data = nullptr;
    VKCHECK(vkMapMemory(device_, uniform_memories_[i], /* offset */ 0, uniform_memory_requirements.size, /* flags */ 0, &uniform_data));
    assert(uniform_data != nullptr);
    memcpy(uniform_data, uniform_entry.value, uniform_entry.size);
    VKLOG(vkUnmapMemory(device_, uniform_memories_[i]));

    VKCHECK(vkBindBufferMemory(device_, uniform_buffers_[i], uniform_memories_[i], /* offset */ 0));

    descriptor_buffer_infos_[i].buffer = uniform_buffers_[i];
    descriptor_buffer_infos_[i].offset = 0;
    descriptor_buffer_infos_[i].range = uniform_entry.size;
  }
}

void VulkanWorker::DestroyUniformResources() {
  for (size_t i = 0; i < uniform_entries_.size(); i++) {
    free(uniform_entries_[i].value);
    VKLOG(vkFreeMemory(device_, uniform_memories_[i], nullptr));
    VKLOG(vkDestroyBuffer(device_, uniform_buffers_[i], nullptr));
  }
}

void VulkanWorker::CreateDescriptorSetLayout() {
  std::vector<VkDescriptorSetLayoutBinding> descriptor_set_layout_bindings;
  descriptor_set_layout_bindings.resize(uniform_entries_.size());

  for (size_t i = 0; i < uniform_entries_.size(); i++) {
    descriptor_set_layout_bindings[i].binding = i;
    descriptor_set_layout_bindings[i].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    descriptor_set_layout_bindings[i].descriptorCount = 1;
    // Uniforms available to both vertex and shader
    descriptor_set_layout_bindings[i].stageFlags = VK_SHADER_STAGE_VERTEX_BIT  | VK_SHADER_STAGE_FRAGMENT_BIT;
    descriptor_set_layout_bindings[i].pImmutableSamplers = nullptr;
  }

  VkDescriptorSetLayoutCreateInfo descriptor_set_layout_create_info = {};
  descriptor_set_layout_create_info.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
  descriptor_set_layout_create_info.pNext = nullptr;
  descriptor_set_layout_create_info.bindingCount = uniform_entries_.size();
  descriptor_set_layout_create_info.pBindings = descriptor_set_layout_bindings.data();
  VKCHECK(vkCreateDescriptorSetLayout(device_, &descriptor_set_layout_create_info, nullptr, &descriptor_set_layout_));
}

void VulkanWorker::DestroyDescriptorSetLayout() {
  VKLOG(vkDestroyDescriptorSetLayout(device_, descriptor_set_layout_, nullptr));
}

void VulkanWorker::CreatePipelineLayout() {
  VkPipelineLayoutCreateInfo pipeline_layout_create_info = {};
  pipeline_layout_create_info.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
  pipeline_layout_create_info.pNext = nullptr;
  pipeline_layout_create_info.flags = 0;
  // TODO: push constant range seem to be another way of passing constants to shader, have a look at it.
  pipeline_layout_create_info.pushConstantRangeCount = 0;
  pipeline_layout_create_info.pPushConstantRanges = nullptr;
  if (uniform_entries_.size() > 0) {
    pipeline_layout_create_info.setLayoutCount = 1;
    pipeline_layout_create_info.pSetLayouts = &descriptor_set_layout_;
  } else {
    pipeline_layout_create_info.setLayoutCount = 0;
    pipeline_layout_create_info.pSetLayouts = nullptr;
  }
  VKCHECK(vkCreatePipelineLayout(device_, &pipeline_layout_create_info, nullptr, &pipeline_layout_));
}

void VulkanWorker::DestroyPipelineLayout() {
  VKLOG(vkDestroyPipelineLayout(device_, pipeline_layout_, nullptr));
}

void VulkanWorker::CreateDescriptorPool() {
  // We need only one descriptor pool, but we could have more using an array.
  VkDescriptorPoolSize descriptor_pool_size;
  descriptor_pool_size.type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
  descriptor_pool_size.descriptorCount = uniform_entries_.size();

  VkDescriptorPoolCreateInfo descriptor_pool_create_info = {};
  descriptor_pool_create_info.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
  descriptor_pool_create_info.pNext = nullptr;
  descriptor_pool_create_info.flags = VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
  descriptor_pool_create_info.maxSets = 1;
  descriptor_pool_create_info.poolSizeCount = 1;
  descriptor_pool_create_info.pPoolSizes = &descriptor_pool_size;

  VKCHECK(vkCreateDescriptorPool(device_, &descriptor_pool_create_info, nullptr, &descriptor_pool_));
}

void VulkanWorker::DestroyDescriptorPool() {
  VKLOG(vkDestroyDescriptorPool(device_, descriptor_pool_, nullptr));
}

void VulkanWorker::AllocateDescriptorSet() {

  VkDescriptorSetAllocateInfo descriptor_set_allocate_info;
  descriptor_set_allocate_info.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
  descriptor_set_allocate_info.pNext = nullptr;
  descriptor_set_allocate_info.descriptorPool = descriptor_pool_;
  descriptor_set_allocate_info.descriptorSetCount = 1;
  descriptor_set_allocate_info.pSetLayouts = &descriptor_set_layout_;
  VKCHECK(vkAllocateDescriptorSets(device_, &descriptor_set_allocate_info, &descriptor_set_));
}

void VulkanWorker::FreeDescriptorSet() {
  VKLOG(vkFreeDescriptorSets(device_, descriptor_pool_, 1, &descriptor_set_));
}

void VulkanWorker::UpdateDescriptorSet() {
  VkWriteDescriptorSet write_descriptor_set;
  write_descriptor_set = {};
  write_descriptor_set.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
  write_descriptor_set.pNext = nullptr;
  write_descriptor_set.dstSet = descriptor_set_;
  write_descriptor_set.descriptorCount = uniform_entries_.size();
  write_descriptor_set.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
  write_descriptor_set.pBufferInfo = descriptor_buffer_infos_.data();
  write_descriptor_set.dstArrayElement = 0;
  write_descriptor_set.dstBinding = 0;

  VKLOG(vkUpdateDescriptorSets(device_, 1, &write_descriptor_set, 0, nullptr));
}

void VulkanWorker::CreateRenderPass() {
  VkAttachmentDescription attachment_descriptions[2];
  // color
  attachment_descriptions[0].format = format_;
  attachment_descriptions[0].flags = 0;
  attachment_descriptions[0].samples = num_samples_;
  attachment_descriptions[0].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
  attachment_descriptions[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
  attachment_descriptions[0].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
  attachment_descriptions[0].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
  attachment_descriptions[0].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
  attachment_descriptions[0].finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
  // depth
  attachment_descriptions[1].format = depth_format_;
  attachment_descriptions[1].flags = 0;
  attachment_descriptions[1].samples = num_samples_;
  attachment_descriptions[1].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
  attachment_descriptions[1].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
  attachment_descriptions[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_LOAD;
  attachment_descriptions[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
  attachment_descriptions[1].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
  attachment_descriptions[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

  VkAttachmentReference color_attachment_reference = {};
  color_attachment_reference.attachment = 0;
  color_attachment_reference.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

  VkAttachmentReference depth_attachment_reference = {};
  depth_attachment_reference.attachment = 1;
  depth_attachment_reference.layout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

  VkSubpassDescription subpass_description = {};
  subpass_description.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
  subpass_description.flags = 0;
  subpass_description.inputAttachmentCount = 0;
  subpass_description.pInputAttachments = nullptr;
  subpass_description.colorAttachmentCount = 1;
  subpass_description.pColorAttachments = &color_attachment_reference;
  subpass_description.pResolveAttachments = nullptr;
  subpass_description.pDepthStencilAttachment = &depth_attachment_reference;
  subpass_description.preserveAttachmentCount = 0;
  subpass_description.pPreserveAttachments = nullptr;

  VkRenderPassCreateInfo render_pass_create_info = {};
  render_pass_create_info.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
  render_pass_create_info.pNext = nullptr;
  render_pass_create_info.flags = 0;
  render_pass_create_info.attachmentCount = 2;
  render_pass_create_info.pAttachments = attachment_descriptions;
  render_pass_create_info.subpassCount = 1;
  render_pass_create_info.pSubpasses = &subpass_description;
  render_pass_create_info.dependencyCount = 0;
  render_pass_create_info.pDependencies = nullptr;
  VKCHECK(vkCreateRenderPass(device_, &render_pass_create_info, nullptr, &render_pass_));
}

void VulkanWorker::DestroyRenderPass() {
  VKLOG(vkDestroyRenderPass(device_, render_pass_, nullptr));
}

void VulkanWorker::CreateShaderModules() {
  VkShaderModuleCreateInfo module_create_info = {};
  module_create_info.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
  module_create_info.pNext = nullptr;
  module_create_info.flags = 0;

  // Vertex
  module_create_info.codeSize = vertex_shader_spv_.size() * sizeof(uint32_t);
  module_create_info.pCode = vertex_shader_spv_.data();
  VKCHECK(vkCreateShaderModule(device_, &module_create_info, nullptr, &vertex_shader_module_));

  // Fragment
  module_create_info.codeSize = fragment_shader_spv_.size() * sizeof(uint32_t);
  module_create_info.pCode = fragment_shader_spv_.data();
  VKCHECK(vkCreateShaderModule(device_, &module_create_info, nullptr, &fragment_shader_module_));
}

void VulkanWorker::DestroyShaderModules() {
  VKLOG(vkDestroyShaderModule(device_, vertex_shader_module_, nullptr));
  VKLOG(vkDestroyShaderModule(device_, fragment_shader_module_, nullptr));
}

void VulkanWorker::PrepareShaderStages() {
  for (size_t i = 0; i < 2; i++) {
    shader_stages_[i].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    shader_stages_[i].pNext = nullptr;
    shader_stages_[i].flags = 0;
    shader_stages_[i].pSpecializationInfo = nullptr;
    shader_stages_[i].pName = "main";
  }
  shader_stages_[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
  shader_stages_[0].module = vertex_shader_module_;
  shader_stages_[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
  shader_stages_[1].module = fragment_shader_module_;
}

void VulkanWorker::CreateFramebuffers() {
  framebuffers_.resize(images_.size());

  VkImageView attachments[2];
  attachments[1] = depth_image_view_;

  VkFramebufferCreateInfo framebuffer_create_info = {};
  framebuffer_create_info.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
  framebuffer_create_info.pNext = nullptr;
  framebuffer_create_info.flags = 0;
  framebuffer_create_info.renderPass = render_pass_;
  framebuffer_create_info.attachmentCount = 2;
  framebuffer_create_info.pAttachments = attachments;
  framebuffer_create_info.width = width_;
  framebuffer_create_info.height = height_;
  framebuffer_create_info.layers = 1;

  for (size_t i = 0; i < images_.size(); i++) {
    attachments[0] = image_views_[i];
    VKCHECK(vkCreateFramebuffer(device_, &framebuffer_create_info, nullptr, &(framebuffers_[i])));
  }
}

void VulkanWorker::DestroyFramebuffers() {
  for (size_t i = 0; i < images_.size(); i++) {
    VKLOG(vkDestroyFramebuffer(device_, framebuffers_[i], nullptr));
  }
}

void VulkanWorker::PrepareVertexBufferObject() {
  VkBufferCreateInfo vertex_buffer_create_info = {};
  vertex_buffer_create_info.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
  vertex_buffer_create_info.pNext = nullptr;
  vertex_buffer_create_info.flags = 0;
  vertex_buffer_create_info.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
  vertex_buffer_create_info.size = sizeof(vertex_input_data);
  vertex_buffer_create_info.queueFamilyIndexCount = 0;
  vertex_buffer_create_info.pQueueFamilyIndices = nullptr;
  vertex_buffer_create_info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
  VKCHECK(vkCreateBuffer(device_, &vertex_buffer_create_info, nullptr, &vertex_buffer_));

  VkMemoryRequirements vertex_memory_requirements = {};
  VKLOG(vkGetBufferMemoryRequirements(device_, vertex_buffer_, &vertex_memory_requirements));

  VkMemoryPropertyFlags vertex_memory_property_flags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
  VkMemoryAllocateInfo vertex_memory_allocate_info = {};
  vertex_memory_allocate_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
  vertex_memory_allocate_info.pNext = nullptr;
  vertex_memory_allocate_info.allocationSize = vertex_memory_requirements.size;
  vertex_memory_allocate_info.memoryTypeIndex = GetMemoryTypeIndex(vertex_memory_requirements.memoryTypeBits, vertex_memory_property_flags);
  VKCHECK(vkAllocateMemory(device_, &vertex_memory_allocate_info, nullptr, &vertex_memory_));

  void *vertex_data = nullptr;
  VKCHECK(vkMapMemory(device_, vertex_memory_, /* offset */ 0, vertex_memory_requirements.size, /* flags */ 0, &vertex_data));
  assert(vertex_data != nullptr);
  memcpy(vertex_data, vertex_input_data, sizeof(vertex_input_data));
  VKLOG(vkUnmapMemory(device_, vertex_memory_));

  VKCHECK(vkBindBufferMemory(device_, vertex_buffer_, vertex_memory_, /* offset */ 0));

  vertex_input_binding_description_.binding = 0;
  vertex_input_binding_description_.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
  vertex_input_binding_description_.stride = sizeof(vertex_input_data[0]);

  vertex_input_attribute_description_[0].binding = 0;
  vertex_input_attribute_description_[0].location = 0;
  vertex_input_attribute_description_[0].format = VK_FORMAT_R32G32B32A32_SFLOAT;
  vertex_input_attribute_description_[0].offset = 0;

  vertex_input_attribute_description_[1].binding = 0;
  vertex_input_attribute_description_[1].location = 1;
  vertex_input_attribute_description_[1].format = VK_FORMAT_R32G32B32A32_SFLOAT;
  vertex_input_attribute_description_[1].offset = 16; // offset in bytes: size of fields x, y, z, w
}

void VulkanWorker::CleanVertexBufferObject() {
  VKLOG(vkFreeMemory(device_, vertex_memory_, nullptr));
  VKLOG(vkDestroyBuffer(device_, vertex_buffer_, nullptr));
}

void VulkanWorker::CreateGraphicsPipeline() {
  VkPipelineVertexInputStateCreateInfo pipeline_vertex_input_state_create_info = {};
  pipeline_vertex_input_state_create_info.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
  pipeline_vertex_input_state_create_info.pNext = nullptr;
  pipeline_vertex_input_state_create_info.flags = 0;
  pipeline_vertex_input_state_create_info.vertexBindingDescriptionCount = 1;
  pipeline_vertex_input_state_create_info.pVertexBindingDescriptions = &vertex_input_binding_description_;
  pipeline_vertex_input_state_create_info.vertexAttributeDescriptionCount = 2;
  pipeline_vertex_input_state_create_info.pVertexAttributeDescriptions = vertex_input_attribute_description_;

  VkPipelineInputAssemblyStateCreateInfo pipeline_input_assembly_state_create_info = {};
  pipeline_input_assembly_state_create_info.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
  pipeline_input_assembly_state_create_info.pNext = nullptr;
  pipeline_input_assembly_state_create_info.flags = 0;
  pipeline_input_assembly_state_create_info.primitiveRestartEnable = VK_FALSE;
  pipeline_input_assembly_state_create_info.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

  VkPipelineRasterizationStateCreateInfo pipeline_rasterization_state_create_info = {};
  pipeline_rasterization_state_create_info.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
  pipeline_rasterization_state_create_info.pNext = nullptr;
  pipeline_rasterization_state_create_info.flags = 0;
  pipeline_rasterization_state_create_info.polygonMode = VK_POLYGON_MODE_FILL;
  pipeline_rasterization_state_create_info.cullMode = VK_CULL_MODE_BACK_BIT;
  pipeline_rasterization_state_create_info.frontFace = VK_FRONT_FACE_CLOCKWISE;
  pipeline_rasterization_state_create_info.depthClampEnable = VK_FALSE;
  pipeline_rasterization_state_create_info.rasterizerDiscardEnable = VK_FALSE;
  pipeline_rasterization_state_create_info.depthBiasEnable = VK_FALSE;
  pipeline_rasterization_state_create_info.depthBiasConstantFactor = 0;
  pipeline_rasterization_state_create_info.depthBiasClamp = 0;
  pipeline_rasterization_state_create_info.depthBiasSlopeFactor = 0;
  pipeline_rasterization_state_create_info.lineWidth = 1.0f;

  VkPipelineColorBlendStateCreateInfo pipeline_color_blend_state_create_info = {};
  pipeline_color_blend_state_create_info.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
  pipeline_color_blend_state_create_info.pNext = nullptr;
  pipeline_color_blend_state_create_info.flags = 0;
  VkPipelineColorBlendAttachmentState pipeline_color_blend_attachment_state[1];
  pipeline_color_blend_attachment_state[0].colorWriteMask = 0xf;
  pipeline_color_blend_attachment_state[0].blendEnable = VK_FALSE;
  pipeline_color_blend_attachment_state[0].alphaBlendOp = VK_BLEND_OP_ADD;
  pipeline_color_blend_attachment_state[0].colorBlendOp = VK_BLEND_OP_ADD;
  pipeline_color_blend_attachment_state[0].srcColorBlendFactor = VK_BLEND_FACTOR_ZERO;
  pipeline_color_blend_attachment_state[0].dstColorBlendFactor = VK_BLEND_FACTOR_ZERO;
  pipeline_color_blend_attachment_state[0].srcAlphaBlendFactor = VK_BLEND_FACTOR_ZERO;
  pipeline_color_blend_attachment_state[0].dstAlphaBlendFactor = VK_BLEND_FACTOR_ZERO;
  pipeline_color_blend_state_create_info.attachmentCount = 1;
  pipeline_color_blend_state_create_info.pAttachments = pipeline_color_blend_attachment_state;
  pipeline_color_blend_state_create_info.logicOpEnable = VK_FALSE;
  pipeline_color_blend_state_create_info.logicOp = VK_LOGIC_OP_NO_OP;
  pipeline_color_blend_state_create_info.blendConstants[0] = 1.0f;
  pipeline_color_blend_state_create_info.blendConstants[1] = 1.0f;
  pipeline_color_blend_state_create_info.blendConstants[2] = 1.0f;
  pipeline_color_blend_state_create_info.blendConstants[3] = 1.0f;

  VkViewport viewports[1] = {};
  viewports[0].minDepth = 0.0f;
  viewports[0].maxDepth = 1.0f;
  viewports[0].x = 0;
  viewports[0].y = 0;
  viewports[0].width = width_;
  viewports[0].height = height_;

  VkRect2D scissors[1] = {};
  scissors[0].extent.width = width_;
  scissors[0].extent.height = height_;
  scissors[0].offset.x = 0;
  scissors[0].offset.y = 0;

  VkPipelineViewportStateCreateInfo pipeline_viewport_state_create_info = {};
  pipeline_viewport_state_create_info.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
  pipeline_viewport_state_create_info.pNext = nullptr;
  pipeline_viewport_state_create_info.flags = 0;
  pipeline_viewport_state_create_info.viewportCount = 1;
  pipeline_viewport_state_create_info.pViewports = viewports;
  pipeline_viewport_state_create_info.scissorCount = 1;
  pipeline_viewport_state_create_info.pScissors = scissors;

  VkPipelineDepthStencilStateCreateInfo pipeline_depth_stencil_state_create_info = {};
  pipeline_depth_stencil_state_create_info.sType = VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO;
  pipeline_depth_stencil_state_create_info.pNext = nullptr;
  pipeline_depth_stencil_state_create_info.flags = 0;
  pipeline_depth_stencil_state_create_info.depthTestEnable = VK_TRUE;
  pipeline_depth_stencil_state_create_info.depthWriteEnable = VK_TRUE;
  pipeline_depth_stencil_state_create_info.depthCompareOp = VK_COMPARE_OP_LESS_OR_EQUAL;
  pipeline_depth_stencil_state_create_info.depthBoundsTestEnable = VK_FALSE;
  pipeline_depth_stencil_state_create_info.minDepthBounds = 0;
  pipeline_depth_stencil_state_create_info.maxDepthBounds = 0;
  pipeline_depth_stencil_state_create_info.stencilTestEnable = VK_FALSE;
  pipeline_depth_stencil_state_create_info.back.failOp = VK_STENCIL_OP_KEEP;
  pipeline_depth_stencil_state_create_info.back.passOp = VK_STENCIL_OP_KEEP;
  pipeline_depth_stencil_state_create_info.back.compareOp = VK_COMPARE_OP_ALWAYS;
  pipeline_depth_stencil_state_create_info.back.compareMask = 0;
  pipeline_depth_stencil_state_create_info.back.reference = 0;
  pipeline_depth_stencil_state_create_info.back.depthFailOp = VK_STENCIL_OP_KEEP;
  pipeline_depth_stencil_state_create_info.back.writeMask = 0;
  pipeline_depth_stencil_state_create_info.front = pipeline_depth_stencil_state_create_info.back;

  VkPipelineMultisampleStateCreateInfo pipeline_multisample_state_create_info = {};
  pipeline_multisample_state_create_info.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
  pipeline_multisample_state_create_info.pNext = nullptr;
  pipeline_multisample_state_create_info.flags = 0;
  pipeline_multisample_state_create_info.pSampleMask = nullptr;
  pipeline_multisample_state_create_info.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
  pipeline_multisample_state_create_info.sampleShadingEnable = VK_FALSE;
  pipeline_multisample_state_create_info.alphaToCoverageEnable = VK_FALSE;
  pipeline_multisample_state_create_info.alphaToOneEnable = VK_FALSE;
  pipeline_multisample_state_create_info.minSampleShading = 0.0;

  VkGraphicsPipelineCreateInfo graphics_pipeline_create_info = {};
  graphics_pipeline_create_info.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
  graphics_pipeline_create_info.pNext = nullptr;
  graphics_pipeline_create_info.layout = pipeline_layout_;
  graphics_pipeline_create_info.basePipelineHandle = VK_NULL_HANDLE;
  graphics_pipeline_create_info.basePipelineIndex = 0;
  graphics_pipeline_create_info.flags = 0;
  graphics_pipeline_create_info.pVertexInputState = &pipeline_vertex_input_state_create_info;
  graphics_pipeline_create_info.pInputAssemblyState = &pipeline_input_assembly_state_create_info;
  graphics_pipeline_create_info.pRasterizationState = &pipeline_rasterization_state_create_info;
  graphics_pipeline_create_info.pColorBlendState = &pipeline_color_blend_state_create_info;
  graphics_pipeline_create_info.pTessellationState = nullptr;
  graphics_pipeline_create_info.pMultisampleState = &pipeline_multisample_state_create_info;
  graphics_pipeline_create_info.pDynamicState = nullptr;
  graphics_pipeline_create_info.pViewportState = &pipeline_viewport_state_create_info;
  graphics_pipeline_create_info.pDepthStencilState = &pipeline_depth_stencil_state_create_info;
  graphics_pipeline_create_info.pStages = shader_stages_;
  graphics_pipeline_create_info.stageCount = 2;
  graphics_pipeline_create_info.renderPass = render_pass_;
  graphics_pipeline_create_info.subpass = 0;

  VKCHECK(vkCreateGraphicsPipelines(device_, VK_NULL_HANDLE, 1, &graphics_pipeline_create_info, nullptr, &graphics_pipeline_));
  log("GFZVK pipeline ok");
}

void VulkanWorker::DestroyGraphicsPipeline() {
  VKLOG(vkDestroyPipeline(device_, graphics_pipeline_, nullptr));
}

void VulkanWorker::LoadSpirvFromFile(FILE *source, std::vector<uint32_t> &spv) {
  uint32_t word;
  spv.resize(0);
  assert(fseek(source, 0, SEEK_SET) == 0);
  while (!feof(source)) {
    size_t num_read = 0;
    num_read = fread(&word, sizeof(uint32_t), 1, source);
    if (num_read == 1) {
      spv.push_back(word);
    } else {
      assert(feof(source) && "Error: cannot load spir-v binary");
    }
  }
}

void VulkanWorker::LoadSpirvFromArray(unsigned char *array, unsigned int len, std::vector<uint32_t> &spv) {
  assert(len % sizeof(uint32_t) == 0);
  unsigned int num_words = len / sizeof(uint32_t);
  spv.resize(num_words);

  uint32_t *p = (uint32_t *)array;
  for (size_t i = 0; i < num_words; i++) {
    spv[i] = *p;
    p++;
  }
}

void VulkanWorker::CreateSemaphore() {
  VkSemaphoreCreateInfo semaphore_create_info = {};
  semaphore_create_info.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
  semaphore_create_info.pNext = nullptr;
  semaphore_create_info.flags = 0;
  VKCHECK(vkCreateSemaphore(device_, &semaphore_create_info, nullptr, &semaphore_));
}

void VulkanWorker::DestroySemaphore() {
  VKLOG(vkDestroySemaphore(device_, semaphore_, nullptr));
}

void VulkanWorker::AcquireNextImage() {
  VKCHECK(vkAcquireNextImageKHR(device_, swapchain_, UINT64_MAX, semaphore_, VK_NULL_HANDLE, &swapchain_image_index_));
}

void VulkanWorker::PrepareCommandBuffer() {
  VkCommandBufferBeginInfo command_buffer_begin_info = {};
  command_buffer_begin_info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
  command_buffer_begin_info.pNext = nullptr;
  command_buffer_begin_info.flags = 0;
  command_buffer_begin_info.pInheritanceInfo = nullptr;
  VKCHECK(vkBeginCommandBuffer(command_buffer_, &command_buffer_begin_info));

  VkClearValue clear_values[2];
  clear_values[0].color.float32[0] = clear_color_[0];
  clear_values[0].color.float32[1] = clear_color_[1];
  clear_values[0].color.float32[2] = clear_color_[2];
  clear_values[0].color.float32[3] = clear_color_[3];
  clear_values[1].depthStencil.depth = 1.0f;
  clear_values[1].depthStencil.stencil = 0;

  VkRenderPassBeginInfo render_pass_begin_info = {};
  render_pass_begin_info.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
  render_pass_begin_info.pNext = nullptr;
  render_pass_begin_info.renderPass = render_pass_;
  render_pass_begin_info.framebuffer = framebuffers_[swapchain_image_index_];
  render_pass_begin_info.renderArea.offset.x = 0;
  render_pass_begin_info.renderArea.offset.y = 0;
  render_pass_begin_info.renderArea.extent.width = width_;
  render_pass_begin_info.renderArea.extent.height = height_;
  render_pass_begin_info.clearValueCount = 2;
  render_pass_begin_info.pClearValues = clear_values;
  VKLOG(vkCmdBeginRenderPass(command_buffer_, &render_pass_begin_info, VK_SUBPASS_CONTENTS_INLINE));

  VKLOG(vkCmdBindPipeline(command_buffer_, VK_PIPELINE_BIND_POINT_GRAPHICS, graphics_pipeline_));

  if (uniform_entries_.size() > 0) {
    VKLOG(vkCmdBindDescriptorSets(command_buffer_, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline_layout_, 0, 1, &descriptor_set_, 0, nullptr));
  }

  const VkDeviceSize offsets[1] = {0};
  VKLOG(vkCmdBindVertexBuffers(command_buffer_, 0, 1, &vertex_buffer_, offsets));

  VKLOG(vkCmdDraw(command_buffer_, /* two triangles */ 2 * 3, 1, 0, 0));

  VKLOG(vkCmdEndRenderPass(command_buffer_));
  VKCHECK(vkEndCommandBuffer(command_buffer_));
}

void VulkanWorker::CreateFence() {
  VkFenceCreateInfo fence_create_info = {};
  fence_create_info.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
  fence_create_info.pNext = nullptr;
  fence_create_info.flags = 0;
  VKCHECK(vkCreateFence(device_, &fence_create_info, nullptr, &fence_));
}

void VulkanWorker::DestroyFence() {
  VKLOG(vkDestroyFence(device_, fence_, nullptr));
}

void VulkanWorker::SubmitCommandBuffer() {
  const VkCommandBuffer command_buffers[1] = {command_buffer_};
  VkPipelineStageFlags pipeline_stage_flags = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
  VkSubmitInfo submit_info[1] = {};
  submit_info[0].sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
  submit_info[0].pNext = nullptr;
  submit_info[0].waitSemaphoreCount = 1;
  submit_info[0].pWaitSemaphores = &semaphore_;
  submit_info[0].pWaitDstStageMask = &pipeline_stage_flags;
  submit_info[0].commandBufferCount = 1;
  submit_info[0].pCommandBuffers = command_buffers;
  submit_info[0].signalSemaphoreCount = 0;
  submit_info[0].pSignalSemaphores = nullptr;
  VKCHECK(vkQueueSubmit(queue_, 1, submit_info, fence_));

  VkResult result = VK_TIMEOUT;
  do {
    // Do not use VKCHECK as VK_TIMEOUT is a valid result
    result = vkWaitForFences(device_, 1, &fence_, VK_TRUE, fence_timeout_nanoseconds_);
    log("vkWaitForFences(): %s", getVkResultString(result));
  } while (result == VK_TIMEOUT);
  assert(result == VK_SUCCESS);
}

void VulkanWorker::PresentToDisplay() {
  VkPresentInfoKHR present_info = {};
  present_info.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
  present_info.pNext = nullptr;
  present_info.swapchainCount = 1;
  present_info.pSwapchains = &swapchain_;
  present_info.pImageIndices = &swapchain_image_index_;
  present_info.pWaitSemaphores = nullptr;
  present_info.waitSemaphoreCount = 0;
  present_info.pResults = nullptr;
  VKCHECK(vkQueuePresentKHR(queue_, &present_info));
}

void VulkanWorker::UpdateImageLayout(VkCommandBuffer command_buffer, VkImage image, VkImageLayout old_image_layout, VkImageLayout new_image_layout, VkPipelineStageFlags src_stage_mask, VkPipelineStageFlags dest_stage_mask) {

  VkImageMemoryBarrier image_memory_barrier = {};
  // generic
  image_memory_barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
  image_memory_barrier.pNext = nullptr;
  image_memory_barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
  image_memory_barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
  image_memory_barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
  image_memory_barrier.subresourceRange.baseMipLevel = 0;
  image_memory_barrier.subresourceRange.levelCount = 1;
  image_memory_barrier.subresourceRange.baseArrayLayer = 0;
  image_memory_barrier.subresourceRange.layerCount = 1;
  // image
  image_memory_barrier.image = image;
  // layouts
  image_memory_barrier.oldLayout = old_image_layout;
  image_memory_barrier.newLayout = new_image_layout;
  // access masks
  image_memory_barrier.srcAccessMask = 0;
  image_memory_barrier.dstAccessMask = 0;

  switch (old_image_layout) {
    case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:
      image_memory_barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
      break;

    case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
      image_memory_barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
      break;

    case VK_IMAGE_LAYOUT_PREINITIALIZED:
      image_memory_barrier.srcAccessMask = VK_ACCESS_HOST_WRITE_BIT;
      break;

    default:
      break;
  }

  switch (new_image_layout) {
    case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
      image_memory_barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
      break;

    case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:
      image_memory_barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
      break;

    case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
      image_memory_barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
      break;

    case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:
      image_memory_barrier.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
      break;

    case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL:
      image_memory_barrier.dstAccessMask = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
      break;

    case VK_IMAGE_LAYOUT_GENERAL:
      image_memory_barrier.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
      break;

    default:
      break;
  }

  VKLOG(vkCmdPipelineBarrier(command_buffer, src_stage_mask, dest_stage_mask, 0, 0, nullptr, 0, nullptr, 1, &image_memory_barrier));
}

void VulkanWorker::PrepareExport() {

  {
    // Prepare export image
    VkImageCreateInfo export_image_create_info = {};
    export_image_create_info.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    export_image_create_info.pNext = nullptr;
    export_image_create_info.flags = 0;
    export_image_create_info.imageType = VK_IMAGE_TYPE_2D;
    export_image_create_info.format = format_;
    export_image_create_info.extent.width = width_;
    export_image_create_info.extent.height = height_;
    export_image_create_info.extent.depth = 1;
    export_image_create_info.mipLevels = 1;
    export_image_create_info.arrayLayers = 1;
    export_image_create_info.samples = VK_SAMPLE_COUNT_1_BIT;
    export_image_create_info.tiling = VK_IMAGE_TILING_LINEAR;
    export_image_create_info.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    export_image_create_info.queueFamilyIndexCount = 0;
    export_image_create_info.pQueueFamilyIndices = nullptr;
    export_image_create_info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    export_image_create_info.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    VKCHECK(vkCreateImage(device_, &export_image_create_info, nullptr, &export_image_));

    VKLOG(vkGetImageMemoryRequirements(device_, export_image_, &export_image_memory_requirements_));

    VkMemoryAllocateInfo export_image_memory_allocate_info = {};
    export_image_memory_allocate_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    export_image_memory_allocate_info.pNext = nullptr;
    export_image_memory_allocate_info.allocationSize = export_image_memory_requirements_.size;
    export_image_memory_allocate_info.memoryTypeIndex = GetMemoryTypeIndex(export_image_memory_requirements_.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    VKCHECK(vkAllocateMemory(device_, &export_image_memory_allocate_info, nullptr, &(export_image_memory_)));

    VKCHECK(vkBindImageMemory(device_, export_image_, export_image_memory_, 0));
  }

  {
    // Prepare export command buffers, one per swapchain image

    uint32_t num_swapchain_images = images_.size();
    export_command_buffers_.resize(num_swapchain_images);

    VkCommandBufferAllocateInfo export_command_buffer_allocate_info = {};
    export_command_buffer_allocate_info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    export_command_buffer_allocate_info.pNext = nullptr;
    export_command_buffer_allocate_info.commandPool = command_pool_;
    export_command_buffer_allocate_info.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    export_command_buffer_allocate_info.commandBufferCount = images_.size();
    VKCHECK(vkAllocateCommandBuffers(device_, &export_command_buffer_allocate_info, export_command_buffers_.data()));

    for (uint32_t i = 0; i < num_swapchain_images; i++) {
      VkCommandBuffer export_command_buffer = export_command_buffers_[i];

      VkCommandBufferBeginInfo export_command_buffer_begin_info = {};
      export_command_buffer_begin_info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
      export_command_buffer_begin_info.pNext = nullptr;
      export_command_buffer_begin_info.flags = 0;
      export_command_buffer_begin_info.pInheritanceInfo = nullptr;
      VKCHECK(vkBeginCommandBuffer(export_command_buffer, &export_command_buffer_begin_info));

      UpdateImageLayout(export_command_buffer, export_image_, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
      UpdateImageLayout(export_command_buffer, images_[i], VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

      VkImageCopy export_image_copy = {};
      export_image_copy.srcSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
      export_image_copy.srcSubresource.mipLevel = 0;
      export_image_copy.srcSubresource.baseArrayLayer = 0;
      export_image_copy.srcSubresource.layerCount = 1;
      export_image_copy.srcOffset.x = 0;
      export_image_copy.srcOffset.y = 0;
      export_image_copy.srcOffset.z = 0;
      export_image_copy.dstSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
      export_image_copy.dstSubresource.mipLevel = 0;
      export_image_copy.dstSubresource.baseArrayLayer = 0;
      export_image_copy.dstSubresource.layerCount = 1;
      export_image_copy.dstOffset.x = 0;
      export_image_copy.dstOffset.y = 0;
      export_image_copy.dstOffset.z = 0;
      export_image_copy.extent.width = width_;
      export_image_copy.extent.height = height_;
      export_image_copy.extent.depth = 1;
      VKLOG(vkCmdCopyImage(export_command_buffer, images_[i], VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, export_image_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &export_image_copy));

      UpdateImageLayout(export_command_buffer, export_image_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT);

      VKCHECK(vkEndCommandBuffer(export_command_buffer));
    }
  }

}

void VulkanWorker::CleanExport() {
  VKLOG(vkFreeCommandBuffers(device_, command_pool_, export_command_buffers_.size(), export_command_buffers_.data()));
  VKLOG(vkFreeMemory(device_, export_image_memory_, nullptr));
  VKLOG(vkDestroyImage(device_, export_image_, nullptr));
}

char *VulkanWorker::GetFileContent(FILE *file) {
  assert(file != nullptr);
  fseek(file, 0, SEEK_END);
  const long file_num_char = ftell(file);
  const long file_content_size = file_num_char + 1; // One more char for final '\0'
  fseek(file, 0, SEEK_SET);
  char *file_content = (char *)malloc(file_content_size);
  assert(file_content != nullptr);
  file_content[file_num_char] = '\0';
  long num_read_char = 0;
  while (num_read_char < file_num_char) {
    char *dest = file_content + num_read_char;
    long to_read = file_num_char - num_read_char;
    num_read_char += fread(dest, 1, to_read, file);
  }
  assert(num_read_char == file_num_char);
  assert(file_content[file_num_char] == '\0');
  return file_content;
}

// TODO: defensive: check that each uniform entry targets a different binding
void VulkanWorker::LoadUniforms(const char *uniforms_string) {

  // Parse
  const char *return_past_end = nullptr;
  cJSON *uniform_json = cJSON_ParseWithOpts(uniforms_string, &return_past_end, true);
  assert(return_past_end == nullptr || "Error when parsing uniform JSON");

  // Extract uniforms
  size_t num_uniforms = cJSON_GetArraySize(uniform_json);
  uniform_entries_.resize(num_uniforms);

  for (size_t i = 0; i < num_uniforms; i++) {
    cJSON *json_entry = cJSON_GetArrayItem(uniform_json, i);
    assert(cJSON_IsObject(json_entry));
    cJSON *json_binding = cJSON_GetObjectItemCaseSensitive(json_entry, "binding");
    assert(json_binding != nullptr && cJSON_IsNumber(json_binding));
    int binding = json_binding->valueint;
    assert(binding >= 0 && (size_t)binding < num_uniforms);

    UniformEntry *uniform_entry = &(uniform_entries_[binding]);

    cJSON *json_func = cJSON_GetObjectItemCaseSensitive(json_entry, "func");
    assert(json_func != nullptr && cJSON_IsString(json_func));
    std::string func = std::string(json_func->valuestring);
    cJSON *json_args = cJSON_GetObjectItemCaseSensitive(json_entry, "args");
    assert(json_args != nullptr && cJSON_IsArray(json_args));

    // Read args based on. Avoid clever tricks and keep this straightforward to ease debugging.
    if (func == "glUniform1f") {
        assert(cJSON_GetArraySize(json_args) == 1);
        cJSON *x = cJSON_GetArrayItem(json_args, 0);
        assert(cJSON_IsNumber(x));
        uniform_entry->size = sizeof(float);
        uniform_entry->value = malloc(uniform_entry->size);
        assert(uniform_entry->value != nullptr);
        ((float *)uniform_entry->value)[0] = x->valuedouble;
    } else if (func == "glUniform2f") {
        assert(cJSON_GetArraySize(json_args) == 2);
        cJSON *x = cJSON_GetArrayItem(json_args, 0);
        assert(cJSON_IsNumber(x));
        cJSON *y = cJSON_GetArrayItem(json_args, 1);
        assert(cJSON_IsNumber(y));
        uniform_entry->size = 2 * sizeof(float);
        uniform_entry->value = malloc(uniform_entry->size);
        assert(uniform_entry->value != nullptr);
        ((float *)uniform_entry->value)[0] = x->valuedouble;
        ((float *)uniform_entry->value)[1] = y->valuedouble;
    } else if (func == "glUniform3f") {
        assert(cJSON_GetArraySize(json_args) == 3);
        cJSON *x = cJSON_GetArrayItem(json_args, 0);
        assert(cJSON_IsNumber(x));
        cJSON *y = cJSON_GetArrayItem(json_args, 1);
        assert(cJSON_IsNumber(y));
        cJSON *z = cJSON_GetArrayItem(json_args, 2);
        assert(cJSON_IsNumber(z));
        uniform_entry->size = 3 * sizeof(float);
        uniform_entry->value = malloc(uniform_entry->size);
        assert(uniform_entry->value != nullptr);
        ((float *)uniform_entry->value)[0] = x->valuedouble;
        ((float *)uniform_entry->value)[1] = y->valuedouble;
        ((float *)uniform_entry->value)[2] = z->valuedouble;
    } else if (func == "glUniform4f") {
        assert(cJSON_GetArraySize(json_args) == 4);
        cJSON *x = cJSON_GetArrayItem(json_args, 0);
        assert(cJSON_IsNumber(x));
        cJSON *y = cJSON_GetArrayItem(json_args, 1);
        assert(cJSON_IsNumber(y));
        cJSON *z = cJSON_GetArrayItem(json_args, 2);
        assert(cJSON_IsNumber(z));
        cJSON *w = cJSON_GetArrayItem(json_args, 3);
        assert(cJSON_IsNumber(z));
        uniform_entry->size = 4 * sizeof(float);
        uniform_entry->value = malloc(uniform_entry->size);
        assert(uniform_entry->value != nullptr);
        ((float *)uniform_entry->value)[0] = x->valuedouble;
        ((float *)uniform_entry->value)[1] = y->valuedouble;
        ((float *)uniform_entry->value)[2] = z->valuedouble;
        ((float *)uniform_entry->value)[3] = w->valuedouble;
    }

    else if (func == "glUniform1i") {
      assert(cJSON_GetArraySize(json_args) == 1);
      cJSON *x = cJSON_GetArrayItem(json_args, 0);
      assert(cJSON_IsNumber(x));
      uniform_entry->size = sizeof(int);
      uniform_entry->value = malloc(uniform_entry->size);
      assert(uniform_entry->value != nullptr);
      ((int *)uniform_entry->value)[0] = x->valueint;
    } else if (func == "glUniform2i") {
      assert(cJSON_GetArraySize(json_args) == 2);
      cJSON *x = cJSON_GetArrayItem(json_args, 0);
      assert(cJSON_IsNumber(x));
      cJSON *y = cJSON_GetArrayItem(json_args, 1);
      assert(cJSON_IsNumber(y));
      uniform_entry->size = 2 * sizeof(int);
      uniform_entry->value = malloc(uniform_entry->size);
      assert(uniform_entry->value != nullptr);
      ((int *)uniform_entry->value)[0] = x->valueint;
      ((int *)uniform_entry->value)[1] = y->valueint;
    } else if (func == "glUniform3i") {
      assert(cJSON_GetArraySize(json_args) == 3);
      cJSON *x = cJSON_GetArrayItem(json_args, 0);
      assert(cJSON_IsNumber(x));
      cJSON *y = cJSON_GetArrayItem(json_args, 1);
      assert(cJSON_IsNumber(y));
      cJSON *z = cJSON_GetArrayItem(json_args, 2);
      assert(cJSON_IsNumber(z));
      uniform_entry->size = 3 * sizeof(int);
      uniform_entry->value = malloc(uniform_entry->size);
      assert(uniform_entry->value != nullptr);
      ((int *)uniform_entry->value)[0] = x->valueint;
      ((int *)uniform_entry->value)[1] = y->valueint;
      ((int *)uniform_entry->value)[2] = z->valueint;
    } else if (func == "glUniform4i") {
      assert(cJSON_GetArraySize(json_args) == 4);
      cJSON *x = cJSON_GetArrayItem(json_args, 0);
      assert(cJSON_IsNumber(x));
      cJSON *y = cJSON_GetArrayItem(json_args, 1);
      assert(cJSON_IsNumber(y));
      cJSON *z = cJSON_GetArrayItem(json_args, 2);
      assert(cJSON_IsNumber(z));
      cJSON *w = cJSON_GetArrayItem(json_args, 3);
      assert(cJSON_IsNumber(z));
      uniform_entry->size = 4 * sizeof(int);
      uniform_entry->value = malloc(uniform_entry->size);
      assert(uniform_entry->value != nullptr);
      ((int *)uniform_entry->value)[0] = x->valueint;
      ((int *)uniform_entry->value)[1] = y->valueint;
      ((int *)uniform_entry->value)[2] = z->valueint;
      ((int *)uniform_entry->value)[3] = w->valueint;
    }

    else {
      log("Error: invalid or unsupported uniform 'func': %s", func.c_str());
      assert(false && "Invalid or unsupported 'func' field in uniform");
    }

  }

  cJSON_Delete(uniform_json);
}

void VulkanWorker::ExportPNG(const char *png_filename) {
  log("EXPORTTOCPU START");

  VKCHECK(vkResetFences(device_, 1, &fence_));

  const VkCommandBuffer command_buffers[1] = {export_command_buffers_[swapchain_image_index_]};
  VkSubmitInfo submit_info[1] = {};
  submit_info[0].sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
  submit_info[0].pNext = nullptr;
  submit_info[0].waitSemaphoreCount = 0;
  submit_info[0].pWaitSemaphores = nullptr;
  submit_info[0].pWaitDstStageMask = nullptr;
  submit_info[0].commandBufferCount = 1;
  submit_info[0].pCommandBuffers = command_buffers;
  submit_info[0].signalSemaphoreCount = 0;
  submit_info[0].pSignalSemaphores = nullptr;

  VKCHECK(vkQueueSubmit(queue_, 1, submit_info, fence_));

  VkResult result = VK_TIMEOUT;
  do {
    // Do not use VKCHECK as VK_TIMEOUT is a valid result
    result = vkWaitForFences(device_, 1, &fence_, VK_TRUE, fence_timeout_nanoseconds_);
    log("vkWaitForFences(): %s", getVkResultString(result));
  } while (result == VK_TIMEOUT);
  assert(result == VK_SUCCESS);

  // Get export image binary blob in whatever format the device exposes
  unsigned char *source_image_blob = (unsigned char *)malloc(export_image_memory_requirements_.size);
  assert(source_image_blob != nullptr);

  void *device_memory = nullptr;
  VKCHECK(vkMapMemory(device_, export_image_memory_, 0, export_image_memory_requirements_.size, 0, &device_memory));
  assert(device_memory != nullptr);
  memcpy(source_image_blob, device_memory, export_image_memory_requirements_.size);
  VKLOG(vkUnmapMemory(device_, export_image_memory_));

  // Convert to plain, continuous rgba, as expected by lodepng
  VkImageSubresource image_subresource = {};
  image_subresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
  image_subresource.mipLevel = 0;
  image_subresource.arrayLayer = 0;
  VkSubresourceLayout subresource_layout;
  VKLOG(vkGetImageSubresourceLayout(device_, export_image_, &image_subresource, &subresource_layout));
  unsigned char *source_line = source_image_blob + subresource_layout.offset;

  unsigned char *rgba_blob = (unsigned char *)malloc(width_ * height_ * 4); // Four channels (RGBA)
  assert(rgba_blob != nullptr);
  uint32_t *rgba_pixel = (uint32_t *)rgba_blob;
  log("EXPORTTOCPU END");

  log("DUMPRGBA START");
  // Do not try to optimise this loop, it is not worth it.
  // If you still want to try: measure, measure, measure.
  // And realize: it's probably not worth it.
  for (uint32_t y = 0; y < height_; y++) {
    uint32_t *source_pixel = (uint32_t *)source_line;
    for (uint32_t x = 0; x < width_; x++) {
      switch (format_) {

        case VK_FORMAT_R8G8B8A8_UNORM:
          *rgba_pixel = *source_pixel;
          break;

        case VK_FORMAT_B8G8R8A8_UNORM:
          // fallthrough
        case VK_FORMAT_B8G8R8A8_SRGB:
          // Convert BGRA (AA RR GG BB) to RGBA (AA BB GG RR)
          *rgba_pixel = (*source_pixel & 0xff00ff00) | ((*source_pixel & 0x00ff0000) >> 16) | ((*source_pixel & 0x000000ff) << 16);
          break;

        default:
          log("Unsupported format for PNG encoding: %d", format_);
          assert(false && "Unsupported format for PNG encoding");
          break;
      }
      rgba_pixel++;
      source_pixel++;
    }
    source_line += subresource_layout.rowPitch;
  }
  log("DUMPRGBA END");

  // Convert to PNG
  std::vector<unsigned char> png;
  log("PNGENCODE START");
  lodepng::State state;
  state.encoder.auto_convert = 0;
  state.info_raw.colortype = LodePNGColorType::LCT_RGBA;
  state.info_raw.bitdepth = 8;
  state.info_png.color.colortype = LodePNGColorType::LCT_RGBA;
  state.info_png.color.bitdepth = 8;
  unsigned int png_encode_error = lodepng::encode(png, rgba_blob, width_, height_, state);
  log("PNGENCODE END");
  assert(!png_encode_error);
  log("PNGSAVEFILE START");
  lodepng::save_file(png, png_filename);
  log("PNGSAVEFILE END");

  free(source_image_blob);
  free(rgba_blob);
}

void VulkanWorker::PrepareTest(std::vector<uint32_t> &vertex_spv, std::vector<uint32_t> &fragment_spv, const char *uniforms_string) {
  log("PREPARETEST START");

  vertex_shader_spv_ = vertex_spv;
  fragment_shader_spv_ = fragment_spv;
  VKLOG(LoadUniforms(uniforms_string));

  PrepareUniformBuffer();

  if (uniform_entries_.size() > 0) {
    CreateDescriptorSetLayout();
    CreateDescriptorPool();
    AllocateDescriptorSet();
    UpdateDescriptorSet();
  }

  CreatePipelineLayout();
  CreateRenderPass();
  CreateShaderModules();
  PrepareShaderStages();
  CreateFramebuffers();
  CreateGraphicsPipeline();

  log("PREPARETEST END");
}

void VulkanWorker::CleanTest() {
  DestroyGraphicsPipeline();
  DestroyFramebuffers();
  DestroyShaderModules();
  DestroyRenderPass();

  if (uniform_entries_.size() > 0) {
    FreeDescriptorSet();
    DestroyDescriptorPool();
    DestroyDescriptorSetLayout();
  }

  DestroyPipelineLayout();
  DestroyUniformResources();
}

void VulkanWorker::DrawTest(const char *png_filename, bool skip_render) {

  if (skip_render) {
    log("SKIP_RENDER");
  } else {

    log("DRAWTEST START");
    CreateSemaphore();
    AcquireNextImage();
    PrepareCommandBuffer();
    CreateFence();
    SubmitCommandBuffer();
    log("DRAWTEST END");

    PresentToDisplay();
    ExportPNG(png_filename);
    DestroyFence();
    DestroySemaphore();
  }
}

void VulkanWorker::RunTest(FILE *vertex_file, FILE *fragment_file, FILE *uniforms_file, bool skip_render) {

  // Coherence before
  PrepareTest(coherence_vertex_shader_spv_, coherence_fragment_shader_spv_, coherence_uniforms_string);
  DrawTest(FLAGS_coherence_before.c_str(), false);
  CleanTest();

  // Test workload
  std::vector<uint32_t> vertex_spv;
  LoadSpirvFromFile(vertex_file, vertex_spv);
  std::vector<uint32_t> fragment_spv;
  LoadSpirvFromFile(fragment_file, fragment_spv);
  char *uniforms_string = GetFileContent(uniforms_file);

  PrepareTest(vertex_spv, fragment_spv, uniforms_string);

  for (int i = 0; i < FLAGS_num_render; i++) {
    std::string png_filename = FLAGS_png_template + "_" + std::to_string(i) + ".png";
    DrawTest(png_filename.c_str(), skip_render);
  }

  CleanTest();
  free(uniforms_string);

  // Coherence after
  PrepareTest(coherence_vertex_shader_spv_, coherence_fragment_shader_spv_, coherence_uniforms_string);
  DrawTest(FLAGS_coherence_after.c_str(), false);
  CleanTest();
}

// DumpWorkerInfo() is static to be callable without creating a full-blown worker.
void VulkanWorker::DumpWorkerInfo(const char *worker_info_filename) {
  VkApplicationInfo dumpinfo_application_info = {};
  dumpinfo_application_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
  dumpinfo_application_info.pNext = nullptr;
  dumpinfo_application_info.pApplicationName = "VulkanWorkerDumpInfo";
  dumpinfo_application_info.applicationVersion = 0;
  dumpinfo_application_info.pEngineName = "GraphicsFuzz";
  dumpinfo_application_info.engineVersion = 0;
  dumpinfo_application_info.apiVersion = VK_MAKE_VERSION(1,0,0);

  VkInstanceCreateInfo dumpinfo_instance_create_info = {};
  dumpinfo_instance_create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
  dumpinfo_instance_create_info.pNext = nullptr;
  dumpinfo_instance_create_info.flags = 0;
  dumpinfo_instance_create_info.pApplicationInfo = &dumpinfo_application_info;
  dumpinfo_instance_create_info.enabledLayerCount = 0;
  dumpinfo_instance_create_info.ppEnabledLayerNames = nullptr;
  dumpinfo_instance_create_info.enabledExtensionCount = 0;
  dumpinfo_instance_create_info.ppEnabledExtensionNames = nullptr;

  VkInstance dumpinfo_instance;
  VKCHECK(vkCreateInstance(&dumpinfo_instance_create_info, nullptr, &dumpinfo_instance));

  uint32_t dumpinfo_num_physical_devices = 0;
  VKCHECK(vkEnumeratePhysicalDevices(dumpinfo_instance, &dumpinfo_num_physical_devices, nullptr));
  assert(dumpinfo_num_physical_devices > 0 || "Cannot find any physical device");
  std::vector<VkPhysicalDevice> dumpinfo_physical_devices;
  dumpinfo_physical_devices.resize(dumpinfo_num_physical_devices);
  VKCHECK(vkEnumeratePhysicalDevices(dumpinfo_instance, &dumpinfo_num_physical_devices, dumpinfo_physical_devices.data()));
  if (dumpinfo_physical_devices.size() > 1) {
    log("Warning: more than one GPU detected, the worker always targets the first one listed");
  }
  VkPhysicalDevice dumpinfo_physical_device = dumpinfo_physical_devices[0];
  VkPhysicalDeviceProperties dumpinfo_physical_device_properties;
  VKLOG(vkGetPhysicalDeviceProperties(dumpinfo_physical_device, &dumpinfo_physical_device_properties));

  std::ofstream info;
  info.open(worker_info_filename);
  assert(info.is_open());
  info << "{\n";
  uint32_t api_version = dumpinfo_physical_device_properties.apiVersion;
  info << "\"apiVersion\": ";
  info << VK_VERSION_MAJOR(api_version) << ".";
  info << VK_VERSION_MINOR(api_version) << ".";
  info << VK_VERSION_PATCH(api_version) << ",\n";
  info << "\"driverVersion\": " << dumpinfo_physical_device_properties.driverVersion << ",\n";
  info << "\"vendorID\": " << dumpinfo_physical_device_properties.vendorID << ",\n";
  info << "\"deviceID\": " << dumpinfo_physical_device_properties.deviceID << ",\n";
  info << "\"deviceName\": \"" << dumpinfo_physical_device_properties.deviceName << "\"\n";
  info << "}\n";
  info.close();

  VKLOG(vkDestroyInstance(dumpinfo_instance, nullptr));
}
