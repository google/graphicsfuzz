# GraphicsFuzz: generate tests

## Synopsis

`glsl-generate [OPTIONS] DONORS REFERENCES NUM_VARIANTS GLSL_VERSION PREFIX OUTPUT_FOLDER`


## Description

glsl-generate takes two directories of GLSL shaders, `DONORS` and `REFERENCES`,
and mutates the shaders in `REFERENCES` to produce families of variant
shaders to `OUTPUT_FOLDER`.

Each shader is provided (and output)
as a **shader job** (a `.json` file)
that represents all shaders and metadata needed to
render an image.
However, the common use-case
is for each input shader to be a fragment shader, e.g. `shader.frag`,
with a corresponding JSON file, `shader.json`, alongside.
The JSON file can either contain `{}` or a dictionary of uniform values.
See below for more details on shader jobs.

`DONORS` is a corpus of shaders; code from these is injected into the `REFERENCES`.

Each output shader family will have `NUM_VARIANTS` variant shaders, plus
the original reference shader from which they are derived.

`GLSL_VERSION` specifies the version of GLSL, such as `100` or `"310 es"`.

Each shader family will be output to a directory starting with `OUTPUT_FOLDER/PREFIX_*`.

## Options

```shell
usage: glsl-generate [-h] [--seed SEED] [--webgl] [--small]
                     [--allow-long-loops] [--disable DISABLE]
                     [--enable-only ENABLE_ONLY]
                     [--aggressively-complicate-control-flow]
                     [--single-pass] [--replace-float-literals]
                     [--generate-uniform-bindings]
                     [--max-uniforms MAX_UNIFORMS] [--no-injection-switch]
                     [--disable-validator] [--keep-bad-variants]
                     [--stop-on-fail] [--verbose] [--max-bytes MAX_BYTES]
                     [--max-factor MAX_FACTOR] [--require-license]
                     references donors num_variants glsl_version prefix
                     output_dir

Generate a set of shader families.

positional arguments:
  references             Path to directory of reference shaders.
  donors                 Path to directory of donor shaders.
  num_variants           Number  of  variants  to   be  produced  for  each
                         generated shader family.
  glsl_version           Version of GLSL to target.
  prefix                 String with which to prefix shader family names.
  output_dir             Output directory for shader families.

optional arguments:
  -h, --help             show this help message and exit
  --seed SEED            Seed to initialize random number generator with.
  --webgl                Restrict to  WebGL-compatible  features. (default:
                         false)
  --small                Try to generate small shaders. (default: false)
  --allow-long-loops     During live  code  injection,  care  is  taken  by
                         default to avoid loops with  very long or infinite
                         iteration counts.  This option  disables this care
                         so that loops may end  up being very long running.
                         (default: false)
  --disable DISABLE      Disable a given series of transformations.
  --enable-only ENABLE_ONLY
                         Disable   all   but    the    given    series   of
                         transformations.
  --aggressively-complicate-control-flow
                         Make  control  flow  very  complicated.  (default:
                         false)
  --single-pass          Do not apply  any  individual  transformation pass
                         more than once. (default: false)
  --replace-float-literals
                         Replace float  literals  with  uniforms. (default:
                         false)
  --generate-uniform-bindings
                         Put all uniforms  in  uniform  blocks and generate
                         bindings;  required   for   Vulkan  compatibility.
                         (default: false)
  --max-uniforms MAX_UNIFORMS
                         Ensure that generated  shaders  have  no more than
                         the given number of  uniforms; required for Vulkan
                         compatibility. (default: 0)
  --no-injection-switch  Do  not  generate   the  injectionSwitch  uniform.
                         (default: false)
  --disable-validator    Disable  calling  validation  tools  on  generated
                         variants. (default: false)
  --keep-bad-variants    Do not remove  invalid  variants  upon generation.
                         (default: false)
  --stop-on-fail         Quit  if   an   invalid   variant   is  generated.
                         (default: false)
  --verbose              Emit detailed information  regarding  the progress
                         of the generation. (default: false)
  --max-bytes MAX_BYTES  Maximum  allowed  size,  in   bytes,  for  variant
                         shader. (default: 500000)
  --max-factor MAX_FACTOR
                         Maximum blowup  allowed,  compared  with  size  of
                         reference shader (default: no limit).
  --require-license      Require a license  file  to  be provided alongside
                         the  reference  and   pass   details   through  to
                         generated shaders. (default: false)

```

## Example

Assuming you have extracted the `graphicsfuzz-1.0.zip` file to get `graphicsfuzz-1.0/`:

```sh
# Copy the sample shaders into the current directory:
cp -r graphicsfuzz-1.0/shaders/samples samples

# Create a work directory to store our generated shader families.
# The directory structure allows glsl-server
# to find the shaders later.
mkdir -p work/shaderfamilies

# Generate several shader families from the set of sample shaders.
# Synopsis:
# glsl-generate [options] donors references num_variants glsl_version prefix output_folder

# Generate some GLSL version 300 es shaders.
glsl-generate --seed 0 samples/donors samples/300es 10 "300 es" family_300es work/shaderfamilies

# Generate some GLSL version 100 shaders.
glsl-generate --seed 0 samples/donors samples/100 10 "100" family_100 work/shaderfamilies

# Generate some "Vulkan-compatible" GLSL version 300 es shaders that can be translated to SPIR-V for Vulkan testing.
glsl-generate --seed 0 --generate_uniform_bindings --max_uniforms 10 samples/donors samples/310es 10 "310 es" family_vulkan work/shaderfamilies

# Each line above will take approx. 1 minute, and will generate a shader family for every
# shader in samples/300es or samples/100:
ls work/shaderfamilies

# Output:

# family_100_bubblesort_flag
# family_100_mandelbrot_blurry
# family_100_squares
# family_100_colorgrid_modulo
# family_100_prefix_sum

# family_300es_bubblesort_flag
# family_300es_mandelbrot_blurry
# family_300es_squares
# family_300es_colorgrid_modulo
# family_300es_prefix_sum

# family_vulkan_bubblesort_flag
# family_vulkan_mandelbrot_blurry
# family_vulkan_squares
# family_vulkan_colorgrid_modulo
# family_vulkan_prefix_sum
```

For this example, each shader family will contain 11 shaders;
1 for the reference shader, and 10 for the variant shaders:

```sh
ls work/shaderfamilies/family_100_bubblesort_flag/

# Output:

# infolog.json      variant_001.json  variant_004.json  variant_007.json
# reference.frag    variant_002.frag  variant_005.frag  variant_008.frag
# reference.json    variant_002.json  variant_005.json  variant_008.json
# variant_000.frag  variant_003.frag  variant_006.frag  variant_009.frag
# variant_000.json  variant_003.json  variant_006.json  variant_009.json
# variant_001.frag  variant_004.frag  variant_007.frag
```

## Shader jobs

Each input is, in fact,
a JSON file that we refer to as a
**shader job** that
represents all shaders and metadata needed to
render an image.
However,
our well-tested use-case is
a shader job that contains a fragment shader
and a set of uniform values.
Our worker applications that are used to render a frame
can only set uniforms
(they cannot set textures, etc.).

For example, from our release zip:

`graphicsfuzz-1.0/samples/300es/squares.json`:

```json
{
  "time": {
    "func": "glUniform1f",
    "args": [
      0.0
    ]
  },
  "mouse": {
    "func": "glUniform2f",
    "args": [
      0.0,
      0.0
    ]
  },
  "resolution": {
    "func": "glUniform2f",
    "args": [
      256.0,
      256.0
    ]
  }
}
```

The fragment shader file for this shader job
must have the same name and be alongside the shader job file
with a `.frag` extension;
in this case, at `graphicsfuzz-1.0/samples/300es/squares.frag`.
