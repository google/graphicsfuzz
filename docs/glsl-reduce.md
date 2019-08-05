# glsl-reduce manual

For instructions on how to run glsl-reduce in conjunction with glsl-fuzz,
see the [glsl-reduce manual for reducing fuzzed shaders](glsl-fuzz-reduce.md).

## Synopsis

```
glsl-reduce [OPTIONS] SHADER_JOB INTERESTINGNESS_TEST
```

## Description

glsl-reduce takes a *shader job* `SHADER_JOB`, and an executable script `INTERESTINGNESS_TEST` that takes a shader job as an argument.  `INTERESTINGNESS_TEST` must exit with code 0 when applied to the initial `SHADER_JOB`.  glsl-reduce will then try to simplify the given shader job to a smaller, simpler shader job for which `INTERESTINGESS_TEST` still exits with code 0.

`SHADER_JOB` must be a `.json` file containing simply `{}`.  (The role of this file is to provide metadata about shaders when glsl-reduce is used in conjunction with [glsl-fuzz](glsl-fuzz-intro.md).)  If the shader job is named `foo.json`, glsl-reduce will look for corresponding shaders named `foo.frag`, `foo.vert` and `foo.comp`, of which there must be at least one, and will apply reduction to all such shaders that are present.  On termination, the reduced shader job will be named `foo_reduced_final.json` (with associated shader files).

Options relevant when the tool is used in stand-alone mode:

```
  --timeout TIMEOUT      Time   in    seconds    after    which    checking
                         interestingness  of  a  shader   job  is  aborted.
                         (default: 30)
  --max-steps MAX_STEPS  The maximum  number  of  reduction  steps  to take
                         before giving up and  outputting the final reduced
                         file. (default: 250)
  --verbose              Emit   detailed   information   related   to   the
                         reduction process. (default: false)
  --seed SEED            Seed with which  to  initialize  the random number
                         that  is  used  to  control  reduction  decisions.
                         (default: -583088443)
  --output OUTPUT        Directory  to  which  reduction  intermediate  and
                         final results will be written. (default: .)
  --continue-previous-reduction
                         Carry on from where  a  previous reduction attempt
                         left off.  Requires  the  temporary  files written
                         by the previous reduction  to be intact, including
                         the  presence  of   a  REDUCTION_INCOMPLETE  file.
                         (default: false)
```
