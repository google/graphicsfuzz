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
import struct
import subprocess
import time
import platform
import json
import sys
from typing import Any, IO, Optional, Union

################################################################################
# Constants

HERE = os.path.abspath(__file__)

LOGFILE_NAME = 'vklog.txt'
TIMEOUT_RUN = 30
NUM_RENDER = 3
BUSY_WAIT_SLEEP_SLOW = 1.0
BUSY_WAIT_SLEEP_FAST = 0.1

################################################################################
# Common

orig_print = print


# noinspection PyShadowingBuiltins
def print(s):
    orig_print(s, flush=True)


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


def glslang_path():
    glslang = os.path.join(BIN_DIR, 'glslangValidator')
    if os.path.isfile(glslang):
        return glslang
    return 'glslangValidator'


def spirvas_path():
    spirvas = os.path.join(BIN_DIR, 'spirv-as')
    if os.path.isfile(spirvas):
        return spirvas
    return 'spirv-as'


def spirvdis_path():
    spirvas = os.path.join(BIN_DIR, 'spirv-dis')
    if os.path.isfile(spirvas):
        return spirvas
    return 'spirv-dis'


def remove_end(str_in: str, str_end: str):
    assert str_in.endswith(str_end), 'Expected {} to end with {}'.format(str_in, str_end)
    return str_in[:-len(str_end)]


def prepare_shader(output_dir: str, shader: Optional[str]):
    """
    Translates a shader to binary SPIR-V.

    None -> None
    shader.frag -> shader.frag.spv
    shader.vert -> shader.vert.spv
    shader.comp -> shader.comp.spv
    shader.frag.asm -> shader.frag.spv
    shader.vert.asm -> shader.vert.spv
    shader.comp.asm -> shader.comp.spv

    :param shader: e.g. shader.frag, shader.vert, shader.frag.asm
    :return: the resulting .spv file
    """

    if shader is None:
        return None

    assert(os.path.isfile(shader))

    shader_basename = os.path.basename(shader)

    result = output_dir + os.sep
    if shader_basename.endswith('.frag') or shader_basename.endswith('.vert') or shader_basename.endswith('.comp'):
        result += shader_basename + '.spv'
        cmd = glslang_path() + ' -V ' + shader + ' -o ' + result
        subprocess.check_call(cmd, shell=True, timeout=TIMEOUT_RUN)
    elif shader_basename.endswith('.asm'):
        result += remove_end(shader_basename, '.asm') + '.spv'
        cmd = spirvas_path() + ' ' + shader + ' -o ' + result
        subprocess.check_call(cmd, shell=True, timeout=TIMEOUT_RUN)
    elif shader_basename.endswith('.spv'):
        result += shader_basename
        shutil.copy(shader, result)
    else:
        assert False, 'unexpected shader extension: {}'.format(shader)

    return result

################################################################################
# Linux


def run_linux(vert, frag, uniform_json, skip_render):
    assert(os.path.isfile(vert))
    assert(os.path.isfile(frag))
    assert(os.path.isfile(uniform_json))

    if skip_render:
        with open('SKIP_RENDER', 'w') as f:
            f.write('SKIP_RENDER')
    elif os.path.isfile('SKIP_RENDER'):
        os.remove('SKIP_RENDER')

    cmd = 'vkworker ' + vert + ' ' + frag + ' ' + uniform_json + ' > ' + LOGFILE_NAME
    status = 'SUCCESS'
    try:
        subprocess.run(cmd, shell=True, timeout=TIMEOUT_RUN).check_returncode()
    except subprocess.TimeoutExpired:
        status = 'TIMEOUT'
    except subprocess.CalledProcessError:
        status = 'CRASH'

    with open(LOGFILE_NAME, 'a') as f:
        f.write('\nSTATUS ' + status + '\n')

    with open('STATUS', 'w') as f:
        f.write(status)


def dump_info_linux():
    cmd = 'vkworker --info'
    status = 'SUCCESS'
    try:
        subprocess.run(cmd, shell=True, timeout=TIMEOUT_RUN).check_returncode()
    except subprocess.TimeoutExpired:
        status = 'TIMEOUT'
    except subprocess.CalledProcessError:
        status = 'CRASH'

    with open('STATUS', 'w') as f:
        f.write(status)

################################################################################
# Android


ANDROID_SDCARD = '/sdcard/graphicsfuzz'
ANDROID_APP = 'com.graphicsfuzz.vkworker'
TIMEOUT_APP = 30


def adb_helper(adb_args, check, stdout: Union[None, int, IO[Any]]):

    adb_cmd = 'adb ' + adb_args

    try:
        p = subprocess.run(
            adb_cmd,
            shell=True,
            check=check,
            timeout=TIMEOUT_RUN,
            stdout=stdout,
            universal_newlines=True,
        )
        return p

    except subprocess.TimeoutExpired as err:
        print('ERROR: adb command timed out: ' + err.cmd)
        raise err


def adb_check(adb_args, stdout: Union[None, int, IO[Any]] = subprocess.PIPE):
    return adb_helper(adb_args, True, stdout)


def adb_can_fail(adb_args, stdout: Union[None, int, IO[Any]] = subprocess.PIPE):
    return adb_helper(adb_args, False, stdout)


def stay_awake_warning():
    res = adb_check('shell settings get global stay_on_while_plugged_in')
    if str(res.stdout).strip() == '0':
        print('\nWARNING: please enable "Stay Awake" from developer settings\n')


def is_screen_off_or_locked():
    """
    :return: True: the screen is off or locked. False: unknown.
    """
    res = adb_can_fail('shell dumpsys nfc')
    if res.returncode != 0:
        return False

    stdout = str(res.stdout)
    # You will often find "mScreenState=OFF_LOCKED", but this catches OFF too, which is good.
    if stdout.find('mScreenState=OFF') >= 0:
        return True
    if stdout.find('mScreenState=ON_LOCKED') >= 0:
        return True

    return False


def prepare_device(wait_for_screen):
    adb_check('logcat -c')
    adb_check('shell pm grant com.graphicsfuzz.vkworker android.permission.READ_EXTERNAL_STORAGE')
    adb_check('shell pm grant com.graphicsfuzz.vkworker android.permission.WRITE_EXTERNAL_STORAGE')
    adb_can_fail('shell am force-stop ' + ANDROID_APP)
    adb_can_fail('shell rm -rf ' + ANDROID_SDCARD)
    adb_check('shell mkdir -p ' + ANDROID_SDCARD)

    if wait_for_screen:
        stay_awake_warning()
        # We cannot reliably know if the screen is on, but this function definitely knows if it is
        # off or locked. So we wait here while we definitely know there is an issue.
        while is_screen_off_or_locked():
            print('\nWARNING: The screen appears to be off or locked. Please unlock the device and '
                  'ensure "Stay Awake" is enabled in developer settings.\n')
            time.sleep(BUSY_WAIT_SLEEP_SLOW)


def run_android(vert, frag, uniform_json, skip_render, wait_for_screen):
    assert(os.path.isfile(vert))
    assert(os.path.isfile(frag))
    assert(os.path.isfile(uniform_json))

    prepare_device(wait_for_screen)

    adb_check('push ' + vert + ' ' + ANDROID_SDCARD + '/test.vert.spv')
    adb_check('push ' + frag + ' ' + ANDROID_SDCARD + '/test.frag.spv')
    adb_check('push ' + uniform_json + ' ' + ANDROID_SDCARD + '/test.json')

    cmd = 'shell am start -n ' + ANDROID_APP + '/android.app.NativeActivity'
    flags = '--num-render {}'.format(NUM_RENDER)
    if skip_render:
        flags += ' --skip-render'

    # Pass command line args as Intent extra. Need to nest-quote, hence the "\'flags\'"
    cmd += ' -e gfz "\'' + flags + '\'"'
    adb_check(cmd)

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
            if adb_can_fail('shell test -f ' + ANDROID_SDCARD + '/STARTED').returncode != 0:
                continue
            status = 'TIMEOUT'

        assert status == 'TIMEOUT'

        # DONE file indicates app is done.
        if adb_can_fail('shell test -f ' + ANDROID_SDCARD + '/DONE').returncode == 0:
            status = 'SUCCESS'
            break

        # Otherwise, keep looping/waiting while the app is still running.
        # Quote >/dev/null otherwise this fails on Windows hosts.
        if adb_can_fail('shell "pidof ' + ANDROID_APP + ' > /dev/null"').returncode == 0:
            continue

        # The app has crashed. Check for DONE file one more time.
        if adb_can_fail('shell test -f ' + ANDROID_SDCARD + '/DONE').returncode == 0:
            status = 'SUCCESS'
            break

        # App has terminated and there is no DONE file; this definitely looks like a crash.
        status = 'CRASH'
        break

    # Grab log:
    with open(LOGFILE, 'w', encoding='utf-8', errors='ignore') as f:
        adb_check('logcat -d', stdout=f)

    # retrieve all files to results/
    res_dir = 'results'
    if os.path.exists(res_dir):
        shutil.rmtree(res_dir)
    adb_check('pull ' + ANDROID_SDCARD + ' ' + res_dir)

    # Check sanity:
    if status == 'SUCCESS':
        sanity_before = res_dir + '/sanity_before.png'
        sanity_after = res_dir + '/sanity_after.png'
        if os.path.isfile(sanity_before) and os.path.isfile(sanity_after):
            if not filecmp.cmp(sanity_before, sanity_after, shallow=False):
                status = 'SANITY_ERROR'

    # Check nondet:
    if status == 'SUCCESS':
        ref_image = res_dir + '/image_0.png'
        if os.path.isfile(ref_image):
            # If reference image is here then report nondet if any image is different or missing.
            for i in range(1, NUM_RENDER):
                next_image = res_dir + '/image_{}.png'.format(i)
                if not os.path.isfile(next_image):
                    status = 'UNEXPECTED_ERROR'
                    with open(LOGFILE, 'a') as f:
                        f.write('\n Not all images were produced? Missing image: {}\n'.format(i))
                elif not filecmp.cmp(ref_image, next_image, shallow=False):
                    status = 'NONDET'
                    shutil.copy(ref_image, 'nondet0.png')
                    shutil.copy(next_image, 'nondet1.png')

    with open(LOGFILE, 'a') as f:
        f.write('\nSTATUS ' + status + '\n')
        if status == 'UNEXPECTED_ERROR':
            f.write('\n App did not start?\n')

    with open('STATUS', 'w') as f:
        f.write(status)

    if status != 'SUCCESS':
        # Something went wrong. Make sure we stop the app.
        adb_can_fail('shell am force-stop ' + ANDROID_APP)

    # Grab image if present.
    image_path = ANDROID_SDCARD + '/image_0.png'
    return_code = adb_can_fail('shell test -f ' + image_path).returncode
    if return_code == 0:
        adb_check('pull ' + image_path)


def dump_info_android(wait_for_screen):
    prepare_device(wait_for_screen)

    info_file = ANDROID_SDCARD + '/worker_info.json'

    adb_check(
        'shell am start -n ' + ANDROID_APP + '/android.app.NativeActivity -e gfz "\'--info\'"')

    # Busy wait for the app to write the gpu info.
    deadline = time.time() + TIMEOUT_APP
    while time.time() < deadline:
        if adb_can_fail('shell test -f ' + info_file).returncode == 0:
            break
        time.sleep(BUSY_WAIT_SLEEP_FAST)

    if adb_can_fail('shell test -f ' + info_file).returncode == 0:
        adb_check('pull ' + info_file)
    else:
        print('Error: failed to obtain worker information')
    adb_can_fail('shell am force-stop ' + ANDROID_APP)

################################################################################
# VkRunner / Amber


def spv_get_disassembly(shader_filename):
    cmd = spirvdis_path() + ' ' + shader_filename
    return subprocess.check_output(cmd, shell=True).decode('utf-8')


def uniform_json_to_amberscript(uniform_json):
    """
    Returns the string representing VkScript version of uniform declarations.
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
    uniform ubo 0:3 float 0 42.0

    """

    uniform_type = {
        'glUniform1i': 'int',
        'glUniform2i': 'ivec2',
        'glUniform3i': 'ivec3',
        'glUniform4i': 'ivec4',
        'glUniform1f': 'float',
        'glUniform2f': 'vec2',
        'glUniform3f': 'vec3',
        'glUniform4f': 'vec4',
    }

    descriptor_set = 0  # always 0 in our tests
    offset = 0          # We never have uniform offset in our tests

    result = ''
    with open(uniform_json, 'r') as f:
        j = json.load(f)
    for name, entry in j.items():

        if name == '$compute':
            continue

        func = entry['func']
        if func not in uniform_type.keys():
            print('Error: unknown uniform type for function: ' + func)
            exit(1)
        uniform_type = uniform_type[func]

        result += '# ' + name + '\n'
        result += 'uniform ubo {}:{}'.format(descriptor_set, entry['binding'])
        result += ' ' + uniform_type
        result += ' {}'.format(offset)
        for arg in entry['args']:
            result += ' {}'.format(arg)
        result += '\n'

    return result


def amberscriptify_img(vert, frag, uniform_json):
    """
    Generates a VkScript representation of an image test
    """

    script = '# Generated\n'

    script += '[vertex shader spirv]\n'
    script += spv_get_disassembly(vert)
    script += '\n\n'

    script += '[fragment shader spirv]\n'
    script += spv_get_disassembly(frag)
    script += '\n\n'

    script += '[test]\n'
    script += '## Uniforms\n'
    script += uniform_json_to_amberscript(uniform_json)
    script += '\n'
    script += 'draw rect -1 -1 2 2\n'

    return script


def run_vkrunner(vert, frag, uniform_json):
    assert(os.path.isfile(vert))
    assert(os.path.isfile(frag))
    assert(os.path.isfile(uniform_json))

    # Produce VkScript
    script = amberscriptify_img(vert, frag, uniform_json)

    tmpfile = 'tmpscript.shader_test'

    with open(tmpfile, 'w') as f:
        f.write(script)

    # Prepare files on device. vkrunner cannot be made executable under
    # /sdcard/, hence we work under /data/local/tmp
    device_dir = '/data/local/tmp'
    adb_check('push ' + tmpfile + ' ' + device_dir)

    device_image = device_dir + '/image.ppm'
    adb_check('shell rm -f ' + device_image)

    # call vkrunner
    cmd = 'shell "cd ' + device_dir + '; ./vkrunner -i image.ppm ' + tmpfile + '"'

    adb_check('logcat -c')

    status = 'UNEXPECTED_ERROR'

    try:
        result = adb_can_fail(cmd)
    except subprocess.TimeoutExpired:
        result = None
        status = 'TIMEOUT'

    if status != 'TIMEOUT':
        if result.returncode != 0:
            status = 'CRASH'
        else:
            if adb_can_fail('shell test -f' + device_image).returncode == 0:
                status = 'SUCCESS'
                adb_check('pull ' + device_image)
                subprocess.run('convert image.ppm image_0.png', shell=True)

    # Grab log:
    with open(LOGFILE, 'w', encoding='utf-8', errors='ignore') as f:
        adb_check('logcat -d', stdout=f)

    with open(LOGFILE, 'a') as f:
        f.write('\nSTATUS ' + status + '\n')

    with open('STATUS', 'w') as f:
        f.write(status)


################################################################################
# Compute


def comp_json_to_vkscript(comp_json):
    """
    Returns the string representing VkScript version of compute shader setup,
    found under the special "$compute" key in JSON

      {
        "my_uniform_name": { ... ignored by this function ... },

        "$compute": {
          "num_groups": [12, 13, 14];
          "buffer": {
            "binding": 123,
            "input": [42, 43, 44, 45]
          }
        }

      }

    becomes:

      ssbo 123 subdata int 0 42 43 44 45

      compute 12 13 14

    """

    with open(comp_json, 'r') as f:
        j = json.load(f)

    assert '$compute' in j.keys(), 'Cannot find "$compute" key in JSON file'
    j = j['$compute']

    result = ""

    binding = j['buffer']['binding']
    offset = 0
    for field_info in j['buffer']['fields']:
        result += 'ssbo ' + str(binding) + ' subdata ' + field_info['type'] + ' ' + str(offset)
        for datum in field_info['data']:
            result += ' ' + str(datum)
            offset += 4
        result += '\n'
    result += '\n\n'

    result += 'compute'
    result += ' ' + str(j['num_groups'][0])
    result += ' ' + str(j['num_groups'][1])
    result += ' ' + str(j['num_groups'][2])
    result += '\n'

    return result


def amberscriptify_comp(comp_spv: str, comp_json: str):
    """
    Generates an AmberScript representation of a compute test
    """

    result = '# Generated\n'

    result += '[compute shader spirv]\n'
    result += spv_get_disassembly(comp_spv)
    result += '\n\n'

    result += '[test]\n'
    result += '## Uniforms\n'
    result += uniform_json_to_amberscript(comp_json)
    result += '## SSBO\n'
    result += comp_json_to_vkscript(comp_json)
    result += '\n'

    return result


def ssbo_text_to_json(ssbo_text_file, ssbo_json_file, comp_json):
    '''
    Read the ssbo_text_file and extract its contents to a json file.
    '''

    values = open(ssbo_text_file, 'r').read().split()
    j = json.load(open(comp_json, 'r'))['$compute']

    assert values[0] == str(j['buffer']['binding'])

    byte_pointer = 1

    result = []

    for field_info in j['buffer']['fields']:
        result_for_field = []
        for counter in range(0, len(field_info['data'])):
            hex = ""
            for byte in range(0, 4):
                hex += values[byte_pointer]
                byte_pointer += 1
            if field_info['type'] == 'int':
                result_for_field.append(int.from_bytes(bytearray.fromhex(hex), byteorder='little'))
            elif field_info['type'] in ['float', 'vec2', 'vec3', 'vec4']:
                result_for_field.append(struct.unpack('f', bytearray.fromhex(hex))[0])
            else:
                raise Exception('Do not know how to handle type "' + field_info['type'] + '"')
        result.append(result_for_field)

    ssbo_json_obj = { 'ssbo': result }

    with open(ssbo_json_file, 'w') as f:
        f.write(json.dumps(ssbo_json_obj))


def get_ssbo_binding(comp_json):
    with open(comp_json, 'r') as f:
        j = json.load(f)
    binding = j['$compute']['buffer']['binding']
    return binding


def run_compute(comp, args):
    assert(os.path.isfile(comp))
    assert(os.path.isfile(args.json))

    amberscript_file = args.output_dir + os.sep + 'tmpscript.shader_test'
    ssbo_output = args.output_dir + os.sep + 'ssbo'
    ssbo_json = args.output_dir + os.sep + 'ssbo.json'
    logfile = args.output_dir + os.sep + LOGFILE_NAME
    statusfile = args.output_dir + os.sep + 'STATUS'

    with open(amberscript_file, 'w') as f:
        f.write(amberscriptify_comp(comp, args.json))

    # FIXME: in case of multiple ssbo, we should pass the binding of the one to dump
    ssbo_binding = str(get_ssbo_binding(args.json))

    if (args.target == 'android'):
        # Prepare files on device. Amber cannot be made executable under
        # /sdcard/, hence we work under /data/local/tmp
        device_dir = '/data/local/tmp'
        adb_check('push ' + amberscript_file + ' ' + device_dir)

        device_ssbo = device_dir + '/ssbo'
        adb_check('shell rm -f ' + device_ssbo)

        # call amber
        cmd = 'shell "cd ' + device_dir + '; ./amber_ndk -b ssbo -B ' + ssbo_binding + ' -d '\
              + os.path.basename(amberscript_file) + '"'

        adb_check('logcat -c')

        status = 'UNEXPECTED_ERROR'

        try:
            result = adb_can_fail(cmd)
        except subprocess.TimeoutExpired:
            result = None
            status = 'TIMEOUT'

        if status != 'TIMEOUT':
            if result.returncode != 0:
                status = 'CRASH'
            else:
                if adb_can_fail('shell test -f' + device_ssbo).returncode == 0:
                    status = 'SUCCESS'
                    adb_check('pull ' + device_ssbo + ' ' + ssbo_output)

        # Grab log:
        with open(logfile, 'w', encoding='utf-8', errors='ignore') as f:
            adb_check('logcat -d', stdout=f)
    else:
        assert args.target == 'host'
        cmd = 'amber -b ' + ssbo_output + ' -B ' + ssbo_binding + ' ' + amberscript_file + ' > ' + logfile
        status = 'SUCCESS'
        try:
            subprocess.run(cmd, shell=True, timeout=TIMEOUT_RUN).check_returncode()
        except subprocess.TimeoutExpired:
            status = 'TIMEOUT'
        except subprocess.CalledProcessError:
            status = 'CRASH'

        if status == 'SUCCESS':
            assert(os.path.isfile(ssbo_output))

        with open(logfile, 'a') as f:
            f.write('\nSTATUS ' + status + '\n')

    if os.path.isfile(ssbo_output):
        ssbo_text_to_json(ssbo_output, ssbo_json, args.json)

    with open(logfile, 'a') as f:
        f.write('\nSTATUS ' + status + '\n')

    with open(statusfile, 'w') as f:
        f.write(status)


def some_shader_format_exists(prefix: str, kind: str) -> bool:
    return os.path.isfile(prefix + '.' + kind)\
           or os.path.isfile(prefix + '.' + kind + '.asm')\
           or os.path.isfile(prefix + '.' + kind + '.spv')


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
        raise ValueError('More than one of .' + kind + ', .' + kind + '.asm and .' + kind + '.spv are present')
    if os.path.isfile(prefix + '.' + kind):
        return prefix + '.' + kind
    if os.path.isfile(prefix + '.' + kind + '.asm'):
        return prefix + '.' + kind + '.asm'
    assert os.path.isfile(prefix + '.' + kind + '.spv')
    return prefix + '.' + kind + '.spv'


def run_image():
    wait_for_screen = not args.force

    # These are mutually exclusive, but we return after each for clarity:

    if args.serial:
        run_android(vert, frag, args.json, args.skip_render, wait_for_screen)
        return

    if args.android:
        run_android(vert, frag, args.json, args.skip_render, wait_for_screen)
        return

    if args.linux:
        run_linux(vert, frag, args.json, args.skip_render)
        return

    if args.vkrunner:
        run_vkrunner(vert, frag, args.json)
        return



################################################################################
# Main


def main_helper(args):
    # TODO: update description to reflect final output.
    description = 'Run SPIR-V shaders.  Output: ' + LOGFILE_NAME + ', image.png'
    parser = argparse.ArgumentParser(description=description)

    # Required arguments
    parser.add_argument('target', help='Target: one of "host" (run on Linux/Windows machine) or "android" (run on '
                                       'Android device).')
    parser.add_argument('json', help='JSON file identifying shader files of interest: given foo.json, there should '
                                     'either be foo.comp[.asm/.spv], or both of foo.vert[.asm/.spv] and foo.frag[.'
                                     'asm/.spv].  In each case, only one of a GLSL shader or .asm or .spv file is '
                                     'allowed.')

    parser.add_argument('output_dir', help='Output directory in which to place temporary and result files')

    # Optional arguments
    parser.add_argument('--serial', help='Android device serial number. Only allowed if target is android.')
    parser.add_argument('--legacy-worker', action='store_true', help='Render using legacy Vulkan worker.')
    parser.add_argument('--skip-render', action='store_true', help='Compile shaders but do not actually run them.')
    parser.add_argument('--force', action='store_true',
                        help='Do not wait for the device\'s screen to be on; just continue.  Only allowed if target '
                             'is android.')

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
        if some_shader_format_exists(shader_prefix, 'vert')\
                or some_shader_format_exists(shader_prefix, 'frag'):
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
    else:
        raise ValueError('No compute nor vertex shader files found')

    # Make the output directory if it does not yet exist.
    if not os.path.exists(args.output_dir):
        os.makedirs(args.output_dir)

    # Copy the shaders into the output directory, turning them into SPIR-V binary format first if needed.
    compute_shader_file = prepare_shader(args.output_dir, compute_shader_file)
    vertex_shader_file = prepare_shader(args.output_dir, vertex_shader_file)
    fragment_shader_file = prepare_shader(args.output_dir, fragment_shader_file)

    if args.serial:
        os.environ['ANDROID_SERIAL'] = args.serial


    if compute_shader_file:
        assert not vertex_shader_file
        assert not fragment_shader_file
        run_compute(compute_shader_file, args)
        return

    assert vertex_shader_file
    assert fragment_shader_file
    run_image(vertex_shader_file, fragment_shader_file, args)


if __name__ == '__main__':
    main_helper(sys.argv[1:])
