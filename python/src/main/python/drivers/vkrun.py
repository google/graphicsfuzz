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
import os
import subprocess
import time

################################################################################
# Constants

LOGFILE = 'vklog.txt'
TIMEOUT_RUN = 30

################################################################################
# Common

# prepare_shader translates a shader to binary spir-v
# shader.frag -> shader.frag.spv
# shader.vert -> shader.vert.spv
# shader.frag.asm -> shader.frag.spv
# shader.vert.asm -> shader.vert.spv
def prepare_shader(shader):
    assert(os.path.exists(shader))

    # Translate shader to spv
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
    except subprocess.TimeoutExpired as err:
        status = 'TIMEOUT'
    except subprocess.CalledProcessError as err:
        status = 'CRASH'

    with open(LOGFILE, 'a') as f:
        f.write('\nSTATUS ' + status + '\n')

def dump_info_linux():
    cmd = 'vkworker --info'
    status = 'SUCCESS'
    try:
        subprocess.run(cmd, shell=True, timeout=TIMEOUT_RUN).check_returncode()
    except subprocess.TimeoutExpired as err:
        status = 'TIMEOUT'
    except subprocess.CalledProcessError as err:
        status = 'CRASH'

    with open('STATUS', 'w') as f:
        f.write(status)

################################################################################
# Android

ANDROID_SDCARD = '/sdcard/graphicsfuzz'
ANDROID_APP = 'com.graphicsfuzz.vkworker'
TIMEOUT_APP = 30

def adb(adbargs):

    adbcmd = 'adb ' + adbargs

    try:
        p = subprocess.run(adbcmd, shell=True, timeout=TIMEOUT_RUN, stdout=subprocess.PIPE, universal_newlines=True)
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


    if skip_render:
        adb('shell touch ' + ANDROID_SDCARD + '/SKIP_RENDER')

    adb('logcat -c')
    cmd = ANDROID_APP + '/android.app.NativeActivity'
    # Explicitely set all options, don't rely on defaults
    flags = '--num-render 3 --png-template image --sanity-before sanity_before.png --sanity-after sanity_after.png'
    # Pass command line args as Intent extra. Need to nest-quote, hence the "\'blabla\'"
    cmd += '-e gfz "\'' + flags + '\'"'
    adb('shell am start ' + ANDROID_APP + '/android.app.NativeActivity')

    # Busy wait
    deadline = time.time() + TIMEOUT_APP
    crash = False
    done = False

    while time.time() < deadline:

        # Begin the busy-wait loop by sleeping to let the app start
        # properly. The 'adb shell am start' may return before the app is
        # actually started (this is all asynchronous), in which case we may not
        # be able to detect the app pid.
        time.sleep(0.1)

        retcode = adb('shell test -f ' + ANDROID_SDCARD + '/DONE').returncode
        if retcode == 0:
            done = True
            break

        retcode = adb('shell pidof ' + ANDROID_APP + ' > /dev/null').returncode
        if retcode == 1:

            # double check that no DONE file is present
            retcode = adb('shell test -f ' + ANDROID_SDCARD + '/DONE').returncode
            if retcode == 0:
                done = True
                break

            # No pid, and no DONE file, this looks like a crash indeed.
            crash = True
            break

    # Grab log
    adb('logcat -d > ' + LOGFILE)

    status = 'SUCCESS'
    if crash:
        status = 'CRASH'
    elif not done:
        status = 'TIMEOUT'

    # Check sanity
    if status == 'SUCCESS':
        sanity_png_before = ANDROID_SDCARD + '/sanity_before.png'
        sanity_png_after = ANDROID_SDCARD + '/sanity_after.png'
        sanity_before_exists = adb('shell test -f ' + sanity_png_before).returncode == 0
        sanity_after_exists = adb('shell test -f ' + sanity_png_after).returncode == 0
        if sanity_before_exists and sanity_after_exists:
            # diff the sanity images on device
            retcode = adb('shell diff ' + sanity_png_before + ' ' + sanity_png_after).returncode
            if retcode != 0:
                status = 'SANITY_ERROR'

    # Check nondet.
    if status == 'SUCCESS':
        ref_image = ANDROID_SDCARD + '/image_0.png'
        ref_image_exists = adb('shell test -f ' + ref_image).returncode == 0
        if (ref_image_exists):
            # If reference image is here, report nondet if either other images a
            # different, or missing, using return code of 'diff' on device
            for i in range(1,3):
                next_image = ANDROID_SDCARD + '/image_' + str(i) + '.png'
                retcode = adb('shell diff ' + ref_image + ' ' + next_image).returncode
                if retcode != 0:
                    status = 'NONDET'
                    adb('pull ' + ref_image + ' nondet0.png')
                    adb('pull ' + next_image + ' nondet1.png')

    with open(LOGFILE, 'a') as f:
        f.write('\nSTATUS ' + status + '\n')

    if status != 'SUCCESS':
        # Something went wrong, make sure to stop the app in any case
        adb('shell am force-stop ' + ANDROID_APP)

    # Grab image if present
    imagepath = ANDROID_SDCARD + '/image_0.png'
    retcode = adb('shell test -f ' + imagepath).returncode
    if retcode == 0:
        adb('pull ' + imagepath)

def dump_info_android():
    infofile = ANDROID_SDCARD + '/worker_info.json'
    adb('shell rm -f ' + infofile)
    adb('shell am force-stop ' + ANDROID_APP)
    adb('shell am start -n ' + ANDROID_APP + '/android.app.NativeActivity -e gfz "\"--info\""')
    deadline = time.time() + 1
    while time.time() < deadline:
        retcode = adb('shell test -f ' + infofile).returncode
        if retcode == 0:
            adb('pull ' + infofile)
        time.sleep(0.1)
    adb('shell am force-stop ' + ANDROID_APP)

################################################################################
# Main

if __name__ == '__main__':

    desc='Run shaders on vulkan worker. Output: ' + LOGFILE + ', image.png'

    parser = argparse.ArgumentParser(description=desc)

    group = parser.add_mutually_exclusive_group()
    group.add_argument('-a', '--android', action='store_true', help='Render on Android')
    group.add_argument('-i', '--serial', help='Android device serial ID. Implies --android')
    group.add_argument('-l', '--linux', action='store_true', help='Render on Linux')

    parser.add_argument('-s', '--skip-render', action='store_true', help='Skip render')

    parser.add_argument('vert', help='Vertex shader: shader.vert[.asm|.spv]')
    parser.add_argument('frag', help='Fragment shader: shader.frag[.asm|.spv]')
    parser.add_argument('json', help='Uniforms values')

    args = parser.parse_args()

    vert = prepare_shader(args.vert)
    frag = prepare_shader(args.frag)

    if args.serial:
        os.environ['ANDROID_SERIAL'] = args.serial
        run_android(vert, frag, args.json, args.skip_render)

    if args.android:
        run_android(vert, frag, args.json, args.skip_render)

    if args.linux:
        run_linux(vert, frag, args.json, args.skip_render)
