# GraphicsFuzz Piglit/shader_runner worker

[Piglit](https://piglit.freedesktop.org/) is a test suite for OpenGL implementations that
is developed as part of the [Mesa](https://mesa.freedesktop.org/) open source graphics
driver project. Along with its huge test suite, Piglit provides a testing framework,
`piglit-gl-framework`, and an associated renderer implementation, `shader_runner`. 
Our `piglit-worker` is a Python script that sends shader jobs to `shader_runner`, retrieves 
the rendered image/crash logs, and sends the results back to the GraphicsFuzz webserver.

## Using the worker

To run the worker, you will first need to build the GraphicsFuzz project - see 
[GraphicsFuzz: developer documentation](https://github.com/google/graphicsfuzz/blob/master/docs/glsl-fuzz-develop.md)
for more details. Ensure that `graphicfuzz/target/graphicsfuzz/python/drivers`
is on your PATH environment variable - this is the final extraction directory of GraphicsFuzz's
Python scripts after building.

Additionally, you will need to clone and build Piglit - see [Piglit's README.md](https://gitlab.freedesktop.org/mesa/piglit/blob/master/README.md)
for build instructions. Once Piglit is built, you **must also** add the build directory, `/piglit/bin`,
to your PATH environment variable. To check this, you can run `$ shader_runner_gles3` in your terminal.

To start the worker, simply use `$ piglit-worker (worker name)`. A server IP/URL can be specified
with an optional --server argument: `$ piglit-worker --server (IP:PORT) (worker name)`.

## graphicsfuzz-piglit-converter

A GraphicsFuzz test is composed two files - a 'shader job' JSON file that encodes uniform variables
and other data that needs to be supplied to the shader, and the shader source code itself. In
contrast, a `shader_runner` test is a `.shader_test` file that contains the required OpenGL/GLSL
versions for the shader, the shader source code, and a special `[test]` header that can be used to
load data, probe pixels for colors, clear the screen, and much more. 

To convert a GraphicsFuzz shader job to a `.shader_test` file for use with `shader_runner`,
`piglit-worker` uses a script, `graphicsfuzz-piglit-converter`, which formats the GraphicsFuzz test
into a working `.shader_test` file. 

`graphicsfuzz-piglit-converter` can be used standalone as well - once you have GraphicsFuzz built
and the Python scripts on your PATH, you can just use `$ graphicsfuzz-piglit-converter
(GraphicsFuzz shader job JSON file)` and the `.shader_test` file will be output in the same
 directory as the JSON file.
