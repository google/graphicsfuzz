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
import os
import sys
from typing import List


def get_result_files(arg: str) -> List[str]:
    for root, folders, files in os.walk(arg):
        for filename in files:
            if filename.endswith('.info.json'):
                yield os.path.join(root, filename)


def main_helper(args: List[str]) -> int:
    description = (
        'Summarize back-traces from a results file or set of directories of results files.')

    parser = argparse.ArgumentParser(description=description)

    # Required arguments
    parser.add_argument('files', help=(
        'One or more directories or results files.  All results files contained under each of the '
        'given directories, plus any singleton results files, will be mined for backtraces'),
                        nargs='+')

    args = parser.parse_args(args)

    files_to_check = []

    for fileordir in args.files:
        files_to_check += get_result_files(fileordir)

    mapping = {}

    for to_check in files_to_check:
        if 'CRASH' in open(to_check, 'r').read():
            textfile = os.path.splitext(os.path.splitext(to_check)[0])[0] + '.txt'
            lines = open(textfile, 'r').readlines()
            for i in range(0, len(lines) - 2):
                if 'backtrace' in lines[i] and '#' in lines[i + 1] and '#' in lines[i + 2]:
                    crash_string = ('      ' + lines[i + 1][lines[i + 1].index('#'):]
                        + '      ' + lines[i + 2][lines[i + 2].index('#'):]).rstrip()
                    if crash_string not in mapping:
                        mapping[crash_string] = (1, textfile)
                    else:
                        existing_entry = mapping[crash_string]
                        mapping[crash_string] = (existing_entry[0] + 1, existing_entry[1])
                    break

    print('Distinct crash string(s) found: ' + str(len(mapping)) + '\n')

    for key in mapping:
        print('Crash string:\n' + key)
        print('  Occurrences: ' + str(mapping[key][0]))
        print('  Including in: ' + str(mapping[key][1]) + '\n')


if __name__ == '__main__':
    main_helper(sys.argv[1:])
