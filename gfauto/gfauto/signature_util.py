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

import re
from pathlib import Path
from typing import Match, Optional, Pattern

from gfauto import subprocess_util, util

# .* does not match newlines

# 06-15 21:17:00.039  7517  7517 F DEBUG   :     #00 pc 00000000009d9c34  /my/library.so ((anonymous namespace)::Bar::Baz(aaa::MyInstr*, void* (*)(unsigned int))+456)
# Another example of the function signature: /my/library.so (myFunction+372)
# Another example of the function signature: /my/library.so (myFunction(...)+372)

HEX_LIKE = r"(0x)?[0-9a-fA-F]"

# Just look for anything that contains "word(" or "word+" from after the (hex-like) PC address.
PATTERN_ANDROID_BACKTRACE_FUNCTION = re.compile(
    r"^.*#00 pc " + HEX_LIKE + r"+ (.*(\w+)([(+]).*)"
)

ANDROID_BACKTRACE_COMMON_TEXT_TO_REMOVE = re.compile(
    r"(/vendor/lib(64)?/(hw/)?|/data/local/(tmp/)?|/system/(lib(64)?/)?|anonymous namespace)"
)

PATTERN_ANDROID_BACKTRACE_CATCHALL = re.compile(r"^.*#00 pc " + HEX_LIKE + r"+ (.*)")

# E.g. ERROR: temp/.../variant/shader.frag:549: 'variable indexing fragment shader output array' : not supported with this profile: es
#                                                variable indexing fragment shader output array   <-- group 1
PATTERN_GLSLANG_ERROR = re.compile(r"ERROR: .*?: '(.*?)'")


# E.g.
# glslangValidator: ../glslang/MachineIndependent/ParseHelper.cpp:2212: void glslang::TParseContext::nonOpBuiltInCheck(const glslang::TSourceLoc&, const glslang::TFunction&, glslang::TIntermAggregate&): Assertion `PureOperatorBuiltins == false' failed.

PATTERN_ASSERTION_FAILURE = re.compile(r"^.*?:\d+: (.*? [Aa]ssert(ion)?)")


# Only used if "0 pass, 1 fail" is found.
# E.g. /data/local/tmp/graphicsfuzz/test.amber: 256: probe ssbo format does not match buffer format
#                                                    probe ssbo format does not match buffer format
PATTERN_AMBER_ERROR = re.compile(r"^.*?\w: \d+: (.*)")

# E.g. error: line 0: Module contains unreachable blocks during merge return.  Run dead branch elimination before merge return.
#      error: line 0: Module contains unreachable blocks during merge return.  Run dead branch elimination before merge return.
#                     Module contains unreachable blocks during merge return.  Run dead branch elimination before merge return.
PATTERN_SPIRV_OPT_ERROR: Pattern[str] = re.compile(r"error: line \d+: (.*)")

# E.g.
# Backtrace:
# /data/git/graphicsfuzz/graphicsfuzz/target/graphicsfuzz/bin/Linux/spirv-opt(_ZN8spvtools3opt21StructuredCFGAnalysis16SwitchMergeBlockEj+0x369)[0x5bd6d9]
PATTERN_CATCHSEGV_STACK_FRAME = re.compile(r"Backtrace:\n.*/([^/(]*\([^)+\[]+)\+")

# E.g.
# Backtrace:
# /data/git/graphicsfuzz/gfauto/temp/june_20/binaries/swiftshader_vulkan/Linux/libvk_swiftshader.so(+0x1d537d)[0x7f51ebd1237d]
# /data/git/graphicsfuzz/gfauto/temp/june_20/binaries/swiftshader_vulkan/Linux/libvk_swiftshader.so  0x1d537d
# ^ group 1                                                                                          ^ group 2
PATTERN_CATCHSEGV_STACK_FRAME_ADDRESS = re.compile(
    r"Backtrace:\n(.*)\(\+([x\da-fA-F]+)+\)\["
)


def remove_hex_like(string: str) -> str:
    temp = string
    # Remove hex like chunks of 4 or more.
    temp = re.sub(HEX_LIKE + r"{4,}", "", temp)
    return temp


def clean_up(string: str) -> str:
    temp = string
    # Remove numbers.
    temp = re.sub(r"\d+", "", temp)
    # Replace spaces with _.
    temp = re.sub(r" ", "_", temp)
    # Remove non-word, non-_ characters.
    temp = re.sub(r"[^\w_]", "", temp)
    # Replace multiple _ with _.
    temp = re.sub(r"__+", "_", temp)
    return temp


def reduce_length(string: str) -> str:
    return string[:50]


def basic_match(pattern: Pattern[str], log_contents: str) -> Optional[str]:
    match: Optional[Match[str]] = re.search(pattern, log_contents)
    if not match:
        return None
    group = match.group(1)
    group = clean_up(group)
    group = reduce_length(group)
    return group


def get_signature_from_log_contents(  # pylint: disable=too-many-return-statements, too-many-branches
    log_contents: str
) -> str:

    # noinspection PyUnusedLocal
    match: Optional[Match[str]]
    # noinspection PyUnusedLocal
    group: Optional[str]

    # glslang error.
    group = basic_match(PATTERN_GLSLANG_ERROR, log_contents)
    if group:
        return group

    # Assertion error pattern, used by glslang.
    group = basic_match(PATTERN_ASSERTION_FAILURE, log_contents)
    if group:
        return group

    # Spirv-opt error.
    group = basic_match(PATTERN_SPIRV_OPT_ERROR, log_contents)
    if group:
        return group

    # Amber error.
    if "0 pass, 1 fail" in log_contents:
        group = basic_match(PATTERN_AMBER_ERROR, log_contents)
        if group:
            return group

    # Android stack traces.
    if "#00 pc" in log_contents:
        lines = log_contents.split("\n")
        for line in lines:
            pc_pos = line.find("#00 pc")
            if pc_pos == -1:
                continue
            line = line[pc_pos:]

            if "/amber_ndk" in line:
                return "amber_ndk"

            match = re.search(PATTERN_ANDROID_BACKTRACE_FUNCTION, log_contents)
            if match:
                group = match.group(1)
                # Remove common text.
                group = re.sub(ANDROID_BACKTRACE_COMMON_TEXT_TO_REMOVE, "", group)
                group = clean_up(group)
                group = reduce_length(group)
                return group

            # TODO: Maybe more.

            # If we get here, we found #00 pc, but nothing else.
            # This regex essentially matches the entire line after the hex-like PC address.
            match = re.search(PATTERN_ANDROID_BACKTRACE_CATCHALL, log_contents)
            if match:
                group = match.group(1)
                # Remove common text.
                group = re.sub(ANDROID_BACKTRACE_COMMON_TEXT_TO_REMOVE, "", group)
                # Remove hex-like chunks.
                group = remove_hex_like(group)
                group = clean_up(group)
                group = reduce_length(group)
                return group

            break

    # catchsegv "Backtrace:" with source code info.
    group = basic_match(PATTERN_CATCHSEGV_STACK_FRAME, log_contents)
    if group:
        return group

    # catchsegv "Backtrace:" with addresses.
    if "Backtrace:" in log_contents:
        result = get_signature_from_catchsegv_frame_address(log_contents)
        if result:
            return result

    if "Shader compilation failed" in log_contents:
        return "compile_error"

    if "Failed to link shaders" in log_contents:
        return "link_error"

    if "Calling vkCreateGraphicsPipelines Fail" in log_contents:
        return "pipeline_failure"

    # TODO: Check for Amber fence failure.

    if "Resource deadlock would occur" in log_contents:
        return "Resource_deadlock_would_occur"

    return "no_signature"


def get_signature_from_catchsegv_frame_address(log_contents: str) -> Optional[str]:
    match = re.search(PATTERN_CATCHSEGV_STACK_FRAME_ADDRESS, log_contents)
    if not match:
        return None
    module = Path(match.group(1))
    if not module.exists():
        return None
    address = match.group(2)
    function_signature = get_function_signature_from_address(module, address)
    if not function_signature:
        return None
    function_signature = clean_up(function_signature)
    function_signature = reduce_length(function_signature)
    return function_signature


def get_function_signature_from_address(module: Path, address: str) -> Optional[str]:
    try:
        address_tool = util.tool_on_path("addr2line")
        result = subprocess_util.run(
            [str(address_tool), "-e", str(module), address, "-f", "-C"],
            check_exit_code=False,
        )
        if result.returncode != 0:
            return None
        stdout: str = result.stdout
        lines = stdout.splitlines()
        if not lines:
            return None
        return lines[0]
    except util.ToolNotOnPathError:
        return None
