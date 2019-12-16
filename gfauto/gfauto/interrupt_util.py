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

"""Interrupt utility module.

Used to override the SIGINT signals and exit gracefully.
"""
import signal
import sys
from typing import Any

from gfauto import util
from gfauto.gflogging import log

exit_triggered = False  # pylint: disable=invalid-name;
original_sigint_handler: Any = None  # pylint: disable=invalid-name;


def interrupt_if_needed() -> None:
    if exit_triggered:
        raise KeyboardInterrupt()


def interrupted() -> bool:
    return exit_triggered


def _sigint_handler(signum: int, _: Any) -> None:
    global exit_triggered  # pylint: disable=invalid-name,global-statement;
    msg = f"\nCaught signal {signum}. Terminating at next safe point.\n"
    log(msg)
    print(msg, flush=True, file=sys.stderr)  # noqa: T001
    exit_triggered = True
    # Restore signal handler.
    signal.signal(signal.SIGINT, original_sigint_handler)


def override_sigint() -> None:
    global original_sigint_handler  # pylint: disable=invalid-name,global-statement;
    util.check(
        original_sigint_handler is None,
        AssertionError("Called override_sig_int more than once"),
    )
    original_sigint_handler = signal.signal(signal.SIGINT, _sigint_handler)
