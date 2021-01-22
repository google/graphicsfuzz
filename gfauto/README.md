# gfauto

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://github.com/google/graphicsfuzz/workflows/.github/workflows/gfauto.yml/badge.svg)](https://github.com/google/graphicsfuzz/actions)


gfauto is a set of tools for using the fuzzers and reducers from the [GraphicsFuzz project](https://github.com/google/graphicsfuzz) (including spirv-fuzz and spirv-reduce) in a "push-button" fashion with minimal interaction.

**Note: 64-bit Linux is currently the only supported platform. Windows and Mac are unlikely to work.**

## Requirements

* Python 3.6+. If contributing, Python 3.6 (not 3.7, 3.8, etc.) is needed.
* Pip must be installed _for the Python binary you will use_. E.g. Try `python3.6 -m pip`.

## Setup

Clone this repo and enter the `gfauto/` directory that contains this `README.md` file. Execute:

```sh
./dev_shell.sh.template
```

> If the default settings don't work, make a copy of the file called `dev_shell.sh`, modify according to the comments, and execute it.

> Pip for Python 3.6 may be broken on certain Debian distributions.
> See "Installing Python" to install Python 3.6 using ~3 commands.

The script generates a Python virtual environment (located at `.venv/`) with all dependencies installed. To activate the virtual environment:

* `source .venv/bin/activate` (on Linux)
* `source .venv/Scripts/activate` (on Windows with the Git Bash shell)
* `.venv/Scripts/activate.bat` (on Windows with cmd)

Skip to [Fuzzing](#fuzzing) to start fuzzing Vulkan devices and tools.

## Presubmit checks and protobuf generation

* Execute `./check_all.sh` to run various presubmit checks, linters, etc.
* Execute `./fix_all.sh` to automatically fix certain issues, such as formatting.
* Execute `./run_protoc.sh` to re-generate protobuf files.

## PyCharm

Use PyCharm to open the top-level `gfauto/` directory (that contains this `README.md` file).
It should detect the Python virtual environment (at `.venv/`) automatically
for both the code
and when you open a `Terminal` or `Python Console` tab.
If you see import errors
then configure the Python interpreter to be
derived from the virtual environment at `.venv/`.

Install and configure plugins:

* Protobuf Support
* File Watchers (may already be installed)
  * The watcher task should already be under version control.
* Mypy: the built-in PyCharm type checking uses Mypy behind-the-scenes, but this plugin enhances it by using the latest version and allowing the use of stricter settings, matching the settings used by the `./check_all.sh` script.

Add `dictionary.dic` as a custom dictionary (search for "Spelling" in Actions). Do not add words via PyCharm's "Quick Fixes" feature, as the word will only be added to your personal dictionary. Instead, manually add the word to `dictionary.dic`.

#### Terminal tab

The `Terminal` tab in PyCharm is useful, as it uses the project's Python virtual environment. Use it to execute the scripts described above, such as `./check_all.sh`.

## [Coding conventions](docs/conventions.md)

## Symlinking other scripts

gfauto moves fast and so it is useful to add symlinks to other repositories that contain Python scripts that depend on gfauto. This allows you to search for all references before changing a function. A `temp/` directory exists for this purpose. For example:

```sh
cd temp
ln -s /path/to/shader-generation shader-generation
```

Now any scripts in the `shader-generation` repository are visible in PyCharm.

You can execute scripts in this repository by opening a Terminal in PyCharm.


## Fuzzing

To start fuzzing, create and change to a directory outside the `gfauto/` directory. E.g. `/data/temp/gfauto_fuzzing/2019_06_24`. From here, create `references/` and `donors/` directories containing GLSL shader jobs as used by GraphicsFuzz.
You can get some samples from a [nightly GraphicsFuzz build](https://github.com/google/gfbuild-graphicsfuzz/releases).

```sh
mkdir references/ donors/
cp /data/graphicsfuzz_zip/samples/310es/* references/
cp /data/graphicsfuzz_zip/samples/310es/* donors/
```

Now run the fuzzer.

```sh
gfauto_fuzz
```

It will fail because there is no settings file, but a default `settings.json` file will be created for you.
Review this file.
All plugged Android devices should have been added to the list of devices.
The `active_device_names` list should be modified to include only the unique devices that you care about:

* Including multiple, identical devices (with the same GPU and drivers) is not recommended, as it will currently result in redundant testing.
* `host_preprocessor` should always be included first, as this virtual device detects failures in tools such as `glslangValidator` and `spirv-opt`, and will ensure such failures will not be logged for real devices.
* You can use `--settings SETTINGS.json` to use a settings file other than `settings.json` (the default). In this way, you can run multiple instances of `gfauto_fuzz` in parallel to test *different* devices.

### Parallel fuzzing and fixed seeds

You can generate a space-separated list of 1000 seeds as follows:

```python
import secrets
from pathlib import Path

content = " ".join([str(secrets.randbits(256)) for i in range(0, 1000)])
Path("seeds.txt").write_text(content, encoding="utf-8", errors="ignore")
```

You can run
the seeds in parallel using `gfauto_fuzz`:

```sh
# Warning: assumes "parallel" is from the "moreutils" pakage.
# Check with "man parallel".

parallel -j 32 gfauto_fuzz --iteration_seed -- $(cat seeds.txt)
```

The above command runs 32 parallel instances
of `gfauto_fuzz`, which is ideal for testing the
`host_preprocessor` and `swift_shader` virtual devices;
running parallel tests on actual hardware might give unreliable results.


You can run parallel instances of gfauto (just for increased throughput, not with fixed seeds) using:

```sh
# Warning: assumes "parallel" is from the "moreutils" pakage.
# Check with "man parallel".

parallel -j 32 -i gfauto_fuzz -- $(seq 100)
```

# Installing Python

To manually install Python on your Linux distribution, you can use `pyenv`.

https://github.com/pyenv/pyenv#basic-github-checkout

In summary:

* Install the required packages recommended [here](https://github.com/pyenv/pyenv/wiki/Common-build-problems).

* Then:

```sh
git clone https://github.com/pyenv/pyenv.git ~/.pyenv

# Add the following two lines to your ~/.bashrc file.
export PYENV_ROOT="$HOME/.pyenv"
export PATH="$PYENV_ROOT/bin:$PATH"

# In a new terminal:
eval "$(pyenv init -)"
pyenv install 3.6.9
pyenv global 3.6.9

# Now execute the development shell script, as usual.
./dev_shell.sh.template
```

You can reactivate the development shell later,
as explained above, using
`source .venv/bin/activate`,
without having to re-execute the above `pyenv` commands.
