# -*- coding: utf-8 -*-

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

from gfauto import gerrit_util


def test_get_latest_change() -> None:
    changes = [
        {"submitted": "2018-09-08 05:54:26.000000000"},
        {"submitted": "2019-06-10 11:54:26.000000000"},
        {"submitted": "2019-09-09 05:54:26.000000000"},
        {"submitted": "2019-09-10 05:54:26.000000000"},  # This one is the latest.
        {"submitted": "2019-09-10 05:53:26.000000000"},  # This one is similar.
        {"submitted": "2018-09-08 05:54:26.000000000"},
    ]
    latest_change = gerrit_util.find_latest_change(changes)
    assert latest_change == changes[3]
    assert latest_change != changes[2]
