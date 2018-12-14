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

################################################################################
# Constants

LOGFILE = 'vklog.txt'
TIMEOUT_RUN = 30
NUM_RENDER = 3

################################################################################
# Common


def prepare_shader(shader):
    """
    Translates a shader to binary SPIR-V.

    shader.frag -> shader.frag.spv
    shader.vert -> shader.vert.spv
    shader.frag.asm -> shader.frag.spv
    shader.vert.asm -> shader.vert.spv

    :param shader: e.g. shader.frag, shader.vert, shader.frag.asm
    :return: the output .spv file
    """
    assert(os.path.exists(shader))

    # Translate shader to spv

    # noinspection PyUnusedLocal
    output = ''
    if shader[-5:] == '.frag' or shader[-5:] == '.vert':
        output = shader + '.spv'
        cmd = 'glslangValidator -V ' + shader + ' -o ' + output
        subprocess.check_call(cmd, shell=True, timeout=TIMEOUT_RUN)
    elif shader[-9:] == '.frag.asm' or shader[-9:] == '.vert.asm':
        output = shader.replace('.asm', '.spv')
        cmd = 'spirv-as ' + shader + ' -o ' + output
        subprocess.check_call(cmd, shell=True, timeout=TIMEOUT_RUN)
    else:
        assert(shader[-4:] == '.spv')
        output = shader

    return output

################################################################################
# Linux


def run_linux(vert, frag, json, skip_render):
    assert(os.path.exists(vert))
    assert(os.path.exists(frag))
    assert(os.path.exists(json))

    if skip_render:
        with open('SKIP_RENDER', 'w') as f:
            f.write('SKIP_RENDER')
    elif os.path.exists('SKIP_RENDER'):
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


def adb(adb_args):

    adb_cmd = 'adb ' + adb_args

    try:
        p = subprocess.run(
            adb_cmd,
            shell=True,
            timeout=TIMEOUT_RUN,
            stdout=subprocess.PIPE,
            universal_newlines=True)

    except subprocess.TimeoutExpired as err:
        print('ERROR: adb command timed out: ' + err.cmd)
        return err
    else:
        return p


def run_android(vert, frag, json, skip_render):
    assert(os.path.exists(vert))
    assert(os.path.exists(frag))
    assert(os.path.exists(json))

    adb('shell rm -rf ' + ANDROID_SDCARD)
    adb('shell mkdir -p ' + ANDROID_SDCARD)
    adb('push ' + vert + ' ' + ANDROID_SDCARD + '/test.vert.spv')
    adb('push ' + frag + ' ' + ANDROID_SDCARD + '/test.frag.spv')
    adb('push ' + json + ' ' + ANDROID_SDCARD + '/test.json')

    adb('logcat -c')

    cmd = 'shell am start -n ' + ANDROID_APP + '/android.app.NativeActivity'
    flags = '--num-render {}'.format(NUM_RENDER)
    if skip_render:
        flags += ' --skip-render'

    # Pass command line args as Intent extra. Need to nest-quote, hence the "\'flags\'"
    cmd += ' -e gfz "\'' + flags + '\'"'
    adb(cmd)

    # Busy wait
    deadline = time.time() + TIMEOUT_APP

    status = 'UNEXPECTED_ERROR'

    # If the app never starts, status remains as UNEXPECTED_ERROR.
    # Once the app starts, status becomes TIMEOUT.
    # If we break out of the loop below, the status is updated just before.

    while time.time() < deadline:
        time.sleep(0.1)

        # Don't pass here until app has started.
        if status == 'UNEXPECTED_ERROR':
            if adb('shell test -f ' + ANDROID_SDCARD + '/STARTED').returncode != 0:
                continue
            status = 'TIMEOUT'

        assert status == 'TIMEOUT'

        return_code = adb('shell test -f ' + ANDROID_SDCARD + '/DONE').returncode
        if return_code == 0:
            status = 'SUCCESS'
            break

        # Make sure to redirect to /dev/null on the device, otherwise this fails on Windows hosts.
        return_code = adb('shell "pidof ' + ANDROID_APP + ' > /dev/null"').returncode
        if return_code == 1:

            # double check that no DONE file is present
            return_code = adb('shell test -f ' + ANDROID_SDCARD + '/DONE').returncode
            if return_code == 0:
                status = 'SUCCESS'
                break

            # No pid, and no DONE file, this looks like a crash indeed.
            status = 'CRASH'
            break

    # Grab log:
    adb('logcat -d > ' + LOGFILE)

    # retrieve all files to results/
    res_dir = 'results'
    if os.path.exists(res_dir):
        shutil.rmtree(res_dir)
    adb('pull ' + ANDROID_SDCARD + ' ' + res_dir)

    # Check sanity:
    if status == 'SUCCESS':
        sanity_before = res_dir + '/sanity_before.png'
        sanity_after = res_dir + '/sanity_after.png'
        if os.path.exists(sanity_before) and os.path.exists(sanity_after):
            if not filecmp.cmp(sanity_before, sanity_after, shallow=False):
                status = 'SANITY_ERROR'

    # Check nondet:
    if status == 'SUCCESS':
        ref_image = res_dir + '/image_0.png'
        if os.path.exists(ref_image):
            # If reference image is here then report nondet if any image is different or missing.
            for i in range(1, NUM_RENDER):
                next_image = res_dir + '/image_{}.png'.format(i)
                if not os.path.exists(next_image):
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
        adb('shell am force-stop ' + ANDROID_APP)

    # Grab image if present.
    image_path = ANDROID_SDCARD + '/image_0.png'
    return_code = adb('shell test -f ' + image_path).returncode
    if return_code == 0:
        adb('pull ' + image_path)


def dump_info_android():
    info_file = ANDROID_SDCARD + '/worker_info.json'
    adb('shell rm -f ' + info_file)
    adb('shell am force-stop ' + ANDROID_APP)
    adb('shell pm grant com.graphicsfuzz.vkworker android.permission.READ_EXTERNAL_STORAGE')
    adb('shell pm grant com.graphicsfuzz.vkworker android.permission.WRITE_EXTERNAL_STORAGE')
    adb('shell am start -n ' + ANDROID_APP + '/android.app.NativeActivity -e gfz "\"--info\""')

    # We wait up to timeout_seconds to let the app produce the worker info. We may have to wait
    # several seconds as the app may take some time to launch.
    timeout_seconds = 5
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        ret_code = adb('shell test -f ' + info_file).returncode
        if ret_code == 0:
            break
        time.sleep(0.1)
    if adb('shell test -f ' + info_file).returncode == 0:
        adb('pull ' + info_file)
    else:
        print('Error: cannot obtain worker information')
    adb('shell am force-stop ' + ANDROID_APP)

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

    parser.add_argument('vert', help='Vertex shader: shader.vert[.asm|.spv]')
    parser.add_argument('frag', help='Fragment shader: shader.frag[.asm|.spv]')
    parser.add_argument('json', help='Uniforms values')

    args = parser.parse_args()

    if not args.android and not args.serial and not args.linux:
        print('You must set either --android, --serial or --linux option.')
        exit(1)

    vert = prepare_shader(args.vert)
    frag = prepare_shader(args.frag)

    # These are mutually exclusive, but we return after each for clarity:

    if args.serial:
        os.environ['ANDROID_SERIAL'] = args.serial
        run_android(vert, frag, args.json, args.skip_render)
        return

    if args.android:
        run_android(vert, frag, args.json, args.skip_render)
        return

    if args.linux:
        run_linux(vert, frag, args.json, args.skip_render)
        return


if __name__ == '__main__':
    main()

