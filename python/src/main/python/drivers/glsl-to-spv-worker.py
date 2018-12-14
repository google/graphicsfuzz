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
import subprocess
import sys
import time

import vkrun

HERE = os.path.abspath(__file__)

# Set path to higher-level directory for access to dependencies
sys.path.append(
    os.path.dirname(os.path.dirname(HERE))
)

# noinspection PyPep8
from fuzzer_service import FuzzerService
# noinspection PyPep8
import fuzzer_service.ttypes as tt
# noinspection PyPep8
from thrift.transport import THttpClient, TTransport
# noinspection PyPep8
from thrift.Thrift import TApplicationException
# noinspection PyPep8
from thrift.protocol import TBinaryProtocol


################################################################################
# Timeouts, in seconds


TIMEOUT_SPIRV_OPT = 120
TIMEOUT_APP = 30
TIMEOUT_ADB_CMD = 5

################################################################################


def write_to_file(content, filename):
    with open(filename, 'w') as f:
        f.write(content)

################################################################################


def remove(f):
    if os.path.isdir(f):
        shutil.rmtree(f)
    elif os.path.isfile(f):
        os.remove(f)

################################################################################


def prepare_vert_file():
    vert_filename = 'test.vert'
    vert_file_default_content = '''#version 310 es
layout(location=0) in highp vec4 a_position;
void main (void) {
  gl_Position = a_position;
}
'''
    if not os.path.isfile(vert_filename):
        write_to_file(vert_file_default_content, vert_filename)

################################################################################


def get_bin_type():
    host = platform.system()
    if host == 'Linux' or host == 'Windows':
        return host
    else:
        assert host == 'Darwin'
        return 'Mac'

################################################################################


def prepare_shaders(frag_file, frag_spv_file, vert_spv_file):
    prepare_vert_file()

    glslang = os.path.dirname(HERE) + '/../../bin/' + get_bin_type() + '/glslangValidator'

    # Frag
    cmd = glslang + ' ' + frag_file + ' -V -o ' + frag_spv_file
    subprocess.run(cmd, shell=True, check=True)

    # Vert
    cmd = glslang + ' test.vert -V -o ' + vert_spv_file
    subprocess.run(cmd, shell=True, check=True)

################################################################################


def do_image_job(args, image_job):
    name = image_job.name[:-5]
    frag_file = name + '.frag'
    json_file = name + '.json'
    png = 'image_0.png'
    log = 'vklog.txt'

    frag_spv_file = name + '.frag.spv'
    vert_spv_file = name + '.vert.spv'

    res = tt.ImageJobResult()

    skip_render = image_job.skipRender

    # Set nice defaults to fields we will not update anyway
    res.passSanityCheck = True
    res.log = 'Start: ' + name + '\n'

    write_to_file(image_job.fragmentSource, frag_file)
    write_to_file(image_job.uniformsInfo, json_file)
    prepare_shaders(frag_file, frag_spv_file, vert_spv_file)

    # Optimize
    if args.spirvopt:
        frag_spv_file_opt = frag_spv_file + '.opt'
        cmd = os.path.dirname(HERE) + '/../../bin/' + get_bin_type() + '/spirv-opt ' + \
            args.spirvopt + ' ' + frag_spv_file + ' -o ' + frag_spv_file_opt

        try:
            res.log += 'spirv-opt flags: ' + args.spirvopt + '\n'
            print('Calling spirv-opt with flags: ' + args.spirvopt)
            subprocess.run(
                cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                universal_newlines=True, check=True, timeout=TIMEOUT_SPIRV_OPT)
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

        frag_spv_file = frag_spv_file_opt

    remove(png)
    remove(log)

    if args.linux:
        vkrun.run_linux(vert_spv_file, frag_spv_file, json_file, skip_render)
    else:
        vkrun.run_android(vert_spv_file, frag_spv_file, json_file, skip_render)

    if os.path.isfile(log):
        with open(log, 'r', encoding='utf-8', errors='ignore') as f:
            res.log += f.read()

    if os.path.isfile(png):
        with open(png, 'rb') as f:
            res.PNG = f.read()

    if os.path.isfile('STATUS'):
        with open('STATUS', 'r') as f:
            status = f.read().rstrip()
        if status == 'SUCCESS':
            res.status = tt.JobStatus.SUCCESS
        elif status == 'CRASH':
            res.status = tt.JobStatus.CRASH
        elif status == 'TIMEOUT':
            res.status = tt.JobStatus.TIMEOUT
        elif status == 'SANITY_ERROR':
            res.status = tt.JobStatus.SANITY_ERROR
        elif status == 'UNEXPECTED_ERROR':
            res.status = tt.JobStatus.UNEXPECTED_ERROR
        elif status == 'NONDET':
            res.status = tt.JobStatus.NONDET
            with open('nondet0.png', 'rb') as f:
                res.PNG = f.read()
            with open('nondet1.png', 'rb') as f:
                res.PNG2 = f.read()
        else:
            res.log += '\nUnknown status value: ' + status + '\n'
            res.status = tt.JobStatus.UNEXPECTED_ERROR
    else:
        # Not even a status file?
        res.log += '\nNo STATUS file\n'
        res.status = tt.JobStatus.UNEXPECTED_ERROR

    return res

################################################################################


def get_service(server, args, worker_info_json_string):
    try:
        http_client = THttpClient.THttpClient(server)
        transport = TTransport.TBufferedTransport(http_client)
        protocol = TBinaryProtocol.TBinaryProtocol(transport)
        service = FuzzerService.Client(protocol)
        transport.open()

        # Get worker name
        platform_info = worker_info_json_string

        try_worker = args.worker
        print("Call getWorkerName()")
        worker_res = service.getWorkerName(platform_info, try_worker)
        assert type(worker_res) is not None

        if worker_res.workerName is None:
            # noinspection PyProtectedMember
            print('Worker error: ' + tt.WorkerNameError._VALUES_TO_NAMES[worker_res.error])
            exit(1)

        worker = worker_res.workerName

        print("Got worker: " + worker)
        assert(worker == args.worker)

        os.makedirs(args.worker, exist_ok=True)

        # Set working dir
        os.chdir(args.worker)

        return service, worker

    except (TApplicationException, ConnectionRefusedError, ConnectionResetError):
        return None, None

################################################################################


def is_device_available(serial):
    cmd = 'adb devices'
    devices = subprocess.run(cmd, shell=True, universal_newlines=True, stdout=subprocess.PIPE,
                             timeout=2).stdout.splitlines()
    for line in devices:
        if serial in line:
            parts = line.split()
            if parts[1] == 'device':
                return True
            else:
                return False
    # Here the serial number was not present in `adb devices` output
    return False

################################################################################
# Main


def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        'worker',
        help='Worker name to identify to the server')

    parser.add_argument(
        '--serial',
        help='Serial number of device to target. Run "adb devices -l" to list the serial numbers. '
             'The serial number will have the form "IP:port" if using adb over TCP. '
             'See: https://developer.android.com/studio/command-line/adb')

    parser.add_argument(
        '--linux',
        action='store_true',
        help='Use Linux worker')

    parser.add_argument(
        '--adb-no-serial',
        action='store_true',
        help='Use adb without providing a device serial number; '
             'this works if "adb devices" just shows one connected device.')

    parser.add_argument(
        '--server',
        default='http://localhost:8080',
        help='Server URL (default: http://localhost:8080 )')

    parser.add_argument(
        '--spirvopt',
        help='Enable spirv-opt with these optimisation flags (e.g. --spirvopt=-O)')

    args = parser.parse_args()

    print('Worker: ' + args.worker)

    server = args.server + '/request'
    print('server: ' + server)

    if not args.linux and not args.adb_no_serial:
        # Set device serial number
        if args.serial:
            os.environ['ANDROID_SERIAL'] = args.serial
        else:
            if 'ANDROID_SERIAL' not in os.environ:
                print('Please set ANDROID_SERIAL env variable, or use --serial or --adb-no-serial.')
                print('Use "adb devices -l" to find the device serial number,'
                      ' which will have the form "IP:port" if using adb over TCP. '
                      'See: https://developer.android.com/studio/command-line/adb')
                exit(1)

    service = None
    worker = None

    # Get worker info
    worker_info_file = 'worker_info.json'
    remove(worker_info_file)

    if args.linux:
        vkrun.dump_info_linux()
    else:
        vkrun.dump_info_android()

    if not os.path.isfile(worker_info_file):
        print('Failed to retrieve worker information. '
              'Make sure the app permission to write to external storage is enabled.')
        exit(1)

    # noinspection PyUnusedLocal
    worker_info_json_string = '{}'  # Default value: dummy but valid JSON string
    with open(worker_info_file, 'r') as f:
        worker_info_json_string = f.read()

    # Main loop
    while True:

        if not args.linux \
                and not args.adb_no_serial \
                and not is_device_available(os.environ['ANDROID_SERIAL']):
            print('#### ABORT: device {} is not available (either offline or not connected?)'
                  .format(os.environ['ANDROID_SERIAL']))
            exit(1)

        if not service:
            service, worker = get_service(server, args, worker_info_json_string)

            if not service:
                print("Cannot connect to server, retry in a second...")
                time.sleep(1)
                continue

        assert worker is not None

        try:
            job = service.getJob(worker)

            if job.noJob is not None:
                print("No job")
            elif job.skipJob is not None:
                print("Skip job")
                service.jobDone(worker, job)
            else:
                assert job.imageJob is not None
                print("#### Image job: " + job.imageJob.name)
                job.imageJob.result = do_image_job(args, job.imageJob)
                print("Send back, results status: {}".format(job.imageJob.result.status))
                service.jobDone(worker, job)
        except (TApplicationException, ConnectionError):
            print("Connection to server lost. Re-initialising client.")
            service = None

        time.sleep(1)


if __name__ == '__main__':
    main()
