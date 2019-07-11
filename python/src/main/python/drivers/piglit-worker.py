#!/usr/bin/env python3

# Copyright 2019 The GraphicsFuzz Project Authors
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
import json
import os
import sys
import subprocess
from subprocess import CalledProcessError
import time

import gfuzz_common
import graphicsfuzz_piglit_converter

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

FRAG_SUFFIX = '.frag'
JSON_SUFFIX = '.json'
SHADERTEST_SUFFIX = '.shader_test'
PNG_FILENAME = 'shader_runner_gles3000.png'
LOGFILE_NAME = 'piglit_log.txt'
STATUS_FILENAME = 'STATUS'

GLXINFO_CMD = ['glxinfo', '-B']
SHADERRUNNER_CMD = ['shader_runner_gles3']
SHADERRUNNER_ARG_PNG = '-png'
SHADERRUNNER_ARG_AUTO = '-auto'
WORKER_INFO_FILE = 'worker_info.json'

NODRAW_ARG = '--nodraw'

RETURNCODE_STR = 'Returncode: '
STDOUT_STR = 'STDOUT: '
STDERR_STR = 'STDERR: '

STATUS_SUCCESS = 'SUCCESS'
STATUS_CRASH = 'CRASH'
STATUS_TIMEOUT = 'TIMEOUT'
STATUS_SANITYERROR = 'SANITY_ERROR'
STATUS_UNEXPECTED = 'UNEXPECTED_ERROR'
STATUS_NONDET = 'NONDET'

TIMEOUT = 30

logfile = None


def log(message: str):
    print(message)
    if logfile is not None:
        logfile.write(message + '\n')


def thrift_connect(server: str, worker_name: str, worker_info: str) -> (FuzzerService, str):
    """
    Helper function to initiate a connection from this worker to a Thrift server.
    Handles sending the worker name and info to the server. If there's a fatal problem with a worker
    (such as a worker info mismatch between client/server), this function will terminate the
    program.
    :param server: The server request URL to connect to.
    :param worker_name: The name of the worker to connect with.
    :param worker_info: The worker info string.
    :return: a FuzzerService object and the confirmed worker name, or None for both if the
            connection failed without a fatal error.
    """
    try:
        http_client = THttpClient.THttpClient(server)
        transport = TTransport.TBufferedTransport(http_client)
        protocol = TBinaryProtocol.TBinaryProtocol(transport)
        service = FuzzerService.Client(protocol)
        transport.open()

        # Get worker name
        log("Call getWorkerName()")
        worker_res = service.getWorkerName(worker_info, worker_name)
        assert type(worker_res) is not None

        if worker_res.workerName is None:
            # noinspection PyProtectedMember
            log('Worker error: ' + tt.WorkerNameError._VALUES_TO_NAMES[worker_res.error])
            exit(1)

        worker = worker_res.workerName

        log("Got worker: " + worker)
        assert (worker == worker_name)

        return service, worker

    except (TApplicationException, ConnectionRefusedError, ConnectionResetError):
        return None, None


def dump_glxinfo(filename: str) -> None:
    """
    Helper function that dumps the stable results of 'glxinfo -B' to a JSON file. Removes any
    file with the same name as filename before writing.
    :param filename: the filename to write to.
    """
    check_working_glxinfo()
    # There are some useless or unstable lines in glxinfo we need to remove before trying to parse
    # into JSON.
    glxinfo_lines = filter(
        lambda glx_line: 'OpenGL' in glx_line,
        subprocess.check_output(GLXINFO_CMD).decode(sys.stdout.encoding).split('\n'))
    # We form keys out of the OpenGL info descriptors and values out of the hardware dependent
    # strings. For example, "OpenGL version string: 4.6.0 NVIDIA 430.14" would become
    # { "OpenGL version string": "4.6.0 NVIDIA 430.14" }.
    glx_dict = dict()
    for line in glxinfo_lines:
        prop = line.split(': ')
        assert len(prop) is 2
        glx_dict.update({prop[0]: prop[1]})
    gfuzz_common.remove(filename)
    with gfuzz_common.open_helper(filename, 'w') as info_file:
        info_file.write(json.JSONEncoder().encode(glx_dict))


def check_working_glxinfo() -> None:
    """
    Helper function to determine if glxinfo works properly on the system. Throws CalledProcessError
    if glxinfo encounters an error.
    """
    try:
        subprocess.check_call(GLXINFO_CMD)
    except CalledProcessError as ex:
        log('glxinfo errored out - something is wrong with your setup.\n'
            'Check your graphics drivers configuration and try again.')
        raise ex


def do_image_job(image_job: tt.ImageJob, work_dir: str) -> tt.ImageJobResult:
    """
    Does an image job. Sets up directories and some files, then delegates to run_image_job to
    convert the job to a shader_test and run it. Sets a global logfile to log to for the lifetime
    of the function. Gets the status of the shader job from a file that is written to by
    run_image_job.
    :param image_job: the image job containing the shader/uniforms.
    :param work_dir: the directory to work in.
    :return: the result of the image job, including the log, PNG and status.
    """
    # Output directory is based on the name of job.
    output_dir = os.path.join(work_dir, image_job.name)

    # Delete and create output directory.
    gfuzz_common.remove(output_dir)
    os.makedirs(output_dir, exist_ok=True)

    name = image_job.name
    if name.endswith('.frag'):
        name = gfuzz_common.remove_end(name, '.frag')

    frag_file = os.path.join(output_dir, name + FRAG_SUFFIX)
    json_file = os.path.join(output_dir, name + JSON_SUFFIX)
    log_file = os.path.join(output_dir, LOGFILE_NAME)
    status_file = os.path.join(output_dir, STATUS_FILENAME)
    png_file = os.path.join(output_dir, PNG_FILENAME)

    gfuzz_common.write_to_file(image_job.fragmentSource, frag_file)
    gfuzz_common.write_to_file(image_job.uniformsInfo, json_file)

    res = tt.ImageJobResult()

    # Set nice defaults to fields we will not update anyway
    res.passSanityCheck = True
    res.log = 'Start: ' + name + '\n'

    # Set a global logfile so our log function knows it can write to file as well as stdout.
    global logfile

    with gfuzz_common.open_helper(log_file, 'w') as f:
        try:
            logfile = f
            run_image_job(frag_file, json_file, status_file, output_dir, image_job.skipRender)
        except Exception as ex:
            log(str(ex))
            log('Removing status file and continuing...')
            gfuzz_common.remove(status_file)
        finally:
            logfile = None

    if os.path.isfile(log_file):
        with gfuzz_common.open_helper(log_file, 'r') as f:
            res.log += f.read()

    if os.path.isfile(png_file):
        with gfuzz_common.open_bin_helper(png_file, 'rb') as f:
            res.PNG = f.read()

    if os.path.isfile(status_file):
        with gfuzz_common.open_helper(status_file, 'r') as f:
            status = f.read().rstrip()
        if status == 'SUCCESS':
            res.status = tt.JobStatus.SUCCESS
        elif status == 'CRASH':
            res.status = tt.JobStatus.CRASH
        elif status == 'TIMEOUT':
            res.status = tt.JobStatus.TIMEOUT
        elif status == 'UNEXPECTED_ERROR':
            res.status = tt.JobStatus.UNEXPECTED_ERROR
        else:
            res.log += '\nUnknown status value: ' + status + '\n'
            res.status = tt.JobStatus.UNEXPECTED_ERROR
    else:
        # Not even a status file?
        res.log += '\nNo STATUS file\n'
        res.status = tt.JobStatus.UNEXPECTED_ERROR

    return res


def run_image_job(frag_file: str, json_file: str, status_file: str,
                  output_dir: str, skip_render: bool):
    """
    Runs an image job. Converts the shader job to a piglit shader_test file, then delegates to
    run_shader_test to render with shader_runner. Writes the status of the job to file.
    :param frag_file: The fragment shader to convert and run.
    :param json_file: The JSON uniforms to use with the shader.
    :param status_file: The status file to write to.
    :param output_dir: The directory to use for the job.
    :param skip_render: whether to skip rendering or not.
    """

    assert os.path.isdir(output_dir)
    assert os.path.isfile(frag_file)
    assert os.path.isfile(json_file)

    # graphicsfuzz_piglit_converter has to skip the first argument since it's meant to be a
    # standalone script taking arguments from a command line.
    arglist = list('')
    arglist.append(str(json_file))
    if skip_render:
        arglist.append(NODRAW_ARG)

    shader_test_file = graphicsfuzz_piglit_converter.get_shader_test_from_job(json_file)

    try:
        log('Creating shader_test file...')
        graphicsfuzz_piglit_converter.main_helper(arglist)
    except Exception as ex:
        log('Could not create shader_test from the given job.')
        raise ex

    status = STATUS_SUCCESS

    try:
        run_shader_test(shader_test_file, skip_render)
    except subprocess.TimeoutExpired:
        status = STATUS_TIMEOUT
    except subprocess.CalledProcessError:
        status = STATUS_CRASH

    # Piglit throws the output PNG render into whatever the current working directory is
    # (and there's no way to specify a location to write to) - we need to move it to wherever our
    # output is.

    if os.path.isfile(os.getcwd() + PNG_FILENAME):
        shutil.move(os.getcwd() + PNG_FILENAME, output_dir + PNG_FILENAME)

    with gfuzz_common.open_helper(status_file, 'w') as f:
        f.write(status)


def run_shader_test(shader_test_file: str, skip_render: bool):
    """
    Runs a shader_test file and logs the output. If the shader runner errors out, the error is
    logged and then raised to the caller.
    :param shader_test_file: the shader_test file to run.
    :param skip_render: whether to skip rendering or not.
    """
    shader_runner_cmd = SHADERRUNNER_CMD + [shader_test_file, SHADERRUNNER_ARG_AUTO]
    if not skip_render:
        shader_runner_cmd.append(SHADERRUNNER_ARG_PNG)
    try:
        log('Exec: ' + shader_test_file + ' with ' + SHADERRUNNER_CMD[0])
        results = subprocess.run(shader_runner_cmd, timeout=TIMEOUT, check=True)
    except subprocess.TimeoutExpired as ex:
        if ex.stdout is not None:
            log(STDOUT_STR + ex.stdout.decode(encoding='utf-8', errors='ignore'))
        if ex.stderr is not None:
            log(STDERR_STR + ex.stderr.decode(encoding='utf-8', errors='ignore'))
        raise ex
    except subprocess.CalledProcessError as ex:
        if ex.stdout is not None:
            log(STDOUT_STR + ex.stdout.decode(encoding='utf-8', errors='ignore'))
        if ex.stderr is not None:
            log(STDERR_STR + ex.stderr.decode(encoding='utf-8', errors='ignore'))
        log(RETURNCODE_STR + str(ex.returncode))
        raise ex
    if results.stdout is not None:
        log(STDOUT_STR + results.stdout.decode(encoding='utf-8', errors='ignore'))
    if results.stderr is not None:
        log(STDERR_STR + results.stderr.decode(encoding='utf-8', errors='ignore'))
    log(RETURNCODE_STR + str(results.returncode))


def main():
    description = (
        'Uses the piglit GLES3 shader runner to render shader jobs.'
    )

    parser = argparse.ArgumentParser(description=description)

    # Required
    parser.add_argument(
        'worker_name',
        help='The name that will refer to this worker.'
    )

    # Optional
    parser.add_argument(
        '--server',
        default='http://localhost:8080',
        help='Server URL to connect to (default: http://localhost:8080 )'
    )

    args = parser.parse_args()

    gfuzz_common.tool_on_path('shader_runner_gles3')
    gfuzz_common.tool_on_path('glxinfo')

    log('Worker: ' + args.worker_name)
    server = args.server + '/request'
    log('server: ' + server)

    # Get worker info
    worker_info_json_string = '{}'

    try:
        dump_glxinfo(WORKER_INFO_FILE)
        if not os.path.isfile(WORKER_INFO_FILE):
            raise FileNotFoundError(
                'Could not create worker info file - make sure you have permissions to write '
                'in the same folder as the script.'
            )
        with gfuzz_common.open_helper(WORKER_INFO_FILE, 'r') as info_file:
            worker_info_json_string = info_file.read()
    except Exception as ex:
        log(str(ex))
        log('Could not get worker info, continuing without it.')

    service = None
    worker = None

    while True:
        if not service:
            service, worker = thrift_connect(server, args.worker_name, worker_info_json_string)
            if not service:
                log('Failed to connect, retrying...')
                time.sleep(1)
                continue

        assert worker is not None

        os.makedirs(worker, exist_ok=True)

        try:
            job = service.getJob(worker)
            if job.noJob is not None:
                log("No job")
            elif job.skipJob is not None:
                log("Skip job")
                service.jobDone(worker, job)
            else:
                assert job.imageJob is not None
                if job.imageJob.computeSource:
                    log("Got a compute job, but Piglit doesn't support compute shaders.")
                    job.imageJob.result.status = tt.JobStatus.UNEXPECTED_ERROR
                else:
                    log("#### Image job: " + job.imageJob.name)
                    job.imageJob.result = do_image_job(job.imageJob, work_dir=worker)
                log("Sending back, results status: {}".format(job.imageJob.result.status))
                service.jobDone(worker, job)
                continue
        except (TApplicationException, ConnectionError):
            log("Connection to server lost. Re-initialising client.")
            service = None
        time.sleep(1)


if __name__ == '__main__':
    main()
