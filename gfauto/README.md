
# GraphicsFuzz auto

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://paulthomson.visualstudio.com/gfauto/_apis/build/status/google.graphicsfuzz?branchName=master)](https://paulthomson.visualstudio.com/gfauto/_build/latest?definitionId=2&branchName=master)


## GraphicsFuzz auto is a set of Python scripts for running GraphicsFuzz

[GraphicsFuzz](https://github.com/google/graphicsfuzz) provides tools that automatically find and simplify bugs in graphics shader compilers.
GraphicsFuzz auto (this project) provides scripts for running these tools with minimal interaction.

## Development setup

> Optional: delete `.venv/` to remove old packages; this is important when 
> flake8 packages have been removed from the Pipfile, as old flake8 packages will 
> continue to be installed and used when running `./check_all.sh` until you
> delete `.venv/`.

Execute `./dev_shell.sh.template` (or, copy to `./dev_shell.sh` and modify as needed before executing).
This generates and activates a Python virtual environment (located at `.venv/`) with all dependencies installed. 

* Execute `./check_all.sh` to run various presubmit checks, linters, etc.
* Execute `./fix_all.sh` automatically fix certain issues, such as formatting.


### PyCharm

Use PyCharm to open the top-level `gfauto` directory.
It should pick up the Python virtual environment (at `.venv/`) automatically
for both the code
and when you open a `Terminal` or `Python Console` tab.

Install and configure plugins:

* Protobuf Support
* File Watchers (may already be installed)
  * The watcher task should already be under version control with the following settings:
    * File type: Python
    * Program: `$ProjectFileDir$/fix_all.sh`
* Mypy: the built-in PyCharm type checking actually use Mypy behinds the scenes, but this plugin enhances it by using the latest version and strict settings used by the `./check_all.sh` script.

Add `whitelist.dic` as a custom dictionary (search for "Spelling" in Actions). Do not add words via PyCharm's "Quick Fixes" feature, as the word will only be added to your personal dictionary. Instead, manually add the word to `whitelist.dic`.


## Symlinking other scripts

GraphicsFuzz auto moves fast and so it is useful to add symlinks to other repositories that contain Python scripts that depend on GraphicsFuzz auto. This allows you to search for all references before changing a function. A `temp/` directory exists for this purpose. For example:

```sh
cd temp
ln -s /path/to/shader-generation shader-generation
```

Now any scripts in the `shader-generation` repository are visible in PyCharm.

You can execute scripts in this repository by opening a Terminal in PyCharm.

## Terminal

To reiterate, the `Terminal` tab in PyCharm is useful and will use the project's Python virtual environment. In any other terminal, you can execute the `./dev_shell.sh` script, but this is fairly slow as it checks and reinstalls all dependencies; a quicker alternative is: `source .venv/activate`.


