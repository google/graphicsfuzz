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
import subprocess
import time
from typing import Union, IO, Any

################################################################################
# Constants


LOGFILE = 'vklog.txt'
TIMEOUT_RUN = 30
NUM_RENDER = 3
BUSY_WAIT_SLEEP_SLOW = 1.0
BUSY_WAIT_SLEEP_FAST = 0.1

################################################################################
# Common


def remove_end(str_in: str, str_end: str):
    assert str_in.endswith(str_end), 'Expected {} to end with {}'.format(str_in, str_end)
    return str_in[:-len(str_end)]


def prepare_shader(shader: str):
    """
    Translates a shader to binary SPIR-V.

    shader.frag -> shader.frag.spv
    shader.vert -> shader.vert.spv
    shader.frag.asm -> shader.frag.spv
    shader.vert.asm -> shader.vert.spv

    :param shader: e.g. shader.frag, shader.vert, shader.frag.asm
    :return: the output .spv file
    """
    assert(os.path.isfile(shader))

    # noinspection PyUnusedLocal
    output = ''
    if shader.endswith('.frag') or shader.endswith('.vert'):
        output = shader + '.spv'
        cmd = 'glslangValidator -V ' + shader + ' -o ' + output
        subprocess.check_call(cmd, shell=True, timeout=TIMEOUT_RUN)
    elif shader.endswith('.frag.asm') or shader.endswith('.vert.asm'):
        output = remove_end(shader, '.asm') + '.spv'
        cmd = 'spirv-as ' + shader + ' -o ' + output
        subprocess.check_call(cmd, shell=True, timeout=TIMEOUT_RUN)
    elif shader.endswith('.spv'):
        output = shader
    else:
        assert False, 'unexpected shader extension: {}'.format(shader)

    return output

################################################################################
# Linux


def run_linux(vert, frag, json, skip_render):
    assert(os.path.isfile(vert))
    assert(os.path.isfile(frag))
    assert(os.path.isfile(json))

    if skip_render:
        with open('SKIP_RENDER', 'w') as f:
            f.write('SKIP_RENDER')
    elif os.path.isfile('SKIP_RENDER'):
        os.remove('SKIP_RENDER')

    cmd = 'vkworker ' + vert + ' ' + frag + ' ' + json + ' > ' + LOGFILE
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


def run_android(vert, frag, json, skip_render, wait_for_screen):
    assert(os.path.isfile(vert))
    assert(os.path.isfile(frag))
    assert(os.path.isfile(json))

    prepare_device(wait_for_screen)

    adb_check('push ' + vert + ' ' + ANDROID_SDCARD + '/test.vert.spv')
    adb_check('push ' + frag + ' ' + ANDROID_SDCARD + '/test.frag.spv')
    adb_check('push ' + json + ' ' + ANDROID_SDCARD + '/test.json')

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
# Main


def main():
    desc = 'Run shaders on vulkan worker. Output: ' + LOGFILE + ', image.png'

    parser = argparse.ArgumentParser(description=desc)

    group = parser.add_mutually_exclusive_group()
    group.add_argument('-a', '--android', action='store_true', help='Render on Android')
    group.add_argument('-i', '--serial', help='Android device serial number. Implies --android')
    group.add_argument('-l', '--linux', action='store_true', help='Render on Linux')

    parser.add_argument('-s', '--skip-render', action='store_true', help='Skip render')

    parser.add_argument('-f', '--force', action='store_true',
                        help='Do not wait for the device\'s screen to be on; just continue.')

    parser.add_argument('vert', help='Vertex shader: shader.vert[.asm|.spv]')
    parser.add_argument('frag', help='Fragment shader: shader.frag[.asm|.spv]')
    parser.add_argument('json', help='Uniforms values')

    args = parser.parse_args()

    if not args.android and not args.serial and not args.linux:
        print('You must set either --android, --serial or --linux option.')
        exit(1)

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


if __name__ == '__main__':
    main()
