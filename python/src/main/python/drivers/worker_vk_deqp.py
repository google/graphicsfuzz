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
import base64
import json
import os
import shutil
import sys
import time
import subprocess
import platform
from subprocess import CalledProcessError

HERE = os.path.abspath(__file__)

#Set path to higher-level directory for access to dependencies
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

def fileContains(filename, s):
    with open(filename, 'r') as f:
        return s in f.read()

################################################################################

def extractTextLogs(log):
    doprint = False

    s = ''

    with open(log, 'r') as f:
        for line in f.readlines():

            if '<Text>' in line:
                # Text seem to be only one-liners, so just add this line
                # (but remove the <Text> </Text> things)
                s += line.replace('<Text>', '').replace('</Text>', '')

            if '<InfoLog>' in line:
                doprint = True

            if '</InfoLog>' in line:
                doprint = False

            if doprint:
                s += line

    return s

################################################################################

def extractImageBase64(log):
    s = ''
    doprint = False

    with open(log, 'r') as f:
        for line in f.readlines():
            if '<Image ' in line and 'Description="Result">' in line:
                doprint = True
                continue

            if '</Image>' in line:
                doprint = False

            if doprint:
                s += line.strip()

    return s

################################################################################

def getImageVulkanAndroid(frag):
    shadername = frag.replace('.frag', '')
    png = shadername + '.png'
    qpa = shadername + '.qpa'
    log = shadername + '.log'
    vk = shadername + '.vk'
    tmpimg = 'img_base64.txt'
    tmpinfolog = 'tmpinfolog.txt'
    LOGFILE = 'dEQP-Log.qpa'
    vkCalls = 'vkCalls.txt'

    remove(png)
    remove(qpa)
    remove(log)
    remove(vk)
    remove(LOGFILE)

    print('## ' + frag)

    vulkanize.vulkanize(frag, 'test')

    adb('push test.frag /sdcard/graphicsfuzz/')
    adb('push test.json /sdcard/graphicsfuzz/')

    # remove the previous log to make sure we get a fresh one
    adb('shell rm -f /sdcard/graphicsfuzz/' + LOGFILE)
    adb('shell rm -f /sdcard/graphicsfuzz/' + vkCalls)

    # clean logcat
    adb('logcat -b crash -b system -c')

    # Do NOT use the '-W' flag to wait for this command to complete, as
    # it sometimes do not seem to return and makes the whole wrapper
    # hang.
    runtestcmd = 'shell am start'
    runtestcmd += ' -n com.drawelements.deqp/android.app.NativeActivity'
    runtestcmd += ' -e'

    # Quote escapes are different depending on platform
    if platform.system() == 'Windows':
        #runtestcmd += ' cmdLine \'deqp --deqp-validation=enable --deqp-case=dEQP-VK.glsl.graphicsfuzz.hugtest'
        runtestcmd += ' cmdLine \'deqp --deqp-case=dEQP-VK.glsl.graphicsfuzz.hugtest'
        runtestcmd += ' --deqp-log-filename=/sdcard/graphicsfuzz/' + LOGFILE + '\''
    else:
        #runtestcmd += ' \'cmdLine "deqp --deqp-validation=enable --deqp-case=dEQP-VK.glsl.graphicsfuzz.hugtest'
        runtestcmd += ' \'cmdLine "deqp --deqp-case=dEQP-VK.glsl.graphicsfuzz.hugtest'
        runtestcmd += ' --deqp-log-filename=/sdcard/graphicsfuzz/' + LOGFILE + '"\''

    print('* Will run: ' + runtestcmd)

    adb(runtestcmd)

    # Try to get the logfile several times until it is completed, or
    # else it is a crash with an incomplete log file.

    sleepDelaySec = 0.5
    time.sleep(sleepDelaySec)

    logsize = 0
    tryrounds = 5

    while tryrounds > 0:
        ret = adb('pull /sdcard/graphicsfuzz/' + LOGFILE)
        if ret != 0:
            print(LOGFILE + 'does not exists on device, sleep and retry')
        else:
            if fileContains(LOGFILE, '#endTestCaseResult'):
                print(LOGFILE + ' is complete')
                break
            else:
                tmplogsize = os.path.getsize(LOGFILE)
                print(LOGFILE + ' is incomplete (size: ' + str(logsize) + '), sleep and retry')
                if tmplogsize > logsize:
                    # log file size git bigget, reset the number of tries
                    tryrounds = 5
                    logsize = tmplogsize

        tryrounds -= 1
        print('Sleep before retrying, tryrounds: ' + str(tryrounds))
        time.sleep(sleepDelaySec)

    if not(os.path.isfile(LOGFILE)):
        print('Error: failed to get any piece of logfile ' + LOGFILE + ' ?')
        return False

    os.rename(LOGFILE, qpa)

    remove(vkCalls)
    adb('pull /sdcard/graphicsfuzz/' + vkCalls)
    if not(os.path.isfile(vkCalls)):
        print('WARNING: No VK calls recorded?')
    else:
        os.rename(vkCalls, vk)

    if not(fileContains(qpa, '#endTestCaseResult')):
        print('CRASH (or at least, incomplete log)')
        return True

    # Extract logs
    with open(log, 'w') as f:
        f.write('# START EXTRACTED LOG\n')
        f.write(extractTextLogs(qpa))
        f.write('# END EXTRACTED LOG\n')

        if fileContains(qpa, '<Image '):
            f.write('# Image produced, see: ' + png + '\n')
        else:
            f.write('# No image produced\n')
            return True

    img64 = extractImageBase64(qpa)
    with open(png, 'wb') as f:
        f.write(base64.b64decode(img64))

    print('IMAGE: ' + png)
    return True

################################################################################

def doImageJob(imageJob):
    name = imageJob.name.replace('.frag','')
    fragFile = name + '.frag'
    jsonFile = name + '.json'
    png = name + '.png'
    log = name + '.log'
    qpa = name + '.qpa'
    vk  = name + '.vk'

    writeToFile(imageJob.fragmentSource, fragFile)
    writeToFile(imageJob.uniformsInfo, jsonFile)

    if os.path.isfile(png):
        os.remove(png)

    # FIXME: translate runVk.sh as a python function in this file
    # cmd = '../runVk.sh ' + fragFile

    # runVkCompleted = False

    for i in range(2):
        getimage = getImageVulkanAndroid(fragFile)

        if (getimage):
            break
        else:

            print('get image failed, restart dEQP')
            # Hugues: do not use the "-W" (wait) adb flag here, it hangs
            adb('shell am force-stop com.drawelements.deqp')
            time.sleep(0.5)
            adb('shell am start -n com.drawelements.deqp/.execserver.ServiceStarter')
            print('Sleep and retry...')
            time.sleep(0.5)

    res = tt.ImageJobResult()

    # Set nice defaults to fields we will not update anyway
    res.passSanityCheck = True

    if not(os.path.isfile(qpa)):
        # if no QPA, something went really wrong
        res.status = tt.JobStatus.UNEXPECTED_ERROR
        res.log = 'Not even a QPA file found?'

    else:
        # Store QPA as log
        with open(qpa, 'r') as f:
            res.log = f.read()

        # Vulkan calls trace
        if os.path.isfile(vk):
            res.log += '\n#### VK CALLS TRACE START\n'
            with open(vk) as f:
                res.log += f.read()
            res.log += '#### VK CALLS TRACE END\n'

        # Set status
        if os.path.isfile(png):
            res.status = tt.JobStatus.SUCCESS
            with open(png, 'rb') as f:
                res.PNG = f.read()

        elif fileContains(qpa, '#endTestCaseResult'):
            res.status = tt.JobStatus.COMPILE_ERROR

        else:
            res.status = tt.JobStatus.CRASH

            # adb log
            adb('logcat -b crash -b system -d > logcat.txt')
            res.log += '\n#### ADB LOGCAT START\n'
            with open('logcat.txt', 'r') as f:
                res.log += f.read()
            res.log += '\n#### ADB LOGCAT END\n'

    return res

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

def get_service():
    try:
        transport = THttpClient.THttpClient(server)
        transport = TTransport.TBufferedTransport(transport)
        protocol = TBinaryProtocol.TBinaryProtocol(transport)
        service = FuzzerService.Client(protocol)
        transport.open()

        # Get token

        platforminfo = '''
        {
          "clientplatform": "wrapper on dEQP vulkan"
        }
        '''

        tryToken = args.token
        print("Call getToken()")
        tokenRes = service.getToken(platforminfo, tryToken)
        assert type(tokenRes) != None
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

# Main loop

# Set device ID
if args.adbID:
    os.environ["ANDROID_SERIAL"] = args.adbID

# Prepare device
adb('shell mkdir -p /sdcard/graphicsfuzz/')

service = None

while True:

    if not(service):
        service, token = get_service()

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
