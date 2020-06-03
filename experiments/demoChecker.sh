#!/bin/bash

# Copyright 2020 The GraphicsFuzz Project Authors
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

# Sample Repo Paths
#
# This script is designed with the expectation that
# it is being run in a directory where all the
# corresponding build scripts have been run.
#

export VK_ICD_FILENAMES="$(pwd)/swiftshader/build-coverage/Linux/vk_swiftshader_icd.json"

IMAGINATION_DEMOS=Imagination-Vulkan-Samples
GOOGLE_SAMPLES=vulkan_test_applications
KHRONOS_SAMPLES=Vulkan-Samples
SASCHA_DEMOS=SaschaWillems_Vulkan

# Default values
OUTPUT_FILE_NAME="demoCheckerLog"
TIMEOUT_DURATION=30
TIMEOUT_KILL_DURATION=5

SASCHA_FLAG=true
GOOGLE_FLAG=true
KHRONOS_FLAG=true
IMAGINATION_FLAG=true

KHRONOS_SAMPLE_IDS=(
    hello_triangle
    dynamic_uniform_buffers
    texture_loading
    hdr
    instancing
    compute_nbody
    terrain_tessellation
    conservative_rasterization
    push_descriptors
    raytracing_basic
    swapchain_images
    surface_rotation
    pipeline_cache
    descriptor_management
    render_passes
    msaa
    render_subpasses
    pipeline_barriers
    wait_idle
    layout_transitions
    specialization_constants
    command_buffer_usage
    afbc
    texture_mipmap_generation
    debug_utils
)

# Helper Functions

usage () {
cat << HELP
demoChecker runs all the demo binaries in the graphicsfuzz/experiments directory and logs the result in a log file.

Options:
-t|--timeout-duration                   The duration for which each demo binary is supposed to run. Default: 30s.
-tk|--timeout-kill-duration             The duration after which the kill signal is sent to the demo binary. Default: 60s.
-o|--output                             The name of the output log file. Default: demoCheckerLog.
-dsascha|--disable-sascha               Excludes the Sascha Willems Demos from the run.
-dgoogle|--disable-google               Excludes the Google Test Applications from the run.
-dkhronos|--disable-khronos             Excludes the Khronos Vulkan Samples from the run.
-dimagination|--disable-imagination     Excludes the Imagination Demos from the run.
-h|--help                               Prints help information.

HELP
}

# Command line arguments
for arg in "$@"
do
    case $arg in
        -t|--timout-duration)
            TIMEOUT_DURATION="$2"
            shift
            shift
        ;;
        -tk|--timeout-kill-duration)
            TIMEOUT_KILL_DURATION="$2"
            shift
            shift
        ;;
        -o|--output)
            OUTPUT_FILE_NAME="$2"
            shift
            shift
        ;;
        -dsascha|--disable-sascha)
            SASCHA_FLAG=false
        ;;
        -dgoogle|--disable-google)
            GOOGLE_FLAG=false;
        ;;
        -dkhronos|--disable-khronos)
            KHRONOS_FLAG=false
        ;;
        -dimagination|--disable-imagination)
            IMAGINATION_FLAG=false;
        ;;
        -h|--help)
            usage
            exit 0
        ;;
    esac
done

OUTPUT_FILE_NAME="$OUTPUT_FILE_NAME.csv"

# If existing log exists, delete it
if [ -f $OUTPUT_FILE_NAME ]; then
    rm $OUTPUT_FILE_NAME
fi

# Run through Imagination Demos
if [[ $IMAGINATION_FLAG = true ]]; then
    echo "Imagination Demos"

    for file in $(find $IMAGINATION_DEMOS/build/bin -executable -type f)
    do
        if [[ $file =~ ^$IMAGINATION_DEMOS/build/bin/Vulkan ]]; then
            echo "File: " $file
            timeout -k $TIMEOUT_KILL_DURATION $TIMEOUT_DURATION $file
            returnCode=$?

            # We expect the apps to timeout. Any other behaviour is a failure.
            if [ $returnCode -eq 124 ] || [ $returnCode -eq 137 ]; then
                echo "OK,$file,$returnCode" >> $OUTPUT_FILE_NAME
            else
                echo "FAIL,$file,$returnCode" >> $OUTPUT_FILE_NAME
            fi
        fi
    done
fi

# Run through Google Samples
if [[ $GOOGLE_FLAG = true ]]; then
    echo "Google Test Applications"

    for file in $(find $GOOGLE_SAMPLES/build/bin -executable -type f)
    do
        echo "File: " $file
        timeout -k $TIMEOUT_KILL_DURATION $TIMEOUT_DURATION $file
        returnCode=$?

        # We expect the apps to timeout. Any other behaviour is a failure.
        if [ $returnCode -eq 124 ] || [ $returnCode -eq 137 ]; then
            echo "OK,$file,$returnCode" >> $OUTPUT_FILE_NAME
        else
            echo "FAIL,$file,$returnCode" >> $OUTPUT_FILE_NAME
        fi
    done
fi

# Run through Sascha Willems Demos
if [[ $SASCHA_FLAG = true ]]; then
    echo "Sascha Willems Demos"

    for file in $(find $SASCHA_DEMOS/build/bin -executable -type f)
    do
        echo "File: " $file
        timeout -k $TIMEOUT_KILL_DURATION $TIMEOUT_DURATION $file
        returnCode=$?

        # We expect the apps to timeout. Any other behaviour is a failure.
        if [ $returnCode -eq 124 ] || [ $returnCode -eq 137 ]; then
            echo "OK,$file,$returnCode" >> $OUTPUT_FILE_NAME
        else
            echo "FAIL,$file,$returnCode" >> $OUTPUT_FILE_NAME
        fi
    done
fi

# Run through Khronos Samples
if [[ $KHRONOS_FLAG = true ]]; then
    echo "Khronos Vulkan Samples"

    pushd $KHRONOS_SAMPLES
    for sample_id in "${KHRONOS_SAMPLE_IDS[@]}"
    do
        file=$KHRONOS_SAMPLES/$sample_id
        echo "File: " $file
        timeout -k $TIMEOUT_KILL_DURATION $TIMEOUT_DURATION ./build/linux/app/bin/Release/x86_64/vulkan_samples --sample $sample_id
        returnCode=$?

        # We expect the apps to timeout. Any other behaviour is a failure.
        if [ $returnCode -eq 124 ] || [ $returnCode -eq 137 ]; then
            echo "OK,$file,$returnCode" >> ../$OUTPUT_FILE_NAME
        else
            echo "FAIL,$file,$returnCode" >> ../$OUTPUT_FILE_NAME
        fi
    done
    popd
fi

