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

import vkrun

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
        p = subprocess.run(adbcmd, shell=True, timeout=TIMEOUT_ADB_CMD, stdout=subprocess.PIPE, universal_newlines=True)
    except subprocess.TimeoutExpired as err:
        print('ERROR: adb command timed out: ' + err.cmd)
        return 1
    else:
        return p

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

def doImageJob(args, imageJob):
    name = imageJob.name.replace('.frag','')
    fragFile = name + '.frag'
    jsonFile = name + '.json'
    png = 'image.png'
    log = 'vklog.txt'

    res = tt.ImageJobResult()

    skipRender = imageJob.skipRender

    # Set nice defaults to fields we will not update anyway
    res.passSanityCheck = True
    res.log = 'Start: ' + name + '\n'

    writeToFile(imageJob.fragmentSource, fragFile)
    writeToFile(imageJob.uniformsInfo, jsonFile)
    prepareShaders(fragFile)
    shutil.copy(jsonFile, 'test.json')

    # Optimize
    if args.spirvopt:
        cmd = os.path.dirname(HERE) + '/../../bin/' + getBinType() + '/spirv-opt ' + args.spirvopt + ' test.frag.spv -o test.frag.spv.opt'
        try:
            res.log += 'spirv-opt flags: ' + args.spirvopt + '\n'
            print('Calling spirv-opt with flags: ' + args.spirvopt)
            subprocess.run(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True, check=True, timeout=TIMEOUT_SPIRVOPT)
        except subprocess.CalledProcessError as err:
            # spirv-opt failed, early return
            res.log += 'Error triggered by spirv-opt\n'
            res.log += 'COMMAND:\n' + err.cmd + '\n'
            res.log += 'RETURNCODE: ' + str(err.returncode) + '\n'
            if err.stdout:
                res.log += 'STDOUT:\n' + err.stdout + '\n'
            if err.stderr:
                res.log += 'STDERR:\n' + err.stderr + '\n'
            res.status = tt.JobStatus.UNEXPECTED_ERROR
            return res
        except subprocess.TimeoutExpired as err:
            # spirv-opt timed out, early return
            res.log += 'Timeout from spirv-opt\n'
            res.log += 'COMMAND:\n' + err.cmd + '\n'
            res.log += 'TIMEOUT: ' + str(err.timeout) + ' sec\n'
            if err.stdout:
                res.log += 'STDOUT:\n' + err.stdout + '\n'
            if err.stderr:
                res.log += 'STDERR:\n' + err.stderr + '\n'
            res.status = tt.JobStatus.UNEXPECTED_ERROR
            return res

        shutil.move('test.frag.spv.opt', 'test.frag.spv')

    remove(png)
    remove(log)

    if args.linux:
        vkrun.run_linux('test.vert.spv', 'test.frag.spv', 'test.json', skipRender)
    else:
        vkrun.run_android('test.vert.spv', 'test.frag.spv', 'test.json', skipRender)

    has_log = os.path.exists(log)
    has_png = os.path.exists(png)

    if has_log:
        with open(log, 'r') as f:
            res.log += f.read()

    pngcontent = ''
    if has_png:
        with open(png, 'rb') as f:
            pngcontent = f.read()

    # Success
    if has_log and has_png:
        # may not have PNG when render_skip
        if has_png:
            res.PNG = pngcontent
        if not('GFZVK DONE' in res.log):
            res.log += '\nHas log and PNG but cannot find GFZVK DONE?\n'
            res.status = tt.JobStatus.UNEXPECTED_ERROR
        else:
            res.status = tt.JobStatus.SUCCESS
        return res

    # Error
    if has_log:

        if 'GFZVK CRASH' in res.log:
            res.status = tt.JobStatus.CRASH
        elif 'GFZVK TIMEOUT' in res.log:
            res.status = tt.JobStatus.TIMEOUT
        else:
            res.status = tt.JobStatus.UNEXPECTED_ERROR

        if has_png:
            res.PNG = pngcontent

        return res

    # Unexpected error
    res.status = tt.JobStatus.UNEXPECTED_ERROR
    if has_png:
        res.PNG = pngcontent
    if not has_log:
        res.log += 'Cannot even retrieve log?\n'
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

def isDeviceOffline(serial):
    devices = adb('devices').stdout.splitlines()
    for d in devices:
        if serial in d and 'offline' in d:
            return True
    return False

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
    '--linux',
    action='store_true',
    help='Use Linux worker')

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

if not args.linux:
    # Set device ID
    if args.adbID:
        os.environ['ANDROID_SERIAL'] = args.adbID
    else:
        if 'ANDROID_SERIAL' not in os.environ:
            print('Please set ANDROID_SERIAL env variable, or use --adbID')
            exit(1)

service = None

# Main loop
while True:

    if not args.linux and isDeviceOffline(os.environ['ANDROID_SERIAL']):
        print('#### ABORT: device is offline')
        exit(1)

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
