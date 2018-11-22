# GraphicsFuzz

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## GraphicsFuzz is a testing framework for shader compilers

GraphicsFuzz provides tools to automatically find and simplify bugs in graphics
shader compilers. It currently operates on GLSL shaders, and uses
glslangValidator and spirv-tools to additionally target SPIR-V.

## Tools

* glsl-reduce: a stand-alone GLSL shader reducer
* glsl-fuzz: a family of tools for testing GLSL shader compilers using randomized metamorphic testing

### glsl-reduce

* [Introduction: my shader is crashing the compiler, now what?](docs/glsl-reduce-intro.md)
* [Walkthrough and user documentation](docs/glsl-reduce.md)

### glsl-fuzz

* [How it works (high-level): metamorphic testing using GraphicsFuzz](docs/glsl-fuzz-intro.md)
* [GraphicsFuzz walkthrough](docs/glsl-fuzz-walkthrough.md):
  * Generating GLSL shaders for the worker applications
  * Starting the server
  * Running GLSL shaders using the worker applications on:
    * OpenGL desktop platforms
    * OpenGL ES Android platforms
    * Vulkan Android platforms
  * Exploring results using the WebUI
  * Reducing buggy shaders using the WebUI
  * Exploring the results on the file system

Additional documentation:

* [glsl-generate: options for generating GLSL shaders for our worker applications](docs/glsl-fuzz-generate.md)
* [glsl-reduce: reducer features specific to glsl-fuzz](docs/glsl-fuzz-reduce.md)

## Get the GraphicsFuzz tools

* **Pre-built binaries** are available on the [GitHub releases page](docs/glsl-fuzz-releases.md)
* [Build from command line](docs/glsl-fuzz-build.md)
* Build from IDE: see [developer getting started](docs/glsl-fuzz-develop.md)

## Contribute

* [Contributing (requires Google CLA)](CONTRIBUTING.md)
* [License (Apache 2.0)](LICENSE)
* [Developer getting started](docs/glsl-fuzz-develop.md)

This is not an officially supported Google product.
