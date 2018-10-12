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
import io
import os
import platform
import shutil
import sys
import time
import subprocess
from subprocess import CalledProcessError

HERE = os.path.abspath(__file__)

# Set path to higher-level directory for access to dependencies
sys.path.append(
    os.path.dirname(os.path.dirname(HERE))
)

from fuzzer_service import FuzzerService
import fuzzer_service.ttypes as tt
from thrift.transport import THttpClient, TTransport
from thrift.Thrift import TApplicationException
from thrift.protocol import TBinaryProtocol

################################################################################
# Timeouts, in seconds

TIMEOUT_SPIRVOPT=120
TIMEOUT_APP=30
TIMEOUT_ADB_CMD=5

################################################################################

def writeToFile(content, filename):
    with open(filename, 'w') as f:
        f.write(content)

################################################################################

def remove(f):
    if os.path.isdir(f):
        shutil.rmtree(f)
    elif os.path.isfile(f):
        os.remove(f)

################################################################################

def adb(adbargs, serial=None):

    adbcmd = 'adb'
    if serial:
        adbcmd += ' -s {}'.format(serial)

    adbcmd += ' ' + adbargs

    try:
        p = subprocess.run(adbcmd, shell=True, timeout=TIMEOUT_ADB_CMD)
    except subprocess.TimeoutExpired as err:
        print('ERROR: adb command timed out: ' + err.cmd)
        return 1
    else:
        return p.returncode

################################################################################

def prepareVertFile():
    vertFilename = 'test.vert'
    vertFileDefaultContent = '''#version 310 es
layout(location=0) in highp vec4 a_position;
void main (void) {
  gl_Position = a_position;
}
'''
    if not os.path.isfile(vertFilename):
        writeToFile(vertFileDefaultContent, vertFilename)

################################################################################

def getBinType():
    host = platform.system()
    if host == 'Linux' or host == 'Windows':
        return host
    else:
        assert host == 'Darwin'
        return 'Mac'

################################################################################

def prepareShaders(frag):
    shutil.copy(frag, 'test.frag')
    prepareVertFile()

    glslang = os.path.dirname(HERE) + '/../../bin/' + getBinType() + '/glslangValidator'

    # Frag
    cmd = glslang + ' test.frag -V -o test.frag.spv'
    subprocess.run(cmd, shell=True, check=True)

    # Vert
    cmd = glslang + ' test.vert -V -o test.vert.spv'
    subprocess.run(cmd, shell=True, check=True)

################################################################################

def getImageVulkanAndroid(args, frag):

    app = 'vulkan.samples.vulkan_worker'

    print('## ' + frag)

    remove('image.ppm')
    remove('image.png')
    adb('shell rm -rf /sdcard/graphicsfuzz/*')

    prepareShaders(frag)

    # Optimize
    if args.spirvopt:
        cmd = os.path.dirname(HERE) + '/../../bin/' + getBinType() + '/spirv-opt ' + args.spirvopt + ' test.frag.spv -o test.frag.spv.opt'
        try:
            print('Calling spirv-opt')
            subprocess.run(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True, check=True, timeout=TIMEOUT_SPIRVOPT)
        except subprocess.CalledProcessError as err:
            # spirv-opt failed, early return
            with open('log.txt', 'w') as f:
                f.write('Error triggered by spirv-opt\n')
                f.write('COMMAND:\n' + err.cmd + '\n')
                f.write('RETURNCODE: ' + str(err.returncode) + '\n')
                if err.stdout:
                    f.write('STDOUT:\n' + err.stdout + '\n')
                if err.stderr:
                    f.write('STDERR:\n' + err.stderr + '\n')
            return 'err_spirvopt'
        except subprocess.TimeoutExpired as err:
            # spirv-opt timed out, early return
            with open('log.txt', 'w') as f:
                f.write('Timeout from spirv-opt\n')
                f.write('COMMAND:\n' + err.cmd + '\n')
                f.write('TIMEOUT: ' + str(err.timeout) + ' sec\n')
                if err.stdout:
                    f.write('STDOUT:\n' + err.stdout + '\n')
                if err.stderr:
                    f.write('STDERR:\n' + err.stderr + '\n')
            return 'err_spirvopt'

        shutil.move('test.frag.spv.opt', 'test.frag.spv')

    # FIXME: Clean up preparation of shader files. Right now it's
    # convenient to have a copy of the JSON with the original name of
    # the variant, for debugging purpose, but otherwise it is a
    # redundant file.
    jsonFile = frag.replace('.frag', '.json')
    shutil.copy(jsonFile, 'test.json')

    adb('push test.vert.spv test.frag.spv test.json /sdcard/graphicsfuzz/')

    # clean all buffers of logcat
    adb('logcat -b all -c')

    runtestcmd = 'shell am start'
    runtestcmd += ' -n ' + app + '/android.app.NativeActivity'

    print('* Will run: ' + runtestcmd)
    adb(runtestcmd)

    # Wait for DONE file, or timeout
    deadline = time.time() + TIMEOUT_APP

    crash = False
    done = False

    # Busy-wait on the worker (not ideal, but there is no simple way to receive
    # a signal when the app is done)
    while time.time() < deadline:

        # Begin the busy-wait loop by sleeping to let the app start
        # properly. Apparently the -W flag of adb is not enough to be sure the
        # app has started, which can lead to failure to detect pid for this app
        # although it is being started.
        time.sleep(0.1)

        retcode = adb('shell test -f /sdcard/graphicsfuzz/DONE')
        if retcode == 0:
            done = True
            break

        retcode = adb('shell pidof ' + app + ' > /dev/null')
        if retcode == 1:

            # double check that no DONE file is present
            retcode = adb('shell test -f /sdcard/graphicsfuzz/DONE')
            if retcode == 0:
                done = True
                break

            # No pid, and no DONE file, this looks like a crash indeed.
            crash = True
            break

    # Try to retrieve the log file in any case
    adb('pull /sdcard/graphicsfuzz/log.txt')

    if crash:
        print('Crash detected')
        return 'crash'

    if not done:
        print('Timeout detected (force-stop app)')
        adb('shell am force-stop ' + app)
        return 'timeout'

    # Get the image and convert it to PNG
    adb('pull /sdcard/graphicsfuzz/image.ppm')
    if os.path.exists('image.ppm'):
        subprocess.run('convert image.ppm image.png', shell=True)
        return 'success'
    else:
        with open('log.txt', 'a') as f:
            f.write('\nWEIRD ERROR: No crash detected but no image found on device ??\n')
        return 'unexpected_error'

################################################################################

def doImageJob(args, imageJob):
    name = imageJob.name.replace('.frag','')
    fragFile = name + '.frag'
    jsonFile = name + '.json'
    png = 'image.png'
    log = 'log.txt'

    res = tt.ImageJobResult()

    # Set nice defaults to fields we will not update anyway
    res.passSanityCheck = True
    res.log = 'Start: ' + name + '\n'

    writeToFile(imageJob.fragmentSource, fragFile)
    writeToFile(imageJob.uniformsInfo, jsonFile)

    remove(png)
    remove(log)

    getimageResult = getImageVulkanAndroid(args, fragFile)

    # Try to get our own log file in any case
    if os.path.exists('log.txt'):
        res.log += '\n#### LOG START\n'
        with open(log, 'r') as f:
            res.log += f.read()
        res.log += '\n#### LOG END\n'

    # Early return if something failed even before we started the app
    if getimageResult == 'err_spirvopt':
        res.status = tt.JobStatus.UNEXPECTED_ERROR
        return res

    # Always add ADB logcat
    res.log += '\n#### ADB LOGCAT START\n'
    adb('logcat -b crash -b system -b main -b events -d > logcat.txt')
    if os.path.exists('logcat.txt'):
        # ADB logcat may have characters that do not respect UTF-8, so ignore errors
        with io.open('logcat.txt', 'r', errors='ignore') as f:
            res.log += f.read()
    else:
        res.log += 'Cannot even retrieve ADB logcat ??'
    res.log += '\n#### ADB LOGCAT END\n'

    if getimageResult == 'crash':
        res.status = tt.JobStatus.CRASH
    elif getimageResult == 'timeout':
        res.status = tt.JobStatus.TIMEOUT
    elif getimageResult == 'unexpected_error':
        res.status = tt.JobStatus.UNEXPECTED_ERROR
    else:
        assert(getimageResult == 'success')
        res.status = tt.JobStatus.SUCCESS
        with open(png, 'rb') as f:
            res.PNG = f.read()

    return res

################################################################################

def get_service(server, args):
    try:
        httpClient = THttpClient.THttpClient(server)
        transport = TTransport.TBufferedTransport(httpClient)
        protocol = TBinaryProtocol.TBinaryProtocol(transport)
        service = FuzzerService.Client(protocol)
        transport.open()

        # Get token

        # TODO: grab information from worker

        platforminfo = '''
        {
          "clientplatform": "Wrapper on vulkan"
        }
        '''

        tryToken = args.token
        print("Call getToken()")
        tokenRes = service.getToken(platforminfo, tryToken)
        assert type(tokenRes) != None

        if tokenRes.token == None:
            print('Token error: ' + tt.TokenError._VALUES_TO_NAMES[tokenRes.error])
            exit(1)

        token = tokenRes.token

        print("Got token: " + token)
        assert(token == args.token)

        if not os.path.exists(args.token):
            os.makedirs(args.token)

        # Set working dir
        os.chdir(args.token)

        return service, token

    except (TApplicationException, ConnectionRefusedError, ConnectionResetError) as exception:
        return None, None

################################################################################
# Main

parser = argparse.ArgumentParser()

parser.add_argument(
    'token',
    help='Worker token to identify to the server')

parser.add_argument(
    '--adbID',
    help='adb (Android Debug Bridge) ID of the device to run tests on. Run "adb devices" to list these IDs')

parser.add_argument(
    '--server',
    default='http://localhost:8080',
    help='Server URL (default: http://localhost:8080 )')

parser.add_argument(
    '--spirvopt',
    help='Enable spirv-opt with these optimisation flags (e.g. --spirvopt=-O)')

args = parser.parse_args()

print('token: ' + args.token)

server = args.server + '/request'
print('server: ' + server)

# Set device ID
if args.adbID:
    os.environ["ANDROID_SERIAL"] = args.adbID

# Prepare device
adb('shell mkdir -p /sdcard/graphicsfuzz/')

service = None

# Main loop
while True:

    if not(service):
        service, token = get_service(server, args)

        if not(service):
            print("Cannot connect to server, retry in a second...")
            time.sleep(1)
            continue

    try:
        job = service.getJob(token)

        if job.noJob != None:
            print("No job")

        elif job.skipJob != None:
            print("Skip job")
            service.jobDone(token, job)

        else:
            assert(job.imageJob != None)
            print("#### Image job: " + job.imageJob.name)
            job.imageJob.result = doImageJob(args, job.imageJob)
            print("Send back, results status: {}".format(job.imageJob.result.status))
            service.jobDone(token, job)

    except (TApplicationException, ConnectionError) as exception:
        print("Connection to server lost. Re-initialising client.")
        service = None

    time.sleep(1)
