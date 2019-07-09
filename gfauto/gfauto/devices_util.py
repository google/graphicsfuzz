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

from pathlib import Path
from typing import Dict, List, Optional

from gfauto import android_device, proto_util, util
from gfauto.device_pb2 import (
    Device,
    DeviceHost,
    DeviceList,
    DevicePreprocess,
    DeviceSwiftShader,
)


def swift_shader_device() -> Device:
    # TODO: version hash, maybe a better name.
    return Device(name="swift_shader", swift_shader=DeviceSwiftShader())


def device_preprocessor() -> Device:
    # TODO: think about the fact that the versions of glslang, spirv-opt etc. are stored in the test and not the device.
    #  This might make it rather strange if you want to test many different versions of, say, spirv-opt.
    #  We could store version hashes in both the test and the "preprocessor device", and let the "preprocessor device"
    #  have higher priority.
    return Device(name="host_preprocessor", preprocess=DevicePreprocess())


def device_host() -> Device:
    # TODO: add details to host (and indeed, to all devices).
    return Device(name="host", host=DeviceHost())


def get_device_list(device_list: Optional[DeviceList] = None) -> DeviceList:

    if not device_list:
        device_list = DeviceList()

    # We use |extend| below (instead of |append|) because you cannot append to a list of non-scalars in protobuf.
    # |extend| copies the elements from the list and appends them.

    # Host preprocessor.
    device = device_preprocessor()
    device_list.devices.extend([device])
    device_list.active_device_names.append(device.name)

    # SwiftShader.
    device = swift_shader_device()
    device_list.devices.extend([device])
    device_list.active_device_names.append(device.name)

    # Host device.
    device = device_host()
    device_list.devices.extend([device])
    device_list.active_device_names.append(device.name)

    # Android devices.
    android_devices = android_device.get_all_android_devices()
    device_list.devices.extend(android_devices)
    device_list.active_device_names.extend([d.name for d in android_devices])

    return device_list


DEVICE_LIST_FILE_PATH = Path("device_list.json")


def read_device_list() -> DeviceList:
    json_contents = util.file_read_text(DEVICE_LIST_FILE_PATH)
    device_list = DeviceList()
    proto_util.json_to_message(json_contents, device_list)
    return device_list


def get_active_devices(device_list: DeviceList) -> List[Device]:
    device_map: Dict[str, Device] = {}
    for device in device_list.devices:
        device_map[device.name] = device
    return [device_map[device] for device in device_list.active_device_names]


def write_device_file() -> None:
    device_list = get_device_list()
    json_contents = proto_util.message_to_json(device_list)
    util.file_write_text(DEVICE_LIST_FILE_PATH, json_contents)
