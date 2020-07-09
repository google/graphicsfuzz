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

#include <vulkan/vulkan.h>

#include <cstdlib>
#include <cstring>
#include <functional>
#include <glm/glm.hpp>
#include <iostream>
#include <optional>
#include <set>
#include <stdexcept>
#include <vector>

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "SPIRV/GlslangToSpv.h"
#include "glslang/Public/ShaderLang.h"
#include "stb_image_write.h"

#define STB_IMAGE_IMPLEMENTATION
#include <stb_image.h>

const uint32_t WIDTH = 800;
const uint32_t HEIGHT = 600;

const int MAX_FRAMES_IN_FLIGHT = 2;

const std::vector<const char*> validationLayers = {
    "VK_LAYER_KHRONOS_validation"};

#ifdef NDEBUG
const bool enableValidationLayers = false;
#else
const bool enableValidationLayers = true;
#endif

const std::vector<const char*> deviceExtensions = {};

const TBuiltInResource DefaultTBuiltInResource = {
    /* .MaxLights = */ 32,
    /* .MaxClipPlanes = */ 6,
    /* .MaxTextureUnits = */ 32,
    /* .MaxTextureCoords = */ 32,
    /* .MaxVertexAttribs = */ 64,
    /* .MaxVertexUniformComponents = */ 4096,
    /* .MaxVaryingFloats = */ 64,
    /* .MaxVertexTextureImageUnits = */ 32,
    /* .MaxCombinedTextureImageUnits = */ 80,
    /* .MaxTextureImageUnits = */ 32,
    /* .MaxFragmentUniformComponents = */ 4096,
    /* .MaxDrawBuffers = */ 32,
    /* .MaxVertexUniformVectors = */ 128,
    /* .MaxVaryingVectors = */ 8,
    /* .MaxFragmentUniformVectors = */ 16,
    /* .MaxVertexOutputVectors = */ 16,
    /* .MaxFragmentInputVectors = */ 15,
    /* .MinProgramTexelOffset = */ -8,
    /* .MaxProgramTexelOffset = */ 7,
    /* .MaxClipDistances = */ 8,
    /* .MaxComputeWorkGroupCountX = */ 65535,
    /* .MaxComputeWorkGroupCountY = */ 65535,
    /* .MaxComputeWorkGroupCountZ = */ 65535,
    /* .MaxComputeWorkGroupSizeX = */ 1024,
    /* .MaxComputeWorkGroupSizeY = */ 1024,
    /* .MaxComputeWorkGroupSizeZ = */ 64,
    /* .MaxComputeUniformComponents = */ 1024,
    /* .MaxComputeTextureImageUnits = */ 16,
    /* .MaxComputeImageUniforms = */ 8,
    /* .MaxComputeAtomicCounters = */ 8,
    /* .MaxComputeAtomicCounterBuffers = */ 1,
    /* .MaxVaryingComponents = */ 60,
    /* .MaxVertexOutputComponents = */ 64,
    /* .MaxGeometryInputComponents = */ 64,
    /* .MaxGeometryOutputComponents = */ 128,
    /* .MaxFragmentInputComponents = */ 128,
    /* .MaxImageUnits = */ 8,
    /* .MaxCombinedImageUnitsAndFragmentOutputs = */ 8,
    /* .MaxCombinedShaderOutputResources = */ 8,
    /* .MaxImageSamples = */ 0,
    /* .MaxVertexImageUniforms = */ 0,
    /* .MaxTessControlImageUniforms = */ 0,
    /* .MaxTessEvaluationImageUniforms = */ 0,
    /* .MaxGeometryImageUniforms = */ 0,
    /* .MaxFragmentImageUniforms = */ 8,
    /* .MaxCombinedImageUniforms = */ 8,
    /* .MaxGeometryTextureImageUnits = */ 16,
    /* .MaxGeometryOutputVertices = */ 256,
    /* .MaxGeometryTotalOutputComponents = */ 1024,
    /* .MaxGeometryUniformComponents = */ 1024,
    /* .MaxGeometryVaryingComponents = */ 64,
    /* .MaxTessControlInputComponents = */ 128,
    /* .MaxTessControlOutputComponents = */ 128,
    /* .MaxTessControlTextureImageUnits = */ 16,
    /* .MaxTessControlUniformComponents = */ 1024,
    /* .MaxTessControlTotalOutputComponents = */ 4096,
    /* .MaxTessEvaluationInputComponents = */ 128,
    /* .MaxTessEvaluationOutputComponents = */ 128,
    /* .MaxTessEvaluationTextureImageUnits = */ 16,
    /* .MaxTessEvaluationUniformComponents = */ 1024,
    /* .MaxTessPatchComponents = */ 120,
    /* .MaxPatchVertices = */ 32,
    /* .MaxTessGenLevel = */ 64,
    /* .MaxViewports = */ 16,
    /* .MaxVertexAtomicCounters = */ 0,
    /* .MaxTessControlAtomicCounters = */ 0,
    /* .MaxTessEvaluationAtomicCounters = */ 0,
    /* .MaxGeometryAtomicCounters = */ 0,
    /* .MaxFragmentAtomicCounters = */ 8,
    /* .MaxCombinedAtomicCounters = */ 8,
    /* .MaxAtomicCounterBindings = */ 1,
    /* .MaxVertexAtomicCounterBuffers = */ 0,
    /* .MaxTessControlAtomicCounterBuffers = */ 0,
    /* .MaxTessEvaluationAtomicCounterBuffers = */ 0,
    /* .MaxGeometryAtomicCounterBuffers = */ 0,
    /* .MaxFragmentAtomicCounterBuffers = */ 1,
    /* .MaxCombinedAtomicCounterBuffers = */ 1,
    /* .MaxAtomicCounterBufferSize = */ 16384,
    /* .MaxTransformFeedbackBuffers = */ 4,
    /* .MaxTransformFeedbackInterleavedComponents = */ 64,
    /* .MaxCullDistances = */ 8,
    /* .MaxCombinedClipAndCullDistances = */ 8,
    /* .MaxSamples = */ 4,
    /* .maxMeshOutputVerticesNV = */ 256,
    /* .maxMeshOutputPrimitivesNV = */ 512,
    /* .maxMeshWorkGroupSizeX_NV = */ 32,
    /* .maxMeshWorkGroupSizeY_NV = */ 1,
    /* .maxMeshWorkGroupSizeZ_NV = */ 1,
    /* .maxTaskWorkGroupSizeX_NV = */ 32,
    /* .maxTaskWorkGroupSizeY_NV = */ 1,
    /* .maxTaskWorkGroupSizeZ_NV = */ 1,
    /* .maxMeshViewCountNV = */ 4,

    /* .limits = */
    {
        /* .nonInductiveForLoops = */ 1,
        /* .whileLoops = */ 1,
        /* .doWhileLoops = */ 1,
        /* .generalUniformIndexing = */ 1,
        /* .generalAttributeMatrixVectorIndexing = */ 1,
        /* .generalVaryingIndexing = */ 1,
        /* .generalSamplerIndexing = */ 1,
        /* .generalVariableIndexing = */ 1,
        /* .generalConstantMatrixVectorIndexing = */ 1,
    }};

static VKAPI_ATTR VkBool32 VKAPI_CALL
debugCallback(VkDebugUtilsMessageSeverityFlagBitsEXT messageSeverity,
              VkDebugUtilsMessageTypeFlagsEXT messageType,
              const VkDebugUtilsMessengerCallbackDataEXT* pCallbackData,
              void* pUserData) {
  std::cerr << "validation layer: " << pCallbackData->pMessage << std::endl;

  return VK_FALSE;
}

VkResult CreateDebugUtilsMessengerEXT(
    VkInstance instance, const VkDebugUtilsMessengerCreateInfoEXT* pCreateInfo,
    const VkAllocationCallbacks* pAllocator,
    VkDebugUtilsMessengerEXT* pDebugMessenger) {
  auto func = (PFN_vkCreateDebugUtilsMessengerEXT)vkGetInstanceProcAddr(
      instance, "vkCreateDebugUtilsMessengerEXT");
  if (func != nullptr) {
    return func(instance, pCreateInfo, pAllocator, pDebugMessenger);
  } else {
    return VK_ERROR_EXTENSION_NOT_PRESENT;
  }
}

void DestroyDebugUtilsMessengerEXT(VkInstance instance,
                                   VkDebugUtilsMessengerEXT debugMessenger,
                                   const VkAllocationCallbacks* pAllocator) {
  auto func = (PFN_vkDestroyDebugUtilsMessengerEXT)vkGetInstanceProcAddr(
      instance, "vkDestroyDebugUtilsMessengerEXT");
  if (func != nullptr) {
    func(instance, debugMessenger, pAllocator);
  }
}

inline void RequireSuccess(VkResult result) {
  if (VK_SUCCESS != result) {
    throw std::runtime_error("Vulkan error: " + std::to_string(result));
  }
}

inline void RequireSuccess(VkResult result, const char* message) {
  if (VK_SUCCESS != result) {
    throw std::runtime_error("Vulkan error: " + std::to_string(result) + " - " +
                             message);
  }
}

const char* vertexShaderText = R"(
#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;
layout(location = 2) in vec2 inTexCoord;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 fragTexCoord;

void main() {
    gl_Position = vec4(inPosition, 0.0, 1.0);
    fragColor = inColor;
    fragTexCoord = inTexCoord;
}
)";

const char* fragmentShaderText = R"(
#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec3 fragColor;
layout(location = 1) in vec2 fragTexCoord;

layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D texSampler;

void main() {
    outColor = texture(texSampler, fragTexCoord);
}
)";

bool checkValidationLayerSupport() {
  uint32_t layerCount;
  vkEnumerateInstanceLayerProperties(&layerCount, nullptr);

  std::vector<VkLayerProperties> availableLayers(layerCount);
  vkEnumerateInstanceLayerProperties(&layerCount, availableLayers.data());

  for (const char* layerName : validationLayers) {
    bool layerFound = false;

    for (const auto& layerProperties : availableLayers) {
      if (strcmp(layerName, layerProperties.layerName) == 0) {
        layerFound = true;
        break;
      }
    }

    if (!layerFound) {
      return false;
    }
  }

  return true;
}

bool checkDeviceExtensionSupport(VkPhysicalDevice device) {
  uint32_t extensionCount;
  vkEnumerateDeviceExtensionProperties(device, nullptr, &extensionCount,
                                       nullptr);

  std::vector<VkExtensionProperties> availableExtensions(extensionCount);
  vkEnumerateDeviceExtensionProperties(device, nullptr, &extensionCount,
                                       availableExtensions.data());

  std::set<std::string> requiredExtensions(deviceExtensions.begin(),
                                           deviceExtensions.end());

  for (const auto& extension : availableExtensions) {
    requiredExtensions.erase(extension.extensionName);
  }

  return requiredExtensions.empty();
}

void populateDebugMessengerCreateInfo(
    VkDebugUtilsMessengerCreateInfoEXT& vkDebugMessengerCreateInfo) {
  vkDebugMessengerCreateInfo = {};
  vkDebugMessengerCreateInfo.sType =
      VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT;
  vkDebugMessengerCreateInfo.messageSeverity =
      VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
      VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
      VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
  vkDebugMessengerCreateInfo.messageType =
      VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
      VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
      VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
  vkDebugMessengerCreateInfo.pfnUserCallback = debugCallback;
}

std::optional<uint32_t> findGraphicsQueueFamilyIndex(VkPhysicalDevice device) {
  uint32_t queueFamilyCount = 0;
  vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, nullptr);

  std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
  vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount,
                                           queueFamilies.data());

  uint32_t i = 0;
  for (const auto& queueFamily : queueFamilies) {
    if (queueFamily.queueFlags & VK_QUEUE_GRAPHICS_BIT) {
      return i;
    }
    i++;
  }
  return std::optional<uint32_t>();
}

bool isDeviceSuitable(VkPhysicalDevice device) {
  auto index = findGraphicsQueueFamilyIndex(device);
  bool extensionsSupported = checkDeviceExtensionSupport(device);
  return index.has_value() && extensionsSupported;
}

VkShaderModule createShaderModule(const std::vector<uint32_t>& code,
                                  VkDevice device) {
  VkShaderModuleCreateInfo vkShaderModuleCreateInfo = {};
  vkShaderModuleCreateInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
  vkShaderModuleCreateInfo.codeSize = code.size() * sizeof(uint32_t);
  vkShaderModuleCreateInfo.pCode = code.data();
  VkShaderModule shaderModule;
  if (vkCreateShaderModule(device, &vkShaderModuleCreateInfo, nullptr,
                           &shaderModule) != VK_SUCCESS) {
    throw std::runtime_error("Failed to create shader module.");
  }
  return shaderModule;
}

uint32_t findMemoryType(uint32_t typeFilter, VkMemoryPropertyFlags properties,
                        VkPhysicalDevice physicalDevice) {
  VkPhysicalDeviceMemoryProperties memProperties;
  vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memProperties);

  for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++) {
    if ((typeFilter & (1 << i)) && (memProperties.memoryTypes[i].propertyFlags &
                                    properties) == properties) {
      return i;
    }
  }

  throw std::runtime_error("Failed to find suitable memory type.");
}

void createBuffer(VkDeviceSize size, VkBufferUsageFlags usage,
                  VkMemoryPropertyFlags properties, VkBuffer& buffer,
                  VkDeviceMemory& bufferMemory, VkDevice device,
                  VkPhysicalDevice physicalDevice) {
  VkBufferCreateInfo bufferInfo = {};
  bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
  bufferInfo.size = size;
  bufferInfo.usage = usage;
  bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

  if (vkCreateBuffer(device, &bufferInfo, nullptr, &buffer) != VK_SUCCESS) {
    throw std::runtime_error("Failed to create buffer.");
  }

  VkMemoryRequirements memRequirements;
  vkGetBufferMemoryRequirements(device, buffer, &memRequirements);

  VkMemoryAllocateInfo allocInfo = {};
  allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
  allocInfo.allocationSize = memRequirements.size;
  allocInfo.memoryTypeIndex = findMemoryType(memRequirements.memoryTypeBits,
                                             properties, physicalDevice);

  if (vkAllocateMemory(device, &allocInfo, nullptr, &bufferMemory) !=
      VK_SUCCESS) {
    throw std::runtime_error("Failed to allocate buffer memory.");
  }

  if (vkBindBufferMemory(device, buffer, bufferMemory, 0) != VK_SUCCESS) {
    throw std::runtime_error("Failed to bind buffer memory.");
  }
}

struct Vertex {
  glm::vec2 pos;
  glm::vec3 color;
  glm::vec2 tex_coord;

  static VkVertexInputBindingDescription getBindingDescription() {
    VkVertexInputBindingDescription bindingDescription = {};
    bindingDescription.binding = 0;
    bindingDescription.stride = sizeof(Vertex);
    bindingDescription.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
    return bindingDescription;
  }

  static std::array<VkVertexInputAttributeDescription, 3>
  getAttributeDescriptions() {
    std::array<VkVertexInputAttributeDescription, 3> attributeDescriptions = {};
    attributeDescriptions[0].binding = 0;
    attributeDescriptions[0].location = 0;
    attributeDescriptions[0].format = VK_FORMAT_R32G32_SFLOAT;
    attributeDescriptions[0].offset =
        static_cast<uint32_t>(offsetof(Vertex, pos));
    attributeDescriptions[1].binding = 0;
    attributeDescriptions[1].location = 1;
    attributeDescriptions[1].format = VK_FORMAT_R32G32B32_SFLOAT;
    attributeDescriptions[1].offset =
        static_cast<uint32_t>(offsetof(Vertex, color));
    attributeDescriptions[2].binding = 0;
    attributeDescriptions[2].location = 2;
    attributeDescriptions[2].format = VK_FORMAT_R32G32_SFLOAT;
    attributeDescriptions[2].offset =
        static_cast<uint32_t>(offsetof(Vertex, tex_coord));
    return attributeDescriptions;
  }
};

// clang-format off
const std::vector<Vertex> vertices =
    {   // Red triangle
        {{0.5f, -0.5f}, {1.0f, 0.0f, 0.0f}, {1.0f, 0.0f}},
        {{0.5f, 0.5f}, {1.0f, 0.0f, 0.0f}, {1.0f, 1.0f}},
        {{-0.5f, 0.5f}, {1.0f, 0.0f, 0.0f}, {0.0f, 1.0f}},
        // Green triangle
        {{-0.5f, 0.5f}, {0.5f, 1.0f, 0.5f},{0.0f, 1.0f}},
        {{-0.5f, -0.5f}, {0.5f, 1.0f, 0.5f},{0.0f, 0.0f}},
        {{0.5f, -0.5f}, {0.5f, 1.0f, 0.5f},{1.0f, 0.0f}}
    };
// clang-format on

// TODO single time command buffer
VkCommandBuffer beginTemporaryCommandBuffer(VkDevice device,
                                            VkCommandPool commandPool) {
  VkCommandBufferAllocateInfo command_buffer_allocate_info{};
  command_buffer_allocate_info.sType =
      VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
  command_buffer_allocate_info.commandPool = commandPool;
  command_buffer_allocate_info.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  command_buffer_allocate_info.commandBufferCount = 1;

  VkCommandBuffer command_buffer_tmp;
  vkAllocateCommandBuffers(device, &command_buffer_allocate_info,
                           &command_buffer_tmp);

  VkCommandBufferBeginInfo command_buffer_begin_info_tmp{};
  command_buffer_begin_info_tmp.sType =
      VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
  command_buffer_begin_info_tmp.flags =
      VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;

  vkBeginCommandBuffer(command_buffer_tmp, &command_buffer_begin_info_tmp);
  return command_buffer_tmp;
}

// TODO end single time command buffer
void endTemporaryCommandBuffer(VkDevice device, VkQueue graphicsQueue,
                               VkCommandPool commandPool,
                               VkCommandBuffer command_buffer_tmp) {
  vkEndCommandBuffer(command_buffer_tmp);

  VkSubmitInfo submit_info{};
  submit_info.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
  submit_info.commandBufferCount = 1;
  submit_info.pCommandBuffers = &command_buffer_tmp;

  vkQueueSubmit(graphicsQueue, 1, &submit_info, VK_NULL_HANDLE);
  vkQueueWaitIdle(graphicsQueue);

  vkFreeCommandBuffers(device, commandPool, 1, &command_buffer_tmp);
}

int main() {
  VkInstance instance;
  VkDebugUtilsMessengerEXT debugMessenger;
  VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
  VkDevice device;
  VkQueue graphicsQueue;
  VkImage offScreenImage;
  VkImage texture_image;
  VkDeviceMemory offScreenImageMemory;
  VkDeviceMemory texture_image_memory;
  VkFormat offScreenImageFormat;
  VkExtent2D offScreenExtent;
  VkImageView offScreenImageView;
  VkImageView image_view_texture;
  VkSampler texture_sampler;
  VkRenderPass render_pass;
  VkDescriptorPool descriptor_pool;
  VkDescriptorSetLayout descriptor_set_layout;
  VkDescriptorSet descriptor_set;
  VkPipelineLayout pipelineLayout;
  VkPipeline graphics_pipeline;
  VkFramebuffer offScreenFramebuffer;
  VkCommandPool commandPool;
  VkBuffer vertexBuffer;
  // VkBuffer texture_image_buffer;
  VkDeviceMemory vertexBufferMemory;
  VkCommandBuffer commandBuffer;

  try {
    // Initialize Vulkan

    // Create instance
    if (enableValidationLayers && !checkValidationLayerSupport()) {
      throw std::runtime_error(
          "Validation layers requested, but not available.");
    }
    VkApplicationInfo appInfo = {};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Sample application";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "No Engine";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo vkInstanceCreateInfo = {};
    vkInstanceCreateInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    vkInstanceCreateInfo.pApplicationInfo = &appInfo;
    std::vector<const char*> extensions;
    if (enableValidationLayers) {
      extensions.push_back(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
    }
    vkInstanceCreateInfo.enabledExtensionCount =
        static_cast<uint32_t>(extensions.size());
    vkInstanceCreateInfo.ppEnabledExtensionNames = extensions.data();
    VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo;
    if (enableValidationLayers) {
      vkInstanceCreateInfo.enabledLayerCount =
          static_cast<uint32_t>(validationLayers.size());
      vkInstanceCreateInfo.ppEnabledLayerNames = validationLayers.data();
      populateDebugMessengerCreateInfo(debugCreateInfo);
      vkInstanceCreateInfo.pNext = &debugCreateInfo;
    } else {
      vkInstanceCreateInfo.enabledLayerCount = 0;
      vkInstanceCreateInfo.pNext = nullptr;
    }

    // Setup debug messenger
    if (vkCreateInstance(&vkInstanceCreateInfo, nullptr, &instance) !=
        VK_SUCCESS) {
      throw std::runtime_error("Failed to create instance.");
    }
    VkDebugUtilsMessengerCreateInfoEXT vkDebugUtilsMessengerCreateInfo;
    if (enableValidationLayers) {
      populateDebugMessengerCreateInfo(vkDebugUtilsMessengerCreateInfo);
      if (CreateDebugUtilsMessengerEXT(
              instance, &vkDebugUtilsMessengerCreateInfo, nullptr,
              &debugMessenger) != VK_SUCCESS) {
        throw std::runtime_error("failed to set up debug messenger!");
      }
    }

    // Pick physical device
    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (deviceCount == 0) {
      throw std::runtime_error("Failed to find GPUs with Vulkan support.");
    }
    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());
    for (const auto& device : devices) {
      if (isDeviceSuitable(device)) {
        physicalDevice = device;
        break;
      }
    }
    if (physicalDevice == VK_NULL_HANDLE) {
      throw std::runtime_error("Failed to find a suitable GPU.");
    }

    // Find queue families
    std::optional<uint32_t> graphicsQueueIndex =
        findGraphicsQueueFamilyIndex(physicalDevice);

    // Create logical device
    std::vector<VkDeviceQueueCreateInfo> queueCreateInfos;
    float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo queueCreateInfo = {};
    queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueCreateInfo.queueFamilyIndex = graphicsQueueIndex.value();
    queueCreateInfo.queueCount = 1;
    queueCreateInfo.pQueuePriorities = &queuePriority;
    queueCreateInfos.push_back(queueCreateInfo);
    VkPhysicalDeviceFeatures deviceFeatures = {};
    // deviceFeatures.samplerAnisotropy = VK_TRUE;
    VkDeviceCreateInfo vkDeviceCreateInfo = {};
    vkDeviceCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    vkDeviceCreateInfo.queueCreateInfoCount =
        static_cast<uint32_t>(queueCreateInfos.size());
    vkDeviceCreateInfo.pQueueCreateInfos = queueCreateInfos.data();
    vkDeviceCreateInfo.pEnabledFeatures = &deviceFeatures;
    vkDeviceCreateInfo.enabledExtensionCount =
        static_cast<uint32_t>(deviceExtensions.size());
    vkDeviceCreateInfo.ppEnabledExtensionNames = deviceExtensions.data();
    vkDeviceCreateInfo.enabledLayerCount =
        static_cast<uint32_t>(validationLayers.size());
    vkDeviceCreateInfo.ppEnabledLayerNames = validationLayers.data();
    if (vkCreateDevice(physicalDevice, &vkDeviceCreateInfo, nullptr, &device) !=
        VK_SUCCESS) {
      throw std::runtime_error("Failed to create logical device.");
    }
    vkGetDeviceQueue(device, graphicsQueueIndex.value(), 0, &graphicsQueue);

    // Image to render to
    offScreenImageFormat = VK_FORMAT_R8G8B8A8_SRGB;
    VkDeviceSize imageSize = WIDTH * HEIGHT * 4;
    VkImageCreateInfo vkImageCreateInfo = {};
    vkImageCreateInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    vkImageCreateInfo.imageType = VK_IMAGE_TYPE_2D;
    vkImageCreateInfo.extent.width = WIDTH;
    vkImageCreateInfo.extent.height = HEIGHT;
    vkImageCreateInfo.extent.depth = 1;
    vkImageCreateInfo.mipLevels = 1;
    vkImageCreateInfo.arrayLayers = 1;
    vkImageCreateInfo.format = offScreenImageFormat;
    vkImageCreateInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    vkImageCreateInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    vkImageCreateInfo.usage =
        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    vkImageCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    vkImageCreateInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    if (vkCreateImage(device, &vkImageCreateInfo, nullptr, &offScreenImage) !=
        VK_SUCCESS) {
      throw std::runtime_error("Failed to create image.");
    }
    VkMemoryRequirements imageMemoryRequirements;
    vkGetImageMemoryRequirements(device, offScreenImage,
                                 &imageMemoryRequirements);
    {
      VkMemoryAllocateInfo vkMemoryAllocInfo = {};
      vkMemoryAllocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
      vkMemoryAllocInfo.allocationSize = imageMemoryRequirements.size;
      vkMemoryAllocInfo.memoryTypeIndex =
          findMemoryType(imageMemoryRequirements.memoryTypeBits,
                         VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, physicalDevice);
      if (vkAllocateMemory(device, &vkMemoryAllocInfo, nullptr,
                           &offScreenImageMemory) != VK_SUCCESS) {
        throw std::runtime_error("Failed to allocate image memory.");
      }
    }
    if (vkBindImageMemory(device, offScreenImage, offScreenImageMemory, 0) !=
        VK_SUCCESS) {
      throw std::runtime_error("Failed to bind image memory.");
    }

    // Off screen extent
    offScreenExtent = {WIDTH, HEIGHT};

    // Create image view
    VkImageViewCreateInfo vkImageViewCreateInfo = {};
    vkImageViewCreateInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    vkImageViewCreateInfo.image = offScreenImage;
    vkImageViewCreateInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    vkImageViewCreateInfo.format = offScreenImageFormat;
    vkImageViewCreateInfo.components.r = VK_COMPONENT_SWIZZLE_IDENTITY;
    vkImageViewCreateInfo.components.g = VK_COMPONENT_SWIZZLE_IDENTITY;
    vkImageViewCreateInfo.components.b = VK_COMPONENT_SWIZZLE_IDENTITY;
    vkImageViewCreateInfo.components.a = VK_COMPONENT_SWIZZLE_IDENTITY;
    vkImageViewCreateInfo.subresourceRange.aspectMask =
        VK_IMAGE_ASPECT_COLOR_BIT;
    vkImageViewCreateInfo.subresourceRange.baseMipLevel = 0;
    vkImageViewCreateInfo.subresourceRange.levelCount = 1;
    vkImageViewCreateInfo.subresourceRange.baseArrayLayer = 0;
    vkImageViewCreateInfo.subresourceRange.layerCount = 1;
    if (vkCreateImageView(device, &vkImageViewCreateInfo, nullptr,
                          &offScreenImageView) != VK_SUCCESS) {
      throw std::runtime_error("Failed to create image view.");
    }

    // TODO texture image
    {
      // TODO descriptor layout
      VkDescriptorSetLayoutBinding sampler_layout_binding{};
      sampler_layout_binding.binding = 0;
      sampler_layout_binding.descriptorCount = 1;
      sampler_layout_binding.descriptorType =
          VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
      sampler_layout_binding.pImmutableSamplers = nullptr;
      sampler_layout_binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;

      std::array<VkDescriptorSetLayoutBinding, 1> descriptor_bindings = {
          sampler_layout_binding};
      VkDescriptorSetLayoutCreateInfo descriptor_layout_info{};
      descriptor_layout_info.sType =
          VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
      descriptor_layout_info.bindingCount =
          static_cast<uint32_t>(descriptor_bindings.size());
      descriptor_layout_info.pBindings = descriptor_bindings.data();

      if (vkCreateDescriptorSetLayout(device, &descriptor_layout_info, nullptr,
                                      &descriptor_set_layout) != VK_SUCCESS) {
        throw std::runtime_error("failed to create descriptor set layout!");
      }

      // TODO create command pool
      VkCommandPoolCreateInfo poolInfo = {};
      poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
      poolInfo.queueFamilyIndex = graphicsQueueIndex.value();
      if (vkCreateCommandPool(device, &poolInfo, nullptr, &commandPool) !=
          VK_SUCCESS) {
        throw std::runtime_error("Failed to create command pool.");
      }

      // TODO Create texture image
      // Load texture
      int tex_width = 0, tex_height = 0, tex_channels;
      stbi_uc* pixels_tex = stbi_load(
          "../../vulkan_samples/vulkan_sample_images/working.png",
          &tex_width, &tex_height, &tex_channels, STBI_rgb_alpha);
      VkDeviceSize image_tex_size = tex_width * tex_height * 4;

      if (!pixels_tex) {
        throw std::runtime_error("failed to load texture image!");
      }

      // TODO staging buffer
      VkBuffer stagingBuffer_tex;
      VkDeviceMemory stagingBufferMemory_tex;
      createBuffer(image_tex_size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                   VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                       VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                   stagingBuffer_tex, stagingBufferMemory_tex, device,
                   physicalDevice);
      {
        void* data;
        vkMapMemory(device, stagingBufferMemory_tex, 0, image_tex_size, 0,
                    &data);
        memcpy(data, pixels_tex, static_cast<size_t>(image_tex_size));
        vkUnmapMemory(device, stagingBufferMemory_tex);

        stbi_image_free(pixels_tex);
      }

      // TODO image create info
      VkImageCreateInfo image_tex_info{};
      image_tex_info.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
      image_tex_info.imageType = VK_IMAGE_TYPE_2D;
      image_tex_info.extent.width = static_cast<uint32_t>(tex_width);
      image_tex_info.extent.height = static_cast<uint32_t>(tex_height);
      image_tex_info.extent.depth = 1;
      image_tex_info.mipLevels = 1;
      image_tex_info.arrayLayers = 1;
      image_tex_info.format = VK_FORMAT_R8G8B8A8_SRGB;
      image_tex_info.tiling = VK_IMAGE_TILING_OPTIMAL;
      image_tex_info.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
      image_tex_info.usage =
          VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
      image_tex_info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
      image_tex_info.samples = VK_SAMPLE_COUNT_1_BIT;
      image_tex_info.flags = 0;

      if (vkCreateImage(device, &image_tex_info, nullptr, &texture_image) !=
          VK_SUCCESS) {
        throw std::runtime_error("failed to create image!");
      }

      // TODO memory requirements
      VkMemoryRequirements memory_image_requirements;
      vkGetImageMemoryRequirements(device, texture_image,
                                   &memory_image_requirements);

      VkMemoryAllocateInfo allocate_image_info{};
      allocate_image_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
      allocate_image_info.allocationSize = memory_image_requirements.size;
      allocate_image_info.memoryTypeIndex =
          findMemoryType(memory_image_requirements.memoryTypeBits,
                         VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, physicalDevice);

      if (vkAllocateMemory(device, &allocate_image_info, nullptr,
                           &texture_image_memory) != VK_SUCCESS) {
        throw std::runtime_error("failed to allocate texture image memory!");
      }
      if (vkBindImageMemory(device, texture_image, texture_image_memory, 0) !=
          VK_SUCCESS) {
        throw std::runtime_error("failed to bind texture image memory!");
      }

      // TODO layout transitions
      {
        VkCommandBuffer command_buffer_tmp =
            beginTemporaryCommandBuffer(device, commandPool);

        VkImageMemoryBarrier image_memory_barrier{};
        image_memory_barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        image_memory_barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        image_memory_barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        image_memory_barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        image_memory_barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        image_memory_barrier.image = texture_image;
        image_memory_barrier.subresourceRange.aspectMask =
            VK_IMAGE_ASPECT_COLOR_BIT;
        image_memory_barrier.subresourceRange.baseMipLevel = 0;
        image_memory_barrier.subresourceRange.levelCount = 1;
        image_memory_barrier.subresourceRange.baseArrayLayer = 0;
        image_memory_barrier.subresourceRange.layerCount = 1;
        image_memory_barrier.srcAccessMask = 0;
        image_memory_barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;

        vkCmdPipelineBarrier(command_buffer_tmp,
                             VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                             VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0,
                             nullptr, 1, &image_memory_barrier);

        endTemporaryCommandBuffer(device, graphicsQueue, commandPool,
                                  command_buffer_tmp);
      }

      // TODO copy buffer to image
      {
        VkCommandBuffer command_buffer_tmp =
            beginTemporaryCommandBuffer(device, commandPool);

        VkBufferImageCopy buffer_image_copy{};
        buffer_image_copy.bufferOffset = 0;
        buffer_image_copy.bufferRowLength = 0;
        buffer_image_copy.bufferImageHeight = 0;
        buffer_image_copy.imageSubresource.aspectMask =
            VK_IMAGE_ASPECT_COLOR_BIT;
        buffer_image_copy.imageSubresource.mipLevel = 0;
        buffer_image_copy.imageSubresource.baseArrayLayer = 0;
        buffer_image_copy.imageSubresource.layerCount = 1;
        buffer_image_copy.imageOffset = {0, 0, 0};
        buffer_image_copy.imageExtent = {static_cast<uint32_t>(tex_width),
                                         static_cast<uint32_t>(tex_height), 1};

        vkCmdCopyBufferToImage(
            command_buffer_tmp, stagingBuffer_tex, texture_image,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &buffer_image_copy);

        endTemporaryCommandBuffer(device, graphicsQueue, commandPool,
                                  command_buffer_tmp);
      }
      // TODO second transition image layout
      {
        VkCommandBuffer command_buffer_tmp =
            beginTemporaryCommandBuffer(device, commandPool);

        VkImageMemoryBarrier image_memory_barrier{};
        image_memory_barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        image_memory_barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        image_memory_barrier.newLayout =
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        image_memory_barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        image_memory_barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        image_memory_barrier.image = texture_image;
        image_memory_barrier.subresourceRange.aspectMask =
            VK_IMAGE_ASPECT_COLOR_BIT;
        image_memory_barrier.subresourceRange.baseMipLevel = 0;
        image_memory_barrier.subresourceRange.levelCount = 1;
        image_memory_barrier.subresourceRange.baseArrayLayer = 0;
        image_memory_barrier.subresourceRange.layerCount = 1;

        image_memory_barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        image_memory_barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;

        vkCmdPipelineBarrier(command_buffer_tmp, VK_PIPELINE_STAGE_TRANSFER_BIT,
                             VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0,
                             nullptr, 0, nullptr, 1, &image_memory_barrier);

        endTemporaryCommandBuffer(device, graphicsQueue, commandPool,
                                  command_buffer_tmp);
      }
      // TODO create imageview
      VkImageViewCreateInfo view_texture_info{};
      view_texture_info.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
      view_texture_info.image = texture_image;
      view_texture_info.viewType = VK_IMAGE_VIEW_TYPE_2D;
      view_texture_info.format = VK_FORMAT_R8G8B8A8_SRGB;
      view_texture_info.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
      view_texture_info.subresourceRange.baseMipLevel = 0;
      view_texture_info.subresourceRange.levelCount = 1;
      view_texture_info.subresourceRange.baseArrayLayer = 0;
      view_texture_info.subresourceRange.layerCount = 1;

      if (vkCreateImageView(device, &view_texture_info, nullptr,
                            &image_view_texture) != VK_SUCCESS) {
        throw std::runtime_error("failed to create texture image view!");
      }

      // TODO Sampler
      VkSamplerCreateInfo tex_sampler_info{};
      tex_sampler_info.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
      tex_sampler_info.magFilter = VK_FILTER_LINEAR;
      tex_sampler_info.minFilter = VK_FILTER_LINEAR;
      tex_sampler_info.addressModeU = VK_SAMPLER_ADDRESS_MODE_REPEAT;
      tex_sampler_info.addressModeV = VK_SAMPLER_ADDRESS_MODE_REPEAT;
      tex_sampler_info.addressModeW = VK_SAMPLER_ADDRESS_MODE_REPEAT;
      tex_sampler_info.anisotropyEnable = VK_FALSE;
      tex_sampler_info.maxAnisotropy = 1.0f;
      tex_sampler_info.borderColor = VK_BORDER_COLOR_INT_OPAQUE_BLACK;
      tex_sampler_info.unnormalizedCoordinates = VK_FALSE;
      tex_sampler_info.compareEnable = VK_FALSE;
      tex_sampler_info.compareOp = VK_COMPARE_OP_ALWAYS;
      tex_sampler_info.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;
      tex_sampler_info.mipLodBias = 0.0f;
      tex_sampler_info.minLod = 0.0f;
      tex_sampler_info.maxLod = 0.0f;

      if (vkCreateSampler(device, &tex_sampler_info, nullptr,
                          &texture_sampler) != VK_SUCCESS) {
        throw std::runtime_error("failed to create texture sampler!");
      }

      // TODO descriptor pool
      std::array<VkDescriptorPoolSize, 1> descriptor_pool_sizes{};
      descriptor_pool_sizes[0].type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
      descriptor_pool_sizes[0].descriptorCount = 1;

      VkDescriptorPoolCreateInfo pool_create_info{};
      pool_create_info.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
      pool_create_info.pNext = nullptr;
      pool_create_info.poolSizeCount =
          static_cast<uint32_t>(descriptor_pool_sizes.size());
      pool_create_info.pPoolSizes = descriptor_pool_sizes.data();
      pool_create_info.maxSets = 1;

      if (vkCreateDescriptorPool(device, &pool_create_info, nullptr,
                                 &descriptor_pool) != VK_SUCCESS) {
        throw std::runtime_error("failed to create descriptor pool!");
      }

      // TODO allocate descriptor set
      VkDescriptorSetAllocateInfo descriptor_set_allocate_info = {};
      descriptor_set_allocate_info.sType =
          VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
      descriptor_set_allocate_info.pNext = nullptr;
      descriptor_set_allocate_info.descriptorPool = descriptor_pool;
      descriptor_set_allocate_info.descriptorSetCount = 1;
      descriptor_set_allocate_info.pSetLayouts = &descriptor_set_layout;

      if (vkAllocateDescriptorSets(device, &descriptor_set_allocate_info,
                                   &descriptor_set) != VK_SUCCESS) {
        throw std::runtime_error("failed to allocate descriptor sets!");
      }

      // TODO update descriptor set
      VkDescriptorImageInfo descriptor_image_info = {};
      descriptor_image_info.imageLayout =
          VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
      descriptor_image_info.imageView = image_view_texture;
      descriptor_image_info.sampler = texture_sampler;

      VkWriteDescriptorSet write_descriptor_set = {};
      write_descriptor_set.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
      write_descriptor_set.pNext = nullptr;
      write_descriptor_set.dstSet = descriptor_set;
      write_descriptor_set.dstBinding = 0;
      write_descriptor_set.descriptorCount = 1;
      write_descriptor_set.dstArrayElement = 0;
      write_descriptor_set.descriptorType =
          VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
      write_descriptor_set.pImageInfo = &descriptor_image_info;

      vkUpdateDescriptorSets(device, 1, &write_descriptor_set, 0, nullptr);

      // TODO clear stuff
      vkDestroyBuffer(device, stagingBuffer_tex, nullptr);
      vkFreeMemory(device, stagingBufferMemory_tex, nullptr);
    }

    // Create render pass
    {
      // Color attachment
      VkAttachmentDescription color_attachment = {};
      color_attachment.format = offScreenImageFormat;
      color_attachment.samples = VK_SAMPLE_COUNT_1_BIT;
      color_attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
      color_attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
      color_attachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_LOAD;
      color_attachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_STORE;
      color_attachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
      color_attachment.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

      VkAttachmentReference colorAttachmentRef = {};
      colorAttachmentRef.attachment = 0;
      colorAttachmentRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

      VkSubpassDescription subpass = {};
      subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
      subpass.colorAttachmentCount = 1;
      subpass.pColorAttachments = &colorAttachmentRef;

      std::array<VkAttachmentDescription, 1> render_pass_attachments = {
          color_attachment};

      VkRenderPassCreateInfo renderPassCreateInfo = {};
      renderPassCreateInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
      renderPassCreateInfo.attachmentCount =
          static_cast<uint32_t>(render_pass_attachments.size());
      renderPassCreateInfo.pAttachments = render_pass_attachments.data();
      renderPassCreateInfo.subpassCount = 1;
      renderPassCreateInfo.pSubpasses = &subpass;
      VkSubpassDependency dependency = {};
      dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
      dependency.dstSubpass = 0;
      dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
      dependency.srcAccessMask = 0;
      dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
      dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
      renderPassCreateInfo.dependencyCount = 1;
      renderPassCreateInfo.pDependencies = &dependency;
      if (vkCreateRenderPass(device, &renderPassCreateInfo, nullptr,
                             &render_pass) != VK_SUCCESS) {
        throw std::runtime_error("Failed to create render pass.");
      }
    }

    // Build shaders
    ShInitialize();
    glslang::TShader vertexShader(EShLangVertex);
    vertexShader.setStrings(&vertexShaderText, 1);
    vertexShader.setEnvInput(glslang::EShSourceGlsl, EShLangVertex,
                             glslang::EShClientVulkan,
                             glslang::EShTargetVulkan_1_0);
    vertexShader.setEnvClient(glslang::EShClientVulkan,
                              glslang::EShTargetVulkan_1_0);
    vertexShader.setEnvTarget(glslang::EShTargetSpv, glslang::EShTargetSpv_1_0);
    if (!vertexShader.parse(&DefaultTBuiltInResource, 100, false,
                            EShMsgDefault)) {
      throw std::runtime_error("Error compiling vertex shader to SPIR-V.");
    }
    glslang::TProgram vertexShaderProgram;
    vertexShaderProgram.addShader(&vertexShader);
    if (!vertexShaderProgram.link(EShMsgDefault)) {
      throw std::runtime_error("Error linking vertex shader program.");
    }
    std::vector<uint32_t> vertexShaderBinary;
    glslang::GlslangToSpv(*vertexShaderProgram.getIntermediate(EShLangVertex),
                          vertexShaderBinary);
    glslang::TShader fragmentShader(EShLangFragment);
    fragmentShader.setStrings(&fragmentShaderText, 1);
    fragmentShader.setEnvInput(glslang::EShSourceGlsl, EShLangFragment,
                               glslang::EShClientVulkan,
                               glslang::EShTargetVulkan_1_0);
    fragmentShader.setEnvClient(glslang::EShClientVulkan,
                                glslang::EShTargetVulkan_1_0);
    fragmentShader.setEnvTarget(glslang::EShTargetSpv,
                                glslang::EShTargetSpv_1_0);
    if (!fragmentShader.parse(&DefaultTBuiltInResource, 100, false,
                              EShMsgDefault)) {
      throw std::runtime_error("Error compiling fragment shader to SPIR-V.");
    }
    glslang::TProgram fragmentShaderProgram;
    fragmentShaderProgram.addShader(&fragmentShader);
    if (!fragmentShaderProgram.link(EShMsgDefault)) {
      throw std::runtime_error("Error linking fragment shader program.");
    }
    std::vector<uint32_t> fragmentShaderBinary;
    glslang::GlslangToSpv(
        *fragmentShaderProgram.getIntermediate(EShLangFragment),
        fragmentShaderBinary);
    ShFinalize();

    // Create graphics pipeline
    VkShaderModule vertShaderModule =
        createShaderModule(vertexShaderBinary, device);
    VkShaderModule fragShaderModule =
        createShaderModule(fragmentShaderBinary, device);
    VkPipelineShaderStageCreateInfo vertShaderStageInfo = {};
    vertShaderStageInfo.sType =
        VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    vertShaderStageInfo.stage = VK_SHADER_STAGE_VERTEX_BIT;
    vertShaderStageInfo.module = vertShaderModule;
    vertShaderStageInfo.pName = "main";
    VkPipelineShaderStageCreateInfo fragShaderStageInfo = {};
    fragShaderStageInfo.sType =
        VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    fragShaderStageInfo.stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    fragShaderStageInfo.module = fragShaderModule;
    fragShaderStageInfo.pName = "main";
    VkPipelineShaderStageCreateInfo shaderStages[] = {vertShaderStageInfo,
                                                      fragShaderStageInfo};
    VkPipelineVertexInputStateCreateInfo vertexInputInfo = {};
    vertexInputInfo.sType =
        VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    vertexInputInfo.vertexBindingDescriptionCount = 1;
    auto bindingDescription = Vertex::getBindingDescription();
    vertexInputInfo.pVertexBindingDescriptions = &bindingDescription;
    auto attributeDescriptions = Vertex::getAttributeDescriptions();
    vertexInputInfo.vertexAttributeDescriptionCount =
        static_cast<uint32_t>(attributeDescriptions.size());
    vertexInputInfo.pVertexAttributeDescriptions = attributeDescriptions.data();
    VkPipelineInputAssemblyStateCreateInfo inputAssembly = {};
    inputAssembly.sType =
        VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
    inputAssembly.primitiveRestartEnable = VK_FALSE;
    VkViewport viewport = {};
    viewport.x = 0.0f;
    viewport.y = 0.0f;
    viewport.width = static_cast<float>(offScreenExtent.width);
    viewport.height = static_cast<float>(offScreenExtent.height);
    viewport.minDepth = 0.0f;
    viewport.maxDepth = 1.0f;
    VkRect2D scissor = {};
    scissor.offset = {0, 0};
    scissor.extent = offScreenExtent;
    VkPipelineViewportStateCreateInfo viewportState = {};
    viewportState.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    viewportState.viewportCount = 1;
    viewportState.pViewports = &viewport;
    viewportState.scissorCount = 1;
    viewportState.pScissors = &scissor;
    VkPipelineRasterizationStateCreateInfo rasterizer = {};
    rasterizer.sType =
        VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    rasterizer.depthClampEnable = VK_FALSE;
    rasterizer.rasterizerDiscardEnable = VK_FALSE;
    rasterizer.polygonMode = VK_POLYGON_MODE_FILL;
    rasterizer.lineWidth = 1.0f;
    rasterizer.cullMode = VK_CULL_MODE_BACK_BIT;
    rasterizer.frontFace = VK_FRONT_FACE_CLOCKWISE;
    rasterizer.depthBiasEnable = VK_FALSE;
    VkPipelineMultisampleStateCreateInfo multisampling = {};
    multisampling.sType =
        VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    multisampling.sampleShadingEnable = VK_FALSE;
    multisampling.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState colorBlendAttachment = {};
    colorBlendAttachment.colorWriteMask =
        VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
        VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    colorBlendAttachment.blendEnable = VK_FALSE;
    VkPipelineColorBlendStateCreateInfo colorBlending = {};
    colorBlending.sType =
        VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    colorBlending.logicOpEnable = VK_FALSE;
    colorBlending.attachmentCount = 1;
    colorBlending.pAttachments = &colorBlendAttachment;

    VkPipelineLayoutCreateInfo pipelineLayoutInfo = {};
    // todo add descriptor set layout to pipeline layout
    pipelineLayoutInfo.setLayoutCount = 1;
    pipelineLayoutInfo.pSetLayouts = &descriptor_set_layout;
    pipelineLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    if (vkCreatePipelineLayout(device, &pipelineLayoutInfo, nullptr,
                               &pipelineLayout) != VK_SUCCESS) {
      throw std::runtime_error("Failed to create pipeline layout.");
    }
    VkGraphicsPipelineCreateInfo pipelineInfo = {};
    pipelineInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pipelineInfo.stageCount = 2;
    pipelineInfo.pStages = shaderStages;
    pipelineInfo.pVertexInputState = &vertexInputInfo;
    pipelineInfo.pInputAssemblyState = &inputAssembly;
    pipelineInfo.pViewportState = &viewportState;
    pipelineInfo.pRasterizationState = &rasterizer;
    pipelineInfo.pMultisampleState = &multisampling;
    pipelineInfo.pColorBlendState = &colorBlending;
    pipelineInfo.layout = pipelineLayout;
    pipelineInfo.renderPass = render_pass;
    pipelineInfo.subpass = 0;
    if (vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &pipelineInfo,
                                  nullptr, &graphics_pipeline) != VK_SUCCESS) {
      throw std::runtime_error("Failed to create graphics pipeline.");
    }
    vkDestroyShaderModule(device, fragShaderModule, nullptr);
    vkDestroyShaderModule(device, vertShaderModule, nullptr);

    // Create framebuffer
    VkImageView attachments[] = {offScreenImageView};
    VkFramebufferCreateInfo framebufferInfo = {};
    framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    framebufferInfo.renderPass = render_pass;
    framebufferInfo.attachmentCount = 1;
    framebufferInfo.pAttachments = attachments;
    framebufferInfo.width = offScreenExtent.width;
    framebufferInfo.height = offScreenExtent.height;
    framebufferInfo.layers = 1;
    if (vkCreateFramebuffer(device, &framebufferInfo, nullptr,
                            &offScreenFramebuffer) != VK_SUCCESS) {
      throw std::runtime_error("Failed to create framebuffer.");
    }

    // Create vertex buffer
    VkBufferCreateInfo vkBufferCreateInfo = {};
    vkBufferCreateInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    vkBufferCreateInfo.size = sizeof(vertices[0]) * vertices.size();
    vkBufferCreateInfo.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    vkBufferCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(device, &vkBufferCreateInfo, nullptr, &vertexBuffer) !=
        VK_SUCCESS) {
      throw std::runtime_error("Failed to create vertex buffer.");
    }
    VkMemoryRequirements vertexBufferMemoryRequirements;
    vkGetBufferMemoryRequirements(device, vertexBuffer,
                                  &vertexBufferMemoryRequirements);
    {
      VkMemoryAllocateInfo vkMemoryAllocateInfo = {};
      vkMemoryAllocateInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
      vkMemoryAllocateInfo.allocationSize = vertexBufferMemoryRequirements.size;
      vkMemoryAllocateInfo.memoryTypeIndex =
          findMemoryType(vertexBufferMemoryRequirements.memoryTypeBits,
                         VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, physicalDevice);
      if (vkAllocateMemory(device, &vkMemoryAllocateInfo, nullptr,
                           &vertexBufferMemory) != VK_SUCCESS) {
        throw std::runtime_error("Failed to allocate vertex buffer memory.");
      }
    }
    if (vkBindBufferMemory(device, vertexBuffer, vertexBufferMemory, 0) !=
        VK_SUCCESS) {
      throw std::runtime_error("Failed binding vertex buffer memory.");
    }
    {
      void* data;
      vkMapMemory(device, vertexBufferMemory, 0, vkBufferCreateInfo.size, 0,
                  &data);
      memcpy(data, vertices.data(),
             static_cast<uint32_t>(vkBufferCreateInfo.size));
      VkMappedMemoryRange rangeToFlush = {};
      rangeToFlush.sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
      rangeToFlush.memory = vertexBufferMemory;
      rangeToFlush.offset = 0;
      rangeToFlush.size = VK_WHOLE_SIZE;
      vkFlushMappedMemoryRanges(device, 1, &rangeToFlush);
      vkUnmapMemory(device, vertexBufferMemory);
    }

    // Create command buffer
    VkCommandBufferAllocateInfo vkCommandBufferAllocateInfo = {};
    vkCommandBufferAllocateInfo.sType =
        VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    vkCommandBufferAllocateInfo.commandPool = commandPool;
    vkCommandBufferAllocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    vkCommandBufferAllocateInfo.commandBufferCount = 1;
    if (vkAllocateCommandBuffers(device, &vkCommandBufferAllocateInfo,
                                 &commandBuffer) != VK_SUCCESS) {
      throw std::runtime_error("Failed to allocate command buffers.");
    }
    // Draw triangles
    {
      VkCommandBufferBeginInfo begin_info = {};
      begin_info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
      begin_info.pNext = nullptr;
      begin_info.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
      begin_info.pInheritanceInfo = nullptr;
      if (vkBeginCommandBuffer(commandBuffer, &begin_info) != VK_SUCCESS) {
        throw std::runtime_error("Failed to begin recording command buffer.");
      }
      std::array<VkClearValue, 2> clear_values = {};
      clear_values[0].color = {0.0f, 0.0f, 0.0f, 1.0f};
      clear_values[1].depthStencil = {0.0f, 0};

      VkRenderPassBeginInfo renderPassInfo = {};
      renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
      renderPassInfo.renderPass = render_pass;
      renderPassInfo.framebuffer = offScreenFramebuffer;
      renderPassInfo.renderArea.offset = {0, 0};
      renderPassInfo.renderArea.extent = offScreenExtent;
      renderPassInfo.clearValueCount =
          static_cast<uint32_t>(clear_values.size());
      renderPassInfo.pClearValues = clear_values.data();
      vkCmdBeginRenderPass(commandBuffer, &renderPassInfo,
                           VK_SUBPASS_CONTENTS_INLINE);
      vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                        graphics_pipeline);
      VkBuffer vertexBuffers[] = {vertexBuffer};
      VkDeviceSize offsets[] = {0};
      vkCmdBindVertexBuffers(commandBuffer, 0, 1, vertexBuffers, offsets);
      // TODO bind descriptor sets
      vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                              pipelineLayout, 0, 1, &descriptor_set, 0,
                              nullptr);
      vkCmdDraw(commandBuffer, 6, 1, 0, 0);
      vkCmdEndRenderPass(commandBuffer);
      if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
        throw std::runtime_error("Failed to record command buffer.");
      }

      VkSubmitInfo submit_info = {};
      submit_info.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
      submit_info.pNext = nullptr;
      submit_info.waitSemaphoreCount = 0;
      submit_info.pSignalSemaphores = nullptr;
      submit_info.pWaitDstStageMask = nullptr;
      submit_info.commandBufferCount = 1;
      submit_info.pCommandBuffers = &commandBuffer;
      if (vkQueueSubmit(graphicsQueue, 1, &submit_info, VK_NULL_HANDLE) !=
          VK_SUCCESS) {
        throw std::runtime_error("Failed to submit draw command buffer.");
      }

      vkDeviceWaitIdle(device);
    }

    VkBuffer stagingBuffer;
    VkDeviceMemory stagingBufferMemory;
    createBuffer(imageSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                 VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, stagingBuffer,
                 stagingBufferMemory, device, physicalDevice);

    {
      VkCommandBufferAllocateInfo tempCommandBufferAllocateInfo = {};
      tempCommandBufferAllocateInfo.sType =
          VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
      tempCommandBufferAllocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
      tempCommandBufferAllocateInfo.commandPool = commandPool;
      tempCommandBufferAllocateInfo.commandBufferCount = 1;
      VkCommandBuffer tempCommandBuffer;
      vkAllocateCommandBuffers(device, &tempCommandBufferAllocateInfo,
                               &tempCommandBuffer);
      VkCommandBufferBeginInfo tempCommandBufferBeginInfo = {};
      tempCommandBufferBeginInfo.sType =
          VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
      tempCommandBufferBeginInfo.flags =
          VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
      vkBeginCommandBuffer(tempCommandBuffer, &tempCommandBufferBeginInfo);

      VkImageMemoryBarrier renderFinishBarrier = {};
      renderFinishBarrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
      renderFinishBarrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
      renderFinishBarrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
      renderFinishBarrier.oldLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
      renderFinishBarrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
      renderFinishBarrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
      renderFinishBarrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
      renderFinishBarrier.image = offScreenImage;
      renderFinishBarrier.subresourceRange.aspectMask =
          VK_IMAGE_ASPECT_COLOR_BIT;
      renderFinishBarrier.subresourceRange.baseMipLevel = 0;
      renderFinishBarrier.subresourceRange.levelCount = 1;
      renderFinishBarrier.subresourceRange.baseArrayLayer = 0;
      renderFinishBarrier.subresourceRange.layerCount = 1;
      vkCmdPipelineBarrier(tempCommandBuffer,
                           VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                           VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0,
                           nullptr, 1, &renderFinishBarrier);

      VkBufferImageCopy region = {};
      region.bufferOffset = 0;
      region.bufferRowLength = WIDTH;
      region.bufferImageHeight = HEIGHT;
      region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
      region.imageSubresource.mipLevel = 0;
      region.imageSubresource.baseArrayLayer = 0;
      region.imageSubresource.layerCount = 1;
      region.imageOffset = {0, 0, 0};
      region.imageExtent = {WIDTH, HEIGHT, 1};
      vkCmdCopyImageToBuffer(tempCommandBuffer, offScreenImage,
                             VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                             stagingBuffer, 1, &region);

      VkBufferMemoryBarrier copyFinishBarrier = {};
      copyFinishBarrier.sType = VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER;
      copyFinishBarrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
      copyFinishBarrier.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
      copyFinishBarrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
      copyFinishBarrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
      copyFinishBarrier.buffer = stagingBuffer;
      copyFinishBarrier.offset = 0;
      copyFinishBarrier.size = VK_WHOLE_SIZE;

      vkCmdPipelineBarrier(tempCommandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                           VK_PIPELINE_STAGE_HOST_BIT, 0, 0, nullptr, 1,
                           &copyFinishBarrier, 0, nullptr);

      vkEndCommandBuffer(tempCommandBuffer);
      VkSubmitInfo tempSubmitInfo = {};
      tempSubmitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
      tempSubmitInfo.commandBufferCount = 1;
      tempSubmitInfo.pCommandBuffers = &tempCommandBuffer;
      vkQueueSubmit(graphicsQueue, 1, &tempSubmitInfo, VK_NULL_HANDLE);
      vkQueueWaitIdle(graphicsQueue);
      vkFreeCommandBuffers(device, commandPool, 1, &tempCommandBuffer);
    }

    vkDeviceWaitIdle(device);

    unsigned char pixels[imageSize];
    memset(pixels, 0, static_cast<size_t>(imageSize));

    {
      void* data;
      vkMapMemory(device, stagingBufferMemory, 0, imageSize, 0, &data);
      VkMappedMemoryRange rangeToInvalidate = {};
      rangeToInvalidate.sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
      rangeToInvalidate.memory = stagingBufferMemory;
      rangeToInvalidate.offset = 0;
      rangeToInvalidate.size = VK_WHOLE_SIZE;
      vkInvalidateMappedMemoryRanges(device, 1, &rangeToInvalidate);
      memcpy(pixels, data, static_cast<size_t>(imageSize));
      vkUnmapMemory(device, stagingBufferMemory);
    }

    const int NUM_CHANNELS = 4;
    stbi_write_png("out.png", WIDTH, HEIGHT, NUM_CHANNELS, pixels,
                   WIDTH * NUM_CHANNELS);

    // Clean up
    vkDestroyBuffer(device, stagingBuffer, nullptr);
    vkFreeMemory(device, stagingBufferMemory, nullptr);
    vkDestroyBuffer(device, vertexBuffer, nullptr);
    vkFreeMemory(device, vertexBufferMemory, nullptr);
    // vkDestroyBuffer(device, texture_image_buffer, nullptr);
    vkDestroyCommandPool(device, commandPool, nullptr);
    vkDestroyFramebuffer(device, offScreenFramebuffer, nullptr);
    vkDestroyPipeline(device, graphics_pipeline, nullptr);
    vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
    vkDestroyRenderPass(device, render_pass, nullptr);
    // vkFreeDescriptorSets(device, descriptor_pool, 1, &descriptor_set);
    vkDestroyDescriptorSetLayout(device, descriptor_set_layout, nullptr);
    vkDestroyDescriptorPool(device, descriptor_pool, nullptr);
    vkDestroySampler(device, texture_sampler, nullptr);
    vkDestroyImageView(device, image_view_texture, nullptr);
    vkDestroyImageView(device, offScreenImageView, nullptr);
    vkDestroyImage(device, offScreenImage, nullptr);
    vkDestroyImage(device, texture_image, nullptr);
    vkFreeMemory(device, offScreenImageMemory, nullptr);
    vkFreeMemory(device, texture_image_memory, nullptr);
    vkDestroyDevice(device, nullptr);
    if (enableValidationLayers) {
      DestroyDebugUtilsMessengerEXT(instance, debugMessenger, nullptr);
    }
    vkDestroyInstance(instance, nullptr);

  } catch (const std::exception& e) {
    std::cerr << e.what() << std::endl;
    return EXIT_FAILURE;
  }

  return EXIT_SUCCESS;
}