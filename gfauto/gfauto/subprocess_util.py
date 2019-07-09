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
import os
import signal
import subprocess
import time
from typing import Dict, List, Optional, Union

from .gflogging import log
from .util import check

LOG_COMMAND_FAILED_PREFIX = "Command failed: "
LOG_COMMAND_TIMED_OUT_PREFIX = "Command timed out: "


def convert_stdout_stderr(
    result: Union[
        subprocess.CalledProcessError,
        subprocess.CompletedProcess,
        subprocess.TimeoutExpired,
    ]
) -> None:

    if result.stdout is not None:
        result.stdout = result.stdout.decode(encoding="utf-8", errors="ignore")
    if result.stderr is not None:
        result.stderr = result.stderr.decode(encoding="utf-8", errors="ignore")


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
        subprocess.CompletedProcess,
        subprocess.TimeoutExpired,
    ],
) -> None:
    log_stdout_stderr_helper(result.stdout, result.stderr)


def log_returncode_helper(returncode: int) -> None:
    log(f"RETURNCODE: {str(returncode)}")


def log_returncode(
    result: Union[
        subprocess.CalledProcessError, subprocess.CompletedProcess, subprocess.Popen
    ],
) -> None:
    log_returncode_helper(result.returncode)


def posix_kill_group(process: subprocess.Popen) -> None:
    os.killpg(process.pid, signal.SIGTERM)
    time.sleep(1)
    os.killpg(process.pid, signal.SIGKILL)


def run_helper(
    cmd: List[str],
    check_exit_code: bool = True,
    timeout: Optional[float] = None,
    env: Optional[Dict[str, str]] = None,
) -> subprocess.CompletedProcess:
    check(
        bool(cmd) and cmd[0] is not None and isinstance(cmd[0], str),
        AssertionError("run takes a list of str, not a str"),
    )

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
        if check_exit_code and exit_code != 0:
            raise subprocess.CalledProcessError(exit_code, process.args, stdout, stderr)
        return subprocess.CompletedProcess(process.args, exit_code, stdout, stderr)


def run(
    cmd: List[str],
    check_exit_code: bool = True,
    timeout: Optional[float] = None,
    verbose: bool = False,
    env: Optional[Dict[str, str]] = None,
) -> subprocess.CompletedProcess:
    log("Exec" + (" (verbose):" if verbose else ":") + str(cmd))
    try:
        result = run_helper(cmd, check_exit_code, timeout, env)
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
