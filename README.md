
**Warning:** this repository is a work-in-progress. Things may break while we transition this project to open source. This is not an officially supported Google product.

# GraphicsFuzz

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

### GraphicsFuzz is a testing framework for GLSL and SPIR-V shader compilers.

GraphicsFuzz can automatically find and simplify bugs in graphics shader compilers. It enables to generate, run and reduce test shaders. It currently operates on GLSL shaders, and uses glslangValidator and spirv tools to target SPIR-V.

**NB:** the **[GLSL reducer](docs/reduce.md)** is available as a **stand-alone** tool.

* [Introduction: why and how to test shader compilers](docs/introduction.md)
* [Getting started: the walkthrough](docs/walkthrough.md)
* User documentation:
  * [Generate tests](docs/generate.md)
  * [Run tests](docs/run.md)
  * [Explore test results](docs/explore.md)
  * [Reduce a test](docs/reduce.md)

## Contribute

* [Contributing (requires Google CLA)](CONTRIBUTING.md)
* [License (Apache 2.0)](LICENSE)
* [Developer getting started](docs/development.md)
