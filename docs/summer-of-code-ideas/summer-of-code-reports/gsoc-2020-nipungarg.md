# Google Summer of Code 2020 Report - Nipun Garg

###### Personal Links:
###### [Github](https://github.com/nipunG314)

###### Proposal:
###### [New Vulkan Samples to Extend Vulkan API Coverage](https://summerofcode.withgoogle.com/projects/#4993422496104448)

###### Mentors: 
###### Hugues Evrard, Paul Thomson

## Deliverables
My project revolved around writing samples in Vulkan that would be utilized for the development of the [Android GPU Inspector](https://github.com/google/agi) project. As such, the focus of the project was on efficiently computing the coverage of the Vulkan API provided by the existing samples and to add new samples to increase that coverage.

While this was the initial scope of our project, we also spent time building a sample that could be used to study the synchronization between different render passes. This was covered by the Overlapping Frames sample. We also started work on a sample to combine the graphics and compute capabilities of Vulkan. However, the last one could not be satisfactorily completed in the alloted time.

This report will describe the deliverables and results of the project over the the summer in more detail.
 
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

### Vulkna Test Applications - Additionl Fixes

These are changes that were not originally part of the scope of the project, but were needed to progress further.

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

### Vulkan Test Applications - Add vkTrimCommandPool to GAPID Test

We added a sample to the (Vulkan Test Applications Repo)(https://github.com/google/vulkan_test_applications) in order to test the Vulkan primitive `vkTrimCommandPool` and extend its API coverage.

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

### Vulkan Test Applications - Add Overlapping Frames
This sample renders a triangle with a post-processing effect split into multiple
interleaved render passes.

The idea was to create a sample that can be used to test the Overlapping Frames Synchronization pattern on the AGI. This pattern is described in more detail below.

##### Synchronization Pattern

We have two render passes:
- Geometry Render Pass
- Post Processing Render Pass

For the rest of the document, we'll refer to them as GRP and PPRP.

For a given frame number N, the sample is synchronized as follows:

... -> GRP\_{N} -> PPRP\_{N-1} -> GRP\_{N+1} -> PPRP\_{N} -> ...

We use three sets of VkSemaphores and one set of VkFences to accomplish this
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

### Vulkan Test Applicatios - Noise Texture Visualization using Raymarching
This was the final sample that was planned under the project. However, I could not get it to work satisfactorily under the current schedule.

The idea behind this sample was to combine the Compute and Graphics capabilities of Vulkan in order to visualize 3D Noise Textures. For the sample, we would procedurally generate a 3D Perlin Texture using the Compute queue and then use the resulting texture and a given surface level as inputs for a Raymarching Renderer.

Currently, the setup for Graphics of the project exists. However, the shaders need to be re-written as they do not work as desired right now. The Compute side of the project is unimplemented till now.
