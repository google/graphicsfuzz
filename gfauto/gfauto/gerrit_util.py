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

"""Gerrit utility module.

Provides functions for interacting with Gerrit via its REST API.
"""

import json
from enum import Enum
from pathlib import Path
from typing import Any, Dict, Optional

import requests
from dateutil.parser import parse as parse_date
from requests import Response

from gfauto import util
from gfauto.gflogging import log
from gfauto.util import check

KHRONOS_GERRIT_URL = "https://gerrit.khronos.org"

KHRONOS_GERRIT_LOGIN_PAGE_START = "<!DOCTYPE html>"

RESPONSE_PREFIX = ")]}'\n"


class BadCookieError(Exception):
    pass


def gerrit_get_stream(
    url: str, path: str, params: Optional[Dict[str, str]], cookie: str
) -> Response:
    return requests.get(
        url + path, params=params, stream=True, cookies={"GerritAccount": cookie}
    )


def gerrit_get(url: str, path: str, params: Dict[str, str], cookie: str) -> Any:
    response = requests.get(
        url + path, params=params, cookies={"GerritAccount": cookie}
    ).text

    check(
        response.startswith(RESPONSE_PREFIX),
        AssertionError(f"Unexpected response from Gerrit: {response}"),
    )
    response = util.remove_start(response, RESPONSE_PREFIX)
    return json.loads(response)


def find_latest_change(changes: Any) -> Any:
    check(
        len(changes) > 0, AssertionError(f"Expected at least one CL but got: {changes}")
    )

    # Find the latest submit date (the default order is based on when the CL was last updated).

    latest_change = changes[0]
    latest_date = parse_date(latest_change["submitted"])

    for i in range(1, len(changes)):
        change = changes[i]
        submitted_date = parse_date(change["submitted"])
        if submitted_date > latest_date:
            latest_change = change
            latest_date = submitted_date

    return latest_change


def get_latest_deqp_change(cookie: str) -> Any:
    log("Getting latest deqp change")
    changes = gerrit_get(
        KHRONOS_GERRIT_URL,
        "/changes/",
        params={"q": "project:vk-gl-cts status:merged branch:master", "n": "1000"},
        cookie=cookie,
    )

    return find_latest_change(changes)


def get_gerrit_change_details(change_number: str, cookie: str) -> Any:
    log(f"Getting change details for change number: {change_number}")
    return gerrit_get(
        KHRONOS_GERRIT_URL,
        f"/changes/{change_number}/detail",
        params={"O": "10004"},
        cookie=cookie,
    )


class DownloadType(Enum):
    """For download_gerrit_revision, specifies what to download."""

    # Downloads the entire repo as a .tgz file.
    Archive = "archive"

    # Downloads the patch in a .zip file.
    Patch = "patch"


def download_gerrit_revision(
    output_path: Path,
    change_number: str,
    revision: str,
    download_type: DownloadType,
    cookie: str,
) -> Path:
    path = f"/changes/{change_number}/revisions/{revision}/{download_type.value}"
    log(f"Downloading revision from: {path}\n to: {str(output_path)}")

    params = {"format": "tgz"} if download_type == DownloadType.Archive else {"zip": ""}

    response = gerrit_get_stream(KHRONOS_GERRIT_URL, path, params=params, cookie=cookie)

    counter = 0
    with util.file_open_binary(output_path, "wb") as output_stream:
        for chunk in response.iter_content(chunk_size=None):
            log(".", skip_newline=True)
            counter += 1
            if counter > 80:
                counter = 0
                log("")  # new line
            output_stream.write(chunk)
    log("")  # new line

    with util.file_open_text(output_path, "r") as input_stream:
        line = input_stream.readline(len(KHRONOS_GERRIT_LOGIN_PAGE_START) * 2)
        if line.startswith(KHRONOS_GERRIT_LOGIN_PAGE_START) or line.startswith(
            "Not found"
        ):
            raise BadCookieError()

    return output_path


def get_deqp_graphicsfuzz_pending_changes(cookie: str) -> Any:
    return gerrit_get(
        KHRONOS_GERRIT_URL,
        "/changes/",
        params={
            "q": "project:vk-gl-cts status:pending branch:master dEQP-VK.graphicsfuzz.",
            "n": "1000",
        },
        cookie=cookie,
    )
