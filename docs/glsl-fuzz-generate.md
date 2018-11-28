# GraphicsFuzz: generate tests

## Synopsis

`glsl-generate [OPTIONS] DONORS REFERENCES NUM_VARIANTS GLSL_VERSION PREFIX OUTPUT_FOLDER`

glsl-generate mutates some reference GLSL shaders to produce families of variant
shaders.

## Options

```shell
usage: glsl-generate.py [-h] [--webgl] [--keep_bad_variants] [--stop_on_fail]
                        [--verbose] [--small] [--avoid_long_loops]
                        [--max_bytes MAX_BYTES] [--max_factor MAX_FACTOR]
                        [--replace_float_literals] [--multi_pass]
                        [--require_license] [--generate_uniform_bindings]
                        [--max_uniforms MAX_UNIFORMS]
                        donors references num_variants glsl_version prefix
                        output_folder

Generate a set of shader families

positional arguments:
  donors                Path to directory of donor shaders.
  references            Path to directory of reference shaders.
  num_variants          Number of variants to be produced for each shader in
                        the donor set.
  glsl_version          Version of GLSL to be used.
  prefix                String with which to prefix shader family name.
  output_folder         Output directory for shader families.

optional arguments:
  -h, --help            show this help message and exit
  --webgl               Restrict transformations to be WebGL-compatible.
  --keep_bad_variants   Do not remove invalid variants upon generation.
  --stop_on_fail        Quit if an invalid variant is generated.
  --verbose             Emit detailed information regarding the progress of
                        the generation.
  --small               Try to generate smaller variants.
  --avoid_long_loops    Avoid long-running loops during live code injection.
  --max_bytes MAX_BYTES
                        Restrict maximum size of generated variants.
  --max_factor MAX_FACTOR
                        Maximum blowup allowed, compared with size of
                        reference shader (default: no limit).
  --replace_float_literals
                        Replace float literals with uniforms.
  --multi_pass          Apply multiple transformation passes.
  --require_license     Require that each shader has an accompanying license,
                        and use this during generation.
  --generate_uniform_bindings
                        Put all uniforms in uniform blocks and generate
                        associated bindings. Necessary for Vulkan
                        compatibility.
  --max_uniforms MAX_UNIFORMS
                        Ensure that no more than the given number of uniforms
                        are included in generated shaders. Necessary for
                        Vulkan compatibility.

```

## Example

From the graphicsfuzz-1.0 directory:

```shell
mkdir generated
glsl-generate shaders/src/main/glsl/samples/donors shaders/src/main/glsl/samples/310es 2 "310 es" my_prefix generated
```

Note that the output directory must already exists.

The above command will produce:

```shell
generated/
├── my_prefix_bubblesort_flag
│   ├── infolog.json
│   ├── reference.frag
│   ├── reference.json
│   ├── variant_000.frag
│   ├── variant_000.json
│   ├── variant_000.prob
│   ├── variant_001.frag
│   ├── variant_001.json
│   └── variant_001.prob
├── my_prefix_colorgrid_modulo
│   ├── infolog.json
│   ├── reference.frag
│   ├── reference.json
│   ├── variant_000.frag
│   ├── variant_000.json
│   ├── variant_000.prob
│   ├── variant_001.frag
│   ├── variant_001.json
│   └── variant_001.prob
[ ... etc ... ]
```
