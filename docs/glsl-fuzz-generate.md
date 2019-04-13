# GraphicsFuzz: generate tests

## Synopsis

`glsl-generate [OPTIONS] REFERENCES DONORS NUM_VARIANTS PREFIX OUTPUT_FOLDER`


## Description

glsl-generate takes two directories of GLSL shaders, `REFERENCES` and `DONORS`,
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

Each shader family will be output to a directory starting with `OUTPUT_FOLDER/PREFIX_*`.

## Options

```shell
usage: GlslGenerate [-h] [--seed SEED] [--small] [--allow-long-loops]
                    [--disable DISABLE] [--enable-only ENABLE_ONLY]
                    [--aggressively-complicate-control-flow]
                    [--single-pass] [--replace-float-literals]
                    [--generate-uniform-bindings]
                    [--max-uniforms MAX_UNIFORMS] [--no-injection-switch]
                    [--disable-validator] [--keep-bad-variants]
                    [--stop-on-fail] [--verbose] [--max-bytes MAX_BYTES]
                    [--max-factor MAX_FACTOR] [--require-license]
                    references donors num_variants prefix output_dir

Generate a set of shader families.

positional arguments:
  references             Path to directory of reference shaders.
  donors                 Path to directory of donor shaders.
  num_variants           Number  of  variants  to   be  produced  for  each
                         generated shader family.
  prefix                 String with which to prefix shader family names.
  output_dir             Output directory for shader families.

optional arguments:
  -h, --help             show this help message and exit
  --seed SEED            Seed to initialize random number generator with.
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

See [the glsl-fuzz walkthrough](glsl-fuzz-walkthrough.md#generating-some-shader-families)
for a detailed guide on how to generate some shader families.

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
can only set uniforms for now
(they cannot set textures, etc.).

For example, from our release zip:

`graphicsfuzz/samples/300es/squares.json`:

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
in this case, at `graphicsfuzz/samples/300es/squares.frag`.

###Random uniform value generation

At runtime, the generator clones the given shader job file to the
provided work directory as `reference.json` and parses it, before using it to create
variants. During this process, one can allow the generator to create
random inputs for your uniforms or compute data, based on the `--seed` given to the
generator at runtime, by using certain strings in the `data` or `args` elements
of a uniform in your shader job file.

These strings are:

`rfloat(value):` Generate a random float in the range of `[0, value]`.
If value is negative, the generated float will be in the range `[value, 0]`.

`rint(value):` Generate a random integer in the range of `[0, value]`.
If value is negative, the generated integer will be in the range `[value, 0]`.

`rbool():` Generate a random boolean in the set `{ 0, 1 }`.

For example,

```json
{
  "time": {
    "func": "glUniform1f",
    "args": [
      "rfloat(100.0)"
    ]
  }
}
```

would become something like

```json
{
  "time": {
    "func": "glUniform1f",
    "args": [
      47.784789
    ]
  }
}
```

All of the generated shader variants will have the same random numbers for
a given uniform - `reference.json` in the work directory will contain the original
shader job after the random values are supplied.

Note that the generator **does not** validate these values beyond ensuring that
no overflow occurs when generating them. For example, generating a negative
integer into a `glUniform1ui` unsigned integer uniform will not be checked.
Vetting inputs for correctness is left to the user.
