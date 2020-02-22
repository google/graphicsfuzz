# GraphicsFuzz

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://github.com/google/graphicsfuzz/workflows/.github/workflows/graphicsfuzz.yml/badge.svg)](https://github.com/google/graphicsfuzz/actions)

## GraphicsFuzz is a set of tools for testing shader compilers

GraphicsFuzz provides tools for automatically finding and simplifying bugs in graphics drivers,
specifically graphics shader compilers. The glsl-fuzz and glsl-reduce tools manipulate GLSL shaders, targeting SPIR-V compilers via translation.
The spirv-fuzz and spirv-reduce tools directly manipulate SPIR-V shaders.

## Download and run

Follow the [gfauto README](https://github.com/google/graphicsfuzz/tree/master/gfauto#gfauto).
The [gfauto](https://github.com/google/graphicsfuzz/tree/master/gfauto#gfauto) command line
tool is the
recommended way of automatically downloading and running our fuzzers to test Vulkan drivers in a "push-button" fashion with minimal interaction. See below if you want to read about
individual tools and/or use
them as standalone command line tools.


## Tool documentation

* **[gfauto](https://github.com/google/graphicsfuzz/tree/master/gfauto#gfauto)**: the recommended way of automatically downloading and running our fuzzers to test Vulkan drivers in a "push-button" fashion with minimal interaction
* **glsl-fuzz**: a family of tools for testing GLSL shader compilers using randomized metamorphic testing
* **glsl-reduce**: a stand-alone GLSL shader reducer
* **spirv-fuzz**: a stand-alone SPIR-V shader fuzzer and shrinker that uses randomized metamorphic testing
* **spirv-reduce**: a stand-alone SPIR-V shader reducer

### glsl-fuzz

* The glsl-fuzz tools are developed in this repo
* [How it works (high-level): metamorphic testing using glsl-fuzz](docs/glsl-fuzz-intro.md)
* [glsl-fuzz walkthrough](docs/glsl-fuzz-walkthrough.md)
* [glsl-generate manual](docs/glsl-fuzz-generate.md)
* [glsl-reduce manual (for reducing fuzzed shaders)](docs/glsl-fuzz-reduce.md)
* **Pre-built binaries** are available on the [GitHub releases page of this repo](docs/glsl-fuzz-releases.md)
* [**Developer documentation with build instructions**](docs/glsl-fuzz-develop.md)


### glsl-reduce

* glsl-reduce is developed in this repo
* [Introduction: my shader is being weird, now what?](docs/glsl-reduce-intro.md)
* [glsl-reduce walkthrough](docs/glsl-reduce-walkthrough.md)
* [glsl-reduce manual](docs/glsl-reduce.md)
* **Pre-built binaries** are available with glsl-fuzz on the [GitHub releases page of this repo](docs/glsl-fuzz-releases.md)

### spirv-fuzz

* spirv-fuzz is developed in the [SPIRV-Tools repo](https://github.com/KhronosGroup/SPIRV-Tools)
* **[Nightly builds of SPIRV-Tools](https://github.com/google/gfbuild-SPIRV-Tools/releases)**
* Try our [spirv-fuzz walkthrough](docs/finding-a-vulkan-driver-bug-using-spirv-fuzz.md) that can be run from your browser


### spirv-reduce

* spirv-reduce is developed in the [SPIRV-Tools repo](https://github.com/KhronosGroup/SPIRV-Tools)
* **[Nightly builds of SPIRV-Tools](https://github.com/google/gfbuild-SPIRV-Tools/releases)**
* Try our [spirv-fuzz walkthrough](docs/finding-a-vulkan-driver-bug-using-spirv-fuzz.md) (includes use of spirv-reduce) that can be run from your browser


## Contribute

* [Contributing (requires Google CLA)](CONTRIBUTING.md)
* [License (Apache 2.0)](LICENSE)
* [Developer documentation for glsl-fuzz](docs/glsl-fuzz-develop.md)

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

This is not an officially supported Google product.
