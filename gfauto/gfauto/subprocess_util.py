# -*- coding: utf-8 -*-

# Copyright 2019 The GraphicsFuzz Project Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Subprocess utility module.

Used to execute a process. In particular, the stdout and stderr are captured, and logged on failure.
The entire process group is killed on timeout, when this feature is available; this can prevent hangs,
especially when using catchsegv.
"""

import os
import signal
import subprocess
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

from gfauto import types
from gfauto.gflogging import log
from gfauto.util import check

LOG_COMMAND_FAILED_PREFIX = "Command failed: "
LOG_COMMAND_TIMED_OUT_PREFIX = "Command timed out: "


def log_stdout_stderr_helper(stdout: str, stderr: str) -> None:

    log("STDOUT:")
    log(stdout)
    log("")

    log("STDERR:")
    log(stderr)
    log("")


def log_stdout_stderr(
    result: Union[
        subprocess.CalledProcessError,
        types.CompletedProcess,
        subprocess.TimeoutExpired,
    ],
) -> None:
    log_stdout_stderr_helper(result.stdout, result.stderr)


def log_returncode_helper(returncode: int) -> None:
    log(f"RETURNCODE: {str(returncode)}")


def log_returncode(
    result: Union[subprocess.CalledProcessError, types.CompletedProcess, types.Popen],
) -> None:
    log_returncode_helper(result.returncode)


def posix_kill_group(process: types.Popen) -> None:
    # Work around type warnings that will only show up on Windows:
    os_alias: Any = os
    signal_alias: Any = signal
    os_alias.killpg(process.pid, signal_alias.SIGTERM)
    time.sleep(1)
    os_alias.killpg(process.pid, signal_alias.SIGKILL)


def run_helper(
    cmd: List[str],
    check_exit_code: bool = True,
    timeout: Optional[float] = None,
    env: Optional[Dict[str, str]] = None,
    working_dir: Optional[Path] = None,
) -> types.CompletedProcess:
    check(
        bool(cmd) and cmd[0] is not None and isinstance(cmd[0], str),
        AssertionError("run takes a list of str, not a str"),
    )

    # When using catchsegv, only SIGSEGV will cause a backtrace to be printed.
    # We can also include SIGABRT by setting the following environment variable.
    if cmd[0].endswith("catchsegv"):
        if env is None:
            env = {}
        env["SEGFAULT_SIGNALS"] = "SEGV ABRT"

    env_child: Optional[Dict[str, str]] = None
    if env:
        log(f"Extra environment variables are: {env}")
        env_child = os.environ.copy()
        env_child.update(env)

    with subprocess.Popen(
        cmd,
        encoding="utf-8",
        errors="ignore",
        start_new_session=True,
        env=env_child,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=working_dir,
    ) as process:
        try:
            stdout, stderr = process.communicate(input=None, timeout=timeout)
        except subprocess.TimeoutExpired:
            try:
                posix_kill_group(process)
            except AttributeError:
                process.kill()
            stdout, stderr = process.communicate()
            assert timeout  # noqa
            raise subprocess.TimeoutExpired(process.args, timeout, stdout, stderr)
        except:  # noqa
            try:
                posix_kill_group(process)
            except AttributeError:
                process.kill()
            raise

        exit_code = process.poll()

        if exit_code is None:
            # Hopefully, it is impossible for poll() to return None at this stage, but if it does
            # we set some recognizable, non-zero exit code and try one more time to kill the process.
            exit_code = 999
            try:
                posix_kill_group(process)
            except AttributeError:
                process.kill()

        if check_exit_code and exit_code != 0:
            raise subprocess.CalledProcessError(exit_code, process.args, stdout, stderr)
        return subprocess.CompletedProcess(process.args, exit_code, stdout, stderr)


def run(
    cmd: List[str],
    check_exit_code: bool = True,
    timeout: Optional[float] = None,
    verbose: bool = False,
    env: Optional[Dict[str, str]] = None,
    working_dir: Optional[Path] = None,
) -> types.CompletedProcess:
    log("Exec" + (" (verbose):" if verbose else ":") + str(cmd))
    try:
        result = run_helper(cmd, check_exit_code, timeout, env, working_dir)
    except subprocess.TimeoutExpired as ex:
        log(LOG_COMMAND_TIMED_OUT_PREFIX + str(cmd))
        # no return code to log in case of timeout
        log_stdout_stderr(ex)
        raise ex
    except subprocess.CalledProcessError as ex:
        log(LOG_COMMAND_FAILED_PREFIX + str(cmd))
        log_returncode(ex)
        log_stdout_stderr(ex)
        raise ex

    log_returncode(result)
    if verbose:
        log_stdout_stderr(result)

    return result
