# glsl-reduce

glsl-reduce is a tool for automatically reducing a GLSL shader with respect to a programmable condition.

In this walkthrough, we will use a mock shader compiler bug to demonstrate how
to use glsl-reduce in action.  We then describe various command-line arguments
that can be passed to glsl-reduce.

We will be using the latest release zip `graphicsfuzz-1.0.zip`.
You can download this from the [releases page](glsl-fuzz-releases.md)
or [build it from source](glsl-fuzz-build.md).

Add the following directories to your path:

* `graphicsfuzz-1.0/python/drivers`
* One of:
  * `graphicsfuzz-1.0/bin/Linux`
  * `graphicsfuzz-1.0/bin/Mac`
  * `graphicsfuzz-1.0/bin/Windows`

The `graphicsfuzz-1.0/` directory is the unzipped graphicsfuzz release.
If building from source, this directory can be found at `graphicsfuzz/target/graphicsfuzz-1.0/`.

You will also need to install the latest version of the Java 8 Development Kit,
either:

* From your system's package manager. E.g. `sudo apt-get install openjdk-8-jdk`.
* By [downloading and installing Oracle's binary distribution](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (look for Java SE 8uXXX then the JDK link).
* By downloading and installing some other OpenJDK binary distribution for your platform.


```sh
# To check that Java 8 is installed and in use:
java -version
# Output: openjdk version "1.8.0_181"
```

## Arguments required by glsl-reduce

glsl-reduce requires two arguments:

* a *shader job*: a path to a `.json` file representing the shader or shaders of interest
* an *interestingness test*: a path to an executable script encoding the properties a shader job must satisfy to be deemed interesting.

If the shader job is called `foo.json`, glsl-reduce will look for shader files
called `foo.frag`, `foo.vert` or `foo.comp`.  At least one of these shaders is
required to be present.  When glsl-reduce is used in conjunction with
[glsl-fuzz](glsl-fuzz-intro.md), the JSON file is used to record data about the execution
environment for the shaders.  However, when glsl-reduce is used in a standalone
fashion, as in this walkthrough, the JSON file should simply contain `{}`.

## A mock shader compiler bug

We'll start by illustrating a mock shader compiler bug.

```sh
# Copy the sample shaders into the current directory:
cp -r graphicsfuzz-1.0/examples/glsl-reduce-walkthrough .

```

Take a look at `colorgrid_modulo.frag` and `colorgrid_modulo.json` under
`glsl-reduce-walkthrough`.  The `.frag` file is a moderately complex fragment
shader.  The `.json` file just contains `{}` as described above.

Let's run a fake shader compiler on the `.frag` file:

```
# Run a fake shader compiler on the fragment shader file:
python glsl-reduce-walkthrough/fake_compiler.py glsl-reduce-walkthrough/colorgrid_modulo.frag

# Output:
# Fatal error: too much indexing.

```

If you look in `fake_compiler.py` you'll see that this script inspects the text
of the shader and generates an error about indexing if the input shader contains
the substring `[i]` more than once.  Let's imagine that in fact a real compiler
gave this fatal error due to a genuine bug.


## Applying glsl-reduce attempt 1: a basic interestingness test

We would like to use glsl-reduce to produce a much smaller input shader that can
still trigger this fatal error: a smaller shader would make the shader compiler
easier to debug.

To do this, we need an *interestingness test* that encodes what it means for a
shader to be interesting.  In our case, we're interested in the shader if it
leads to a compilation failure.  In a different setting we might be interested
in a completely different property, e.g. whether rendering using a given shader
causes a device's temperature to exceed some threshold, causes a driver to
consume more than a certain amount of memory, or send the shader compiler into
an infinite loop.

Whatever condition we care about, we need to write a script that takes a shader
job (JSON file) as an argument, and exits with code 0 if and only if the shader
job is interesting.

Have a look at `glsl-reduce-walkthrough/basic_interestingness_test.py`.  This
basic interestingness test takes a shader job name, `foo.json` say, turns it
into a corresponding fragment shader name, `foo.frag`, invokes the fake compiler
on `foo.frag`, and exits with code 0 if and only if the fake compiler failed.
That is: a shader job comprised of a single fragment shader is interesting if
and only if the fake compiler does *not* succeed in compiling the fragment
shader.

The files `glsl-reduce-walkthrough/basic_interestingness_test` and
`glsl-reduce-walkthrough/basic_interestingness_test.bat` are shell scripts for
Linux/Mac and Windows that invoke the Python script.

Make whichever of these scripts is relevant to your platform executable.  If
you're working under Linux or Mac, also make
`glsl-reduce-walkthrough/basic_interestingness_test.py` executable.  Then try
running glsl-reduce:

```
glsl-reduce glsl-reduce-walkthrough/colorgrid_modulo.json ./glsl-reduce-walkthrough/basic_interestingness_test --output basic_reduction
```

You should see a lot of output fly by, and within a few seconds the reducer
should complete.

In the `basic_reduction` directory (created by glsl-reduce in response to the
`--output` option) you will find a final shader job,
`colorgrid_modulo_reduced_final`, representing the end result of reduction.

Take a look at the contents of `colorgrid_modulo_reduced_final.frag`: it's
basically just an empty main function.

Let's check whether this really causes our mock compiler bug to trigger:

```
# Run a fake shader compiler on the reduced fragment shader file:
python glsl-reduce-walkthrough/fake_compiler.py basic_reduction/colorgrid_modulo_reduced_final.frag

# Output:
# Internal error: something went wrong inlining 'floor'.

```

The reduced file does cause a bug to trigger in the fake compiler, but it is a
different bug to the one we saw before: we get a different error message!  (Look
in `glsl-reduce-walkthrough/fake_compiler.py` to see why this message is
generated; again this is a mock bug made up for this illustrative walkthrough.)

This is known as *bug slippage* ([see Section 3.5 of this
paper](https://www.cs.utah.edu/~regehr/papers/pldi13.pdf)): in reducing a shader
that triggers one bug we have ended up triggering a second bug and switching
attention to that second bug.  What we end up with is a minimized shader that
triggers the *second* bug but not the original bug.

Slippage can be useful as a method for discovering additional bugs ([see this
blog post on the topic](https://blog.regehr.org/archives/1284)), but is a pain
if we really did want to reduce with respect to the original bug.

In `basic_reduction` you will also find many partially-reduced shader jobs named
`colorgrid_modulo_reduced_X_Y`, where `Y` is `success` or `fail` depending on
whether the shader job was interesting, and `X` is a counter associated with
each shader job, indicating at what point during the reduction process it was
considered.  These shader jobs can be ignored, but can sometimes be useful for
understanding at what point slippage occurred.


## Applying glsl-reduce attempt 2: a better interestingness test

The reason we ended up with bug slippage is that our interestingness test was
very relaxed: it merely required the fake compiler to fail, and did not consider
the error that was generated.

Have a look at `glsl-reduce-walkthrough/better_interestingness_test.py`.  In
addition to checking that compilation fails, this script only deems a shader
interesting if the resulting error output from the compiler contains the
substring `too much indexing`.

Make sure `glsl-reduce-walkthrough/better_interestingness_test` (Linux/Mac) or
`glsl-reduce-walkthrough/better_interestingness_test.bat` (Windows) is
executable, and that `glsl-reduce-walkthrough/better_interestingness_test.py` is
also executable if on Linux/Mac.  Now let's try reducing using this more strict
interestingness test:

```
# Run the reducer with the better interestingness test
glsl-reduce glsl-reduce-walkthrough/colorgrid_modulo.json ./glsl-reduce-walkthrough/better_interestingness_test --output better_reduction
```

This time you might notice that the reducer takes a little longer to run.  The
final result, in `better_reduction/colorgrid_modulo_reduced_final.frag`, is not
quite as small as before: it contains at least two occurrences of `[i]` in order
for the indexing bug to trigger, and also contains some other stuff which, if
removed, would cause slippage to the `something went wrong inlining 'floor'`
bug.

```
# Run a fake shader compiler on the reduced fragment shader file:
python glsl-reduce-walkthrough/fake_compiler.py better_reduction/colorgrid_modulo_reduced_final.frag

# Output:
# Fatal error: too much indexing.

```

Great: our minimized shader preserves the original bug!


## Summary from walkthrough

The walkthrough has illustrated:

* How to run glsl-reduce on a shader job with a given interestingness test
* The concept of bug slippage, whereby a weak interestingness test can cause attention to switch from an original shader compiler bug to some other bug
* How to edit the interestingness test to be stricter and avoid slippage

It is hopefully clear that the interestingness test is *entirely programmable*.
In this tutorial it has simply involved invoking a (fake) compiler and checking
the compiler's output.  In practice it could do something much more complex,
such as launching an app that will consume the shader of interest and then
checking for some specific app behavior.


## Additional glsl-reduce options

glsl-reduce accepts various command-line options, most of which are only
relevant when using the tool in conjunction with glsl-fuzz.

We describe here the command-line options that are relevant when the tool is
used in stand-alone mode.

* `--output`: a directory (which will be created if it doesn't exist) into which
  all reduction temporary files will be placed.

* `--max-steps`: for many bugs reduction completes within approximately 100
  steps, but it can sometimes take much longer than this.  This option allows
  the maximum number of reduction steps that will be tried before glsl-reduce
  bails out to be specified.

* `--continue-previous-reduction`: if you find that you set too small a step
  limit for a reduction and want the reduction to continue from where it left
  off, kick off another reduction using this flag.

* `--timeout`: sets a limit on how long any given invocation of the
  interestingness test can run for.  If the interestingness test is killed due
  to reaching a timeout, the result is deemed uninteresting.

* `--seed`: the reducer employs randomization internally, and you can seed the
  random number generator using this flag.  This should not be necessary for
  regular use, but if you find a bug in glsl-reduce then being able to run the
  reducer deterministically via a given seed can be useful; relatedly kicking
  off a reduction with a different seed may allow you to luck your way out of
  hitting a glsl-reduce bug.

* `--verbose`: print extra output during reduction; it's useful to run with this
  option if you find a glsl-reduce bug and want to report it.
