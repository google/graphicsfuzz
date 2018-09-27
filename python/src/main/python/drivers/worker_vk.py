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

import vulkanize

from fuzzer_service import FuzzerService
import fuzzer_service.ttypes as tt
from thrift.transport import THttpClient, TTransport
from thrift.Thrift import TApplicationException
from thrift.protocol import TBinaryProtocol

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

    p = subprocess.run(adbcmd, shell=True)
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

def prepareShaders(frag):
    vulkanize.vulkanize(frag, 'test')
    prepareVertFile()

    host = platform.system()
    binType = ''
    if host == 'Linux' or host == 'Windows':
        binType = host
    else:
        assert host == 'Darwin'
        binType = 'Mac'
    glslang = os.path.dirname(HERE) + '/../../bin/' + binType + '/glslangValidator'

    # Frag
    cmd = glslang + ' test.frag -V -o test.frag.spv'
    subprocess.run(cmd, shell=True, check=True)

    # Vert
    cmd = glslang + ' test.vert -V -o test.vert.spv'
    subprocess.run(cmd, shell=True, check=True)

################################################################################

def getImageVulkanAndroid(frag):

    app = 'vulkan.samples.T15_draw_cube'

    print('## ' + frag)

    remove('image.ppm')
    remove('image.png')
    adb('shell rm -rf /sdcard/graphicsfuzz/*')

    prepareShaders(frag)

    adb('push test.vert.spv test.frag.spv test.json /sdcard/graphicsfuzz/')

    # clean logcat
    adb('logcat -b crash -b system -c')

    runtestcmd = 'shell am start'
    runtestcmd += ' -n ' + app + '/android.app.NativeActivity'

    print('* Will run: ' + runtestcmd)
    adb(runtestcmd)

    # Wait for DONE file, or timeout
    timeoutSec = 30
    deadline = time.time() + timeoutSec

    crash = False
    done = False

    while time.time() < deadline:

        retcode = adb('shell pidof ' + app + ' > /dev/null')
        if retcode == 1:
            crash = True
            break

        retcode = adb('shell test -f /sdcard/graphicsfuzz/DONE')
        if retcode == 0:
            done = True
            break
        else:
            time.sleep(0.1)

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
    subprocess.run('convert image.ppm image.png', shell=True)

    return 'success'

################################################################################

def doImageJob(imageJob):
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

    getimageResult = getImageVulkanAndroid(fragFile)

    # Try to get our own log file in any case
    if os.path.exists('log.txt'):
        res.log += '\n#### LOG START\n'
        with open(log, 'r') as f:
            res.log += f.read()
        res.log += '\n#### LOG END\n'

    # Always add ADB logcat
    adb('logcat -b crash -b system -d > logcat.txt')
    res.log += '\n#### ADB LOGCAT START\n'
    with open('logcat.txt', 'r') as f:
        res.log += f.read()
    res.log += '\n#### ADB LOGCAT END\n'

    if getimageResult == 'crash':
        res.status = tt.JobStatus.CRASH
    elif getimageResult == 'timeout':
        res.status = tt.JobStatus.TIMEOUT
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
            job.imageJob.result = doImageJob(job.imageJob)
            print("Send back, results status: {}".format(job.imageJob.result.status))
            service.jobDone(token, job)

    except (TApplicationException, ConnectionError) as exception:
        print("Connection to server lost. Re-initialising client.")
        service = None

    time.sleep(1)
