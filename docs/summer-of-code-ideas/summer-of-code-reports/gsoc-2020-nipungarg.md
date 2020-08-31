# Google Summer of Code 2020 Report - Nipun Garg

###### Personal Links:
###### [Github](https://github.com/nipunG314)

###### Proposal:
###### [New Vulkan Samples to Extend Vulkan API Coverage](https://summerofcode.withgoogle.com/projects/#4993422496104448)

###### Mentors: 
###### Hugues Evrard, Paul Thomson

## Deliverables
My project revolved around writing samples in Vulkan that would be utilized for the development of the [Android GPU Inspector](https://github.com/google/agi) project. As such, the focus of the project was twofold: efficiently measuring the coverage of the Vulkan API provided by the existing samples, and to add new samples to increase that coverage.

While this was the initial scope of our project, we also spent time building a sample that could be used to study the synchronization between different render passes. This was covered by the new Overlapping Frames sample. We also started work on a sample to combine the graphics and compute capabilities of Vulkan. However, the last one could not be satisfactorily completed in the alloted time.

Over the course of the project, code was contributed to two separate repos:
* [GraphicsFuzz](https://github.com/google/graphicsfuzz/) for Code Coverage Scripts
* [Vulkan Test Applications](https://github.com/google/vulkan_test_applications) for the new Sample and various maintenance fixes

This report describes the deliverables and results of the project over the summer in more detail.
 
### GraphicsFuzz - Coverage Analysis Tools for Vulkan Samples

We added a simple set of Bash+Python tools for quickly determining the coverage of the Vulkan API provided by the samples we had in hand.

PRs:
[#990](https://github.com/google/graphicsfuzz/pull/990):
Add experiments directory for coverage experiments 

[#998](https://github.com/google/graphicsfuzz/pull/998):
Add demoChecker to run all demos and log return codes

[#1005](https://github.com/google/graphicsfuzz/pull/1005):
Add build scripts for Vulkan Khronos Samples and Imagination Demos

[#1006](https://github.com/google/graphicsfuzz/pull/1006):
Add find\_uncovered\_api.py

[#1007](https://github.com/google/graphicsfuzz/pull/1007):
Replace the bash shebang with `#!/usr/bin/env bash`

[#1011](https://github.com/google/graphicsfuzz/pull/1011):
Modify find\_uncovered\_api.py into api\_coverage.py

[#1012](https://github.com/google/graphicsfuzz/pull/1012):
Fix swiftshader build config

We used these Code Coverage Scripts to measure the coverage of the Vulkan API from four different repos:
* [Google Test Applications](https://github.com/google/vulkan_test_applications)
* [Khronos Samples](https://github.com/KhronosGroup/Vulkan-Samples)
* [Sascha Willems Demos](https://github.com/SaschaWillems/Vulkan)
* [Imagination Demos](https://github.com/powervr-graphics/Native_SDK)

From the above projects, we filtered out the Vulkan API calls and measured their execution counts. This allowed us to narrow down API calls that had never been called such as `vkTrimCommandPool`. For our Vulkan driver, we used [SwiftShader](https://github.com/google/swiftshader) as our CPU-based Vulkan Implementation.

Below is the full-list that was compiled.

```
vkAcquireNextImage2KHR,0
vkAcquireNextImageKHR,25839
vkAllocateCommandBuffers,95
vkAllocateDescriptorSets,25
vkAllocateMemory,379
vkBeginCommandBuffer,19477
vkBindBufferMemory,507
vkBindBufferMemory2,0
vkBindImageMemory,133
vkBindImageMemory2,0
vkCmdBeginQuery,2
vkCmdBeginRenderPass,19124
vkCmdBeginRenderPass2KHR,0
vkCmdBindDescriptorSets,150450
vkCmdBindIndexBuffer,12
vkCmdBindPipeline,19133
vkCmdBindVertexBuffers,14
vkCmdBlitImage,1
vkCmdClearAttachments,1
vkCmdClearColorImage,3
vkCmdClearDepthStencilImage,1
vkCmdCopyBuffer,203
vkCmdCopyBufferToImage,6
vkCmdCopyImage,1
vkCmdCopyImageToBuffer,52
vkCmdCopyQueryPoolResults,0
vkCmdDispatch,3
vkCmdDispatchBase,0
vkCmdDispatchIndirect,1
vkCmdDraw,150440
vkCmdDrawIndexed,9
vkCmdDrawIndexedIndirect,1
vkCmdDrawIndirect,1
vkCmdEndQuery,2
vkCmdEndRenderPass,19124
vkCmdEndRenderPass2KHR,0
vkCmdExecuteCommands,1
vkCmdFillBuffer,2
vkCmdNextSubpass,1
vkCmdNextSubpass2KHR,0
vkCmdPipelineBarrier,38507
vkCmdPushConstants,3
vkCmdResetEvent,1
vkCmdResetQueryPool,3
vkCmdResolveImage,3
vkCmdSetBlendConstants,1
vkCmdSetDepthBias,1
vkCmdSetDepthBounds,0
vkCmdSetDeviceMask,0
vkCmdSetEvent,3
vkCmdSetLineStippleEXT,0
vkCmdSetLineWidth,1
vkCmdSetScissor,1
vkCmdSetStencilCompareMask,3
vkCmdSetStencilReference,3
vkCmdSetStencilWriteMask,3
vkCmdSetViewport,19108
vkCmdUpdateBuffer,3
vkCmdWaitEvents,11
vkCmdWriteTimestamp,0
vkCreateBuffer,683
vkCreateBufferView,2
vkCreateCommandPool,54
vkCreateComputePipelines,3
vkCreateDescriptorPool,25
vkCreateDescriptorSetLayout,50
vkCreateDescriptorUpdateTemplate,0
vkCreateDevice,77
vkCreateEvent,6
vkCreateFence,39
vkCreateFramebuffer,21
vkCreateGraphicsPipelines,37
vkCreateImage,188
vkCreateImageView,32
vkCreateInstance,112
vkCreatePipelineCache,51
vkCreatePipelineLayout,32
vkCreateQueryPool,5
vkCreateRenderPass,46
vkCreateRenderPass2KHR,0
vkCreateSampler,12
vkCreateSamplerYcbcrConversion,0
vkCreateSemaphore,6754
vkCreateShaderModule,87
vkCreateSwapchainKHR,55
vkCreateXcbSurfaceKHR,69
vkCreateXlibSurfaceKHR,0
vkDestroyBuffer,684
vkDestroyBufferView,3
vkDestroyCommandPool,54
vkDestroyDescriptorPool,26
vkDestroyDescriptorSetLayout,51
vkDestroyDescriptorUpdateTemplate,0
vkDestroyDevice,75
vkDestroyEvent,6
vkDestroyFence,40
vkDestroyFramebuffer,22
vkDestroyImage,80
vkDestroyImageView,33
vkDestroyInstance,97
vkDestroyPipeline,40
vkDestroyPipelineCache,53
vkDestroyPipelineLayout,33
vkDestroyQueryPool,6
vkDestroyRenderPass,47
vkDestroySampler,13
vkDestroySamplerYcbcrConversion,0
vkDestroySemaphore,6755
vkDestroyShaderModule,88
vkDestroySurfaceKHR,69
vkDestroySwapchainKHR,56
vkDeviceWaitIdle,98
vkEndCommandBuffer,19477
vkEnumerateDeviceExtensionProperties,493
vkEnumerateDeviceLayerProperties,0
vkEnumerateInstanceExtensionProperties,634
vkEnumerateInstanceLayerProperties,0
vkEnumerateInstanceVersion,0
vkEnumeratePhysicalDeviceGroups,0
vkEnumeratePhysicalDevices,924
vkFlushMappedMemoryRanges,307601
vkFreeCommandBuffers,86
vkFreeDescriptorSets,14
vkFreeMemory,271
vkGetBufferMemoryRequirements,707
vkGetBufferMemoryRequirements2,0
vkGetDescriptorSetLayoutSupport,0
vkGetDeviceGroupPeerMemoryFeatures,0
vkGetDeviceGroupPresentCapabilitiesKHR,0
vkGetDeviceGroupSurfacePresentModesKHR,0
vkGetDeviceMemoryCommitment,0
vkGetDeviceProcAddr,19551
vkGetDeviceQueue,58
vkGetDeviceQueue2,0
vkGetEventStatus,3
vkGetFenceStatus,4
vkGetImageMemoryRequirements,80
vkGetImageMemoryRequirements2,0
vkGetImageSparseMemoryRequirements,2
vkGetImageSparseMemoryRequirements2,0
vkGetImageSubresourceLayout,1
vkGetInstanceProcAddr,0
vkGetMemoryFdKHR,0
vkGetMemoryFdPropertiesKHR,0
vkGetMemoryHostPointerPropertiesEXT,0
vkGetPhysicalDeviceExternalBufferProperties,0
vkGetPhysicalDeviceExternalFenceProperties,0
vkGetPhysicalDeviceExternalSemaphoreProperties,0
vkGetPhysicalDeviceFeatures,98
vkGetPhysicalDeviceFeatures2,0
vkGetPhysicalDeviceFormatProperties,762
vkGetPhysicalDeviceFormatProperties2,0
vkGetPhysicalDeviceImageFormatProperties,502
vkGetPhysicalDeviceImageFormatProperties2,0
vkGetPhysicalDeviceMemoryProperties,124
vkGetPhysicalDeviceMemoryProperties2,0
vkGetPhysicalDevicePresentRectanglesKHR,0
vkGetPhysicalDeviceProperties,192
vkGetPhysicalDeviceProperties2,3
vkGetPhysicalDeviceQueueFamilyProperties,323
vkGetPhysicalDeviceQueueFamilyProperties2,0
vkGetPhysicalDeviceSparseImageFormatProperties,0
vkGetPhysicalDeviceSparseImageFormatProperties2,0
vkGetPhysicalDeviceSurfaceCapabilitiesKHR,59
vkGetPhysicalDeviceSurfaceFormatsKHR,119
vkGetPhysicalDeviceSurfacePresentModesKHR,119
vkGetPhysicalDeviceSurfaceSupportKHR,66
vkGetPhysicalDeviceXcbPresentationSupportKHR,1
vkGetPhysicalDeviceXlibPresentationSupportKHR,0
vkGetPipelineCacheData,4
vkGetQueryPoolResults,0
vkGetRenderAreaGranularity,1
vkGetSemaphoreFdKHR,0
vkGetSwapchainImagesKHR,118
vkImportSemaphoreFdKHR,0
vkInvalidateMappedMemoryRanges,244
vkMapMemory,339519
vkMergePipelineCaches,1
vkQueueBindSparse,0
vkQueuePresentKHR,25838
vkQueueSubmit,65288
vkQueueWaitIdle,19437
vkResetCommandBuffer,19396
vkResetCommandPool,4
vkResetDescriptorPool,2
vkResetEvent,12
vkResetFences,25838
vkSetEvent,11
vkTrimCommandPool,1
vkUnmapMemory,339519
vkUpdateDescriptorSetWithTemplate,0
vkUpdateDescriptorSets,26
vkWaitForFences,44991
vk_icdGetInstanceProcAddr,9838
vk_icdNegotiateLoaderICDInterfaceVersion,205
```

### Vulkan Test Applications - Add vkTrimCommandPool to GAPID Test

From the list that was generated by our code coverage scripts, we narrowed down the Vulkan primitives that had not been executed even once. We added a sample to the [Vulkan Test Applications Repo](https://github.com/google/vulkan_test_applications) in order to test the Vulkan primitive `vkTrimCommandPool` and extend its API coverage.

##### Signatures
```c++
void vkTrimCommandPool(
    VkDevice                                    device,
    VkCommandPool                               commandPool,
    VkCommandPoolTrimFlags                      flags);
```

PRs:
[#162](https://github.com/google/vulkan_test_applications/pull/162):
This PR adds a `vkTrimCommandPool` call to the `CreateResetDestroyCommandPool\_test`, renaming it to `CreateTrimResetDestroyCommandPool\_test`.

### Vulkan Test Applications - Add Overlapping Frames Sample

The idea behind this sample is to implement the Overlapping Frames method of preparing multiple frames at once in the GPU. This work was beyond the original goal of extending the coverage of Vulkan primitives, but we decided to do this because this particular technique is now being adopted into game engines and software that depends on heavy graphics processing. This presents a challenge for Vulkan tools, such as AGI, to know exactly when and where one frame ends and the next one begins.

Since none of the existing samples that we found had implemented this method, we decided to implement the new Overlapping Frames sample.

This sample renders a triangle with a post-processing effect split into multiple interleaved render passes.

The idea is to create a sample that can be used to test the Overlapping Frames Synchronization pattern on AGI. This pattern is described in more detail below.

##### Frame Creation Pattern

We have two render passes:
- Geometry Render Pass (GRP)
- Post Processing Render Pass (PPRP)

For a given frame number N, the sample is submitting work as follows:

... -> GRP\_{N} -> PPRP\_{N-1} -> GRP\_{N+1} -> PPRP\_{N} -> ...

We use three sets of VkSemaphores and one set of VkFences to accomplish the necessary Vulkan
synchronization. Each set has a size equal to the number of swapchain images
(triple-buffered at a minimum).

##### Semaphores
- gRenderFinished: Signaled when the Geometry Render Pass is completed.
- imageAcquired: Signaled when a swapchain image is acquired for the Post Processing Render Pass.
- postRenderFinished: Signaled when the Post Processing Render Pass is completed.

##### Fences
- renderingFence: Signaled when the Post Processing Render Pass is completed.

We use only one set of fences to synchronize both render passes. Since the two
render passes are sequential - PPRP\_{N} cannot run before GRP\_{N} has been
completed.

PRs:
[#169](https://github.com/google/vulkan_test_applications/pull/169):
This PR creates a simple overlapping frames sample where we first draw a triangle to a temporary Vulkan Image, and then invert its color in separate render passes.

[#184](https://github.com/google/vulkan_test_applications/pull/184):
This PR modifies the overlapping_frames sample for different image each frame.

### Vulkan Test Applications - Combining Graphics and Compute with Raymarching and Noise Texture
The idea behind this sample is to combine the Compute and Graphics capabilities of Vulkan in order to visualize 3D Noise Textures. For the sample, we would procedurally generate a 3D Perlin Texture using the Compute queue and then use the resulting texture and a given surface level as inputs for a Raymarching Renderer.

This is the final sample that was planned under the project. However, I could not get it to work satisfactorily under the current schedule. Currently, the setup for Graphics of the project exists. Still, the shaders need to be re-written as they do not work as desired right now. The Compute side of the project is unimplemented at the time of writing this report.

[Current Work Link](https://github.com/nipunG314/vulkan_test_applications/tree/noise_visual_raymarcher)

I plan to spend a couple weeks after the program to get this up and running.

### Vulkan Test Applications - Additional Fixes

These are changes that were not originally part of the scope of the project, but were needed to progress further. They are general maintenance and refactoring changes that benefit the Vulkan Test Applications project as a whole.

PRs:
[#159](https://github.com/google/vulkan_test_applications/pull/159):
Fix Vulkan Version

[#161](https://github.com/google/vulkan_test_applications/pull/161):
Fix bug in GetMemoryIndex

[#172](https://github.com/google/vulkan_test_applications/pull/172):
Add support for Push Constants in PipelineLayout 

[#173](https://github.com/google/vulkan_test_applications/pull/173):
Add Push Constants to CreatePipelineLayout

[#180](https://github.com/google/vulkan_test_applications/pull/180):
Fix missing allocator in PipelineLayout constructor

### Conclusion

It was a great opportunity to have been able to work with the Android Graphics Tools team for Google Summer of Code 2020. I was able to learn in-depth about my areas of interest: Vulkan, GPU programming and computer graphics. I would like to thank my mentors Hugues Evrard, Paul Thomson and Andrew Woloszyn for all their support.
