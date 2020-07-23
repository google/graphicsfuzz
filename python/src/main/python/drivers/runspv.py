#!/usr/bin/env python3

# Copyright 2018 The GraphicsFuzz Project Authors
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

import argparse
import filecmp
import os
import shutil
import signal
import struct
import subprocess
import time
import platform
import json
import sys
from typing import Optional, Union, List
import io

################################################################################
# Help messages for options that are duplicated elsewhere

FORCE_OPTION_HELP = (
    'Do not wait for the device\'s screen to be on; just continue.  '
    'Only allowed if \'target\' is \'android\'. '
)

LEGACY_OPTION_HELP = 'Render using legacy Vulkan worker.'

SERIAL_OPTION_HELP = (
    'Android device serial number. Only allowed if \'target\' is \'android\'.  '
    'Run "adb devices -l" to list available serial numbers.  '
    'A serial number need not be provided if only one device is connected.  '
    'The serial number will have the form "IP:port" if using adb over TCP.  '
    'See: https://developer.android.com/studio/command-line/adb ')

SKIP_RENDER_OPTION_HELP = 'Compile shaders but do not actually run them.'

SPIRV_OPT_OPTION_HELP = (
    'Enable spirv-opt with these optimization flags.  Multiple arguments should be space-separated.'
    ' E.g. --spirvopt="-O --merge-blocks" ')

TARGET_HELP = 'One of \'host\' (run on host machine) or \'android\' (run on Android device).'

################################################################################
# Constants

HERE = os.path.abspath(__file__)

LOGFILE_NAME = 'vklog.txt'
SSBO_JSON_FILENAME = 'ssbo.json'
TIMEOUT_RUN = 30
NUM_RENDER = 3
BUSY_WAIT_SLEEP_SLOW = 1.0
BUSY_WAIT_SLEEP_FAST = 0.1
AMBER_FENCE_TIMEOUT_MS = 60000
TIMEOUT_SPIRV_OPT_SECONDS = 120

################################################################################
# Common


orig_print = print


# noinspection PyShadowingBuiltins
def print(s):
    orig_print(s, flush=True)


log_to_stdout = True
log_to_file = None  # type: io.TextIOBase


def log(message: str) -> None:
    if log_to_stdout:
        print(message)
    if log_to_file:
        log_to_file.write(message)
        log_to_file.write('\n')
        log_to_file.flush()


def log_stdout_stderr(
    result: Union[
        subprocess.CalledProcessError,
        subprocess.CompletedProcess,
        subprocess.TimeoutExpired,
    ],
) -> None:

    log('STDOUT:')
    log(result.stdout)
    log('')

    log('STDERR:')
    log(result.stderr)
    log('')


def log_returncode(
    result: Union[
        subprocess.CalledProcessError, subprocess.CompletedProcess, subprocess.Popen],
) -> None:
    log('RETURNCODE: ' + str(result.returncode))


def convert_stdout_stderr(
    result: Union[
        subprocess.CalledProcessError, subprocess.CompletedProcess, subprocess.TimeoutExpired]
) -> None:

    if result.stdout is not None:
        result.stdout = result.stdout.decode(encoding='utf-8', errors='ignore')
    if result.stderr is not None:
        result.stderr = result.stderr.decode(encoding='utf-8', errors='ignore')


def subprocess_helper(
    cmd: List[str],
    check=True,
    timeout=None,
    verbose=False
) -> subprocess.CompletedProcess:

    assert cmd[0] is not None and isinstance(cmd[0], str)

    # We capture stdout and stderr by default so we have something to report if the command fails.

    # Note: "encoding=" and "errors=" are Python 3.6.
    # We manually decode to utf-8 instead of using "universal_newlines=" so we have full control.
    # text= is Python 3.6.
    # Do not use shell=True.

    # Note: changes here should be reflected in run_catchsegv()

    try:
        log('Exec' + (' (verbose):' if verbose else ':') + str(cmd))

        result = subprocess.run(
            cmd,
            check=check,
            timeout=timeout,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    except subprocess.TimeoutExpired as ex:
        convert_stdout_stderr(ex)
        # no returncode to log in case of timeout
        log_stdout_stderr(ex)
        raise ex

    except subprocess.CalledProcessError as ex:
        convert_stdout_stderr(ex)
        log_returncode(ex)
        log_stdout_stderr(ex)
        raise ex

    convert_stdout_stderr(result)
    log_returncode(result)

    if verbose:
        log_stdout_stderr(result)

    return result


def run_catchsegv(cmd: List[str], timeout=None, verbose=False) -> str:
    # Subprocess_helper cannot handle the proper killing of other sub-processes
    # spawned by catchsegv, hence the direct Popen call and process group
    # management below.
    status = 'SUCCESS'

    log('Exec' + (' (verbose):' if verbose else ':') + str(cmd))

    proc = subprocess.Popen(
        cmd,
        start_new_session=True,  # this creates a process group for all children
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )

    try:
        outs, errs = proc.communicate(timeout=TIMEOUT_RUN)
        if proc.returncode != 0:
            status = 'CRASH'
        log_returncode(proc)

        if verbose or proc.returncode != 0:
            if outs is not None:
                outs = outs.decode(encoding='utf-8', errors='ignore')
            if errs is not None:
                errs = errs.decode(encoding='utf-8', errors='ignore')
            log('STDOUT:')
            log(outs)
            log('')
            log('STDERR:')
            log(errs)
            log('')

    except subprocess.TimeoutExpired:
        # Kill the whole process group to include all children of catchsegv
        os.killpg(proc.pid, signal.SIGKILL)
        outs, errs = proc.communicate()
        if outs is not None:
            outs = outs.decode(encoding='utf-8', errors='ignore')
        if errs is not None:
            errs = errs.decode(encoding='utf-8', errors='ignore')
        log('STDOUT:')
        log(outs)
        log('')
        log('STDERR:')
        log(errs)
        log('')

        status = 'TIMEOUT'

    return status


def open_helper(file: str, mode: str):
    return open(file, mode, encoding='utf-8', errors='ignore')


def open_bin_helper(file: str, mode: str):
    assert 'b' in mode
    return open(file, mode)


def get_platform():
    host = platform.system()
    if host == 'Linux' or host == 'Windows':
        return host
    elif host == 'Darwin':
        return 'Mac'
    else:
        raise AssertionError('Unsupported platform: {}'.format(host))


def get_bin_dir():
    # graphics-fuzz/python/drivers/
    bin_dir = os.path.dirname(HERE)
    # graphics-fuzz/
    bin_dir = os.path.join(bin_dir, os.path.pardir, os.path.pardir)
    # e.g. graphics-fuzz/bin/Linux
    bin_dir = os.path.join(bin_dir, 'bin', get_platform())
    return bin_dir


BIN_DIR = get_bin_dir()


# A distinguished exception for the case where a tool is not on the path.
class ToolNotOnPathError(Exception):
    pass


def tool_on_path(tool: str) -> str:
    result = shutil.which(tool)
    if result is None:
        raise ToolNotOnPathError('Could not find {} on PATH. Please add to PATH.'.format(tool))
    return result


def glslang_path():
    glslang = os.path.join(BIN_DIR, 'glslangValidator')
    if os.path.isfile(glslang):
        return glslang
    return tool_on_path('glslangValidator')


def spirvas_path():
    spirvas = os.path.join(BIN_DIR, 'spirv-as')
    if os.path.isfile(spirvas):
        return spirvas
    return tool_on_path('spirv-as')


def spirvdis_path():
    spirvas = os.path.join(BIN_DIR, 'spirv-dis')
    if os.path.isfile(spirvas):
        return spirvas
    return tool_on_path('spirv-dis')


def spirvopt_path():
    spirvopt = os.path.join(BIN_DIR, 'spirv-opt')
    if os.path.isfile(spirvopt):
        return spirvopt
    return tool_on_path('spirv-opt')


def maybe_add_catchsegv(cmd: List[str]) -> bool:
    try:
        cmd.append(tool_on_path('catchsegv'))
        return True
    except ToolNotOnPathError:
        # Didn't find catchsegv on path; that's OK
        pass
    return False


def adb_path():
    if 'ANDROID_HOME' in os.environ:
        platform_tools_path = os.path.join(os.environ['ANDROID_HOME'], 'platform-tools')
        adb = shutil.which('adb', path=platform_tools_path)
        if adb is not None:
            return adb
    return tool_on_path('adb')


def remove_end(str_in: str, str_end: str):
    assert str_in.endswith(str_end), 'Expected {} to end with {}'.format(str_in, str_end)
    return str_in[:-len(str_end)]


def run_spirv_opt(
    spv_file: str,
    spirv_opt_args: List[str],
    output: str
) -> None:
    """
    Optimizes a SPIR-V file.

    :param spv_file: name of SPIR-V file to be optimized
    :param spirv_opt_args: arguments to be passed to spirv-opt
    :param output: name of file into which optimized SPIR-V will be written
    :return: None
    """

    log('Running optimizer.')

    cmd = [spirvopt_path(), spv_file, '-o', output]
    cmd += spirv_opt_args

    subprocess_helper(cmd, timeout=TIMEOUT_SPIRV_OPT_SECONDS)


def convert_glsl_to_spv(
    glsl_shader: str,
    output: str
) -> None:

    """
    Runs glslangValidator on a GLSL shader to convert it to SPIR-V.

    :param glsl_shader: name of GLSL file on which to run glslangValidator
    :param output: name of file into which generated SPIR-V will be written
    :return: None
    """

    log('Running glslangValidator.')

    cmd = [
        glslang_path(),
        '-V', glsl_shader,
        '-o', output
    ]
    subprocess_helper(cmd, timeout=TIMEOUT_RUN)


def filename_extension_suggests_glsl(file: str):
    return (file.endswith('.frag')
            or file.endswith('.vert')
            or file.endswith('.comp'))


def prepare_shader(
    output_dir: str,
    shader: Optional[str],
    spirv_opt_args: Optional[List[str]],
):
    """
    Translates a shader to binary SPIR-V, and optionally optimizes it.

    None -> None
    shader.frag -> shader.frag.[opt].spv
    shader.vert -> shader.vert.[opt].spv
    shader.comp -> shader.comp.[opt].spv
    shader.frag.asm -> shader.frag.[opt].spv
    shader.vert.asm -> shader.vert.[opt].spv
    shader.comp.asm -> shader.comp.[opt].spv
    shader.spv -> shader.[opt].spv

    :param spirv_opt_args: spirv-opt arguments, if the shader should be optimized
    :param output_dir: output directory
    :param shader: e.g. shader.frag, shader.vert, shader.frag.asm
    :return: the resulting .spv file
    """

    if shader is None:
        return None

    assert os.path.isfile(shader)

    shader_basename = os.path.basename(shader)

    if filename_extension_suggests_glsl(shader_basename):
        result = os.path.join(output_dir, shader_basename + '.spv')
        cmd = [
            glslang_path(),
            '-V', shader,
            '-o', result
        ]
        subprocess_helper(cmd, timeout=TIMEOUT_RUN)
    elif shader_basename.endswith('.asm'):
        result = os.path.join(output_dir, remove_end(shader_basename, '.asm') + '.spv')
        cmd = [
            spirvas_path(),
            shader,
            '-o', result
        ]
        subprocess_helper(cmd, timeout=TIMEOUT_RUN)
    elif shader_basename.endswith('.spv'):
        result = os.path.join(output_dir, shader_basename)
        try:
            shutil.copy(shader, result)
        except shutil.SameFileError:
            pass
    else:
        assert False, 'unexpected shader extension: {}'.format(shader)

    assert len(result) > 0

    if spirv_opt_args:
        optimized_spv = result + '.opt.spv'
        run_spirv_opt(result, spirv_opt_args, optimized_spv)
        result = optimized_spv

    return result


################################################################################
# Android general


ANDROID_SDCARD_GRAPHICSFUZZ_DIR = '/sdcard/graphicsfuzz'
# Amber cannot be made executable under /sdcard/, hence we work under /data/local/tmp
ANDROID_DEVICE_DIR = '/data/local/tmp'
ANDROID_DEVICE_GRAPHICSFUZZ_DIR = ANDROID_DEVICE_DIR + '/graphicsfuzz'
ANDROID_AMBER_NDK = 'amber_ndk'
ANDROID_DEVICE_AMBER = ANDROID_DEVICE_DIR + '/' + ANDROID_AMBER_NDK
ANDROID_LEGACY_APP = 'com.graphicsfuzz.vkworker'
TIMEOUT_APP = 30


def adb_helper(
    adb_args: List[str],
    check: bool,
    verbose: bool = False
) -> subprocess.CompletedProcess:

    adb_cmd = [adb_path()] + adb_args

    return subprocess_helper(
        adb_cmd,
        check=check,
        timeout=TIMEOUT_RUN,
        verbose=verbose
    )


def adb_check(
    adb_args: List[str],
    verbose: bool = False
) -> subprocess.CompletedProcess:

    return adb_helper(adb_args, check=True, verbose=verbose)


def adb_can_fail(
    adb_args: List[str],
    verbose: bool = False
) -> subprocess.CompletedProcess:

    return adb_helper(adb_args, check=False, verbose=verbose)


def stay_awake_warning():
    res = adb_check(['shell', 'settings get global stay_on_while_plugged_in'])
    if str(res.stdout).strip() == '0':
        print('\nWARNING: please enable "Stay Awake" from developer settings\n')


def is_screen_off_or_locked():
    """
    :return: True: the screen is off or locked. False: unknown.
    """
    res = adb_can_fail(['shell', 'dumpsys nfc'])
    if res.returncode != 0:
        return False

    stdout = str(res.stdout)
    # You will often find "mScreenState=OFF_LOCKED", but this catches OFF too, which is good.
    if stdout.find('mScreenState=OFF') >= 0:
        return True
    if stdout.find('mScreenState=ON_LOCKED') >= 0:
        return True

    return False


def prepare_device(
    wait_for_screen: bool,
    using_legacy_worker: bool,
) -> None:
    adb_check(['logcat', '-c'])

    if using_legacy_worker:
        # If the legacy worker is being used, give it the right permissions, stop it if already
        # running, and get its working directory ready.
        adb_check([
            'shell',
            'pm grant com.graphicsfuzz.vkworker android.permission.READ_EXTERNAL_STORAGE'
        ])
        adb_check([
            'shell',
            'pm grant com.graphicsfuzz.vkworker android.permission.WRITE_EXTERNAL_STORAGE'
        ])
        adb_can_fail([
            'shell',
            'am force-stop ' + ANDROID_LEGACY_APP
        ])
        adb_can_fail([
            'shell',
            'rm -rf ' + ANDROID_SDCARD_GRAPHICSFUZZ_DIR
        ])
        adb_check([
            'shell',
            'mkdir -p ' + ANDROID_SDCARD_GRAPHICSFUZZ_DIR
        ])
    else:
        res = adb_can_fail(['shell', 'test -e ' + ANDROID_DEVICE_AMBER])
        if res.returncode != 0:
            raise AssertionError('Failed to find amber on device at: ' + ANDROID_DEVICE_AMBER)
        adb_can_fail(['shell', 'rm -rf ' + ANDROID_DEVICE_GRAPHICSFUZZ_DIR])
        adb_check(['shell', 'mkdir -p ' + ANDROID_DEVICE_GRAPHICSFUZZ_DIR])

    if wait_for_screen:
        stay_awake_warning()
        # We cannot reliably know if the screen is on, but this function definitely knows if it is
        # off or locked. So we wait here while we definitely know there is an issue.
        while is_screen_off_or_locked():
            print('\nWARNING: The screen appears to be off or locked. Please unlock the device and '
                  'ensure "Stay Awake" is enabled in developer settings.\n')
            time.sleep(BUSY_WAIT_SLEEP_SLOW)


################################################################################
# Legacy worker: image test


def run_image_android_legacy(
    vert_original: str,
    frag_original: str,
    json_file: str,
    output_dir: str,
    force: bool,
    skip_render: bool,
    spirv_opt_args: Optional[List[str]],
) -> None:

    assert os.path.isfile(vert_original)
    assert os.path.isfile(frag_original)
    assert os.path.isfile(json_file)

    vert = prepare_shader(output_dir, vert_original, spirv_opt_args)
    frag = prepare_shader(output_dir, frag_original, spirv_opt_args)
    status_file = os.path.join(output_dir, 'STATUS')

    coherence_before = os.path.join(output_dir, 'coherence_before.png')
    coherence_after = os.path.join(output_dir, 'coherence_after.png')
    ref_image = os.path.join(output_dir, 'image_0.png')
    next_image_template = os.path.join(output_dir, 'image_{}.png')

    nondet_0 = os.path.join(output_dir, 'nondet0.png')
    nondet_1 = os.path.join(output_dir, 'nondet1.png')

    prepare_device(not force, True)

    adb_check(['push', vert, ANDROID_SDCARD_GRAPHICSFUZZ_DIR + '/test.vert.spv'])
    adb_check(['push', frag, ANDROID_SDCARD_GRAPHICSFUZZ_DIR + '/test.frag.spv'])
    adb_check(['push', json_file, ANDROID_SDCARD_GRAPHICSFUZZ_DIR + '/test.json'])

    # Build app args.
    flags = '--num-render {}'.format(NUM_RENDER)
    if skip_render:
        flags += ' --skip-render'

    # Pass app args as Intent extra.
    # Quote app args. No need for double quotes because we are not using shell=True.

    shell_cmd = (
        'am start'
        ' -n ' + ANDROID_LEGACY_APP + '/android.app.NativeActivity -e gfz "{}"'.format(flags)
    )

    adb_check(['shell', shell_cmd])

    # Busy wait
    deadline = time.time() + TIMEOUT_APP

    status = 'UNEXPECTED_ERROR'

    # If the app never starts, status remains as UNEXPECTED_ERROR.
    # Once the app starts, status becomes TIMEOUT.
    # If we reach the end of the loop below, we break and the status is updated just before.

    while time.time() < deadline:
        time.sleep(BUSY_WAIT_SLEEP_SLOW)

        # Don't pass here until app has started.
        if status == 'UNEXPECTED_ERROR':
            if adb_can_fail([
                'shell',
                'test -f ' + ANDROID_SDCARD_GRAPHICSFUZZ_DIR + '/STARTED'
            ]).returncode != 0:
                continue
            status = 'TIMEOUT'

        assert status == 'TIMEOUT'

        # DONE file indicates app is done.
        if adb_can_fail([
            'shell',
            'test -f ' + ANDROID_SDCARD_GRAPHICSFUZZ_DIR + '/DONE']
        ).returncode == 0:
            status = 'SUCCESS'
            break

        # Otherwise, keep looping/waiting while the app is still running.
        # No need to quote >/dev/null. We are passing this all to the Android shell via one
        # argument; we are not using shell=True.
        if adb_can_fail([
            'shell',
            'pidof ' + ANDROID_LEGACY_APP + ' > /dev/null'
        ]).returncode == 0:
            continue

        # The app has crashed. Check for DONE file one more time.
        if adb_can_fail([
            'shell',
            'test -f ' + ANDROID_SDCARD_GRAPHICSFUZZ_DIR + '/DONE'
        ]).returncode == 0:
            status = 'SUCCESS'
            break

        # App has terminated and there is no DONE file; this definitely looks like a crash.
        status = 'CRASH'
        break

    # Grab log:
    adb_check(['logcat', '-d'], verbose=True)

    # retrieve all files: the "/." is a special syntax to pull all files from the graphicsfuzz
    # directory without creating "output_dir/graphicsfuzz"
    adb_check(['pull', ANDROID_SDCARD_GRAPHICSFUZZ_DIR + '/.', output_dir])

    # Check coherence:
    if status == 'SUCCESS':
        if os.path.isfile(coherence_before) and os.path.isfile(coherence_after):
            if not filecmp.cmp(coherence_before, coherence_after, shallow=False):
                status = 'COHERENCE_ERROR'

    # Check nondet:
    if status == 'SUCCESS':
        if os.path.isfile(ref_image):
            # If reference image is here then report nondet if any image is different or missing.
            for i in range(1, NUM_RENDER):
                next_image = next_image_template.format(i)
                if not os.path.isfile(next_image):
                    status = 'UNEXPECTED_ERROR'
                    log('\n Not all images were produced? Missing image: {}\n'.format(i))
                elif not filecmp.cmp(ref_image, next_image, shallow=False):
                    status = 'NONDET'
                    shutil.copy(ref_image, nondet_0)
                    shutil.copy(next_image, nondet_1)

    log('\nSTATUS ' + status + '\n')
    if status == 'UNEXPECTED_ERROR':
        log('\n App did not start?\n')

    with open_helper(status_file, 'w') as f:
        f.write(status)

    if status != 'SUCCESS':
        # Something went wrong. Make sure we stop the app.
        adb_can_fail([
            'shell',
            'am force-stop ' + ANDROID_LEGACY_APP
        ])


def dump_info_android_legacy(wait_for_screen):
    prepare_device(wait_for_screen, True)

    info_file = ANDROID_SDCARD_GRAPHICSFUZZ_DIR + '/worker_info.json'

    adb_check([
        'shell',
        'am start -n ' + ANDROID_LEGACY_APP + '/android.app.NativeActivity -e gfz --info'
    ])

    # Busy wait for the app to write the gpu info.
    deadline = time.time() + TIMEOUT_APP
    while time.time() < deadline:
        if adb_can_fail([
            'shell',
            'test -f ' + info_file
        ]).returncode == 0:
            break
        time.sleep(BUSY_WAIT_SLEEP_FAST)

    if adb_can_fail([
        'shell',
        'test -f ' + info_file
    ]).returncode == 0:
        adb_check(['pull', info_file])
    else:
        log('Warning: failed to obtain worker information')
    adb_can_fail([
        'shell',
        'am force-stop ' + ANDROID_LEGACY_APP
    ])


def run_image_host_legacy(
    vert_original: str,
    frag_original: str,
    json_file: str,
    output_dir: str,
    skip_render: bool,
    spirv_opt_args: Optional[List[str]],
) -> None:

    assert os.path.isfile(vert_original)
    assert os.path.isfile(frag_original)
    assert os.path.isfile(json_file)

    vert = prepare_shader(output_dir, vert_original, spirv_opt_args)
    frag = prepare_shader(output_dir, frag_original, spirv_opt_args)

    status_file = os.path.join(output_dir, 'STATUS')

    cmd = [
        tool_on_path('vkworker'),
        vert,
        frag,
        json_file,
        '-png_template={}'.format(os.path.join(output_dir, 'image')),
        '-coherence_before={}'.format(os.path.join(output_dir, 'coherence_before.png')),
        '-coherence_after={}'.format(os.path.join(output_dir, 'coherence_after.png')),
        '-skip_render=' + ('true' if skip_render else 'false'),
    ]
    status = 'SUCCESS'
    try:
        subprocess_helper(
            cmd,
            timeout=TIMEOUT_RUN,
            verbose=True
        )
    except subprocess.TimeoutExpired:
        status = 'TIMEOUT'
    except subprocess.CalledProcessError:
        status = 'CRASH'

    log('\nSTATUS ' + status + '\n')

    with open_helper(status_file, 'w') as f:
        f.write(status)


def dump_info_host_legacy():
    cmd = [tool_on_path('vkworker'), '--info']
    subprocess_helper(cmd, timeout=TIMEOUT_RUN)


def run_image_legacy(
    vert_original: str,
    frag_original: str,
    args: argparse.Namespace,
    spirv_opt_args: Optional[List[str]],
) -> None:
    if args.target == 'android':
        run_image_android_legacy(
            vert_original=vert_original,
            frag_original=frag_original,
            json_file=args.json,
            output_dir=args.output_dir,
            force=args.force,
            skip_render=args.skip_render,
            spirv_opt_args=spirv_opt_args,
        )
        return

    assert args.target == 'host'
    run_image_host_legacy(
        vert_original=vert_original,
        frag_original=frag_original,
        json_file=args.json,
        output_dir=args.output_dir,
        skip_render=args.skip_render,
        spirv_opt_args=spirv_opt_args,
    )


################################################################################
# Amber worker: image test


def spv_get_disassembly(shader_filename):
    cmd = [spirvdis_path(), shader_filename, '--raw-id']
    return subprocess_helper(cmd).stdout


def get_shader_as_comment(shader: str) -> str:
    with open_helper(shader, 'r') as f:
        lines = f.readlines()

    lines = [('# ' + line).rstrip() for line in lines]
    return '\n'.join(lines)


def get_spirv_opt_args_comment(spirv_args: Optional[List[str]]) -> str:
    if spirv_args:
        result = '# spirv-opt was used with the following arguments:\n'
        args = ['# \'{}\''.format(arg) for arg in spirv_args]
        result += '\n'.join(args)
        result += '\n\n'
        return result
    else:
        return ''

def get_header_comment_original_source(
    vert_original: str,
    frag_original: str,
    comp_original: str,
    spirv_args: str
):
    has_frag_glsl = frag_original and filename_extension_suggests_glsl(frag_original)
    has_vert_glsl = vert_original and filename_extension_suggests_glsl(vert_original)
    has_comp_glsl = comp_original and filename_extension_suggests_glsl(comp_original)

    result = get_spirv_opt_args_comment(spirv_args)

    if has_frag_glsl or has_vert_glsl or has_comp_glsl:
        result += '# Derived from the following GLSL.\n\n'

    if has_vert_glsl:
        result += '# Vertex shader GLSL:\n'
        result += get_shader_as_comment(vert_original)
        result += '\n\n'

    if has_frag_glsl:
        result += '# Fragment shader GLSL:\n'
        result += get_shader_as_comment(frag_original)
        result += '\n\n'

    if has_comp_glsl:
        result += '# Compute shader GLSL:\n'
        result += get_shader_as_comment(comp_original)
        result += '\n\n'

    return result


def get_header_comment_original_source_image(
    vert_original: str,
    frag_original: str,
    spirv_args: str
):
    return get_header_comment_original_source(vert_original, frag_original, None, spirv_args)


def get_header_comment_original_source_comp(
    comp_original: str,
    spirv_args: str
):
    return get_header_comment_original_source(None, None, comp_original, spirv_args)


def amberscript_uniform_buffer_decl(uniform_json):
    '''
    Returns the string representing AmberScript version of uniform declarations.
    Skips the special '$compute' key, if present.

    {
      "myuniform": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "binding": 3
      },
      "$compute": { ... will be ignored ... }
    }

    becomes:

    # myuniform
    BUFFER myuniform DATA_TYPE float DATA
      42.0
    END

    '''

    UNIFORM_TYPE = {
        'glUniform1i': 'int32',
        'glUniform2i': 'vec2<int32>',
        'glUniform3i': 'vec3<int32>',
        'glUniform4i': 'vec4<int32>',
        'glUniform1f': 'float',
        'glUniform2f': 'vec2<float>',
        'glUniform3f': 'vec3<float>',
        'glUniform4f': 'vec4<float>',
        'glUniformMatrix2fv': 'mat2x2<float>',
        'glUniformMatrix2x3fv': 'mat2x3<float>',
        'glUniformMatrix2x4fv': 'mat2x4<float>',
        'glUniformMatrix3x2fv': 'mat3x2<float>',
        'glUniformMatrix3fv': 'mat3x3<float>',
        'glUniformMatrix3x4fv': 'mat3x4<float>',
        'glUniformMatrix4x2fv': 'mat4x2<float>',
        'glUniformMatrix4x3fv': 'mat4x3<float>',
        'glUniformMatrix4fv': 'mat4x4<float>',
    }

    result = ''
    with open(uniform_json, 'r') as f:
        j = json.load(f)
    for name, entry in j.items():

        if name == '$compute':
            continue

        func = entry['func']
        if func not in UNIFORM_TYPE.keys():
            raise ValueError('Error: unknown uniform type for function: ' + func)
        uniform_type = UNIFORM_TYPE[func]

        result += '# ' + name + '\n'
        result += 'BUFFER ' + name + ' DATA_TYPE ' + uniform_type + ' DATA\n'
        for arg in entry['args']:
            result += ' {}'.format(arg)
        result += '\n'
        result += 'END\n'

    return result


def amberscript_uniform_buffer_bind(uniform_json):
    '''
    Returns the string representing AmberScript version of uniform binding.
    Skips the special '$compute' key, if present.

    {
      "myuniform": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "binding": 3
      },
      "$compute": { ... will be ignored ... }
    }

    becomes:

    BIND BUFFER myuniform AS uniform DESCRIPTOR_SET 0 BINDING 3
    '''

    result = ''
    with open(uniform_json, 'r') as f:
        j = json.load(f)
    for name, entry in j.items():

        if name == '$compute':
            continue

        result += 'BIND BUFFER ' + name + ' AS uniform '
        result += 'DESCRIPTOR_SET 0 BINDING {}\n'.format(entry['binding'])

    return result


def amberscriptify_image(
    vert,
    frag,
    uniform_json,
    vert_original,
    frag_original,
    spirv_args
):
    '''
    Generates AmberScript representation of an image test
    '''

    has_frag_glsl = frag_original and filename_extension_suggests_glsl(frag_original)
    has_vert_glsl = vert_original and filename_extension_suggests_glsl(vert_original)

    result = '#!amber\n'
    result += '# Generated AmberScript for a bug found by GraphicsFuzz\n\n'

    result += get_header_comment_original_source_image(vert_original, frag_original, spirv_args)

    result += 'SET ENGINE_DATA fence_timeout_ms ' + str(AMBER_FENCE_TIMEOUT_MS) + '\n\n'

    if vert:
        result += 'SHADER vertex gfz_vert SPIRV-ASM\n'
        result += spv_get_disassembly(vert)
        result += 'END\n\n'
    else:
        result += 'SHADER vertex gfz_vert PASSTHROUGH\n\n'

    result += 'SHADER fragment gfz_frag SPIRV-ASM\n'
    result += spv_get_disassembly(frag)
    result += 'END\n\n'

    # This buffer MUST be named framebuffer to be able to retrieve the image
    # Format MUST be B8G8R8A8_UNORM (other options may become available once
    # Amber supports more formats for image extraction)
    result += 'BUFFER framebuffer FORMAT B8G8R8A8_UNORM\n'
    result += amberscript_uniform_buffer_decl(uniform_json)
    result += '\n'

    result += 'PIPELINE graphics gfz_pipeline\n'
    result += '  ATTACH gfz_vert\n'
    result += '  ATTACH gfz_frag\n'
    result += '  FRAMEBUFFER_SIZE 256 256\n'
    result += '  BIND BUFFER framebuffer AS color LOCATION 0\n'
    result += amberscript_uniform_buffer_bind(uniform_json)
    result += 'END\n\n'

    result += 'CLEAR_COLOR gfz_pipeline 0 0 0 255\n'
    result += 'CLEAR gfz_pipeline\n'
    result += 'RUN gfz_pipeline DRAW_RECT POS 0 0 SIZE 256 256\n'

    return result


def run_image_amber(
    vert_original: str,
    frag_original: str,
    json_file: str,
    output_dir: str,
    force: bool,
    is_android: bool,
    skip_render: bool,
    spirv_opt_args: Optional[List[str]]
):
    # The vertex shader is optional; passthrough will be used if it is not present
    assert not vert_original or os.path.isfile(vert_original)
    assert os.path.isfile(frag_original)
    assert os.path.isfile(json_file)

    frag = prepare_shader(output_dir, frag_original, spirv_opt_args)
    vert = prepare_shader(output_dir, vert_original, spirv_opt_args) if vert_original else None

    status_file = os.path.join(output_dir, 'STATUS')
    png_image = os.path.join(output_dir, 'image_0.png')

    device_image = ANDROID_DEVICE_GRAPHICSFUZZ_DIR + '/image_0.png'

    shader_test_file = os.path.join(output_dir, 'tmp_shader_test.amber')
    with open_helper(shader_test_file, 'w') as f:
        f.write(amberscriptify_image(
            vert,
            frag,
            json_file,
            vert_original,
            frag_original,
            spirv_opt_args,
        ))

    if is_android:
        prepare_device(force, using_legacy_worker=False)

        adb_check([
            'push',
            shader_test_file,
            ANDROID_DEVICE_GRAPHICSFUZZ_DIR
        ])

        # If the output file exists at this stage, something has gone very wrong.
        adb_check([
            'shell',
            'test ! -e ' + device_image
        ])

        if skip_render:
            # -ps tells amber to stop after graphics pipeline creation
            flags = ' -ps '
        else:
            # -i tells amber to dump the framebuffer
            flags = ' -i ' + device_image

        # Call amber.
        # Note the use of '/' rather than 'os.sep' in the command that will run under Android.
        cmd = [
            'shell',
            # The following is a single string:
            'cd ' + ANDROID_DEVICE_DIR + ' && '
            './' + ANDROID_AMBER_NDK
            + flags
            + ' -d ' + ANDROID_DEVICE_GRAPHICSFUZZ_DIR + '/' + os.path.basename(shader_test_file)
        ]

        adb_check(['logcat', '-c'])

        status = 'UNEXPECTED_ERROR'

        try:
            result = adb_can_fail(cmd, verbose=True)
        except subprocess.TimeoutExpired:
            result = None
            status = 'TIMEOUT'

        if status != 'TIMEOUT':
            if result.returncode != 0:
                status = 'CRASH'
            elif skip_render:
                status = 'SUCCESS'
            else:
                # It is a success only if we can retrieve the output image
                if adb_can_fail([
                    'shell',
                    'test -f' + device_image
                ]).returncode == 0:
                    status = 'SUCCESS'
                    adb_check([
                        'pull',
                        device_image,
                        png_image
                    ])

        # Grab log:
        adb_check(['logcat', '-d'], verbose=True)

    else:
        cmd = []
        added_catchsegv = maybe_add_catchsegv(cmd)
        cmd.append(tool_on_path('amber'))
        if skip_render:
            # -ps tells amber to stop after graphics pipeline creation
            cmd.append('-ps')
        else:
            # -i tells amber to dump the framebuffer
            cmd.append('-i')
            cmd.append(png_image)
        cmd.append(shader_test_file)
        status = 'SUCCESS'

        if added_catchsegv:
            status = run_catchsegv(cmd, timeout=TIMEOUT_RUN, verbose=True)
        else:
            try:
                subprocess_helper(cmd, timeout=TIMEOUT_RUN, verbose=True)
            except subprocess.TimeoutExpired:
                status = 'TIMEOUT'
            except subprocess.CalledProcessError:
                status = 'CRASH'

        if status == 'SUCCESS':
            assert skip_render or os.path.isfile(png_image)

    log('\nSTATUS ' + status + '\n')

    with open_helper(status_file, 'w') as f:
        f.write(status)


################################################################################
# Amber worker: compute test

def amber_check_buffer_single_type(json_filename):
    with open_helper(json_filename, 'r') as f:
        j = json.load(f)

    # Amber only supports one type per buffer, check this limitation.
    field_type = None
    for field_info in j['$compute']['buffer']['fields']:
        if not field_type:
            field_type = field_info['type']
        elif field_type != field_info['type']:
            raise ValueError('Amber only supports one type per buffer')


def amberscript_comp_buff_decl(comp_json):
    """
    Returns the string representing AmberScript declaration of buffers for a
    compute shader test.

      {
        "myuniform": {
          "func": "glUniform1f",
          "args": [ 42.0 ],
          "binding": 3
        },

        "$compute": {
          "num_groups": [12, 13, 14];
          "buffer": {
            "binding": 123,
            "fields":
            [
              { "type": "int", "data": [ 0 ] },
              { "type": "int", "data": [ 1, 2 ] },
            ]
          }
        }

      }

    becomes:

      # myuniform
      BUFFER myuniform DATA_TYPE float DATA
        42.0
      END

      BUFFER gfz_ssbo DATA_TYPE int DATA
        0 1 2
      END
    """

    SSBO_TYPES = {
        'int': 'int32',
        'ivec2': 'vec2<int32>',
        'ivec3': 'vec3<int32>',
        'ivec4': 'vec4<int32>',
        'uint': 'uint32',
        'float': 'float',
        'vec2': 'vec2<float>',
        'vec3': 'vec3<float>',
        'vec4': 'vec4<float>',
    }

    # regular uniforms
    result = amberscript_uniform_buffer_decl(comp_json)

    with open_helper(comp_json, 'r') as f:
        j = json.load(f)

    assert '$compute' in j.keys(), 'Cannot find "$compute" key in JSON file'
    j = j['$compute']

    binding = j['buffer']['binding']
    offset = 0

    # A single type for all fields is assumed here
    assert len(j['buffer']['fields']) > 0, 'Compute shader test with empty SSBO'
    json_datum_type = j['buffer']['fields'][0]['type']
    if json_datum_type not in SSBO_TYPES.keys():
        raise ValueError('Unsupported SSBO datum type: ' + json_datum_type)
    datum_type = SSBO_TYPES[json_datum_type]

    result += 'BUFFER gfz_ssbo DATA_TYPE ' + datum_type + ' DATA\n'
    for field_info in j['buffer']['fields']:
        for datum in field_info['data']:
            result += ' ' + str(datum)
    result += '\n'
    result += 'END\n\n'

    return result


def amberscript_comp_buff_bind(comp_json):
    """
    Returns the string representing AmberScript binding of buffers for a
    compute shader test.

      {
        "myuniform": {
          "func": "glUniform1f",
          "args": [ 42.0 ],
          "binding": 3
        },

        "$compute": {
          "num_groups": [12, 13, 14];
          "buffer": {
            "binding": 123,
            "fields":
            [
              { "type": "int", "data": [ 0 ] },
              { "type": "int", "data": [ 1, 2 ] },
            ]
          }
        }

      }

    becomes:

      BIND BUFFER myuniform AS uniform DESCRIPTOR_SET 0 BINDING 3
      BIND BUFFER gfz_ssbo AS storage DESCRIPTOR_SET 0 BINDING 123
    """

    # regular uniforms
    result = amberscript_uniform_buffer_bind(comp_json)

    result += 'BIND BUFFER gfz_ssbo AS storage DESCRIPTOR_SET 0 BINDING ' + str(get_ssbo_binding(comp_json)) + '\n\n'

    return result


def amberscriptify_comp(
    comp_spv: str,
    comp_json: str,
    comp_original: Optional[str],
    spirv_args: Optional[List[str]]
):
    """
    Generates an AmberScript representation of a compute test
    """

    has_comp_glsl = comp_original and filename_extension_suggests_glsl(comp_original)

    result = '#!amber\n'
    result += '# Generated AmberScript for a bug found by GraphicsFuzz\n\n'

    result += get_header_comment_original_source_comp(comp_original, spirv_args)

    result += 'SET ENGINE_DATA fence_timeout_ms ' + str(AMBER_FENCE_TIMEOUT_MS) + '\n\n'

    result += 'SHADER compute gfz_comp SPIRV-ASM\n'
    result += spv_get_disassembly(comp_spv)
    result += 'END\n\n'

    result += amberscript_comp_buff_decl(comp_json)

    result += 'PIPELINE compute gfz_pipeline\n'
    result += '  ATTACH gfz_comp\n'
    result += amberscript_comp_buff_bind(comp_json)
    result += 'END\n\n'

    result += 'RUN gfz_pipeline'
    with open_helper(comp_json, 'r') as f:
        j = json.load(f)
        num_groups = j['$compute']['num_groups']
        for dimension in num_groups:
            result += ' ' + str(dimension)
    result += '\n\n'

    return result


def ssbo_text_to_json(ssbo_text_file, ssbo_json_file, comp_json):
    """
    Reads the ssbo_text_file and extracts its contents to a json file.
    """

    with open_helper(ssbo_text_file, 'r') as f:
        values = f.read().split()
    with open_helper(comp_json, 'r') as f:
        j = json.load(f)['$compute']

    assert values[0] == str(j['buffer']['binding'])

    byte_pointer = 1

    result = []

    for field_info in j['buffer']['fields']:
        result_for_field = []
        for counter in range(0, len(field_info['data'])):
            hex_val = ""
            for byte in range(0, 4):
                hex_val += values[byte_pointer]
                byte_pointer += 1
            if field_info['type'] in ['bool', 'int', 'uint']:
                result_for_field.append(
                    int.from_bytes(bytearray.fromhex(hex_val), byteorder='little'))
            elif field_info['type'] in ['float', 'vec2', 'vec3', 'vec4']:
                result_for_field.append(struct.unpack('f', bytearray.fromhex(hex_val))[0])
            else:
                raise Exception('Do not know how to handle type "' + field_info['type'] + '"')
        result.append(result_for_field)

    ssbo_json_obj = {'ssbo': result}

    with open_helper(ssbo_json_file, 'w') as f:
        f.write(json.dumps(ssbo_json_obj))


def get_ssbo_binding(comp_json):
    with open_helper(comp_json, 'r') as f:
        j = json.load(f)
    binding = j['$compute']['buffer']['binding']
    return binding


def run_compute_amber(
    comp_original: str,
    json_file: str,
    output_dir: str,
    force: bool,
    is_android: bool,
    skip_render: bool,
    spirv_opt_args: Optional[List[str]]
) -> None:

    assert os.path.isfile(comp_original)
    assert os.path.isfile(json_file)
    amber_check_buffer_single_type(json_file)

    ssbo_output = os.path.join(output_dir, 'ssbo')
    ssbo_json = os.path.join(output_dir, SSBO_JSON_FILENAME)
    status_file = os.path.join(output_dir, 'STATUS')

    comp = prepare_shader(output_dir, comp_original, spirv_opt_args)

    shader_test_file = os.path.join(output_dir, 'tmp_shader_test.amber')
    with open_helper(shader_test_file, 'w') as f:
        f.write(amberscriptify_comp(
            comp,
            json_file,
            comp_original,
            spirv_opt_args,
        ))

    # FIXME: in case of multiple SSBOs, we should pass the binding of the ones to be dumped
    ssbo_binding = str(get_ssbo_binding(json_file))

    if is_android:
        prepare_device(force, False)

        # Prepare files on device.
        adb_check([
            'push',
            shader_test_file,
            ANDROID_DEVICE_GRAPHICSFUZZ_DIR,
        ])

        # If the output file exists at this stage, something has gone very wrong.
        device_ssbo = ANDROID_DEVICE_GRAPHICSFUZZ_DIR + '/ssbo'
        adb_check([
            'shell',
            'test ! -e ' + device_ssbo
        ])

        flags = ' -d '
        if skip_render:
            # -ps tells amber to stop after graphics pipeline creation
            flags += '-ps '
        else:
            flags += '-b ' + device_ssbo + ' -B ' + ssbo_binding + ' '

        # Call amber.
        # Note the use of '/' rather than 'os.sep' in the command that will run under Android.
        cmd = [
            'shell',
            # The following is a single string:
            'cd ' + ANDROID_DEVICE_DIR + ' && '
            './' + ANDROID_AMBER_NDK
            + flags
            + ANDROID_DEVICE_GRAPHICSFUZZ_DIR + '/' + os.path.basename(shader_test_file)
        ]

        adb_check(['logcat', '-c'])

        status = 'UNEXPECTED_ERROR'

        try:
            result = adb_can_fail(cmd, verbose=True)
        except subprocess.TimeoutExpired:
            result = None
            status = 'TIMEOUT'

        if status != 'TIMEOUT':
            if result.returncode != 0:
                status = 'CRASH'
            elif skip_render:
                status = 'SUCCESS'
            else:
                # It is a success only if we can retrieve the output ssbo
                if adb_can_fail([
                    'shell',
                    'test -f' + device_ssbo
                ]).returncode == 0:
                    status = 'SUCCESS'
                    adb_check([
                        'pull',
                        device_ssbo,
                        ssbo_output
                    ])

        # Grab logcat:
        adb_check(['logcat', '-d'], verbose=True)

    else:
        cmd = []
        added_catchsegv = maybe_add_catchsegv(cmd)
        cmd.append(tool_on_path('amber'))
        if skip_render:
            cmd.append('-ps')
        else:
            cmd.append('-b')
            cmd.append(ssbo_output)
            cmd.append('-B')
            cmd.append(ssbo_binding)
        cmd.append(shader_test_file)

        status = 'SUCCESS'

        if added_catchsegv:
            status = run_catchsegv(cmd, timeout=TIMEOUT_RUN, verbose=True)
        else:
            try:
                subprocess_helper(cmd, timeout=TIMEOUT_RUN, verbose=True)
            except subprocess.TimeoutExpired:
                status = 'TIMEOUT'
            except subprocess.CalledProcessError:
                status = 'CRASH'

        if status == 'SUCCESS':
            assert skip_render or os.path.isfile(ssbo_output)

    if os.path.isfile(ssbo_output):
        ssbo_text_to_json(ssbo_output, ssbo_json, json_file)

    log('\nSTATUS ' + status + '\n')

    with open_helper(status_file, 'w') as f:
        f.write(status)


################################################################################
# Main


def some_shader_format_exists(prefix: str, kind: str) -> bool:
    return (
        os.path.isfile(prefix + '.' + kind)
        or os.path.isfile(prefix + '.' + kind + '.asm')
        or os.path.isfile(prefix + '.' + kind + '.spv')
    )


def multiple_shader_formats_exist(prefix: str, kind: str) -> bool:
    count = 0
    if os.path.isfile(prefix + '.' + kind):
        count += 1
    if os.path.isfile(prefix + '.' + kind + '.asm'):
        count += 1
    if os.path.isfile(prefix + '.' + kind + '.spv'):
        count += 1
    return count > 1


def pick_shader_format(prefix: str, kind: str) -> str:
    if multiple_shader_formats_exist(prefix, kind):
        raise ValueError(
            'More than one of .' + kind + ', .' + kind + '.asm and .' + kind + '.spv are present')
    if os.path.isfile(prefix + '.' + kind):
        return prefix + '.' + kind
    if os.path.isfile(prefix + '.' + kind + '.asm'):
        return prefix + '.' + kind + '.asm'
    assert os.path.isfile(prefix + '.' + kind + '.spv')
    return prefix + '.' + kind + '.spv'


def main_helper(args):
    description = (
        'Run SPIR-V shaders.  '
        'Output: ' + LOGFILE_NAME + ', image_0.png, or '
        + SSBO_JSON_FILENAME + 'for compute shader output.')

    parser = argparse.ArgumentParser(description=description)

    # Required arguments
    parser.add_argument('target', help=TARGET_HELP)

    parser.add_argument(
        'json',
        help='JSON file identifying shader files of interest: given foo.json, there should either '
             'be foo.comp[.asm/.spv], or both of foo.vert[.asm/.spv] and foo.frag[.asm/.spv].  In '
             'each case, only one of a GLSL shader or .asm or .spv file is allowed.')

    parser.add_argument(
        'output_dir',
        help='Output directory in which to place temporary and result files')

    # Optional arguments
    parser.add_argument('--serial', help=SERIAL_OPTION_HELP)
    parser.add_argument('--legacy-worker', action='store_true', help=LEGACY_OPTION_HELP)
    parser.add_argument('--skip-render', action='store_true', help=SKIP_RENDER_OPTION_HELP)
    parser.add_argument('--spirvopt', help=SPIRV_OPT_OPTION_HELP)
    parser.add_argument('--force', action='store_true', help=FORCE_OPTION_HELP)

    args = parser.parse_args(args)

    # Check the target is known.
    if not (args.target == 'android' or args.target == 'host'):
        raise ValueError('Target must be "android" or "host"')

    # Record whether or not we are targeting Android.
    is_android = (args.target == 'android')

    # Check the optional arguments are consistent with the target.
    if not is_android and args.force:
        raise ValueError('"force" option not compatible with "host" target')

    if not is_android and args.serial:
        raise ValueError('"serial" option not compatible with "host" target')

    # Check the JSON file used to identify other shaders is present.
    if not os.path.isfile(args.json):
        raise ValueError('The given JSON file does not exist: ' + args.json)

    # If the JSON argument is foo.json the prefix will be foo.
    shader_prefix = os.path.splitext(args.json)[0]

    # If a compute shader is present...
    if some_shader_format_exists(shader_prefix, 'comp'):
        if (
            some_shader_format_exists(shader_prefix, 'vert')
            or some_shader_format_exists(shader_prefix, 'frag')
        ):
            raise ValueError('Compute shader cannot coexist with vertex/fragment shaders')
        compute_shader_file = pick_shader_format(shader_prefix, 'comp')
        vertex_shader_file = None
        fragment_shader_file = None
    elif some_shader_format_exists(shader_prefix, 'vert'):
        if not some_shader_format_exists(shader_prefix, 'frag'):
            raise ValueError('Vertex shader but no fragment shader found')
        compute_shader_file = None
        vertex_shader_file = pick_shader_format(shader_prefix, 'vert')
        fragment_shader_file = pick_shader_format(shader_prefix, 'frag')
    elif some_shader_format_exists(shader_prefix, 'frag'):
        if args.legacy_worker:
            raise ValueError(
                'Fragment shader requires accompanying vertex shader when legacy worker is used'
            )
        # Because Amber has a pass through option, we can do without a vertex shader
        compute_shader_file = None
        vertex_shader_file = None
        fragment_shader_file = pick_shader_format(shader_prefix, 'frag')
    else:
        raise ValueError('No shader files found')

    if compute_shader_file and args.legacy_worker:
        raise ValueError('Compute shaders are not supported with the legacy worker')

    # Make the output directory if it does not yet exist.
    if not os.path.isdir(args.output_dir):
        os.makedirs(args.output_dir)

    if args.serial:
        os.environ['ANDROID_SERIAL'] = args.serial

    logfile = os.path.join(args.output_dir, LOGFILE_NAME)

    spirv_args = None  # type: Optional[List[str]]
    if args.spirvopt:
        spirv_args = args.spirvopt.split()

    global log_to_file

    # Set global log function "log" to write to logfile.
    # Use try-finally to clean up.
    with open_helper(logfile, 'w') as f:
        try:
            log_to_file = f

            if compute_shader_file:
                assert not vertex_shader_file
                assert not fragment_shader_file
                assert not args.legacy_worker
                run_compute_amber(
                    comp_original=compute_shader_file,
                    json_file=args.json,
                    output_dir=args.output_dir,
                    force=args.force,
                    is_android=(args.target == 'android'),
                    skip_render=args.skip_render,
                    spirv_opt_args=spirv_args
                )
                return

            assert vertex_shader_file or not args.legacy_worker
            assert fragment_shader_file

            if args.legacy_worker:
                run_image_legacy(
                    vert_original=vertex_shader_file,
                    frag_original=fragment_shader_file,
                    args=args,
                    spirv_opt_args=spirv_args,
                )
                return

            run_image_amber(
                vert_original=vertex_shader_file,
                frag_original=fragment_shader_file,
                json_file=args.json,
                output_dir=args.output_dir,
                force=args.force,
                is_android=(args.target == 'android'),
                skip_render=args.skip_render,
                spirv_opt_args=spirv_args
            )
        finally:
            log_to_file = None


if __name__ == '__main__':
    main_helper(sys.argv[1:])
