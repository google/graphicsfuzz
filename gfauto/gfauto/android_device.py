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

"""Android devices.

Provides functions for interacting with Android devices.
"""

import os
import re
import shlex
import shutil
import subprocess
import time
from pathlib import Path
from typing import List, Optional, Pattern

from gfauto import (
    binaries_util,
    devices_util,
    fuzz,
    gflogging,
    result_util,
    subprocess_util,
    types,
    util,
)
from gfauto.device_pb2 import Device, DeviceAndroid
from gfauto.gflogging import log
from gfauto.util import check, file_open_text, file_write_text

ANDROID_DEVICE_DIR = "/sdcard/Android/data/com.google.amber/cache"

ANDROID_DEVICE_GRAPHICSFUZZ_DIR = ANDROID_DEVICE_DIR + "/graphicsfuzz"
ANDROID_DEVICE_RESULT_DIR = ANDROID_DEVICE_GRAPHICSFUZZ_DIR + "/result"
ANDROID_DEVICE_AMBER_SCRIPT_FILE = ANDROID_DEVICE_GRAPHICSFUZZ_DIR + "/test.amber"

BUSY_WAIT_SLEEP_SLOW = 1.0

WAIT_AFTER_BOOT_ANIMATION = 10

WAIT_AFTER_BOOT_AND_UNLOCK = 20

ADB_DEFAULT_TIME_LIMIT = 30

ADB_SHORT_LOGCAT_TIME_LIMIT = 3


def adb_path() -> Path:
    if "ANDROID_HOME" in os.environ:
        platform_tools_path = Path(os.environ["ANDROID_HOME"]) / "platform-tools"
        adb = shutil.which("adb", path=str(platform_tools_path))
        if adb:
            return Path(adb)
    return util.tool_on_path("adb")


def adb_helper(
    serial: Optional[str],
    adb_args: List[str],
    check_exit_code: bool,
    verbose: bool = False,
    timeout: Optional[int] = ADB_DEFAULT_TIME_LIMIT,
) -> types.CompletedProcess:

    adb_cmd = [str(adb_path())]
    if serial:
        adb_cmd.append("-s")
        adb_cmd.append(serial)

    adb_cmd.extend(adb_args)

    return subprocess_util.run(
        adb_cmd, check_exit_code=check_exit_code, timeout=timeout, verbose=verbose
    )


def adb_check(
    serial: Optional[str],
    adb_args: List[str],
    verbose: bool = False,
    timeout: Optional[int] = ADB_DEFAULT_TIME_LIMIT,
) -> types.CompletedProcess:

    return adb_helper(
        serial, adb_args, check_exit_code=True, verbose=verbose, timeout=timeout
    )


def adb_can_fail(
    serial: Optional[str],
    adb_args: List[str],
    verbose: bool = False,
    timeout: Optional[int] = ADB_DEFAULT_TIME_LIMIT,
) -> types.CompletedProcess:

    return adb_helper(
        serial, adb_args, check_exit_code=False, verbose=verbose, timeout=timeout
    )


def stay_awake_warning(serial: Optional[str] = None) -> None:
    try:
        res = adb_check(
            serial, ["shell", "settings get global stay_on_while_plugged_in"]
        )
        if str(res.stdout).strip() == "0":
            log('\nWARNING: please enable "Stay Awake" from developer settings\n')
    except subprocess.CalledProcessError:
        log(
            "Failed to check Stay Awake setting. This can happen if the device has just booted."
        )


def is_screen_off_or_locked(serial: Optional[str] = None) -> bool:
    """:return: True: the screen is off or locked. False: unknown."""
    res = adb_can_fail(serial, ["shell", "dumpsys nfc"])
    if res.returncode != 0:
        log("Failed to run dumpsys.")
        return False

    stdout = str(res.stdout)
    # You will often find "mScreenState=OFF_LOCKED", but this catches OFF too, which is good.
    if "mScreenState=OFF" in stdout:
        return True
    if "mScreenState=ON_LOCKED" in stdout:
        return True

    return False


def get_device_driver_details(serial: str) -> str:
    prepare_device(wait_for_screen=True, serial=serial)
    amber_args = [
        "-d",  # Disables validation layers.
        "-V",  # Print version information.
    ]

    cmd = [
        "shell",
        get_amber_adb_shell_cmd(amber_args),
    ]

    try:
        adb_check(serial, cmd, verbose=True)
        result = adb_check(
            serial,
            ["shell", "cat", f"{ANDROID_DEVICE_RESULT_DIR}/amber_stdout.txt"],
            verbose=True,
        )
    except subprocess.SubprocessError as ex:
        raise devices_util.GetDeviceDetailsError() from ex

    match = devices_util.AMBER_DEVICE_DETAILS_PATTERN.search(result.stdout)

    if not match:
        raise devices_util.GetDeviceDetailsError(
            "Could not find device details in stdout: " + result.stdout
        )

    return match.group(1)


def ensure_amber_installed(
    device_serial: Optional[str], binary_manager: binaries_util.BinaryManager
) -> None:
    amber_apk_binary = binary_manager.get_binary_path_by_name("amber_apk")
    amber_apk_test_binary = binary_manager.get_binary_path_by_name("amber_apk_test")

    adb_can_fail(device_serial, ["uninstall", "com.google.amber"])
    adb_can_fail(device_serial, ["uninstall", "com.google.amber.test"])
    adb_check(device_serial, ["install", "-r", str(amber_apk_binary.path)])
    adb_check(device_serial, ["install", "-r", str(amber_apk_test_binary.path)])

    # Run Amber once to ensure the external cache directory (on /sdcard) gets created by the application.
    adb_check(device_serial, ["shell", get_amber_adb_shell_cmd(["-d"])], verbose=True)


def update_details(binary_manager: binaries_util.BinaryManager, device: Device) -> None:

    check(
        device.HasField("android"),
        AssertionError(f"Expected Android device: {device}"),
    )

    build_fingerprint = ""
    try:
        adb_fingerprint_result = adb_check(
            device.android.serial,
            ["shell", "getprop ro.build.fingerprint"],
            verbose=True,
        )
        build_fingerprint = adb_fingerprint_result.stdout
        build_fingerprint = build_fingerprint.strip()
    except subprocess.CalledProcessError:
        log("Failed to get device fingerprint")

    device_properties = ""
    ensure_amber_installed(device.android.serial, binary_manager)
    try:
        device_properties = get_device_driver_details(device.android.serial)
    except devices_util.GetDeviceDetailsError as ex:
        log(f"WARNING: Failed to get device driver details: {ex}")

    device.android.build_fingerprint = build_fingerprint
    device.device_properties = device_properties


def get_all_android_devices(  # pylint: disable=too-many-locals;
    binary_manager: binaries_util.BinaryManager, include_device_details: bool = True,
) -> List[Device]:
    result: List[Device] = []

    log("Getting the list of connected Android devices via adb\n")

    adb_devices = adb_check(None, ["devices", "-l"], verbose=True)
    stdout: str = adb_devices.stdout
    lines: List[str] = stdout.splitlines()
    # Remove empty lines.
    lines = [line for line in lines if line]
    check(
        lines[0].startswith("List of devices"),
        AssertionError("Could find list of devices from 'adb devices'"),
    )
    for i in range(1, len(lines)):
        fields = lines[i].split()
        device_serial = fields[0]
        device_state = fields[1]
        if device_state != "device":
            log(
                f'Skipping adb device with serial {device_serial} as its state "{device_state}" is not "device".'
            )
        # Set a simple model name, but then try to find the actual model name.
        device_model = "android_device"
        for field_index in range(2, len(fields)):
            if fields[field_index].startswith("model:"):
                device_model = util.remove_start(fields[field_index], "model:")
                break

        log(f"Found Android device: {device_model}, {device_serial}")

        device = Device(
            name=f"{device_model}_{device_serial}",
            android=DeviceAndroid(serial=device_serial, model=device_model),
        )
        if include_device_details:
            update_details(binary_manager, device)
            log(f"Android device details:\n{str(device)}")
        else:
            log(f"Skipped getting Android device details:\n{str(device)}")

        result.append(device)

    return result


TIMESTAMP_PATTERN: Pattern[str] = re.compile(r"[\d.\-:,\s]+")


def get_next_logcat_timestamp(serial: Optional[str]) -> Optional[str]:
    # Get last log event.
    res = adb_check(serial, ["logcat", "-d", "-T", "1"])
    out = res.stdout  # type: Optional[str]
    if not out:
        return None

    lines = out.splitlines()
    if not lines:
        return None

    # Get last line. E.g. "04-02 19:23:23.579   611   611 D logd    :  etc.".
    last_line = lines[-1]

    parts = last_line.split(" ")
    # The first two parts are the timestamp.
    if len(parts) < 2:
        return None
    timestamp = f"{parts[0]} {parts[1]}"
    if not TIMESTAMP_PATTERN.fullmatch(timestamp):
        return None

    check(
        last_line.startswith(timestamp),
        AssertionError(
            f"Last line and extracted timestamp did not match: \n{last_line}\n{timestamp}"
        ),
    )

    # We want a timestamp that is _later_ than the final timestamp.
    # Timestamp arguments passed to adb are rounded (by adb), so concatenating a "9"
    # is a simple way to get the "next" timestamp at the finest granularity.
    return timestamp + "9"


def prepare_device(
    wait_for_screen: bool, serial: Optional[str] = None
) -> Optional[str]:
    """
    Prepares an Android device, ensuring it is unlocked, the logcat is cleared, etc.

    Returns the next logcat timestamp (or None if one cannot be obtained) because clearing the logcat is unreliable;
    the timestamp can be used to ignore old logcat events when getting the logcat later.
    """
    device_was_booting_or_locked = False

    res = adb_can_fail(serial, ["get-state"])
    if res.returncode != 0 or res.stdout.strip() != "device":
        device_was_booting_or_locked = True

    adb_check(serial, ["wait-for-device"], timeout=None)

    # Conservatively check that booting has finished.
    while True:

        log("Checking if boot animation has finished.")

        res_bootanim = adb_can_fail(serial, ["shell", "getprop init.svc.bootanim"])
        res_bootanim_exit = adb_can_fail(
            serial, ["shell", "getprop service.bootanim.exit"]
        )
        if res_bootanim.returncode != 0 and res_bootanim_exit.returncode != 0:
            # Both commands failed so there is no point in trying to use either result.
            log("Could not check boot animation; continuing.")
            break
        if (
            res_bootanim.stdout.strip() != "running"
            and res_bootanim_exit.stdout.strip() != "0"
        ):
            # Both commands suggest the boot animation is NOT running.
            # This may include one or both commands returning nothing because the property doesn't exist on this device.
            log("Boot animation is not running.")
            break

        # If either command suggests the boot animation is running, we assume it is accurate, so we wait.
        device_was_booting_or_locked = True
        time.sleep(BUSY_WAIT_SLEEP_SLOW)

    if device_was_booting_or_locked:
        log(
            f"Device appeared to be booting previously, so waiting a further {WAIT_AFTER_BOOT_ANIMATION} seconds."
        )
        time.sleep(WAIT_AFTER_BOOT_ANIMATION)

    if wait_for_screen:
        stay_awake_warning(serial)
        # We cannot reliably know if the screen is on, but this function definitely knows if it is
        # off or locked. So we wait here while we definitely know there is an issue.
        count = 0
        while is_screen_off_or_locked(serial):
            log(
                "\nWARNING: The screen appears to be off or locked. Please unlock the device and ensure 'Stay Awake' is enabled in developer settings.\n"
            )
            device_was_booting_or_locked = True
            time.sleep(BUSY_WAIT_SLEEP_SLOW)
            count += 1
            if count > 1 and (count % 3) == 0:
                log("Pressing the menu key.")
                adb_can_fail(serial, ["shell", "input keyevent 82"], verbose=True)

    if device_was_booting_or_locked:
        log(
            f"Device appeared to be booting previously, so waiting a further {WAIT_AFTER_BOOT_AND_UNLOCK} seconds."
        )
        time.sleep(WAIT_AFTER_BOOT_AND_UNLOCK)

    # We do NOT use "mkdir -p" here; it is important that the Amber app creates the
    # "/sdcard/Android/data/com.google.amber/cache" directory, and then we can create additional directories within
    # this.
    adb_check(
        serial,
        [
            "shell",
            # One string:
            # Remove graphicsfuzz directory.
            f"rm -rf {ANDROID_DEVICE_GRAPHICSFUZZ_DIR} && "
            # Make the graphicsfuzz directory.
            f"mkdir {ANDROID_DEVICE_GRAPHICSFUZZ_DIR} && "
            # Make the result directory.
            f"mkdir {ANDROID_DEVICE_RESULT_DIR}",
        ],
    )

    log("Clearing logcat.")
    adb_check(serial, ["logcat", "-c"])

    # Logcat is not always cleared, so get the last timestamp from the logcat (if there is one); we will use that
    # to ignore old logcat entries later.
    next_logcat_timestamp = get_next_logcat_timestamp(serial)

    return next_logcat_timestamp


def run_amber_on_device(
    amber_script_file: Path,
    output_dir: Path,
    dump_image: bool,
    dump_buffer: bool,
    skip_render: bool = False,
    serial: Optional[str] = None,
) -> Path:

    with file_open_text(result_util.get_amber_log_path(output_dir), "w") as log_file:
        try:
            gflogging.push_stream_for_logging(log_file)

            run_amber_on_device_helper(
                amber_script_file,
                output_dir,
                dump_image,
                dump_buffer,
                skip_render,
                serial,
            )
        finally:
            gflogging.pop_stream_for_logging()

    return output_dir


def get_amber_adb_shell_cmd(amber_args: List[str]) -> str:
    shell_command = [
        "am",
        "instrument",
        "-w",
        "-e",
        "stdout",
        f"{ANDROID_DEVICE_RESULT_DIR}/amber_stdout.txt",
        "-e",
        "stderr",
        f"{ANDROID_DEVICE_RESULT_DIR}/amber_stderr.txt",
    ]

    # Amber arguments are passed as key-value pairs via -e. E.g. for "-d": -e arg1 -d
    arg_index = 1
    for amber_arg in amber_args:
        shell_command.append("-e")
        shell_command.append(f"arg{arg_index}")
        shell_command.append(amber_arg)
        arg_index += 1

    shell_command.append(
        "com.google.amber.test/androidx.test.runner.AndroidJUnitRunner"
    )

    shell_command = [shlex.quote(c) for c in shell_command]
    return " ".join(shell_command)


def run_amber_on_device_helper(
    amber_script_file: Path,
    output_dir: Path,
    dump_image: bool,
    dump_buffer: bool,
    skip_render: bool = False,
    serial: Optional[str] = None,
) -> Path:

    next_logcat_timestamp_after_clear = prepare_device(
        wait_for_screen=True, serial=serial
    )

    adb_check(
        serial, ["push", str(amber_script_file), ANDROID_DEVICE_AMBER_SCRIPT_FILE]
    )

    amber_args = [
        "-d",  # Disables validation layers.
        ANDROID_DEVICE_AMBER_SCRIPT_FILE,
        "--log-graphics-calls-time",
        "--disable-spirv-val",
    ]
    if skip_render:
        # -ps tells amber to stop after pipeline creation
        amber_args.append("-ps")
    else:
        if dump_image:
            amber_args += [
                "-I",
                "variant_framebuffer",
                "-i",
                f"{ANDROID_DEVICE_RESULT_DIR}/{fuzz.VARIANT_IMAGE_FILE_NAME}",
                "-I",
                "reference_framebuffer",
                "-i",
                f"{ANDROID_DEVICE_RESULT_DIR}/{fuzz.REFERENCE_IMAGE_FILE_NAME}",
            ]
        if dump_buffer:
            amber_args += [
                "-b",
                f"{ANDROID_DEVICE_RESULT_DIR}/{fuzz.BUFFER_FILE_NAME}",
                "-B",
                "0",
            ]

    cmd = [
        "shell",
        get_amber_adb_shell_cmd(amber_args),
    ]

    status = "UNEXPECTED_ERROR"

    result: Optional[types.CompletedProcess] = None

    # Before running, try to ensure the app is not already running.
    adb_can_fail(serial, ["shell", "am force-stop com.google.amber"])

    try:
        result = adb_can_fail(
            serial, cmd, verbose=True, timeout=fuzz.AMBER_RUN_TIME_LIMIT
        )
    except subprocess.TimeoutExpired:
        status = fuzz.STATUS_TIMEOUT
        adb_can_fail(serial, ["shell", "am force-stop com.google.amber"])

    try:
        if result:
            if result.returncode != 0:
                log(
                    "WARNING: am instrument command failed, which is unexpected, even if the GPU driver crashed!"
                )
                status = fuzz.STATUS_CRASH
            elif "shortMsg=Process crashed" in result.stdout:
                status = fuzz.STATUS_CRASH
            else:
                status = fuzz.STATUS_SUCCESS

            adb_check(
                serial,
                # The /. syntax means the contents of the results directory will be copied into output_dir.
                ["pull", ANDROID_DEVICE_RESULT_DIR + "/.", str(output_dir)],
            )

        gflogging.log_a_file(output_dir / "amber_stdout.txt")
        gflogging.log_a_file(output_dir / "amber_stderr.txt")

        # Grab the log.
        logcat_dump_cmd = ["logcat", "-d"]
        if next_logcat_timestamp_after_clear:
            # Only include logcat events after the previous logcat clear.
            logcat_dump_cmd += ["-T", next_logcat_timestamp_after_clear]
        # Use a short time limit to increase the chance of detecting a device reboot.
        adb_check(
            serial, logcat_dump_cmd, verbose=True, timeout=ADB_SHORT_LOGCAT_TIME_LIMIT
        )

    except subprocess.SubprocessError:
        # If we fail in getting the results directory or log, assume the device has rebooted.
        status = fuzz.STATUS_UNRESPONSIVE

    log("\nSTATUS " + status + "\n")

    file_write_text(result_util.get_status_path(output_dir), status)

    return output_dir
