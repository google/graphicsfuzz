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

import os
import io
import sys
import typing
import http.client
import urllib.request

path = os.path.join


def get_extras():
    return {

        'angle': {
            'name': 'ANGLE - Almost Native Graphics Layer Engine',
            'license': 'BSD License',
            'url': 'https://github.com/google/angle',
            'license_file': '',
            'license_url': 'https://raw.githubusercontent.com/google/angle/a0ccea1e8d1f2b7620db08c7cbbac7d01cfc38d6/LICENSE',
            'skipped': '',
        },

        'spirv-headers': {
            'name': 'SPIR-V Headers',
            'url': 'https://github.com/KhronosGroup/SPIRV-Headers',
            'license_file': '',
            'license_url': 'https://raw.githubusercontent.com/KhronosGroup/SPIRV-Headers/d5b2e1255f706ce1f88812217e9a554f299848af/LICENSE',
            'skipped': '',
        },

        'libpng': {
            'comment': 'Used by angle',
            'name': 'libpng',
            'url': 'http://libpng.org/pub/png/libpng.html',
            'license_file': path('third_party', 'licenses', 'libpng-LICENSE.txt'),
            'license_url': '',
            'skipped': '',
        },

        'minigbm': {
            'comment': 'Used by angle',
            'name': 'minigbm',
            'url': 'https://chromium.googlesource.com/chromiumos/platform/minigbm/+/master',
            'license_url': '',
            'license_file': path('third_party', 'licenses', 'minigbm.txt'),
            'skipped': '',
        },

        'rapidjson': {
            'comment': 'Used by angle',
            'name': 'RapidJSON',
            'url': 'https://github.com/TenCent/rapidjson',
            'license_url': 'https://raw.githubusercontent.com/Tencent/rapidjson/6a6bed2759d42891f9e29a37b21315d3192890ed/license.txt',
            'license_file': '',
            'skipped': '',
        },

        'glslang': {
            'comment': 'Used by us, angle, swiftshader',
            'name': 'glslang - OpenGL / OpenGL ES Reference Compiler',
            'url': 'https://github.com/KhronosGroup/glslang',
            'license_url': 'https://raw.githubusercontent.com/google/shaderc/98ab88bcac1724ced19b8add0b582e402261ec17/third_party/LICENSE.glslang',
            'license_file': '',
            'skipped': '',
        },

        'spirv-tools': {
            'comment': 'Used by us, glslang, angle',
            'name': 'SPIR-V Tools',
            'url': 'https://github.com/KhronosGroup/SPIRV-Tools',
            'license_url': 'https://raw.githubusercontent.com/KhronosGroup/SPIRV-Tools/a8697b0612cdc8c607ca21683a0e9916f4bd2f52/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'vulkan-headers': {
            'comment': 'Used by angle',
            'name': 'Vulkan-Headers',
            'url': 'https://github.com/KhronosGroup/Vulkan-Headers',
            'license_url': 'http://www.apache.org/licenses/LICENSE-2.0.txt',
            'license_file': '',
            'skipped': '',
        },

        'vulkan-loader': {
            'comment': 'Used by angle',
            'name': 'Vulkan Loader - Vulkan Ecosystem Components',
            'url': 'https://github.com/KhronosGroup/Vulkan-Loader',
            'license_url': 'http://www.apache.org/licenses/LICENSE-2.0.txt',
            'license_file': '',
            'skipped': '',
        },

        'vulkan-tools': {
            'comment': 'Used by angle',
            'name': 'Vulkan Tools - Vulkan Ecosystem Components',
            'url': 'https://github.com/KhronosGroup/Vulkan-Tools',
            'license_url': 'http://www.apache.org/licenses/LICENSE-2.0.txt',
            'license_file': '',
            'skipped': '',
        },

        'vulkan-validation-layers': {
            'comment': 'Used by angle',
            'name': 'Vulkan Validation Layers - Vulkan Ecosystem Components',
            'url': 'https://github.com/KhronosGroup/Vulkan-ValidationLayers',
            'license_url': 'https://raw.githubusercontent.com/KhronosGroup/Vulkan-ValidationLayers/b8d149f81be4496ef8df11097e17365268ea832f/LICENSE.txt',
            'license_file': '',
            'skipped': '',
        },

        'ArrayBoundsClamper': {
            'comment': 'Used by angle',
            'name': 'ArrayBoundsClamper from WebKit',
            'url': 'http://webkit.org',
            'license_url': 'https://raw.githubusercontent.com/google/angle/38f24ee666e1db9d2566e1d0d94a6e90db3549d1/src/third_party/compiler/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'libXNVCtrl': {
            'comment': 'Used by angle',
            'name': 'NVidia Control X Extension Library',
            'url': 'http://cgit.freedesktop.org/~aplattner/nvidia-settings/',
            'license_url': 'https://raw.githubusercontent.com/google/angle/38f24ee666e1db9d2566e1d0d94a6e90db3549d1/src/third_party/libXNVCtrl/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'SystemInfo': {
            'comment': 'Used by angle',
            'name': 'SystemInfo from WebKit',
            'url': 'http://webkit.org',
            'license_url': 'https://raw.githubusercontent.com/google/angle/38f24ee666e1db9d2566e1d0d94a6e90db3549d1/src/third_party/compiler/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'trace_event': {
            'comment': 'Used by angle',
            'name': 'trace_event from Chromium',
            'url': 'https://www.chromium.org/Home',
            'license_url': 'https://raw.githubusercontent.com/adobe/chromium/cfe5bf0b51b1f6b9fe239c2a3c2f2364da9967d7/LICENSE',
            'license_file': '',
            'skipped': '',
        },



        'swiftshader': {
            'comment': '',
            'name': 'SwiftShader',
            'url': 'https://github.com/google/swiftshader',
            'license_url': 'https://raw.githubusercontent.com/google/swiftshader/720aec1cd6ebf4c4d74326c5faaddd57ee351609/LICENSE.txt',
            'license_file': '',
            'skipped': '',
        },

        'libbacktrace': {
            'comment': 'Used by swiftshader',
            'name': 'libbacktrace',
            'url': 'https://github.com/ianlancetaylor/libbacktrace',
            'license_url': 'https://raw.githubusercontent.com/ianlancetaylor/libbacktrace/5a99ff7fed66b8ea8f09c9805c138524a7035ece/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'llvm-7.0': {
            'comment': '',
            'name': 'LLVM 7.0',
            'url': 'https://llvm.org/',
            'license_url': 'https://raw.githubusercontent.com/google/swiftshader/ae022faf53b9f648324874063d7147ba7b555417/third_party/llvm-7.0/llvm/LICENSE.TXT',
            'license_file': '',
            'skipped': '',
        },

        'marl': {
            'comment': '',
            'name': 'Marl',
            'url': 'https://github.com/google/marl',
            'license_url': 'https://raw.githubusercontent.com/google/swiftshader/ae022faf53b9f648324874063d7147ba7b555417/third_party/marl/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'stlport-cpp11-extension': {
            'comment': '',
            'name': 'stlport-cpp11-extension',
            'url': 'https://github.com/google/swiftshader/tree/ae022faf53b9f648324874063d7147ba7b555417/third_party/stlport-cpp11-extension',
            'license_url': 'http://www.apache.org/licenses/LICENSE-2.0.txt',
            'license_file': '',
            'skipped': '',
        },


        'opengl-headers': {
            'comment': '',
            'name': 'OpenGL headers',
            'url': 'https://github.com/KhronosGroup/OpenGL-Registry',
            'license_url': '',
            'license_file': path('third_party', 'licenses', 'opengl-headers.txt'),
            'skipped': '',
        },

        'egl-headers': {
            'comment': '',
            'name': 'EGL headers',
            'url': 'https://github.com/KhronosGroup/EGL-Registry',
            'license_url': '',
            'license_file': path('third_party', 'licenses', 'opengl-headers.txt'),
            'skipped': '',
        },

        'glad': {
            'comment': '',
            'name': 'glad',
            'url': 'https://github.com/Dav1dde/glad',
            'license_url': 'https://raw.githubusercontent.com/Dav1dde/glad/4d3367c063fc23eea6fb5b7c91eb7bfd74a3599b/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'get-image-glsl': {
            'comment': '',
            'name': 'get-image-glsl',
            'url': 'https://github.com/mc-imperial/get-image-glsl',
            'license_url': 'https://raw.githubusercontent.com/mc-imperial/get-image-glsl/b3afe59bfd1353dfa705526ff2473ffbb5937f34/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'nlohmann_json': {
            'comment': '',
            'name': 'JSON for Modern C++',
            'url': 'https://github.com/nlohmann/json',
            'license_url': 'https://raw.githubusercontent.com/nlohmann/json/c8231eff75ca51a03416976b490d876f3dcd93f6/LICENSE.MIT',
            'license_file': '',
            'skipped': '',
        },

        'lodepng': {
            'comment': 'Used by Vulkan worker and Amber',
            'name': 'LodePNG',
            'url': 'https://github.com/lvandeve/lodepng',
            'license_url': 'https://raw.githubusercontent.com/lvandeve/lodepng/941de186edfc68bca5ba1043423d0937b4baf3c6/LICENSE',
            'license_file': '',
            'skipped': '',
        },


        'opencv': {
            'comment': 'We just use histogram and PSNR image comparison algorithms.',
            'name': 'OpenCV',
            'url': 'https://github.com/opencv/opencv',
            'license_url': 'https://raw.githubusercontent.com/opencv/opencv/690fb0544c5847dbd6122deb705009abf29818a8/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'max-sills-shaders': {
            'comment': '',
            'name': 'Max Sills\' GLSL shaders',
            'url':
                [
                    'https://www.shadertoy.com/view/4sy3D3',
                    'https://www.shadertoy.com/view/MtdGzf',
                    'https://www.shadertoy.com/view/llKGRc',
                    'https://www.shadertoy.com/view/lsKXDW',
                ],
            'license_url': '',
            'license_file': path('third_party', 'licenses', 'max-sills-shaders.txt'),
            'skipped': '',
        },

        'valters-mednis-shaders': {
            'comment': '',
            'name': 'Valters Mednis\' GLSL shaders',
            'url':'https://www.shadertoy.com/view/ltVGWG',
            'license_url': '',
            'license_file': path('third_party', 'licenses', 'valters-mednis-shaders.txt'),
            'skipped': '',
        },

        'libgdx': {
            'comment': '',
            'name': 'LibGDX',
            'url': 'https://github.com/libgdx/libgdx',
            'license_url': 'https://raw.githubusercontent.com/libgdx/libgdx'
                           '/621682e916ab7934edb7d25c88e8a1fac4b216dd/LICENSE',
            'license_file': '',
            'skipped': '',
        },


        'ar.com.hjg:pngj': {
            'comment': 'License specified in Maven package.',
            'name': 'PNGJ',
            'url': 'https://github.com/leonbloy/pngj',
            'license_url': 'http://www.apache.org/licenses/LICENSE-2.0.txt',
            'license_file': '',
            'skipped': '',
        },

        'com.android.support': {
            'comment': '',
            'name': 'Android support libraries',
            'url': 'https://source.android.com/',
            'license_url': '',
            'license_file': path('third_party', 'licenses', 'android-support-libraries.txt'),
            'skipped': '',
        },

        'lwjgl3': {
            'comment': '',
            'name': 'LWJGL - Lightweight Java Game Library 3',
            'url': 'https://github.com/LWJGL/lwjgl3',
            'license_url': 'https://raw.githubusercontent.com/LWJGL/lwjgl3'
                           '/e17a1f9872a8ca3a6f00acb4341f2df0ac0c27b7/LICENSE.md',
            'license_file': '',
            'skipped': '',
        },

        'jemalloc': {
            'comment': '',
            'name': 'jemalloc',
            'url': 'https://github.com/jemalloc/jemalloc',
            'license_url': 'https://raw.githubusercontent.com/jemalloc/jemalloc'
                           '/115ce93562ab76f90a2509bf0640bc7df6b2d48f/COPYING',
            'license_file': '',
            'skipped': '',
        },

        'jna': {
            'comment': '',
            'name': 'jna',
            'url': 'https://github.com/java-native-access/jna',
            'license_url': 'https://raw.githubusercontent.com/java-native-access/jna'
                           '/0463bc0504efd29f07c5d22ec7c6be56b358ac77/AL2.0',
            'license_file': '',
            'skipped': '',
        },

        'org.zeroturnaround:zt-exec': {
            'comment': '',
            'name': 'zt-exec',
            'url': 'https://github.com/zeroturnaround/zt-exec',
            'license_url': [
                'https://raw.githubusercontent.com/zeroturnaround/zt-exec'
                '/6c3b93b99bf3c69c9f41d6350bf7707005b6a4cd/NOTICE.txt',
                'https://raw.githubusercontent.com/zeroturnaround/zt-exec'
                '/6c3b93b99bf3c69c9f41d6350bf7707005b6a4cd/LICENSE',
            ],
            'license_file': '',
            'skipped': '',
        },

        'org.zeroturnaround:zt-process-killer': {
            'comment': '',
            'name': 'zt-process-killer',
            'url': 'https://github.com/zeroturnaround/zt-process-killer',
            'license_url': '',
            'license_file': path('third_party', 'licenses', 'zt-process-killer.txt'),
            'skipped': '',
        },

        'cJSON': {
            'comment': '',
            'name': 'cJSON',
            'url': 'https://github.com/DaveGamble/cJSON',
            'license_url': 'https://raw.githubusercontent.com/DaveGamble/cJSON'
                           '/2c914c073d71701b596fa58a84529712a0bd1eeb/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'gflags': {
            'comment': '',
            'name': 'gflags',
            'url': 'https://github.com/gflags/gflags',
            'license_url': 'https://raw.githubusercontent.com/gflags/gflags'
                           '/498cfa8b137652b636c0dbc2427eaf8637766693/COPYING.txt',
            'license_file': '',
            'skipped': '',
        },


        'amber': {
            'comment': '',
            'name': 'Amber',
            'url': 'https://github.com/google/amber',
            'license_url': 'https://raw.githubusercontent.com/google/amber/ab41eacc3fba9ea3365a57b1712837cb4ed6d2c4/LICENSE',
            'license_file': '',
            'skipped': '',
        },



        'amdvlk': {
            'comment': '',
            'name': 'AMD Open Source Driver for Vulkan (AMDVLK)',
            'url': 'https://github.com/GPUOpen-Drivers/AMDVLK',
            'license_url': 'https://raw.githubusercontent.com/GPUOpen-Drivers/AMDVLK/808b6d5603653e6efa7a0da46b4f519f0c105b27/LICENSE.txt',
            'license_file': '',
            'skipped': '',
        },

        'cwpack': {
            'comment': '',
            'name': 'CWPack',
            'url': 'https://github.com/clwi/CWPack',
            'license_url': 'https://raw.githubusercontent.com/clwi/CWPack/43583ff9abe6f5e68602eccb24d5f5c3aceac51c/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'metrohash': {
            'comment': '',
            'name': 'MetroHash',
            'url': 'https://github.com/jandrewrogers/MetroHash',
            'license_url': 'https://raw.githubusercontent.com/jandrewrogers/MetroHash/690a521d9beb2e1050cc8f273fdabc13b31bf8f6/LICENSE',
            'license_file': '',
            'skipped': '',
        },

        'llvm': {
            'comment': 'Used by AMDVLK',
            'name': 'LLVM',
            'url': 'https://llvm.org/',
            'license_url': 'https://raw.githubusercontent.com/GPUOpen-Drivers/llvm/21b028dc97ee9b0c6629ff83c2924b3b80c5d6e7/LICENSE.TXT',
            'license_file': '',
            'skipped': '',
        },

        'llpc': {
            'comment': '',
            'name': 'LLVM-Based Pipeline Compiler (LLPC)',
            'url': 'https://github.com/GPUOpen-Drivers/llpc',
            'license_url': [
                'https://raw.githubusercontent.com/GPUOpen-Drivers/llpc/8b8fc751408274f1f96064e183956d6fab1d54d1/LICENSE',
                'https://raw.githubusercontent.com/GPUOpen-Drivers/AMDVLK/808b6d5603653e6efa7a0da46b4f519f0c105b27/LICENSE.txt',
            ],
            'license_file': '',
            'skipped': '',
        },

        'pal': {
            'comment': '',
            'name': 'Platform Abstraction Library (PAL)',
            'url': 'https://github.com/GPUOpen-Drivers/pal',
            'license_url': 'https://raw.githubusercontent.com/GPUOpen-Drivers/pal/3e28ea331cf8e7b0e5733e5bd7f7d16582c603c5/LICENSE.txt',
            'license_file': '',
            'skipped': '',
        },

        'spvgen': {
            'comment': '',
            'name': 'SPVGEN',
            'url': 'https://github.com/GPUOpen-Drivers/spvgen',
            'license_url': [
                'https://raw.githubusercontent.com/GPUOpen-Drivers/spvgen/2f31d1170e8a12a66168b23235638c4bbc43ecdc/LICENSE',
                'https://raw.githubusercontent.com/GPUOpen-Drivers/AMDVLK/808b6d5603653e6efa7a0da46b4f519f0c105b27/LICENSE.txt',
            ],
            'license_file': '',
            'skipped': '',
        },

        'xgl': {
            'comment': '',
            'name': 'Vulkan API Layer (XGL)',
            'url': 'https://github.com/GPUOpen-Drivers/xgl',
            'license_url': 'https://raw.githubusercontent.com/GPUOpen-Drivers/xgl/3252b6223947f9fc67399e0798b1062983925fce/LICENSE.txt',
            'license_file': '',
            'skipped': '',
        },


        'gfauto': {
            'comment': '',
            'name': 'GraphicsFuzz auto (gfauto)',
            'url': 'https://github.com/google/graphicsfuzz/tree/dev/gfauto',
            'license_url': 'https://raw.githubusercontent.com/google/graphicsfuzz/143f46a1298a2a1e012b8b3ab31fc87c2075e82f/LICENSE',
            'license_file': '',
            'skipped': '',
        },


        # Android stuff:

        'bionic': {
            'comment': '',
            'name': 'Android bionic libc',
            'url': 'https://android.googlesource.com/platform/bionic/',
            'license_url': 'https://raw.githubusercontent.com/aosp-mirror/platform_bionic/48da33389b086d1a52524feee049d8219dc4a190/libc/NOTICE',
            'license_file': '',
            'skipped': '',
        },



    }


def get_maven_dependencies_populated():
    return {
        'ant-contrib:ant-contrib': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Just used during build.',
        },
        'com.google.code.gson:gson': {
            'comment': '',
            'name': 'GSON',
            'url': 'https://github.com/google/gson',
            'license_url': 'https://raw.githubusercontent.com/google/gson'
                           '/3f4ac29f9112799a7374a99b18acabd0232ff075/LICENSE',
            'license_file': '',
            'skipped': '',
        },
        'com.graphicsfuzz.thirdparty:alphanum-comparator': {
            'comment': '',
            'name': 'The Alphanum Algorithm',
            'url': 'http://www.davekoelle.com/alphanum.html',
            'license_url': '',
            'license_file': path('third_party', 'alphanum-comparator', 'LICENSE'),
            'skipped': '',
        },
        'com.graphicsfuzz:assembly-binaries': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:ast': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:common': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:fuzzerserver': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:generate-and-run-shaders': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:generator': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz.thirdparty:gif-sequence-writer': {
            'comment': '',
            'name': 'Animated GIF Writer',
            'url': 'http://elliot.kroo.net/software/java/GifSequenceWriter/',
            'license_url': '',
            'license_file': path('third_party', 'gif-sequence-writer', 'LICENSE'),
            'skipped': '',
        },
        'com.graphicsfuzz.thirdparty:jquery-js': {
            'comment': '',
            'name': 'jQuery',
            'url': 'https://jquery.org/',
            'license_url': '',
            'license_file': [
                path('third_party', 'jquery-js', 'LICENSE.txt'),
                path('third_party', 'jquery-js', 'AUTHORS.txt'),
            ],
            'skipped': '',
        },
        'com.graphicsfuzz.thirdparty:python-six': {
            'comment': 'Used by thrift',
            'name': 'six',
            'url': 'https://pypi.org/project/six/',
            'license_url': '',
            'license_file': path('third_party', 'python-six', 'LICENSE'),
            'skipped': '',
        },
        'com.graphicsfuzz:python': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:reducer': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz.thirdparty:semantic-ui': {
            'comment': '',
            'name': 'Semantic UI',
            'url': 'https://github.com/semantic-org/semantic-ui/',
            'license_url': '',
            'license_file': path('third_party', 'semantic-ui', 'LICENSE'),
            'skipped': '',
        },
        'com.graphicsfuzz:server-static-public': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:server-thrift-gen': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:server': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:shadersets-util': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:tester': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz.thirdparty:thrift-js': {
            'comment': '',
            'name': 'Apache Thrift',
            'url': 'https://thrift.apache.org/',
            'license_url': '',
            'license_file': [path('third_party', 'thrift-js', 'NOTICE'), path('third_party', 'thrift-js', 'LICENSE')],
            'skipped': '',
        },
        'com.graphicsfuzz.thirdparty:thrift-py': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Duplicate underlying project of thrift-js',
        },
        'com.graphicsfuzz:thrift': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:tool': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:util': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'commons-codec:commons-codec': {
            'comment': '',
            'name': 'Apache Commons Codec',
            'url': 'https://github.com/apache/commons-codec',
            'license_url': [
                'https://raw.githubusercontent.com/apache/commons-codec/'
                '5ce931b904d1f2961078b99049c293c11e779fec/NOTICE.txt',
                'https://raw.githubusercontent.com/apache/commons-codec'
                '/5ce931b904d1f2961078b99049c293c11e779fec/LICENSE.txt'
            ],
            'license_file': '',
            'skipped': '',
        },
        'commons-io:commons-io': {
            'comment': '',
            'name': 'Apache Commons IO',
            'url': 'https://github.com/apache/commons-io',
            'license_url': [
                'https://raw.githubusercontent.com/apache/commons-io'
                '/58b0f795b31482daa6bb5473a8b2c398e029f5fb/NOTICE.txt',
                'https://raw.githubusercontent.com/apache/commons-io'
                '/58b0f795b31482daa6bb5473a8b2c398e029f5fb/LICENSE.txt'
            ],
            'license_file': '',
            'skipped': '',
        },
        'org.apache.commons:commons-rng-core': {
            'comment': '',
            'name': 'Apache Commons RNG',
            'url': 'https://github.com/apache/commons-rng',
            'license_url': [
                'https://raw.githubusercontent.com/apache/commons-rng'
                '/838f60e09ce458ced96ea05bf09e576c4283136f/NOTICE.txt',
                'https://raw.githubusercontent.com/apache/commons-rng'
                '/838f60e09ce458ced96ea05bf09e576c4283136f/LICENSE.txt'
            ],
            'license_file': '',
            'skipped': '',
        },
        'org.apache.commons:commons-rng-simple': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as commons-rng-core.',
        },
        'org.apache.commons:commons-rng-sampling': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as commons-rng-core.',
        },
        'org.apache.commons:commons-rng-client-api': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as commons-rng-core.',
        },
        'commons-logging:commons-logging': {
            'comment': '',
            'name': 'Apache Commons Logging',
            'url': 'https://github.com/apache/commons-logging',
            'license_url': [
                'https://raw.githubusercontent.com/apache/commons-logging'
                '/0548efba5be8c7dd04f71d81e642488fec6f5472/NOTICE.txt',
                'https://raw.githubusercontent.com/apache/commons-logging'
                '/0548efba5be8c7dd04f71d81e642488fec6f5472/LICENSE.txt'
            ],
            'license_file': '',
            'skipped': '',
        },
        'javax.servlet:javax.servlet-api': {
            'comment': '',
            'name': 'Java Servlet API',
            'url': 'https://javaee.github.io/servlet-spec/',
            'license_url': 'https://raw.githubusercontent.com/javaee/glassfish'
                           '/0f95c749c12c44bf5ee73b413d6a38da256a3989/LICENSE',
            'license_file': '',
            'skipped': '',
        },
        'javax.annotation:javax.annotation-api': {
            'comment': '',
            'name': 'JavaX Annotation API',
            'url': 'https://github.com/javaee/javax.annotation',
            'license_url': 'https://raw.githubusercontent.com/javaee/javax.annotation'
                           '/624529a8156a7b61ed4f5241455b5c3863579531/LICENSE',
            'license_file': '',
            'skipped': '',
        },
        'net.sourceforge.argparse4j:argparse4j': {
            'comment': '',
            'name': 'Argparse4j',
            'url': 'https://github.com/argparse4j/argparse4j',
            'license_url': 'https://raw.githubusercontent.com/argparse4j/argparse4j'
                           '/1aaaa569fd0fdc9f0f50cb3590e5d6270bb26091/LICENSE.txt',
            'license_file': '',
            'skipped': '',
        },
        'org.antlr:antlr4': {
            'comment': '',
            'name': 'ANTLR v4',
            'url': 'https://github.com/antlr/antlr4',
            'license_url': 'https://raw.githubusercontent.com/antlr/antlr4'
                           '/432022fc1ca098f2ab419d6e26a09124a518c345/LICENSE.txt',
            'license_file': '',
            'skipped': '',
        },
        'org.antlr:antlr4-runtime': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as org.antlr:antlr4.',
        },
        'org.apache.commons:commons-lang3': {
            'comment': '',
            'name': 'Apache Commons Lang',
            'url': 'https://github.com/apache/commons-lang',
            'license_url': [
                'https://raw.githubusercontent.com/apache/commons-lang'
                '/69e843890c09861a168c6fe77d63fc72f0c73195/NOTICE.txt',
                'https://raw.githubusercontent.com/apache/commons-lang'
                '/69e843890c09861a168c6fe77d63fc72f0c73195/LICENSE.txt'
            ],
            'license_file': '',
            'skipped': '',
        },
        'org.apache.httpcomponents:httpclient': {
            'comment': '',
            'name': 'Apache HttpComponents Client',
            'url': 'https://github.com/apache/httpcomponents-client',
            'license_url': [
                'https://raw.githubusercontent.com/apache/httpcomponents-client'
                '/d711bd637ec113d9c3452051f25e8923d7f80764/NOTICE.txt',
                'https://raw.githubusercontent.com/apache/httpcomponents-client'
                '/d711bd637ec113d9c3452051f25e8923d7f80764/LICENSE.txt',
            ],
            'license_file': '',
            'skipped': '',
        },
        'org.apache.httpcomponents:httpcore': {
            'comment': '',
            'name': 'Apache HttpComponents Core',
            'url': 'https://github.com/apache/httpcomponents-core',
            'license_url': [
                'https://raw.githubusercontent.com/apache/httpcomponents-core'
                '/45280cc6f87f7c8ed472238ae1cd6279336b4aff/NOTICE.txt',
                'https://raw.githubusercontent.com/apache/httpcomponents-core'
                '/45280cc6f87f7c8ed472238ae1cd6279336b4aff/LICENSE.txt',
            ],
            'license_file': '',
            'skipped': '',
        },
        'org.apache.logging.log4j:log4j-api': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as log4j-core.',
        },
        'org.apache.logging.log4j:log4j-core': {
            'comment': '',
            'name': 'Apache Log4j 2',
            'url': 'https://github.com/apache/logging-log4j2/',
            'license_url': [
                'https://raw.githubusercontent.com/apache/logging-log4j2'
                '/47d2f975e6cbb8a23e3cd8a44007cd2b46fbe24c/NOTICE.txt',
                'https://raw.githubusercontent.com/apache/logging-log4j2'
                '/47d2f975e6cbb8a23e3cd8a44007cd2b46fbe24c/LICENSE.txt',
            ],
            'license_file': '',
            'skipped': '',
        },
        'org.apache.logging.log4j:log4j-slf4j-impl': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as log4j-core.',
        },
        'org.apache.thrift:libthrift': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as thirft-js.',
        },
        'org.bytedeco:javacpp': {
            'comment': '',
            'name': 'JavaCPP',
            'url': 'https://github.com/bytedeco/javacpp',
            'license_url': '',
            'license_file': path('third_party', 'licenses', 'javacpp.txt'),
            'skipped': '',
        },
        'org.bytedeco.javacpp-presets:opencv': {
            'comment': 'This will just cover javacpp-presets; must make another entry for opencv.',
            'name': 'JavaCPP Presets (OpenCV)',
            'url': 'https://github.com/bytedeco/javacpp-presets',
            'license_url': '',
            'license_file': path('third_party', 'licenses', 'javacpp.txt'),
            'skipped': '',
        },
        'org.eclipse.jetty:jetty-http': {
            'comment': '',
            'name': 'Eclipse Jetty',
            'url': 'https://github.com/eclipse/jetty.project',
            'license_url': [
                'https://raw.githubusercontent.com/eclipse/jetty.project'
                '/103b1292ea42d625c38d73b168a879e0e52bcafa/NOTICE.txt',
                'http://www.apache.org/licenses/LICENSE-2.0.txt'
            ],
            'license_file': '',
            'skipped': '',
        },
        'org.eclipse.jetty:jetty-io': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as jetty-http',
        },
        'org.eclipse.jetty:jetty-security': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as jetty-http',
        },
        'org.eclipse.jetty:jetty-server': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as jetty-http',
        },
        'org.eclipse.jetty:jetty-servlet': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as jetty-http',
        },
        'org.eclipse.jetty:jetty-util': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as jetty-http',
        },
        'org.eclipse.jetty:jetty-util-ajax': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'Same project as jetty-http',
        },
        'org.slf4j:slf4j-api': {
            'comment': '',
            'name': 'Simple Logging Facade for Java (SLF4J)',
            'url': 'https://github.com/qos-ch/slf4j',
            'license_url': 'https://raw.githubusercontent.com/qos-ch/slf4j'
                           '/5e93462dd781677fb75aefc6cc7df65822970ca0/LICENSE.txt',
            'license_file': '',
            'skipped': '',
        },
        'github.paulthomson:build-angle': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'see angle',
        },
        'github.paulthomson:build-swiftshader': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'see swiftshader',
        },
        'github.paulthomson:build-glslang': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'see glslang',
        },
        'github.mc-imperial:get-image-glsl': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'github.google:gfbuild-SPIRV-Tools': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'see spirv-tools',
        },
    }


def read_maven_dependencies(
        dependencies_dict: typing.Dict[str, typing.Dict],
        dependencies_file: str):

    with io.open(dependencies_file, 'r', encoding='utf-8', errors='ignore') as fin:
        for line in fin:
            if not line.startswith('   '):
                continue
            line = line.strip()
            line = line.split(':')
            dependency = line[0] + ':' + line[1]
            # Ignore com.graphicsfuzz: internal projects
            # Note that third party internal projects start with com.graphicsfuzz.thirdparty:
            if dependency.startswith('com.graphicsfuzz:'):
                continue
            if dependency not in dependencies_dict:
                dependencies_dict[dependency] = dict()


def write_license_from_file(fout: typing.TextIO, license_file: str) -> None:
    fout.write('\n')
    fout.write('\n')
    with io.open(license_file, 'r', encoding='utf-8', errors='ignore') as fin:
        contents = fin.read()
        fout.write(contents)


def write_license_from_url(fout: typing.TextIO, url: str) -> None:
    fout.write('\n')
    fout.write('\n')

    # noinspection PyUnusedLocal
    response = None  # type: http.client.HTTPResponse
    with urllib.request.urlopen(url) as response:
        contents = response.read()
        fout.write(contents.decode())


def go():
    maven_dependencies = dict()  # type: typing.Dict[str, typing.Dict]
    read_maven_dependencies(maven_dependencies, path('graphicsfuzz', 'target', 'dependencies.txt'))
    dependencies_populated = get_maven_dependencies_populated()
    dependencies_populated.update(get_extras())

    # Find maven dependencies for which we have not provided license information.
    for dep in maven_dependencies:
        if dep not in dependencies_populated:
            print('Missing dependency license information ' + dep)
            sys.exit(1)

    dependencies_populated_list = sorted(dependencies_populated.items(), key=lambda x: x[1]['name'].lower())

    # Write an OPEN_SOURCE_LICENSES.TXT file.
    with io.open(
            'OPEN_SOURCE_LICENSES.TXT',
            'w',
            newline='\r\n',
            encoding='utf-8',
            errors='ignore') as fout:

        fout.write('\n')
        fout.write('Open source licenses for the GraphicsFuzz project.\n')
        fout.write('https://github.com/google/graphicsfuzz\n\n')
        fout.write('Summary of projects:\n\n')

        for (dep, details) in dependencies_populated_list:
            print('Dependency: ' + dep)
            if len(details['skipped']) > 0:
                print('Skipping (' + details['skipped'] + ')')
                continue

            fout.write('  ' + details['name'] + ' (' + dep + ') ')
            project_url = details['url']
            if isinstance(project_url, list):
                for url in project_url:
                    fout.write(url + ' ')
            else:
                fout.write(project_url)

            fout.write('\n')

        fout.write('\n')
        fout.write('All projects and licenses:\n')

        for (dep, details) in dependencies_populated_list:
            print('Dependency: ' + dep)
            if len(details['skipped']) > 0:
                print('Skipping (' + details['skipped'] + ')')
                continue

            fout.write('\n\n')
            fout.write('Name: ' + details['name'] + '\n')
            fout.write('Short name: ' + dep + '\n')

            project_url = details['url']
            if isinstance(project_url, list):
                fout.write('Project URL: ')
                for url in project_url:
                    fout.write(url + ' ')
            else:
                fout.write('Project URL: ' + project_url)

            fout.write('\n')

            license_url = details['license_url']
            license_file = details['license_file']

            # license_url xor license_file should be present.
            assert (len(license_url) == 0) != (len(license_file) == 0)

            if len(license_url) > 0:
                if isinstance(license_url, list):
                    for url in license_url:
                        write_license_from_url(fout, url)
                else:
                    write_license_from_url(fout, license_url)

            if len(license_file) > 0:
                if isinstance(license_file, list):
                    for license_file_inner in license_file:
                        write_license_from_file(fout, license_file_inner)
                else:
                    write_license_from_file(fout, license_file)


if __name__ == '__main__':
    go()

