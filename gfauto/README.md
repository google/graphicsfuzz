# GraphicsFuzz auto

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://paulthomson.visualstudio.com/gfauto/_apis/build/status/google.graphicsfuzz?branchName=master)](https://paulthomson.visualstudio.com/gfauto/_build/latest?definitionId=2&branchName=master)


## GraphicsFuzz auto is a set of Python scripts for running GraphicsFuzz

[GraphicsFuzz](https://github.com/google/graphicsfuzz) provides tools that automatically find and simplify bugs in graphics shader compilers.
GraphicsFuzz auto (this project) provides scripts for running these tools with minimal interaction.

## Development setup

> Optional: if you have just done `git pull` to get a more recent version of GraphicsFuzz auto, consider deleting `.venv/` to start from a fresh virtual environment. This is rarely needed.

Execute `./dev_shell.sh.template`. If the default settings don't work, make a copy of the file called `dev_shell.sh` and modify according to the comments before executing. `pip` must be installed for the version of Python you wish to use.

The script generates and activates a Python virtual environment (located at `.venv/`) with all dependencies installed.

* Execute `./check_all.sh` to run various presubmit checks, linters, etc.
* Execute `./fix_all.sh` to automatically fix certain issues, such as formatting.


### PyCharm

Use PyCharm to open the top-level `gfauto` directory.
It should pick up the Python virtual environment (at `.venv/`) automatically
for both the code
and when you open a `Terminal` or `Python Console` tab.

Install and configure plugins:

* Protobuf Support
* File Watchers (may already be installed)
  * The watcher task should already be under version control.
* Mypy: the built-in PyCharm type checking uses Mypy behinds the scenes, but this plugin enhances it by using the latest version and allowing the use of stricter settings, matching the settings used by the `./check_all.sh` script.

Add `whitelist.dic` as a custom dictionary (search for "Spelling" in Actions). Do not add words via PyCharm's "Quick Fixes" feature, as the word will only be added to your personal dictionary. Instead, manually add the word to `whitelist.dic`.

## Imports

We use the `black` Python code formatter and `isort` for sorting imports. 

When importing things from gfauto, use the `gfauto` package name and only import modules, not functions:

```python
# Do this:
from gfauto import result_util

# Don't do this:

# Using ".".
from . import result_util

# Importing a function.
from gfauto.result_util import get_status_path

# Using "." AND importing a function!
from .result_util import get_status_path

```

## Symlinking other scripts

GraphicsFuzz auto moves fast and so it is useful to add symlinks to other repositories that contain Python scripts that depend on GraphicsFuzz auto. This allows you to search for all references before changing a function. A `temp/` directory exists for this purpose. For example:

```sh
cd temp
ln -s /path/to/shader-generation shader-generation
```

Now any scripts in the `shader-generation` repository are visible in PyCharm.

You can execute scripts in this repository by opening a Terminal in PyCharm.

## Terminal

To reiterate, the `Terminal` tab in PyCharm is useful and will use the project's Python virtual environment. In any other terminal, you can execute the `./dev_shell.sh` script, but this is fairly slow as it checks and reinstalls all dependencies; a quicker alternative is: `source .venv/bin/activate`.

## Fuzzing

To start fuzzing, create and change to a directory outside the `gfauto/` directory. E.g. `/data/temp/gfauto_fuzzing/2019_06_24`. From here, create a `donors/` directory containing GLSL shader jobs as used by GraphicsFuzz.
You can get some samples from the GraphicsFuzz project.

```sh
mkdir donors/
cp /data/graphicsfuzz_zip/samples/310es/* donors/
cp /data/graphicsfuzz_zip/samples/compute/310es/* donors/
```

Now run the fuzzer.

```sh
gfauto_fuzz
```

It will fail because there is no settings file, but a default `settings.json` file will be created for you.
Review this file.
All plugged Android devices should have been added to the list of devices.
The `active_device_names` list should be modified to include only the unique devices that you care about:

* Including multiple devices with the same GPU and drivers will currently result in redundant testing.
* `host_preprocessor` should always be included first, as this virtual device detects failures in tools such as `glslangValidator` and `spirv-opt`, and will ensure such failures will not be logged for real devices.


