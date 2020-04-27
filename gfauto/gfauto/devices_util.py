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

"""Devices utility module.

Used to enumerate the available devices and work with device lists.
"""
import re
from typing import Dict, List, Optional, Pattern

from gfauto import android_device, binaries_util, host_device_util
from gfauto.device_pb2 import (
    Device,
    DeviceHost,
    DeviceList,
    DevicePreprocess,
    DeviceShaderCompiler,
    DeviceSwiftShader,
)
from gfauto.gflogging import log
from gfauto.util import ToolNotOnPathError, check

# [\s\S] matches anything, including newlines.
# *? is non-greedy.

AMBER_DEVICE_DETAILS_PATTERN: Pattern[str] = re.compile(
    r"Physical device properties:\n([\s\S]*?)End of physical device properties."
)


class GetDeviceDetailsError(Exception):
    pass


def swift_shader_device(binary_manager: binaries_util.BinaryManager) -> Device:
    device = Device(name="swift_shader", swift_shader=DeviceSwiftShader())
    update_device(binary_manager, device)
    return device


def device_preprocessor() -> Device:
    # TODO: think about the fact that the versions of glslang, spirv-opt etc. are stored in the test and not the device.
    #  This might make it rather strange if you want to test many different versions of, say, spirv-opt.
    #  We could store version hashes in both the test and the "preprocessor device", and let the "preprocessor device"
    #  have higher priority.
    return Device(name="host_preprocessor", preprocess=DevicePreprocess())


def device_host(binary_manager: binaries_util.BinaryManager) -> Device:
    device = Device(name="host", host=DeviceHost())
    update_device(binary_manager, device)
    return device


def _update_device_swiftshader(
    binary_manager: binaries_util.BinaryManager, device: Device
) -> None:

    check(
        device.HasField("swift_shader"),
        AssertionError(f"Expected SwiftShader device: {device}"),
    )

    amber_path = binary_manager.get_binary_path_by_name(binaries_util.AMBER_NAME).path

    swift_shader_binary_and_path = binary_manager.get_binary_path_by_name(
        binaries_util.SWIFT_SHADER_NAME
    )
    driver_details = ""
    try:
        driver_details = host_device_util.get_driver_details(
            amber_path, swift_shader_binary_and_path.path
        )
    except GetDeviceDetailsError as ex:
        log(f"WARNING: Failed to get device driver details: {ex}")

    device.device_properties = driver_details

    del device.binaries[:]
    device.binaries.extend([swift_shader_binary_and_path.binary])


def _update_device_host(
    binary_manager: binaries_util.BinaryManager, device: Device
) -> None:
    check(
        device.HasField("host"), AssertionError(f"Expected host device: {device}"),
    )

    amber_path = binary_manager.get_binary_path_by_name(binaries_util.AMBER_NAME).path

    driver_details = ""
    try:
        driver_details = host_device_util.get_driver_details(
            amber_path, custom_launcher=list(device.host.custom_launcher)
        )
    except GetDeviceDetailsError as ex:
        log(f"WARNING: Failed to get device driver details: {ex}")

    device.device_properties = driver_details


def _update_device_shader_compiler(
    binary_manager: binaries_util.BinaryManager, device: Device
) -> None:
    check(
        device.HasField("shader_compiler"),
        AssertionError(f"Expected shader_compiler device: {device}"),
    )

    # The only thing we can do is update the shader compiler binary if it is a built-in binary.

    if binaries_util.is_built_in_binary_name(device.shader_compiler.binary):
        # Remove existing binaries with this name from the device's binaries list.
        binaries = list(device.binaries)
        binaries = [b for b in binaries if b.name != device.shader_compiler.binary]
        del device.binaries[:]
        device.binaries.extend(binaries)

        # Add our latest version of the binary.
        device.binaries.extend(
            [binary_manager.get_binary_by_name(device.shader_compiler.binary)]
        )


def update_device(binary_manager: binaries_util.BinaryManager, device: Device) -> None:
    """
    Updates a device.

    This mostly just means setting the "device_properties" field of the device to the output of "amber -d -V".
    Android devices will also have their build_fingerprint updated.

    :param binary_manager:
    :param device:
    :return:
    """
    if device.HasField("preprocess"):
        pass
    elif device.HasField("swift_shader"):
        _update_device_swiftshader(binary_manager, device)
    elif device.HasField("host"):
        _update_device_host(binary_manager, device)
    elif device.HasField("android"):
        android_device.update_details(binary_manager, device)
    elif device.HasField("shader_compiler"):
        _update_device_shader_compiler(binary_manager, device)
    else:
        raise AssertionError(f"Unrecognized device type: {device}")


def get_device_list(
    binary_manager: binaries_util.BinaryManager,
    device_list: Optional[DeviceList] = None,
) -> DeviceList:

    if not device_list:
        device_list = DeviceList()

    # We use |extend| below (instead of |append|) because you cannot append to a list of non-scalars in protobuf.
    # |extend| copies the elements from the list and appends them.

    # Host preprocessor.
    device = device_preprocessor()
    device_list.devices.extend([device])
    device_list.active_device_names.append(device.name)

    # SwiftShader.
    device = swift_shader_device(binary_manager)
    device_list.devices.extend([device])
    device_list.active_device_names.append(device.name)

    # Host device.
    device = device_host(binary_manager)
    device_list.devices.extend([device])
    device_list.active_device_names.append(device.name)

    try:
        # Android devices.
        android_devices = android_device.get_all_android_devices(binary_manager)
        device_list.devices.extend(android_devices)
        device_list.active_device_names.extend([d.name for d in android_devices])
    except ToolNotOnPathError:
        log(
            "WARNING: adb was not found on PATH nor was ANDROID_HOME set; "
            "Android devices will not be added to settings.json"
        )

    # Offline compiler.
    device = Device(
        name="amdllpc",
        shader_compiler=DeviceShaderCompiler(
            binary="amdllpc",
            args=["-val=false", "-gfxip=9.0.0", "-verify-ir", "-auto-layout-desc"],
        ),
        binaries=[binary_manager.get_binary_by_name("amdllpc")],
    )
    device_list.devices.extend([device])
    # Don't add to active devices, since this is mostly just an example.

    return device_list


def get_active_devices(
    device_list: DeviceList, active_device_names: Optional[List[str]] = None
) -> List[Device]:
    device_map: Dict[str, Device] = {}
    for device in device_list.devices:
        device_map[device.name] = device
    if not active_device_names:
        active_device_names = list(device_list.active_device_names)
    return [device_map[device] for device in active_device_names]
