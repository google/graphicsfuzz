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

import shlex
import subprocess
import os
import argparse

def clear_logcat():
    clear_cmd = "adb logcat -c"
    subprocess.run(shlex.split(clear_cmd))

# start_logcat() returns the logcat stdout
def start_logcat():
    clear_logcat()
    logcat_cmd = "adb logcat -b system 'ActivityManager:I' '*:S'"
    logcat_subprocess_arg = shlex.split(logcat_cmd)
    # The universal_newlines flag enable to get stdout as text and not byte stream
    logcat_subprocess = subprocess.Popen(logcat_subprocess_arg, stdout=subprocess.PIPE, universal_newlines=True)
    return logcat_subprocess.stdout

def start_worker(server, worker, newtab=False):
    # Note the escaped ampersand in the command
    worker_cmd = "adb shell am start -n org.mozilla.firefox/org.mozilla.gecko.LauncherActivity"
    if newtab:
        worker_cmd += " -a android.intent.action.VIEW -d 'http://" + server + "/static/runner.html?context=webgl2\&worker=" + worker + "'"
    # shlex.split() doesn't keep the escape around the URL ... ? resort
    # to shell=True
    subprocess.run(worker_cmd, shell=True)

################################################################################
# Main

parser = argparse.ArgumentParser()

parser.add_argument(
    'server',
    help='Server URL, e.g. localhost:8080')

parser.add_argument(
    'worker',
    help='Worker name to identify to the server')

args = parser.parse_args()

logcat = start_logcat()
start_worker(args.server, args.worker, newtab=True)

while True:
    line = logcat.readline()
    if (" Process org.mozilla.firefox " in line) and (" has died" in line):
        print("Detected a crash: " + line, end='')
        print('Restart worker...')
        start_worker(args.server, args.worker)
