The GraphicsFuzz vulkan worker is based on the Vulkan API samples:

https://github.com/googlesamples/vulkan-basic-samples

The vulkan worker is structured as a sample. The code here keeps the build
system infrastructure of the Vulkan API samples, edited to reference only our
worker as a build target. As far as possible, other samples and demos have been
removed.
