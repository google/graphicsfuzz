# GraphicsFuzz

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## GraphicsFuzz is a testing framework for shader compilers

GraphicsFuzz provides tools to automatically find and simplify bugs in graphics
shader compilers. It currently operates on GLSL shaders, and uses
glslangValidator and spirv-tools to additionally target SPIR-V.

## Tools

* glsl-reduce: a stand-alone test case reducer for GLSL shaders
* glsl-fuzz: a family of tools for testing GLSL shader compilers using randomized metamorphic testing

### glsl-reduce

* [Introduction: my shader is crashing the compiler, now what?](docs/glsl-reduce-intro.md)
* [Walkthrough and user documentation](docs/glsl-reduce.md)

### glsl-fuzz

* [Introduction: why and how to test shader compilers](docs/glsl-fuzz-intro.md)
* [Getting started: the walkthrough](docs/glsl-fuzz-walkthrough.md)
* User documentation:
  * [Generate tests](docs/glsl-fuzz-generate.md)
  * [Run tests](docs/glsl-fuzz-run.md)
  * [Explore test results](docs/glsl-fuzz-explore.md)
  * [Reduce a test](docs/glsl-fuzz-reduce.md)

## Get the GraphicsFuzz tools

* **Pre-built binaries** are available on the [GitHub releases page](https://github.com/google/graphicsfuzz/releases)
* [Build from command line](docs/glsl-fuzz-build.md)
* Build from IDE: see [developer getting started](docs/glsl-fuzz-develop.md)

## Contribute

* [Contributing (requires Google CLA)](CONTRIBUTING.md)
* [License (Apache 2.0)](LICENSE)
* [Developer getting started](docs/glsl-fuzz-develop.md)

This is not an officially supported Google product.
