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

import subprocess
import signal
import sys
import argparse
import os
from threading import Timer
from time import sleep

parser = argparse.ArgumentParser(
    description="Wrapper for browser running WebGL experiments")

parser.add_argument("--browserPath", type=str, action="store",
                    default=r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
                    help="Path to web browser to use")
parser.add_argument("--url", type=str, action="store",
                    default=r"http://localhost:8080/static/runner.html",
                    help="URL of server managing experiments.")
parser.add_argument("--worker", type=str, action="store",
                    default="",
                    help="Worker to be used by server.")
parser.add_argument("browserArgs", action="store", nargs="*",
                    default = ["--enable-logging=stderr --enable-logging --v=1 --disable-gpu-process-crash-limit"],
                    help="Arguments to be passed to the browser")
parser.add_argument("--log", action="store", type=str,
                    default="web_gl.log",
                    help="Path to log file.")

args = parser.parse_args()

if not os.path.isfile(args.browserPath):
    print("Could not find valid browser path at " + args.browserPath + ".")
    sys.exit(1)

logfile = open(args.log, 'w')

if sys.platform == 'win32':
    args.browserArgs.append(" --user-data-dir=" + os.path.expanduser(r"~\AppData\Local\Google\Chrome\User Data"))
    chrome_log_name = os.path.expanduser(r"~\AppData\Local\Google\Chrome\User Data\chrome_debug.log")
else:
    assert sys.platform == "linux2"
    args.browserArgs.append(" --user-data-dir=" + os.path.expanduser("~/temp-dir"))
    chrome_log_name = os.path.expanduser("~/temp-dir/chrome_debug.log")

def signal_handler(sig, frame):
    browser_proc.kill()
    sys.exit(0)


def run():
    global browser_proc
    full_url = args.url
    if args.worker != "":
        full_url += "?worker=" + args.worker
    cmd = [args.browserPath] + [full_url] + args.browserArgs
    print("Running command:\n\t" + " ".join(cmd) + "\n")
    browser_proc = subprocess.Popen(cmd, stderr=subprocess.PIPE, stdout=subprocess.PIPE);
    jobDone_new = jobDone_old = 0

    while True:
        sleep(30)
        try:
            file = open(chrome_log_name, 'r')
        except IOError:
            print("Could not find valid browser path at " + chrome_log_name)
            sys.exit(1)
        else:
            # only take the last 10 kb of the file
            file.seek(0, 2)
            fsize = file.tell()
            file.seek (max (fsize-10240, 0), 0)
            lines = file.readlines()

            # iterate backwards to get the number of the last completed job
            for line in reversed(lines):
                if "Job Done" in line:
                    # line is of the form "Job Done 1 "
                    jobDone_new = int(line.split("Job Done")[1].split(' ')[1])
                    break
            # If no new job has been executed then either the worker did not get any new job to execute
            # or there has been an issue processing the last job. In both cases we restart the browser.
            if (jobDone_new == jobDone_old):
                browser_proc.kill()
                return False
            jobDone_old = jobDone_new


signal.signal(signal.SIGINT, signal_handler)
while not run():
    pass
