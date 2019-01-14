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
from typing import Union, IO, Any

################################################################################
# Constants

HERE = os.path.abspath(__file__)

LOGFILE = 'vklog.txt'
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


def remove_end(str_in: str, str_end: str):
    assert str_in.endswith(str_end), 'Expected {} to end with {}'.format(str_in, str_end)
    return str_in[:-len(str_end)]


def prepare_shader(shader: str):
    """
    Translates a shader to binary SPIR-V.

    shader.frag -> shader.frag.spv
    shader.vert -> shader.vert.spv
    shader.comp -> shader.comp.spv
    shader.frag.asm -> shader.frag.spv
    shader.vert.asm -> shader.vert.spv
    shader.comp.asm -> shader.comp.spv

    :param shader: e.g. shader.frag, shader.vert, shader.frag.asm
    :return: the output .spv file
    """
    assert(os.path.isfile(shader))

    # noinspection PyUnusedLocal
    output = ''
    if shader.endswith('.frag') or shader.endswith('.vert') or shader.endswith('.comp'):
        output = shader + '.spv'
        cmd = glslang_path() + ' -V ' + shader + ' -o ' + output
        subprocess.check_call(cmd, shell=True, timeout=TIMEOUT_RUN)
    elif shader.endswith('.frag.asm') or shader.endswith('.vert.asm') or shader.endswith('.comp.asm'):
        output = remove_end(shader, '.asm') + '.spv'
        cmd = spirvas_path() + ' ' + shader + ' -o ' + output
        subprocess.check_call(cmd, shell=True, timeout=TIMEOUT_RUN)
    elif shader.endswith('.spv'):
        output = shader
    else:
        assert False, 'unexpected shader extension: {}'.format(shader)

    return output

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

    cmd = 'vkworker ' + vert + ' ' + frag + ' ' + uniform_json + ' > ' + LOGFILE
    status = 'SUCCESS'
    try:
        subprocess.run(cmd, shell=True, timeout=TIMEOUT_RUN).check_returncode()
    except subprocess.TimeoutExpired:
        status = 'TIMEOUT'
    except subprocess.CalledProcessError:
        status = 'CRASH'

    with open(LOGFILE, 'a') as f:
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
# VkRunner


def spv_get_bin_as_uint(shader_filename):
    with open(shader_filename, 'rb') as f:
        data = f.read()
    assert(len(data) >= 4)
    assert(len(data) % 4 == 0)

    # Check SPIRV magic header to guess endianness
    header = struct.unpack('>I', data[0:4])[0]

    if header == 0x07230203:
        endianness = '>'
    elif header == 0x03022307:
        endianness = '<'
    else:
        print('Invalid magic header for SPIRV file')
        exit(1)

    fmt = endianness + 'I'

    result = ''
    for i in range(0, len(data), 4):
        word = struct.unpack(fmt, data[i:i+4])[0]
        result += ' {:x}'.format(word)

    return result


def uniform_json_to_vkscript(uniform_json):
    '''
    Returns the string representing VkScript version of uniform declarations.

    {
      "myuniform": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "binding": 3
      }
    }

    becomes:

    # myuniform
    uniform ubo 0:3 float 0 42.0

    '''

    UNIFORM_TYPE = {
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

        func = entry['func']
        if func not in UNIFORM_TYPE.keys():
            print('Error: unknown uniform type for function: ' + func)
            exit(1)
        uniform_type = UNIFORM_TYPE[func]

        result += '# ' + name + '\n'
        result += 'uniform ubo {}:{}'.format(descriptor_set, entry['binding'])
        result += ' ' + uniform_type
        result += ' {}'.format(offset)
        for arg in entry['args']:
            result += ' {}'.format(arg)
        result += '\n'

    return result


def vkscriptify_img(vert, frag, uniform_json):
    '''
    Generates a VkScript representation of an image test
    '''

    script = '# Generated\n'

    script += '[vertex shader binary]\n'
    script += spv_get_bin_as_uint(vert)
    script += '\n\n'

    script += '[fragment shader binary]\n'
    script += spv_get_bin_as_uint(frag)
    script += '\n\n'

    script += '[test]\n'
    script += '## Uniforms\n'
    script += uniform_json_to_vkscript(uniform_json)
    script += '\n'
    script += 'draw rect -1 -1 2 2\n'

    return script


def run_vkrunner(vert, frag, uniform_json):
    assert(os.path.isfile(vert))
    assert(os.path.isfile(frag))
    assert(os.path.isfile(uniform_json))

    # Produce VkScript
    script = vkscriptify_img(vert, frag, uniform_json)

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
    adb_check(cmd)

    # Get result
    status = 'UNEXPECTED_ERROR'
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
    '''
    Returns the string representing VkScript version of compute shader setup.

      {
        "num_groups": [12, 13, 14];
        "buffer": {
          "binding": 123,
          "input": [42, 43, 44, 45]
        }
      }

    becomes:

      ssbo 123 subdata int 0 42 43 44 45

      compute 12 13 14

    '''

    offset = 0

    with open(comp_json, 'r') as f:
        j = json.load(f)

    binding = j['buffer']['binding']
    data = j['buffer']['input']

    result = 'ssbo ' + str(binding) + ' subdata int ' + str(offset)
    for d in data:
        result += ' ' + str(d)
    result += '\n\n'

    result += 'compute'
    result += ' '  + str(j['num_groups'][0])
    result += ' '  + str(j['num_groups'][1])
    result += ' '  + str(j['num_groups'][2])
    result += '\n'

    return result


def vkscriptify_comp(comp, comp_json):
    '''
    Generates a VkScript representation of a compute test
    '''

    script = '# Generated\n'

    script += '[compute shader binary]\n'
    script += spv_get_bin_as_uint(comp)
    script += '\n\n'

    script += '[test]\n'
    script += '## SSBO\n'
    script += comp_json_to_vkscript(comp_json)
    script += '\n'

    return script

def ssbo_bin_to_json(ssbo_bin_file, ssbo_json_file):
    '''
    Read the ssbo_bin_file and extract its contents to a json file as a single array of ints.

    Assumes: binary in little endian (which is almost ubiquitous on ARM CPUs) storing 32 bit ints.
    '''

    with open(ssbo_bin_file, 'rb') as f:
        data = f.read()

    int_width = 4 # 4 bytes for each 32 bits int
    assert(len(data) % int_width == 0)

    ssbo = []
    for i in range(0, len(data), int_width):
        int_val = struct.unpack('<I', data[i:i + int_width])[0]
        ssbo.append(int_val)

    ssbo_json_obj = { 'ssbo': ssbo }

    with open(ssbo_json_file, 'w') as f:
        f.write(json.dumps(ssbo_json_obj))

def run_compute(comp, comp_json):
    assert(os.path.isfile(comp))
    assert(os.path.isfile(comp_json))

    script = vkscriptify_comp(comp, comp_json)

    tmpfile = 'tmpscript.shader_test'

    with open(tmpfile, 'w') as f:
        f.write(script)

    # Prepare files on device. vkrunner cannot be made executable under
    # /sdcard/, hence we work under /data/local/tmp
    device_dir = '/data/local/tmp'
    adb_check('push ' + tmpfile + ' ' + device_dir)

    device_ssbo = device_dir + '/ssbo'
    adb_check('shell rm -f ' + device_ssbo)

    # call vkrunner
    # FIXME: in case of multiple ssbo, we should pass the binding of the one to dump
    cmd = 'shell "cd ' + device_dir + '; ./vkrunner -b ssbo ' + tmpfile + '"'

    adb_check('logcat -c')

    if adb_can_fail(cmd).returncode != 0:
        status = 'CRASH'
    else:
        status = 'SUCCESS'

    if status == 'SUCCESS':
        if adb_can_fail('shell test -f' + device_ssbo).returncode != 0:
            status = 'UNEXPECTED_ERROR'
        else:
            adb_check('pull ' + device_ssbo)
            ssbo_bin_to_json('ssbo', 'ssbo.json')

    # Grab log:
    with open(LOGFILE, 'w', encoding='utf-8', errors='ignore') as f:
        adb_check('logcat -d', stdout=f)

    with open(LOGFILE, 'a') as f:
        f.write('\nSTATUS ' + status + '\n')

    with open('STATUS', 'w') as f:
        f.write(status)


################################################################################
# Main


def main():
    desc = 'Run shaders on vulkan worker. Output: ' + LOGFILE + ', image.png'

    parser = argparse.ArgumentParser(description=desc)

    group = parser.add_mutually_exclusive_group()
    group.add_argument('-a', '--android', action='store_true', help='Render on Android')
    group.add_argument('-i', '--serial', help='Android device serial number. Implies --android')
    group.add_argument('-l', '--linux', action='store_true', help='Render on Linux')
    group.add_argument('--vkrunner', action='store_true', help='Render using vkrunner')
    group.add_argument('--compute', help='Run compute shader using vkrunner. Temp: Values for vert and frag arguments must be provided, but will be ignored.')

    parser.add_argument('-s', '--skip-render', action='store_true', help='Skip render')

    parser.add_argument('-f', '--force', action='store_true',
                        help='Do not wait for the device\'s screen to be on; just continue.')

    parser.add_argument('vert', help='Vertex shader: shader.vert[.asm|.spv]')
    parser.add_argument('frag', help='Fragment shader: shader.frag[.asm|.spv]')
    parser.add_argument('json', help='Uniforms values')

    args = parser.parse_args()

    if not args.android and not args.serial and not args.linux and not args.vkrunner and not args.compute:
        print('You must set either --android, --serial, --linux, --vkrunner or --compute option.')
        exit(1)

    if args.compute:
        comp = prepare_shader(args.compute)
        run_compute(comp, args.json)
        return

    vert = prepare_shader(args.vert)
    frag = prepare_shader(args.frag)

    wait_for_screen = not args.force

    # These are mutually exclusive, but we return after each for clarity:

    if args.serial:
        os.environ['ANDROID_SERIAL'] = args.serial
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

if __name__ == '__main__':
    main()
