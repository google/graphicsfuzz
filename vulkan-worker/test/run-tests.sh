#!/bin/bash

# Copyright 2019 Google Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

usage() {
  printf "Usage: %s /path/to/vkworker\n" `basename $0`
}

vkworker=$1

if test -z "$vkworker"
then
  if which vkworker > /dev/null
  then
    vkworker="vkworker"
  else
    printf "Missing vkworker argument"
    usage
    exit 1
  fi
elif test ! -f "$vkworker"
then
  printf "Argument for vkworker is not a file\n"
  usage
  exit 1
fi

tmpdir=`mktemp -d`
testsrcdir=`pwd`

cd $tmpdir
printf "Running tests in $tmpdir\n"

for json in $testsrcdir/test_*.json
do

  base=`basename $json .json`

  vert=$testsrcdir/$base.vert.spv
  if test ! -f $vert
  then
    vert=$testsrcdir/default.vert.spv
  fi

  frag=$testsrcdir/$base.frag.spv
  if test ! -f $frag
  then
    frag=$testsrcdir/default.frag.spv
  fi

  $vkworker $vert $frag $json > $base.stdout 2> $base.stderr
  ret=$?
  if test $ret -eq 0
  then
    result="OK"
  else
    result="FAIL"
  fi

  printf "TEST %-10.10s %s\n" $base $result

done
