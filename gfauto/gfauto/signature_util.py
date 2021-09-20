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

"""Bug signature module.

Used to compute the "signature" of a bug, typically using the error message or the function name from the top of the
stack trace.
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Callable, Match, Optional, Pattern

from gfauto import subprocess_util, util
from gfauto.gflogging import log

# .* does not match newlines
# (?:   ) non-group parentheses

NO_SIGNATURE = "no_signature"

HEX_LIKE = r"(?:0x)?[0-9a-fA-F]"

# 06-15 21:17:00.039  7517  7517 F DEBUG   :     #00 pc 00000000009d9c34  /my/library.so ((anonymous namespace)::Bar::Baz(aaa::MyInstr*, void* (*)(unsigned int))+456)
# Another example of the function signature: /my/library.so (myFunction+372)
# Another example of the function signature: /my/library.so (myFunction(...)+372)

# Just look for anything that contains "word(" or "word+" from after the (hex-like) PC address.
PATTERN_ANDROID_BACKTRACE_FUNCTION = re.compile(
    r"\n.*#00 pc " + HEX_LIKE + r"+ (.*[\w\d_]+[(+].*)"
)

ANDROID_BACKTRACE_COMMON_TEXT_TO_REMOVE = re.compile(
    r"(?:"
    r"vendor/"
    r"|hw/"
    r"|data/local/(?:tmp/)?"
    r"|system/(?:lib(?:64)?/)?"
    r"|lib(?:64)?/"
    r"|apex/"
    r"|bionic/"
    r"|com.android.runtime(?:/lib(?:64)?)?/"
    r"|anonymous namespace"
    r"|\(BuildId: " + HEX_LIKE + r"+\)"
    r"|.*\.apk!"
    r"|offset"
    r")"
)

PATTERN_CDB_CALL_SITE = re.compile(
    fr"\n{HEX_LIKE}+`{HEX_LIKE}+ {HEX_LIKE}+`{HEX_LIKE}+ (.*)"
)

# E.g.
# 01-23 11:51:21.141  1814  1811 F DEBUG   :       #00 pc 00000000012313ec  /vendor/lib64/egl/libGLES.so (BuildId: 123b5800abef5fc4b86c0032ddd223d5)
PATTERN_ANDROID_BACKTRACE_CATCHALL = re.compile(fr"\n.*#00 pc ({HEX_LIKE}+ .*)")

# E.g. ERROR: temp/.../variant/shader.frag:549: 'variable indexing fragment shader output array' : not supported with this profile: es
#                                                variable indexing fragment shader output array   <-- group 1
# E.g. ERROR: reports/.../part_1_preserve_semantics/reduction_work/variant/shader_reduced_0173/0_glsl/shader_reduced_0173.frag:456: '=' :  cannot convert from ' const 3-component vector of bool' to ' temp bool'
PATTERN_GLSLANG_ERROR = re.compile(r"ERROR: .*?:\d+: (.*)")


# E.g.
# glslangValidator: ../glslang/MachineIndependent/ParseHelper.cpp:2212: void glslang::TParseContext::nonOpBuiltInCheck(const glslang::TSourceLoc&, const glslang::TFunction&, glslang::TIntermAggregate&): Assertion `PureOperatorBuiltins == false' failed.

PATTERN_ASSERTION_FAILURE = re.compile(r"\n.*?:\d+: (.*? [Aa]ssert(?:ion)?)")


# Only used if "0 pass, 1 fail" is found.
# E.g. /data/local/tmp/graphicsfuzz/test.amber: 256: probe ssbo format does not match buffer format
#                                                    probe ssbo format does not match buffer format
PATTERN_AMBER_ERROR = re.compile(r"\n.*?[.]amber: \d+: (.*)")

# E.g. error: line 0: Module contains unreachable blocks during merge return.  Run dead branch elimination before merge return.
#      error: line 0: Module contains unreachable blocks during merge return.  Run dead branch elimination before merge return.
#                     Module contains unreachable blocks during merge return.  Run dead branch elimination before merge return.
PATTERN_SPIRV_OPT_ERROR: Pattern[str] = re.compile(r"error: line \d+: (.*)")

# E.g.
# Backtrace:
# /data/git/graphicsfuzz/graphicsfuzz/target/graphicsfuzz/bin/Linux/spirv-opt(_ZN8spvtools3opt21StructuredCFGAnalysis16SwitchMergeBlockEj+0x369)[0x5bd6d9]
#                                                                   |--- group 1 -------------------------------------------------------|
#
# E.g. Backtrace:
# /home/runner/work/gfbuild-llpc/gfbuild-llpc/vulkandriver/drivers/llvm-project/llvm/lib/CodeGen/LiveInterval.cpp:758(_ZN4llvm9LiveRange20MergeValueNumberIntoEPNS_6VNInfoES2_)[0x135342c]
#                                                                                                |--- group 1 ----------------------------------------------------------------|
#
#                                                |--- group 1 ----|
# Using "c" "<>" "<cc...>" for chars         c.*c(<ccc>*<><ccccc>+)<cc>
PATTERN_CATCHSEGV_STACK_FRAME = re.compile(r"/.*/([^/(]*\([^)+\[]+)[+)]")

# E.g.
# /data/git/graphicsfuzz/gfauto/temp/june_20/binaries/swiftshader_vulkan/Linux/libvk_swiftshader.so(+0x1d537d)[0x7f51ebd1237d]
# /data/git/graphicsfuzz/gfauto/temp/june_20/binaries/swiftshader_vulkan/Linux/libvk_swiftshader.so  0x1d537d
# ^ group 1                                                                                          ^ group 2
PATTERN_CATCHSEGV_STACK_FRAME_ADDRESS = re.compile(r"(.*)\(\+([x\da-fA-F]+)+\)\[")

PATTERN_SWIFT_SHADER_ABORT = re.compile(r":\d+ ABORT:(.*)")

PATTERN_SWIFT_SHADER_WARNING = re.compile(r":\d+ WARNING:(.*)")

PATTERN_CATCH_ALL_ERROR = re.compile(r"\nERROR: (.*)", flags=re.IGNORECASE)

# [\s\S] matches anything, including newlines.
PATTERN_LLVM_FATAL_ERROR = re.compile(
    r"LLVM FATAL ERROR:[ ]*Broken function found, compilation aborted![\s\S]*STDERR:\n(.*)"
)

PATTERN_LLVM_MACHINE_CODE_ERROR = re.compile(
    r"ERROR: LLVM FATAL ERROR:[ ]*Found .* machine code error[\s\S]*Bad machine code: (.*)"
)

PATTERN_LLVM_ERROR_DIAGNOSIS = re.compile(r"ERROR: LLVM DIAGNOSIS INFO: (.*)")

PATTERN_ADDRESS_SANITIZER_ERROR = re.compile(
    r"SUMMARY: AddressSanitizer: ([a-z\-]+) .* in (.*)"
)

PATTERN_MESA_NIR_VALIDATION_ERROR = re.compile(
    r"error: (.*)\(\.\./src/compiler/nir/nir_validate\.c:\d+\)"
)

PATTERN_MESA_SPIRV_PARSE_ERROR = re.compile(
    r"SPIR-V parsing FAILED:\s+In file.*\n\s+(.*)"
)

PATTERN_AMBER_TOLERANCE_ERROR = re.compile(
    r"is greater th[ae]n tolerance|Buffers have different values"
)

PATTERN_MALI_ERROR = re.compile(r"E mali [\w.]+:(.*)")

BAD_IMAGE_SIGNATURE = "bad_image"


def remove_hex_like(string: str) -> str:
    temp = string
    # Remove hex like chunks of 4 or more.
    temp = re.sub(HEX_LIKE + r"{4,}", "", temp)
    return temp


def clean_up(string: str, remove_numbers: bool = True) -> str:
    temp: str = string
    # Remove numbers.
    if remove_numbers:
        temp = re.sub(r"\d+", "", temp)
    # Replace spaces with _.
    temp = re.sub(r" ", "_", temp)
    # Remove non-word, non-_ characters.
    temp = re.sub(r"[^\w_]", "", temp)
    # Replace multiple _ with _.
    temp = re.sub(r"__+", "_", temp)
    # Strip _
    temp = temp.strip("_")
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


def get_function_signature_from_address(
    module: Path,
    address: str,
    addr2line_mock: Optional[Callable[[Path, str], str]] = None,
) -> Optional[str]:
    # stdout result can be mocked for testing.
    stdout: str
    if addr2line_mock:
        stdout = addr2line_mock(module, address)
    else:
        try:
            address_tool = util.tool_on_path("addr2line")
        except util.ToolNotOnPathError:
            return None
        result = subprocess_util.run(
            [str(address_tool), "-e", str(module), address, "-f", "-C"],
            check_exit_code=False,
            verbose=True,
        )
        if result.returncode != 0:
            return None
        stdout = result.stdout

    lines = stdout.splitlines()
    if not lines:
        return None
    if lines[0].startswith("??"):
        return None
    return lines[0]


def get_signature_from_log_contents(  # pylint: disable=too-many-return-statements, too-many-branches, too-many-statements;
    log_contents: str, addr2line_mock: Optional[Callable[[Path, str], str]] = None,
) -> str:
    # noinspection PyUnusedLocal
    match: Optional[Match[str]]
    # noinspection PyUnusedLocal
    group: Optional[str]

    # Mali error.
    # Find the last match.
    last_match = None
    for match in re.finditer(PATTERN_MALI_ERROR, log_contents):
        # Assign each time to avoid linter warning B007.
        last_match = match
    if last_match:
        group = "mali_" + last_match.group(1)
        group = clean_up(group, remove_numbers=False)
        group = reduce_length(group)
        return group

    # LLVM FATAL ERROR (special override).
    group = basic_match(PATTERN_LLVM_FATAL_ERROR, log_contents)
    if group:
        return group

    # LLVM MACHINE CODE ERROR (special override).
    group = basic_match(PATTERN_LLVM_MACHINE_CODE_ERROR, log_contents)
    if group:
        return group

    # LLVM ERROR DIAGNOSIS: should come before PATTERN_ASSERTION_FAILURE.
    group = basic_match(PATTERN_LLVM_ERROR_DIAGNOSIS, log_contents)
    if group:
        return group

    # AddressSanitizer error.
    match = re.search(PATTERN_ADDRESS_SANITIZER_ERROR, log_contents)
    if match:
        group = match.group(1) + "_" + match.group(2)
        group = clean_up(group)
        group = reduce_length(group)
        return group

    # Mesa NIR validation error.
    if "NIR validation failed" in log_contents:
        group = basic_match(PATTERN_MESA_NIR_VALIDATION_ERROR, log_contents)
        if group:
            return group

    # Mesa SPIR-V parse failure.
    if "SPIR-V parsing FAILED" in log_contents:
        group = basic_match(PATTERN_MESA_SPIRV_PARSE_ERROR, log_contents)
        if group:
            return group

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

    # ABORT message from SwiftShader.
    group = basic_match(PATTERN_SWIFT_SHADER_ABORT, log_contents)
    if group:
        return group

    # WARNING message from SwiftShader.
    group = basic_match(PATTERN_SWIFT_SHADER_WARNING, log_contents)
    if group:
        return group

    match = re.search(PATTERN_AMBER_TOLERANCE_ERROR, log_contents)
    if match:
        return BAD_IMAGE_SIGNATURE

    # Amber error.
    if "0 pass, 1 fail" in log_contents:
        group = basic_match(PATTERN_AMBER_ERROR, log_contents)
        if group:
            return group

    # Cdb stack trace
    cdb_call_site = re.search(
        PATTERN_CDB_CALL_SITE, log_contents
    )  # type: Optional[Match[str]]
    if cdb_call_site:
        site = cdb_call_site.group(1)
        if "!" in site:
            # We probably have symbols, so remove the address and everything after e.g. "+0x111 [file/path @ 123]"
            site = re.sub(rf"\+{HEX_LIKE}+.*", "", site)
            site = clean_up(site)
        else:
            # We don't have symbols so we may as well keep offsets around; don't remove numbers.
            site = clean_up(site, remove_numbers=False)
        site = reduce_length(site)
        return site

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
            break

        # Check for stack line with libc alloc.
        if re.search(r"\n.*#\d+ pc .*libc\.so \(\w?alloc", log_contents):
            # Find the first stack frame without libc.so and replace the log_contents with that frame.
            # We do this because the error is better identified by this line and because out of memory errors
            # often occur at a nondeterministic location within libc.
            for line in lines:
                if (
                    re.search(r" #\d+ pc ", line)
                    and "libc.so" not in line
                    and "operator new" not in line
                ):
                    # Replace the stack frame number so it looks like the 0th frame.
                    line = re.sub(r" #\d+ ", " #00 ", line)
                    log_contents = f"\n{line}\n"
                    break

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
            # Don't remove hex-like chunks, nor numbers because we want to fallback to any hex offsets in this case.
            # group = remove_hex_like(group)
            group = clean_up(group, remove_numbers=False)
            group = reduce_length(group)
            return group

    if "\nBacktrace:\n" in log_contents:
        result = get_signature_from_catchsegv_backtrace(log_contents, addr2line_mock)
        if result:
            return result

    group = basic_match(PATTERN_CATCH_ALL_ERROR, log_contents)
    if group:
        return group

    if "Shader compilation failed" in log_contents:
        return "compile_error"

    if "Failed to link shaders" in log_contents:
        return "link_error"

    if "Calling vkCreateGraphicsPipelines Fail" in log_contents:
        return "pipeline_failure"

    # TODO: Check for Amber fence failure.

    if "Resource deadlock would occur" in log_contents:
        return "Resource_deadlock_would_occur"

    if "pure virtual method called" in log_contents:
        return "pure_virtual_method_called"

    return NO_SIGNATURE


def get_signature_from_catchsegv_backtrace(
    log_contents: str, addr2line_mock: Optional[Callable[[Path, str], str]] = None,
) -> Optional[str]:
    lines = log_contents.splitlines()
    i = 0
    # Skip to just after "Backtrace:".
    while True:
        if i >= len(lines):
            return None
        if lines[i] == "Backtrace:":
            i += 1
            break
        i += 1

    # Find the first stack frame line.
    # It will normally be the first line.
    # It should start with "/".
    # We skip libc stack frames.
    while True:
        if i >= len(lines):
            return None
        if lines[i].startswith("/"):
            # Skip frame if it is libc.
            if "libc.so" in lines[i]:
                i += 1
                continue
            break
        i += 1

    group = basic_match(PATTERN_CATCHSEGV_STACK_FRAME, lines[i])
    if group:
        return group

    result = get_signature_from_catchsegv_frame_address(lines[i], addr2line_mock)
    if result:
        return result

    return None


def get_signature_from_catchsegv_frame_address(
    log_contents: str, addr2line_mock: Optional[Callable[[Path, str], str]] = None,
) -> Optional[str]:
    match = re.search(PATTERN_CATCHSEGV_STACK_FRAME_ADDRESS, log_contents)
    if not match:
        return None
    module = Path(match.group(1))
    address = match.group(2)
    function_signature = None
    if module.exists():
        function_signature = get_function_signature_from_address(
            module, address, addr2line_mock
        )
    if not function_signature or "nvvm" in function_signature:
        # The module does not exist or we could not get any symbols using addr2line.
        # Or: the function name contains "nvvm", which is seen in NVIDIA drivers but
        # leads to a poor signature.
        # As a last resort, we can use the module name + offset as the signature.
        return get_hex_signature_from_frame(module, address)
    function_signature = clean_up(function_signature)
    function_signature = reduce_length(function_signature)
    return function_signature


def get_hex_signature_from_frame(module: Path, address: str) -> str:
    signature = f"{module.name}+{address}"
    signature = clean_up(signature, remove_numbers=False)
    signature = reduce_length(signature)
    return signature


def main() -> None:
    parser = argparse.ArgumentParser(
        description="A tool for extracting a signature from a log file."
    )

    parser.add_argument(
        "log_file", help="The log file from which a signature should be extracted.",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    log_file: Path = Path(parsed_args.log_file)

    log(get_signature_from_log_contents(util.file_read_text(log_file)))


if __name__ == "__main__":
    main()
