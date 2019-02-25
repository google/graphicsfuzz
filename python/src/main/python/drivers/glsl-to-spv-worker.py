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

import runspv

HERE = os.path.abspath(__file__)

# Add directory above to Python path for access to dependencies.
# Prepend it so we override any globally installed dependencies.
sys.path.insert(0, os.path.dirname(os.path.dirname(HERE)))

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

orig_print = print


# noinspection PyShadowingBuiltins
def print(s):
    orig_print(s, flush=True)


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


def remove_end(str_in: str, str_end: str):
    assert str_in.endswith(str_end), 'Expected {} to end with {}'.format(str_in, str_end)
    return str_in[:-len(str_end)]


################################################################################


def get_bin_type():
    host = platform.system()
    if host == 'Linux' or host == 'Windows':
        return host
    else:
        assert host == 'Darwin'
        return 'Mac'


################################################################################


def glsl2spv(glsl, spv):
    glslang = os.path.dirname(HERE) + '/../../bin/' + get_bin_type() + '/glslangValidator'
    cmd = glslang + ' ' + glsl + ' -V -o ' + spv
    subprocess.run(cmd, shell=True, check=True)


################################################################################


def prepare_shaders(frag_file, frag_spv_file, vert_spv_file):
    # Vert
    prepare_vert_file()
    glsl2spv('test.vert', vert_spv_file)

    # Frag
    glsl2spv(frag_file, frag_spv_file)


################################################################################


def run_spirv_opt(spv_file, args):
    print("Running optimizer.")
    spv_file_opt = spv_file + '.opt'
    cmd = os.path.dirname(HERE) + '/../../bin/' + get_bin_type() + '/spirv-opt ' + \
        args.spirvopt + ' ' + spv_file + ' -o ' + spv_file_opt
    log = ""

    success = True

    try:
        log += 'spirv-opt flags: ' + args.spirvopt + '\n'
        print('Calling spirv-opt with flags: ' + args.spirvopt)
        subprocess.run(
            cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            universal_newlines=True, check=True, timeout=TIMEOUT_SPIRV_OPT)
    except subprocess.CalledProcessError as err:
        # spirv-opt failed
        success = False
        log += 'Error triggered by spirv-opt\n'
        log += 'COMMAND:\n' + err.cmd + '\n'
        log += 'RETURNCODE: ' + str(err.returncode) + '\n'
        if err.stdout:
            log += 'STDOUT:\n' + err.stdout + '\n'
        if err.stderr:
            log += 'STDERR:\n' + err.stderr + '\n'
    except subprocess.TimeoutExpired as err:
        success = False
        # spirv-opt timed out
        log += 'Timeout from spirv-opt\n'
        log += 'COMMAND:\n' + err.cmd + '\n'
        log += 'TIMEOUT: ' + str(err.timeout) + ' sec\n'
        if err.stdout:
            log += 'STDOUT:\n' + err.stdout + '\n'
        if err.stderr:
            log += 'STDERR:\n' + err.stderr + '\n'

    # Return the name of the optimized file, and the error log.  If the error
    # log is non-empty, the optimized file should be ignored.
    return spv_file_opt, log, success


################################################################################


def do_image_job(args, image_job):
    name = image_job.name
    if name.endswith('.frag'):
        name = remove_end(name, '.frag')
    frag_file = name + '.frag'
    json_file = name + '.json'
    png = 'image_0.png'

    frag_spv_file = name + '.frag.spv'
    vert_spv_file = name + '.vert.spv'

    res = tt.ImageJobResult()

    skip_render = image_job.skipRender

    # Set nice defaults to fields we will not update anyway
    res.passSanityCheck = True
    res.log = 'Start: ' + name + '\n'

    write_to_file(image_job.fragmentSource, frag_file)
    write_to_file(image_job.uniformsInfo, json_file)

    # Shader preparation may fail for invalid shaders
    try:
        prepare_shaders(frag_file, frag_spv_file, vert_spv_file)
    except subprocess.CalledProcessError as err:
        res.log += 'ERROR\n'
        res.log += 'COMMAND: {}\n'.format(err.cmd)
        res.log += 'RETURNCODE: {}\n'.format(err.returncode)
        res.log += 'STDOUT: {}\n'.format(err.stdout)
        res.log += 'STDERR: {}\n'.format(err.stderr)
        res.status = tt.JobStatus.UNEXPECTED_ERROR
        return res

    # Optimize
    if args.spirvopt:
        frag_spv_file, log, success = run_spirv_opt(frag_spv_file, args)
        if not success:
            res.status = tt.JobStatus.UNEXPECTED_ERROR
            return res

    remove(png)
    remove(runspv.LOGFILE_NAME)

    if args.legacy_worker:
        if args.target == 'host':
            runspv.run_image_host_legacy(
                vert=vert_spv_file,
                frag=frag_spv_file,
                json=json_file,
                output_dir=os.getcwd(),
                skip_render=skip_render)
        else:
            assert args.target == 'android'
            runspv.run_image_android_legacy(
                vert=vert_spv_file,
                frag=frag_spv_file,
                json=json_file,
                output_dir=os.getcwd(),
                force=args.force,
                skip_render=skip_render)
    else:
        runspv.run_image_amber(
            vert=vert_spv_file,
            frag=frag_spv_file,
            json=json_file,
            output_dir=os.getcwd(),
            force=args.force,
            is_android=(args.target == 'android'))

    if os.path.isfile(runspv.LOGFILE_NAME):
        with open(runspv.LOGFILE_NAME, 'r', encoding='utf-8', errors='ignore') as f:
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


def do_compute_job(args, comp_job):
    ssbo = 'ssbo'
    tmpcomp = 'tmp.comp'
    tmpcompspv = 'tmp.comp.spv'
    write_to_file(comp_job.computeSource, tmpcomp)
    glsl2spv(tmpcomp, tmpcompspv)

    tmpjson = 'tmp.json'
    write_to_file(comp_job.computeInfo, tmpjson)

    remove(ssbo)

    res = tt.ImageJobResult()
    res.log = '#### Start compute shader\n\n'

    if args.spirvopt:
        tmpcompspv, log, success = run_spirv_opt(tmpcompspv, args)
        res.log += log
        if not success:
            res.status = tt.JobStatus.UNEXPECTED_ERROR
            return res

    assert not args.legacy_worker
    runspv.run_compute_amber(
        comp=tmpcompspv,
        json=tmpjson,
        output_dir=os.getcwd(),
        force=args.force,
        is_android=(args.target == 'android')
    )

    if os.path.isfile(runspv.LOGFILE_NAME):
        with open(runspv.LOGFILE_NAME, 'r', encoding='utf-8', errors='ignore') as f:
            res.log += f.read()

    if os.path.isfile('STATUS'):
        with open('STATUS', 'r') as f:
            status = f.read().rstrip()
        if status == 'SUCCESS':
            res.status = tt.JobStatus.SUCCESS
            assert (os.path.isfile('ssbo.json'))
            with open('ssbo.json', 'r') as f:
                res.computeOutputs = f.read()

        elif status == 'CRASH':
            res.status = tt.JobStatus.CRASH
        elif status == 'TIMEOUT':
            res.status = tt.JobStatus.TIMEOUT
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
        assert (worker == args.worker)

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
                             timeout=runspv.TIMEOUT_RUN).stdout.splitlines()
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

    # Required arguments
    parser.add_argument(
        'worker',
        help='Worker name to identify to the server')

    parser.add_argument(
        'target',
        help=runspv.TARGET_HELP)

    # Optional arguments
    parser.add_argument(
        '--force',
        action='store_true',
        help=runspv.FORCE_OPTION_HELP)

    parser.add_argument(
        '--legacy-worker',
        action='store_true',
        help=runspv.LEGACY_OPTION_HELP)

    parser.add_argument(
        '--serial',
        help=runspv.SERIAL_OPTION_HELP)

    parser.add_argument(
        '--server',
        default='http://localhost:8080',
        help='Server URL (default: http://localhost:8080 )')

    parser.add_argument(
        '--spirvopt',
        help='Enable spirv-opt with these optimisation flags (e.g. --spirvopt=-O)')

    parser.add_argument(
        '--local-shader-job',
        help='Execute a single, locally stored shader job (for debugging), instead of using the '
             'server.')

    args = parser.parse_args()

    # Check the target is known.
    if not (args.target == 'android' or args.target == 'host'):
        raise ValueError('Target must be "android" or "host"')

    # Record whether or not we are targeting Android.
    is_android = (args.target == 'android')

    # Check the optional arguments are consistent with the target.
    if not is_android and args.force:
        raise ValueError('"force" option is only compatible with "android" target')

    if not is_android and args.serial:
        raise ValueError('"serial" option is only compatible with "android" target')

    print('Worker: ' + args.worker)

    server = args.server + '/request'
    print('server: ' + server)

    if args.serial:
        os.environ['ANDROID_SERIAL'] = args.serial

    service = None
    worker = None

    # Get worker info
    worker_info_file = 'worker_info.json'
    remove(worker_info_file)

    if is_android:
        runspv.dump_info_android_legacy(wait_for_screen=not args.force)
    else:
        runspv.dump_info_host_legacy()

    if not os.path.isfile(worker_info_file):
        raise Exception('Failed to retrieve worker information.  If targeting Android, make sure '
                        'the app permission to write to external storage is enabled.')

    with open(worker_info_file, 'r') as f:
        worker_info_json_string = f.read()

    # Main loop
    while True:

        if is_android \
            and 'ANDROID_SERIAL' in os.environ and \
                not is_device_available(os.environ['ANDROID_SERIAL']):
            raise Exception(
                '#### ABORT: device {} is not available (either offline or not connected?)'
                .format(os.environ['ANDROID_SERIAL'])
            )

        # Special case: local shader job for debugging.
        if args.local_shader_job:
            assert args.local_shader_job.endswith('.json'), \
                'Expected local shader job "{}" to end with .json'

            shader_job_prefix = remove_end(args.local_shader_job, '.json')

            fake_job = tt.ImageJob()
            fake_job.name = os.path.basename(shader_job_prefix)

            assert os.path.isfile(args.local_shader_job), \
                'Shader job {} does not exist'.format(args.local_shader_job)
            with open(args.local_shader_job, 'r', encoding='utf-8', errors='ignore') as f:
                fake_job.uniformsInfo = f.read()
            if os.path.isfile(shader_job_prefix + '.frag'):
                with open(shader_job_prefix + '.frag', 'r', encoding='utf-8', errors='ignore') as f:
                    fake_job.fragmentSource = f.read()
            if os.path.isfile(shader_job_prefix + '.vert'):
                with open(shader_job_prefix + '.vert', 'r', encoding='utf-8', errors='ignore') as f:
                    fake_job.vertexSource = f.read()
            if os.path.isfile(shader_job_prefix + '.comp'):
                with open(shader_job_prefix + '.comp', 'r', encoding='utf-8', errors='ignore') as f:
                    fake_job.computeSource = f.read()
                fake_job.computeInfo = fake_job.uniformsInfo
            do_image_job(args, fake_job)
            return

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

                if job.imageJob.computeSource:
                    print("#### Compute job: " + job.imageJob.name)
                    job.imageJob.result = do_compute_job(args, job.imageJob)

                else:
                    print("#### Image job: " + job.imageJob.name)
                    job.imageJob.result = do_image_job(args, job.imageJob)

                print("Send back, results status: {}".format(job.imageJob.result.status))
                service.jobDone(worker, job)
                continue

        except (TApplicationException, ConnectionError):
            print("Connection to server lost. Re-initialising client.")
            service = None

        time.sleep(1)


if __name__ == '__main__':
    main()
