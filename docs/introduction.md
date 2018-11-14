# Introduction: why and how to test shader compilers

GraphicsFuzz is a testing framework for automatically finding and simplifying bugs in graphics shader compilers. Our tools currently manipulate GLSL shaders, but we can indirectly test other targets such as SPIR-V, HLSL and Metal. Our current priority is testing Vulkan drivers.

## The problem

A graphics driver takes a *shader program* as input and executes it on a GPU (graphics processing unit) to render an image.

![shader program, to GPU, to image](docs/images/shader-gpu-image.png)

Compiling and executing shaders is complex, and many graphics drivers are unreliable: a valid shader can lead to wrong images, driver errors or even security issues.

![shader program, to GPU, to crash](docs/images/shader-gpu-crash.png)

## Automatically finding bugs

We start with a *reference shader* that renders an image. The reference shader can be any shader you like, such as a high-value shader from a game or existing test suite.

![reference, to GPU, to image](docs/images/reference-gpu-image.png)

Shaders are programs, so by applying *semantics-preserving* source code transformations, we can obtain a shader with significantly different source code that still has the same effect.

![transformation example: wrapping a statement in a do-while-false loop](docs/images/transformation-example.png)

For example, wrapping code in a single-iteration loop does not change the meaning (semantics) of a program. By applying various semantics-preserving transformations to the reference shader, we generate a family of *variant shaders*, where each variant must render the same image as the reference.

![reference and variants, to GPU, to many equivalent images](docs/images/variant-same.png)

If a variant shader leads to a seriously different image (or a driver error), then we have found a graphics driver bug!

![reference and one variant, to GPU, to two different images](docs/images/variant-bug-wrongimg.png)

This approach is known as *metamorphic testing*.

## Reduction

Finding bugs is not the end of the story: a variant shader that exposes a bug is typically very large (thousands of lines), full of code coming from the semantics-preserving transformations. Typically only a fraction of this code is needed to expose the bug.

![Source code, the majority of which is highlighted in yellow, but parts of one statement are not highlighted.](docs/images/variant-haystack.png)

Fortunately, our reducer is able to selectively reverse those transformations that are not relevant to the bug. After reduction, we obtain a small difference sufficient to expose the driver issue.

![The same source code, the majority of which is highlighted in yellow and striked out, but parts of one statement remain.](docs/images/variant-reduced.png)

The reduced variant *still exposes the bug*, and differs from the reference only slightly: this is a great starting point to isolate the root cause of the bug in the graphics driver.

## Summary

GraphicsFuzz finds bugs in graphics drivers by rendering families of semantically equivalent shaders, and looking for output discrepancies. This approach is known as *metamorphic testing*. For each bug, the reducer saves a lot of debugging time by producing a simpler *minimal-difference test case* that still exposes the bug.

## Further reading

### GraphicsFuzz blog posts:

* 17 January 2018: [Samsung Galaxy S8 (ARM, Qualcomm)](https://medium.com/@afd_icl/a-tale-of-two-samsungs-arm-vs-qualcomm-in-android-graphics-c1c6f1eef828)
* 22 January 2018: [Nvidia Shield TV, Tablet (Nvidia)](https://medium.com/@afd_icl/nvidia-shield-reliable-graphics-2aa79e04e150)
* 25 January 2018: [Google Pixel Phone 1, 2 (Qualcomm)](https://medium.com/@afd_icl/arm-gpus-in-huawei-phones-cb81280fbbab)
* 5 February 2018: [Google Nexus Player (Imagination Technologies)](https://medium.com/@afd_icl/arm-gpus-in-huawei-phones-cb81280fbbab)
* 15 February 2018: [Huawei Honor 9, 9 lite, 10 (ARM)](https://medium.com/@afd_icl/arm-gpus-in-huawei-phones-cb81280fbbab)
* 22 February 2018: [Apple iPhone 6, 7, 8, X (Apple, Imagination Technologies)](https://medium.com/@afd_icl/an-apple-sandwich-449931ab4509)
* 12 March 2018: [Samsung Galaxy S6, S7 (ARM, Qualcomm)](https://medium.com/@afd_icl/not-all-galaxies-are-made-equal-9812d6dcc0bb)
* 22 May 2018: [Samsung Galaxy S9 (ARM, Qualcomm)](https://medium.com/@afd_icl/samsung-s9s-head-to-head-arm-vs-qualcomm-decf438eb255)

### Academic research project blog posts:

* 30 November 2016: [Intro](https://medium.com/@afd_icl/crashes-hangs-and-crazy-images-by-adding-zero-689d15ce922b)
* 1 December 2016: [AMD](https://medium.com/@afd_icl/first-stop-amd-bluescreen-via-webgl-and-more-ba3eaf76c5fb)
* 7 December 2016: [Apple](https://medium.com/@afd_icl/how-to-render-garbage-on-your-iphone-213fb577d67c)
* 12 December 2016: [ARM](https://medium.com/@afd_icl/bugs-can-be-beautiful-65b93c5c58f9)
* 15 December 2016: [Imagination Technologies](https://medium.com/@afd_icl/gpu-folks-we-need-to-talk-about-control-flow-c20fd225197e)
* 20 December 2016: [Intel](https://medium.com/@afd_icl/intel-locking-up-safari-bluescreening-windows-135c1dc29495)
* 25 January 2017: [Nvidia](https://medium.com/@afd_icl/nvidia-system-freeze-via-webgl-61a78cea1116)
* 1 February 2017: [Qualcomm](https://medium.com/@afd_icl/hey-a-web-page-just-restarted-my-phone-c06d3db76542)

### Academic publications:

* OOPSLA 2017: [Automated Testing of Graphics Shader Compilers](http://multicore.doc.ic.ac.uk/publications/oopsla-17.html)
* Metamorphic [Testing Workshop at ICSE 2016: Metamorphic Testing for (Graphics) Compilers](http://multicore.doc.ic.ac.uk/publications/met-16.html)
