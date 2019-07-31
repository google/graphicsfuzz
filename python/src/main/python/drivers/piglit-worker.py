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
import filecmp
import shutil
import time
from typing import List

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
PNG_SUFFIX = '.png'
SHADER_TEST_SUFFIX = '.shader_test'

SHADER_RUNNER_ARG_PNG = '-png'
SHADER_RUNNER_ARG_AUTO = '-auto'
SHADER_RUNNER_ARG_UNIFORMS = '-ignore-missing-uniforms'
SHADER_RUNNER_ARG_FBO = '-fbo'
SHADER_RUNNER_ARG_SUBTESTS = '-report-subtests'

WORKER_INFO_FILE = 'worker_info.json'
PNG_FILENAME = 'shader_runner_gles3000.png'
COMPARE_PNG_FILENAME = 'shader_runner_gles3001.png'
NONDET0_PNG = 'nondet0.png'
NONDET1_PNG = 'nondet1.png'
LOGFILE_NAME = 'piglit_log.txt'
STATUS_FILENAME = 'STATUS'

NO_DRAW_ARG = '--nodraw'

STATUS_SUCCESS = 'SUCCESS'
STATUS_CRASH = 'CRASH'
STATUS_TIMEOUT = 'TIMEOUT'
STATUS_UNEXPECTED = 'UNEXPECTED_ERROR'
STATUS_NONDET = 'NONDET'

TIMEOUT = 30


def glxinfo_cmd() -> List[str]:
    return [gfuzz_common.tool_on_path('glxinfo'), '-B']


def shader_runner_cmd() -> List[str]:
    return [gfuzz_common.tool_on_path('shader_runner_gles3')]


def catchsegv_cmd() -> str:
    return gfuzz_common.tool_on_path('catchsegv')


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
        gfuzz_common.log("Call getWorkerName()")
        worker_res = service.getWorkerName(worker_info, worker_name)
        assert type(worker_res) is not None

        if worker_res.workerName is None:
            # noinspection PyProtectedMember
            gfuzz_common.log('Worker error: ' + tt.WorkerNameError._VALUES_TO_NAMES[worker_res.error])
            exit(1)

        worker = worker_res.workerName

        gfuzz_common.log("Got worker: " + worker)
        assert (worker == worker_name)

        return service, worker

    except (TApplicationException, ConnectionRefusedError, ConnectionResetError):
        return None, None


def dump_glxinfo(filename: str) -> None:
    """
    Helper function that dumps the stable results of 'glxinfo -B' to a JSON file. Removes any
    file with the same name as filename before writing. Will throw an exception if 'glxinfo'
    fails or the JSON file can't be written.
    :param filename: the filename to write to.
    """
    # There are some useless or unstable lines in glxinfo we need to remove before trying to parse
    # into JSON.
    glxinfo_lines = filter(
        lambda glx_line: 'OpenGL' in glx_line,
        gfuzz_common.subprocess_helper(glxinfo_cmd()).stdout.split('\n'))
    # We form keys out of the OpenGL info descriptors and values out of the hardware dependent
    # strings. For example, "OpenGL version string: 4.6.0 NVIDIA 430.14" would become
    # { "OpenGL version string": "4.6.0 NVIDIA 430.14" }.
    glx_dict = dict()
    for line in glxinfo_lines:
        prop = line.split(': ')
        assert len(prop) is 2
        glx_dict.update({prop[0]: prop[1]})
    with gfuzz_common.open_helper(filename, 'w') as info_file:
        info_file.write(json.JSONEncoder().encode(glx_dict))


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
    png_file = os.path.join(output_dir, name + PNG_SUFFIX)
    nondet_0 = os.path.join(output_dir, NONDET0_PNG)
    nondet_1 = os.path.join(output_dir, NONDET1_PNG)

    gfuzz_common.write_to_file(image_job.fragmentSource, frag_file)
    gfuzz_common.write_to_file(image_job.uniformsInfo, json_file)

    res = tt.ImageJobResult()

    # Set nice defaults to fields we will not update anyway
    res.passSanityCheck = True
    res.log = 'Start: ' + name + '\n'

    with gfuzz_common.open_helper(log_file, 'w') as f:
        try:
            gfuzz_common.set_logfile(f)
            run_image_job(json_file, status_file, png_file, output_dir, image_job.skipRender)
        except Exception as ex:
            gfuzz_common.log(str(ex))
            gfuzz_common.log('Removing status file and continuing...')
            gfuzz_common.remove(status_file)
        finally:
            gfuzz_common.unset_logfile()

    if os.path.isfile(log_file):
        with gfuzz_common.open_helper(log_file, 'r') as f:
            res.log += f.read()

    if os.path.isfile(png_file):
        with gfuzz_common.open_bin_helper(png_file, 'rb') as f:
            res.PNG = f.read()

    if os.path.isfile(status_file):
        with gfuzz_common.open_helper(status_file, 'r') as f:
            status = f.read().rstrip()
        if status == STATUS_SUCCESS:
            res.status = tt.JobStatus.SUCCESS
        elif status == STATUS_CRASH:
            res.status = tt.JobStatus.CRASH
        elif status == STATUS_TIMEOUT:
            res.status = tt.JobStatus.TIMEOUT
        elif status == STATUS_UNEXPECTED:
            res.status = tt.JobStatus.UNEXPECTED_ERROR
        elif status == STATUS_NONDET:
            res.status = tt.JobStatus.NONDET
            with gfuzz_common.open_bin_helper(nondet_0, 'rb') as f:
                res.PNG = f.read()
            with gfuzz_common.open_bin_helper(nondet_1, 'rb') as f:
                res.PNG2 = f.read()
        else:
            res.log += '\nUnknown status value: ' + status + '\n'
            res.status = tt.JobStatus.UNEXPECTED_ERROR
    else:
        # Not even a status file?
        res.log += '\nNo STATUS file\n'
        res.status = tt.JobStatus.UNEXPECTED_ERROR

    return res


def run_image_job(json_file: str, status_file: str,
                  png_file: str, output_dir: str, skip_render: bool):
    """
    Runs an image job. Converts the shader job to a piglit shader_test file, then delegates to
    run_shader_test to render with shader_runner. Writes the status of the job to file.
    :param json_file: The JSON uniforms to use with the shader.
    :param status_file: The status file to write to.
    :param png_file: The PNG file to write to.
    :param output_dir: The directory to use for the job.
    :param skip_render: whether to skip rendering or not.
    """

    use_catchsegv = True

    try:
        gfuzz_common.tool_on_path('catchsegv')
    except gfuzz_common.ToolNotOnPathError:
        use_catchsegv = False

    assert os.path.isdir(output_dir)
    assert os.path.isfile(json_file)

    arglist = [json_file]
    if skip_render:
        arglist.append(NO_DRAW_ARG)

    shader_test_file = graphicsfuzz_piglit_converter.get_shader_test_from_job(json_file)

    try:
        gfuzz_common.log('Creating shader_test file...')
        graphicsfuzz_piglit_converter.main_helper(arglist)
    except Exception as ex:
        gfuzz_common.log('Could not create shader_test from the given job.')
        raise ex
    shader_runner_cmd_list = shader_runner_cmd() + \
        [shader_test_file, SHADER_RUNNER_ARG_AUTO,
         SHADER_RUNNER_ARG_UNIFORMS, SHADER_RUNNER_ARG_FBO, SHADER_RUNNER_ARG_SUBTESTS]
    if use_catchsegv:
        shader_runner_cmd_list.insert(0, catchsegv_cmd())
    if not skip_render:
        shader_runner_cmd_list.append(SHADER_RUNNER_ARG_PNG)

    gfuzz_common.remove(PNG_FILENAME)
    gfuzz_common.remove(COMPARE_PNG_FILENAME)

    status = \
        gfuzz_common.run_catchsegv(shader_runner_cmd_list, timeout=TIMEOUT, verbose=True) \
        if use_catchsegv else \
        gfuzz_common.subprocess_helper(shader_runner_cmd_list, timeout=TIMEOUT, verbose=True)

    # Piglit throws the output PNG render into whatever the current working directory is
    # (and there's no way to specify a location to write to) - we need to move it to wherever our
    # output is.

    if not skip_render and status == STATUS_SUCCESS:
        try:
            # An image was rendered, so we need to check for nondet. We do this by renaming the
            # rendered image, rendering a second image, and using filecmp to compare the files.
            assert os.path.isfile(PNG_FILENAME), \
                "Shader runner successfully rendered, but no image was dumped?"
            gfuzz_common.log('An image was rendered - rendering again to check for nondet.')
            os.rename(PNG_FILENAME, COMPARE_PNG_FILENAME)
            status = \
                gfuzz_common.run_catchsegv(shader_runner_cmd_list, timeout=TIMEOUT, verbose=True) \
                if use_catchsegv else \
                gfuzz_common.subprocess_helper(shader_runner_cmd_list, timeout=TIMEOUT, verbose=True)
            # Something is horribly wrong if shader crashes/timeouts are inconsistent per shader.
            assert status == STATUS_SUCCESS, \
                "Shader inconsistently fails - check your graphics drivers?"
            assert os.path.isfile(PNG_FILENAME), \
                "Shader runner successfully rendered, but no image was dumped?"

            gfuzz_common.log('Comparing dumped PNG images...')
            if filecmp.cmp(PNG_FILENAME, COMPARE_PNG_FILENAME):
                gfuzz_common.log('Images are identical.')
                shutil.move(PNG_FILENAME, png_file)
            else:
                gfuzz_common.log('Images are different.')
                status = STATUS_NONDET
                shutil.move(COMPARE_PNG_FILENAME, os.path.join(output_dir, NONDET0_PNG))
                shutil.move(PNG_FILENAME, os.path.join(output_dir, NONDET1_PNG))
        finally:
            gfuzz_common.log('Removing dumped images...')
            gfuzz_common.remove(PNG_FILENAME)
            gfuzz_common.remove(COMPARE_PNG_FILENAME)

    gfuzz_common.log('STATUS: ' + status)

    with gfuzz_common.open_helper(status_file, 'w') as f:
        f.write(status)


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
    gfuzz_common.log('Worker: ' + args.worker_name)
    server = args.server + '/request'
    gfuzz_common.log('server: ' + server)

    # Get worker info
    worker_info_json_string = '{}'

    gfuzz_common.log('Dumping glxinfo to file for worker info string...')
    try:
        dump_glxinfo(WORKER_INFO_FILE)
        with gfuzz_common.open_helper(WORKER_INFO_FILE, 'r') as info_file:
            worker_info_json_string = info_file.read()
    except Exception as ex:
        gfuzz_common.log(str(ex))
        gfuzz_common.log('Could not get worker info, continuing without it.')

    service = None
    worker = None

    while True:
        if not service:
            gfuzz_common.log('Connecting to server...')
            service, worker = thrift_connect(server, args.worker_name, worker_info_json_string)
            if not service:
                gfuzz_common.log('Failed to connect, retrying...')
                time.sleep(1)
                continue

        assert worker

        os.makedirs(worker, exist_ok=True)

        try:
            job = service.getJob(worker)
            if job.noJob is not None:
                gfuzz_common.log("No job")
            elif job.skipJob is not None:
                gfuzz_common.log("Skip job")
                service.jobDone(worker, job)
            else:
                assert job.imageJob
                if job.imageJob.computeSource:
                    gfuzz_common.log("Got a compute job, but this worker "
                                     "doesn't support compute shaders.")
                    job.imageJob.result = tt.ImageJobResult()
                    job.imageJob.result.status = tt.JobStatus.UNEXPECTED_ERROR
                else:
                    gfuzz_common.log("#### Image job: " + job.imageJob.name)
                    job.imageJob.result = do_image_job(job.imageJob, work_dir=worker)
                gfuzz_common.log("Sending back, results status: {}"
                                 .format(job.imageJob.result.status))
                service.jobDone(worker, job)
                gfuzz_common.remove(worker)
                continue
        except (TApplicationException, ConnectionError):
            gfuzz_common.log("Connection to server lost. Re-initialising client.")
            service = None
        time.sleep(1)


if __name__ == '__main__':
    main()
