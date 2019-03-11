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
import random
import shutil
import subprocess
import sys
import time
from typing import Optional, List

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


TIMEOUT_APP = 30
TIMEOUT_ADB_CMD = 5

################################################################################

orig_print = print


# noinspection PyShadowingBuiltins
def print(s):
    orig_print(s, flush=True)


################################################################################


def write_to_file(content, filename):
    with runspv.open_helper(filename, 'w') as f:
        f.write(content)


################################################################################


def remove(f):
    if os.path.isdir(f):
        shutil.rmtree(f)
    elif os.path.isfile(f):
        os.remove(f)


################################################################################


def prepare_vert_file(output_dir: str) -> str:
    vert_file = os.path.join(output_dir, 'test.vert')
    if not os.path.isfile(vert_file):
        vert_file_default_content = '''#version 310 es
layout(location=0) in highp vec4 a_position;
void main (void) {
  gl_Position = a_position;
}
'''
        write_to_file(vert_file_default_content, vert_file)
    return vert_file


################################################################################


def remove_end(str_in: str, str_end: str):
    assert str_in.endswith(str_end), 'Expected {} to end with {}'.format(str_in, str_end)
    return str_in[:-len(str_end)]


################################################################################

OPT_OPTIONS = ['--ccp',
               '--combine-access-chains',
               '--convert-local-access-chains',
               '--copy-propagate-arrays',
               '--eliminate-dead-branches',
               '--eliminate-dead-code-aggressive',
               '--eliminate-dead-inserts',
               '--eliminate-local-multi-store',
               '--eliminate-local-single-block',
               '--eliminate-local-single-store',
               '--if-conversion',
               '--inline-entry-points-exhaustive',
               '--merge-blocks',
               '--merge-return',
               '--private-to-local',
               '--reduce-load-size',
               '--redundancy-elimination',
               '--scalar-replacement=100',
               '--simplify-instructions',
               '--vector-dce',
               ]

MAX_OPT_ARGS = 30


def random_spirvopt_args() -> List[str]:
    result = []
    num_args = random.randint(0, MAX_OPT_ARGS)
    for i in range(0, num_args):
        arg = OPT_OPTIONS[random.randint(0, len(OPT_OPTIONS) - 1)]
        if arg == '--merge-return':
            result.append('--eliminate-dead-code-aggressive')
        result.append(arg)
    return result


################################################################################


def do_image_job(
    args,
    image_job,
    spirv_opt_args: Optional[List[str]],
    work_dir: str
) -> tt.ImageJobResult:

    # Output directory is based on the name of job.
    output_dir = os.path.join(work_dir, image_job.name)

    # Delete and create output directory.
    remove(output_dir)
    os.makedirs(output_dir, exist_ok=True)

    name = image_job.name
    if name.endswith('.frag'):
        name = remove_end(name, '.frag')
    # TODO(324): the worker currently assumes that no vertex shader is present in the image job.

    vert_file = prepare_vert_file(output_dir) if args.legacy_worker else None
    frag_file = os.path.join(output_dir, name + '.frag')
    json_file = os.path.join(output_dir, name + '.json')
    png_file = os.path.join(output_dir, 'image_0.png')
    log_file = os.path.join(output_dir, runspv.LOGFILE_NAME)
    status_file = os.path.join(output_dir, 'STATUS')
    nondet_0 = os.path.join(output_dir, 'nondet0.png')
    nondet_1 = os.path.join(output_dir, 'nondet1.png')

    res = tt.ImageJobResult()

    skip_render = image_job.skipRender

    # Set nice defaults to fields we will not update anyway
    res.passSanityCheck = True
    res.log = 'Start: ' + name + '\n'

    write_to_file(image_job.fragmentSource, frag_file)
    write_to_file(image_job.uniformsInfo, json_file)

    # Set runspv logger. Use try-finally to clean up.

    with runspv.open_helper(log_file, 'w') as f:
        try:
            runspv.log_to_file = f

            if args.legacy_worker:
                if args.target == 'host':
                    runspv.run_image_host_legacy(
                        vert_original=vert_file,
                        frag_original=frag_file,
                        json_file=json_file,
                        output_dir=output_dir,
                        skip_render=skip_render,
                        spirv_opt_args=spirv_opt_args,
                    )
                else:
                    assert args.target == 'android'
                    runspv.run_image_android_legacy(
                        vert_original=vert_file,
                        frag_original=frag_file,
                        json_file=json_file,
                        output_dir=output_dir,
                        force=args.force,
                        skip_render=skip_render,
                        spirv_opt_args=spirv_opt_args,
                    )
            else:
                runspv.run_image_amber(
                    vert_original=vert_file,
                    frag_original=frag_file,
                    json_file=json_file,
                    output_dir=output_dir,
                    force=args.force,
                    is_android=(args.target == 'android'),
                    skip_render=skip_render,
                    spirv_opt_args=spirv_opt_args,
                )
        except Exception as ex:
            runspv.log('Exception: ' + str(ex))
            runspv.log('Removing STATUS file.')
            remove(status_file)
            runspv.log('Continuing.')
        finally:
            runspv.log_to_file = None

    if os.path.isfile(log_file):
        with runspv.open_helper(log_file, 'r') as f:
            res.log += f.read()

    if os.path.isfile(png_file):
        with runspv.open_bin_helper(png_file, 'rb') as f:
            res.PNG = f.read()

    if os.path.isfile(status_file):
        with runspv.open_helper(status_file, 'r') as f:
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
            with runspv.open_bin_helper(nondet_0, 'rb') as f:
                res.PNG = f.read()
            with runspv.open_bin_helper(nondet_1, 'rb') as f:
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


def do_compute_job(
    args,
    comp_job: tt.ImageJob,
    spirv_opt_args: Optional[List[str]],
    work_dir: str
) -> tt.ImageJobResult:

    # Output directory is based on the name of job.
    output_dir = os.path.join(work_dir, comp_job.name)

    # Delete and create output directory.
    remove(output_dir)
    os.makedirs(output_dir, exist_ok=True)

    tmpcomp = os.path.join(output_dir, 'tmp.comp')
    tmpjson = os.path.join(output_dir, 'tmp.json')
    log_file = os.path.join(output_dir, runspv.LOGFILE_NAME)
    ssbo_json_file = os.path.join(output_dir, 'ssbo.json')

    # Output files from running the app.
    status_file = os.path.join(output_dir, 'STATUS')

    write_to_file(comp_job.computeSource, tmpcomp)

    write_to_file(comp_job.computeInfo, tmpjson)

    res = tt.ImageJobResult()
    res.log = '#### Start compute shader\n\n'

    assert not args.legacy_worker

    # Set runspv logger. Use try-finally to clean up.

    with runspv.open_helper(log_file, 'w') as f:
        try:
            runspv.log_to_file = f

            runspv.run_compute_amber(
                comp_original=tmpcomp,
                json_file=tmpjson,
                output_dir=output_dir,
                force=args.force,
                is_android=(args.target == 'android'),
                skip_render=comp_job.skipRender,
                spirv_opt_args=spirv_opt_args,
            )
        except Exception as ex:
            runspv.log('Exception: ' + str(ex))
            runspv.log('Removing STATUS file.')
            remove(status_file)
            runspv.log('Continuing.')
        finally:
            runspv.log_to_file = None

    if os.path.isfile(log_file):
        with runspv.open_helper(log_file, 'r') as f:
            res.log += f.read()

    if os.path.isfile(status_file):
        with runspv.open_helper(status_file, 'r') as f:
            status = f.read().rstrip()
        if status == 'SUCCESS':
            res.status = tt.JobStatus.SUCCESS
            assert (os.path.isfile(ssbo_json_file))
            with runspv.open_helper(ssbo_json_file, 'r') as f:
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
        help=runspv.SPIRV_OPT_OPTION_HELP + 'Pass RANDOM to have a random selection of '
                                            'optimization arguments used for each shader job that'
                                            ' is processed.')

    parser.add_argument(
        '--local-shader-job',
        help='Execute a single, locally stored shader job (for debugging), instead of using the '
             'server.')

    args = parser.parse_args()

    spirvopt_args = None  # type: Optional[List[str]]
    if args.spirvopt:
        if args.spirvopt == "RANDOM":
            spirvopt_args = random_spirvopt_args()
        else:
            spirvopt_args = args.spirvopt.split()

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

    worker_info_json_string = '{}'

    try:
        if is_android:
            runspv.dump_info_android_legacy(wait_for_screen=not args.force)
        else:
            runspv.dump_info_host_legacy()

        if not os.path.isfile(worker_info_file):
            raise Exception(
                'Failed to retrieve worker information.  If targeting Android, make sure '
                'the app permission to write to external storage is enabled.'
            )

        with runspv.open_helper(worker_info_file, 'r') as f:
            worker_info_json_string = f.read()

    except Exception as ex:
        if args.legacy_worker:
            raise ex
        else:
            print(ex)
            print('Continuing without worker information.')

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
            with runspv.open_helper(args.local_shader_job) as f:
                fake_job.uniformsInfo = f.read()
            if os.path.isfile(shader_job_prefix + '.frag'):
                with runspv.open_helper(shader_job_prefix + '.frag', 'r') as f:
                    fake_job.fragmentSource = f.read()
            if os.path.isfile(shader_job_prefix + '.vert'):
                with runspv.open_helper(shader_job_prefix + '.vert', 'r') as f:
                    fake_job.vertexSource = f.read()
            if os.path.isfile(shader_job_prefix + '.comp'):
                with runspv.open_helper(shader_job_prefix + '.comp', 'r') as f:
                    fake_job.computeSource = f.read()
                fake_job.computeInfo = fake_job.uniformsInfo
            do_image_job(args, fake_job, spirvopt_args, work_dir='out')
            return

        if not service:
            service, worker = get_service(server, args, worker_info_json_string)

            if not service:
                print("Cannot connect to server, retry in a second...")
                time.sleep(1)
                continue

        assert worker is not None

        os.makedirs(worker, exist_ok=True)

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
                    job.imageJob.result = do_compute_job(
                        args,
                        job.imageJob,
                        spirvopt_args,
                        work_dir=worker
                    )

                else:
                    print("#### Image job: " + job.imageJob.name)
                    job.imageJob.result = do_image_job(
                        args,
                        job.imageJob,
                        spirvopt_args,
                        work_dir=worker
                    )

                print("Send back, results status: {}".format(job.imageJob.result.status))
                service.jobDone(worker, job)
                continue

        except (TApplicationException, ConnectionError):
            print("Connection to server lost. Re-initialising client.")
            service = None

        time.sleep(1)


if __name__ == '__main__':
    main()
