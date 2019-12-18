# glsl-reduce

glsl-reduce is a tool for automatically reducing a GLSL shader with respect to a programmable condition.

In this walkthrough, we will use a mock shader compiler bug to demonstrate how
to use glsl-reduce in action.

## Requirements

**Summary:** the latest release zip, Java 8+, and Python 3.5+.

### Release zip

We will be using the latest release zip `graphicsfuzz.zip`.
You can download this from the [releases page](glsl-fuzz-releases.md)
or [build it from source](glsl-fuzz-develop.md).

Add the following directories to your path:

* `graphicsfuzz/python/drivers`
* One of:
  * `graphicsfuzz/bin/Linux`
  * `graphicsfuzz/bin/Mac`
  * `graphicsfuzz/bin/Windows`

The `graphicsfuzz/` directory is the unzipped release.
If building from source, this directory can be found at `graphicsfuzz/target/graphicsfuzz/`.

### Java 8+

You will also need to install the latest version of the Java 8 Development Kit,
either:

* From your system's package manager. E.g. Ubuntu: `sudo apt-get install openjdk-8-jdk`.
* By [downloading and installing Oracle's binary distribution](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (look for Java SE 8uXXX then the JDK link).
* By downloading and installing some other OpenJDK binary distribution for your platform.


```sh
# To check that Java 8 is installed and in use:
java -version
# Output: openjdk version "1.8.0_181"
```

### Python 3.5+

You will need to install Python 3.5 or higher, either:

* From your system's package manager. E.g. Ubuntu: `sudo apt-get install python3`.
* By downloading from [https://www.python.org/downloads/](https://www.python.org/downloads/).
* By downloading and installing some other Python 3 distribution.

For Windows: most recent installers of Python
add `py`, a Python launcher, to your path.
Our scripts attempt to use `py -3 SCRIPT.py` to
execute `SCRIPT.py` using Python 3.5+.

For shells (other than Windows command prompt):
our scripts attempt to use `python3`,
then `py -3`, then `python`.
Set `PYTHON_GF` to override the Python
command. For example, `export PYTHON_GF=python3.6`.

## glsl-reduce in action

We'll start by illustrating a mock shader compiler bug.  We present commands assuming a Linux/Mac environment,
but Windows users can adapt the commands or
use the Git Bash shell.

```sh
# Copy the sample files into the current directory:
cp -r graphicsfuzz/examples/glsl-reduce-walkthrough .

# For simplicity, add the directory to your PATH.
export PATH="$(pwd)/glsl-reduce-walkthrough:${PATH}"

# Run the fake shader compiler on the fragment shader file:
fake_compiler glsl-reduce-walkthrough/colorgrid_modulo.frag

# Output:
# Fatal error: too much indexing.

```

Our fake compiler fails to compile the valid shader because it cannot handle shaders
with a lot of indexing. Thus, we have found a compiler bug.

> Take a look at `glsl-reduce-walkthrough/fake_compiler.py` if you want to see why the fake compiler generates this error
> message for the shader. This is not important for understanding how to use the reducer.

Let's now use glsl-reduce to get a much smaller shader that causes the compiler to fail with this error:

```sh
# Observe that the initial shader is reasonably large
cat glsl-reduce-walkthrough/colorgrid_modulo.frag

# Output:
# <contents of the original shader>

# Run glsl-reduce, putting the tool's output in the 'reduction_results' directory (created if it does not exist)
glsl-reduce glsl-reduce-walkthrough/colorgrid_modulo.json interestingness_test --output reduction_results

# Output:
# <lots of messages about the reducer's progress>

# Confirm that the reduced fragment shader file still reproduces the issue
fake_compiler reduction_results/colorgrid_modulo_reduced_final.frag

# Output:
# Fatal error: too much indexing.

# Observe that the reduced shader is much smaller
cat reduction_results/colorgrid_modulo_reduced_final.frag

# Output:
# <contents of the reduced shader>

```

In `reduction_results` you will also find many partially-reduced shader jobs named
`colorgrid_modulo_reduced_X_Y.json`, where `Y` is `success` or `fail` depending on
whether the shader job was interesting, and `X` is a counter associated with
each shader job, indicating at what point during the reduction process it was
considered.  These shader jobs are sometimes useful for understanding the steps that were made during a reduction, but can otherwise be ignored.


## In more detail

glsl-reduce requires two arguments:

* a *shader job*: a path to a `.json` file representing the shader or shaders of interest
* an *interestingness test*: a path to an executable script encoding the properties a shader job must satisfy to be deemed interesting.

It is advisable to also use the `--output` option to specify a directory for generated results; this will be the current directory by default.

The `.json` file serves two purposes: (1) it tells glsl-reduce which shaders to operate on -- if it is called `foo.json` then at least one of `foo.frag`, `foo.vert` and `foo.comp` must be present, and any that are present will be reduced; (2) it allows metadata about these shaders to be stored.  At present, glsl-reduce does not allow any metadata when used in stand-alone mode, so the JSON file is required to simply contain `{}`.  (The file is used for meaningful metadata when glsl-reduce is used in conjunction with [glsl-fuzz](glsl-fuzz-intro.md).)

The *interestingness test* (name inspired by [C-Reduce](https://embed.cs.utah.edu/creduce/using/)) is a script that codifies what it means for a shader to be interesting.  It takes the name of a shader job as an argument, and should return 0 if and only if the shaders associated with the shader job are interesting.  What "interesting" means is specific to the context in which you are using glsl-reduce.

In the above example we deemed a shader job `foo.json` to be interesting if the associated fragment shader `foo.frag` caused the fake compiler to fail with the "`too much indexing`" message.  Have a look at `glsl-reduce-walkthrough/interestingness_test.py` (which is invoked by `glsl-reduce-walkthrough/interestingness_test` for Linux/Mac, and the corresponding `.bat` file for Windows): it checks precisely this.

In a different setting we might be interested in reducing with respect to a completely different property, e.g. we might check whether rendering using a given shader causes a device's temperature to exceed some threshold, whether a shader causes a driver to consume more than a certain amount of memory, or whether a shader sends a shader compiler into
a seemingly infinite loop.  Writing the interestingness test is entirely in the hands of the user of glsl-reduce.  Notice that in each of these examples, the property we care about (temperature, memory or execution time) is non-binary; it needs to be turned into a binary property by using an appropriate limit on the resource in question inside the interestingness test.


# Bug slippage: the need for a strong interestingness test

Let's try our reduction again but with a different interestingness test.  Have a look at `glsl-reduce-walkthrough/weak_interestingness_test.py`.  This test just checks whether compilation failed; it does not check the failure string.

Let's use it to perform a reduction:

```sh
# Run glsl-reduce
glsl-reduce glsl-reduce-walkthrough/colorgrid_modulo.json weak_interestingness_test --output slipped_reduction_results

# Output:
# <lots of messages about the reducer's progress>

# Whoah, it's an even smaller result!
cat slipped_reduction_results/colorgrid_modulo_reduced_final.frag

# Output:
# <a shader with an empty main>

# Does it still give us the fatal error?
fake_compiler slipped_reduction_results/colorgrid_modulo_reduced_final.frag

# Output:
# Internal error: something went wrong inlining 'floor'.

```

The reduced shader *does* cause a bug to trigger in the fake compiler, but it is a
*different* bug to the one we saw before: we get a different error message!  (Look
in `glsl-reduce-walkthrough/fake_compiler.py` to see why this message is
generated; this is of course just a mock bug made up for this illustrative walkthrough.)

This is known as *bug slippage* ([see Section 3.5 of this
paper](https://www.cs.utah.edu/~regehr/papers/pldi13.pdf)): in reducing a shader
that triggers one bug we have ended up triggering a second bug and switching
attention to that second bug.  What we end up with is a minimized shader that
triggers the *second* bug but not the original bug.

Slippage can be useful as a method for discovering additional bugs ([see this
blog post on the topic](https://blog.regehr.org/archives/1284)), and the series of partially-reduced shader jobs produced during reduction sometimes provides a rich source of by-product bugs.  However, slippage is annoying if we really care about reducing with respect to the original bug.  To avoid slippage, you need to write a sufficiently strong interestingness test.


## Summary from walkthrough

The walkthrough has illustrated:

* How to run glsl-reduce on an example
* What the arguments to glsl-reduce mean, including the concept of an *interestingness test*
* The notion of *bug slippage*, whereby a weak interestingness test can cause reduction to switch from an original bug to  some other bug

We emphasize again that the interestingness test is *entirely programmable*.
In this tutorial it has simply involved invoking a (fake) compiler and checking
the compiler's output.  In practice it could do something much more complex,
such as launching an app that will consume the shader of interest and then
checking for some specific app behavior.
