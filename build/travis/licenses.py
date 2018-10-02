#!/usr/bin/env python

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
from typing import Dict

path = os.path.join


def get_extras():
    return {

        'angle': {
            'license': 'BSD License',
            'url': 'https://github.com/google/angle',
            'license_url': 'https://raw.githubusercontent.com/google/angle/a0ccea1e8d1f2b7620db08c7cbbac7d01cfc38d6/LICENSE',
            'skipped': '',
        },

        'spirv-headers': {
            'name': 'SPIR-V Headers',
            'url': 'https://github.com/KhronosGroup/SPIRV-Headers',
            'license_url': 'https://raw.githubusercontent.com/KhronosGroup/SPIRV-Headers/d5b2e1255f706ce1f88812217e9a554f299848af/LICENSE',
            'skipped': '',
        },

        'libpng': {
            'comment': 'Used by angle',
            'name': 'libpng',
            'url': 'http://libpng.org/pub/png/libpng.html',
            'license_url': 'http://libpng.org/pub/png/src/libpng-LICENSE.txt',
            'skipped': '',
        },

        'minigbm': {
            'comment': 'Used by angle',
            'name': 'minigbm',
            'url': 'https://chromium.googlesource.com/chromiumos/platform/minigbm/+/master',
            'license_url': '',
            'license_file': 'build/licenses/minigbm.txt',
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
            'comment': 'Used by glslang, angle',
            'name': 'SPIR-V Tools',
            'url': 'https://github.com/KhronosGroup/SPIRV-Tools',
            'license_url': 'https://raw.githubusercontent.com/KhronosGroup/SPIRV-Tools/a77bb2e54b35f1f6f8bb20f4dcab4e33999edb60/LICENSE',
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

        'vulkan-validationLayers': {
            'comment': 'Used by angle',
            'name': 'Vulkan Validation Layers - Vulkan Ecosystem Components',
            'url': 'https://github.com/KhronosGroup/Vulkan-ValidationLayers',
            'license_url': [
                'http://www.apache.org/licenses/LICENSE-2.0.txt',
                'https://raw.githubusercontent.com/KhronosGroup/Vulkan-ValidationLayers/44b7b80e5e52f2962077346caa4ed175798d16df/COPYRIGHT.txt'
            ],
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

        'llvm': {
            'comment': '',
            'name': 'LLVM',
            'url': 'https://llvm.org/',
            'license_url': 'https://raw.githubusercontent.com/google/swiftshader/720aec1cd6ebf4c4d74326c5faaddd57ee351609/third_party/LLVM/LICENSE.TXT',
            'license_file': '',
            'skipped': '',
        },

        'stlport-cpp11-extension': {
            'comment': '',
            'name': 'stlport-cpp11-extension',
            'url': 'https://github.com/google/swiftshader/tree/720aec1cd6ebf4c4d74326c5faaddd57ee351609/third_party/stlport-cpp11-extension',
            'license_url': 'http://www.apache.org/licenses/LICENSE-2.0.txt',
            'license_file': '',
            'skipped': '',
        },

        'subzero': {
            'comment': '',
            'name': 'Subzero from Chromium',
            'url': 'https://github.com/google/swiftshader/tree/720aec1cd6ebf4c4d74326c5faaddd57ee351609/third_party/subzero',
            'license_url': 'https://raw.githubusercontent.com/google/swiftshader/720aec1cd6ebf4c4d74326c5faaddd57ee351609/third_party/subzero/LICENSE.TXT',
            'license_file': '',
            'skipped': '',
        },


        'opengl-headers': {
            'comment': '',
            'name': 'OpenGL headers',
            'url': 'https://github.com/KhronosGroup/OpenGL-Registry',
            'license_url': '',
            'license_file': 'build/licenses/opengl-headers.txt',
            'skipped': '',
        },

        'egl-headers': {
            'comment': '',
            'name': 'EGL headers',
            'url': 'https://github.com/KhronosGroup/EGL-Registry',
            'license_url': '',
            'license_file': 'build/licenses/opengl-headers.txt',
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
            'comment': '',
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
            'license_file': 'build/licenses/max-sills-shaders.txt',
            'skipped': '',
        },

        'valters-mednis-shaders': {
            'comment': '',
            'name': 'Valters Mednis\' GLSL shaders',
            'url':
                [
                    'https://www.shadertoy.com/view/ltVGWG',
                ],
            'license_url': '',
            'license_file': 'build/licenses/valters-mednis-shaders.txt',
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
        'com.graphicsfuzz:alphanum-comparator': {
            'comment': '',
            'name': 'The Alphanum Algorithm',
            'url': 'http://www.davekoelle.com/alphanum.html',
            'license_url': '',
            'license_file': 'third_party/alphanum-comparator/LICENSE',
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
        'com.graphicsfuzz:generator': {
            'comment': '',
            'name': '',
            'url': '',
            'license_url': '',
            'license_file': '',
            'skipped': 'internal project',
        },
        'com.graphicsfuzz:gif-sequence-writer': {
            'comment': '',
            'name': 'Animated GIF Writer',
            'url': 'http://elliot.kroo.net/software/java/GifSequenceWriter/',
            'license_url': '',
            'license_file': 'third_party/gif-sequence-writer/LICENSE',
            'skipped': '',
        },
        'com.graphicsfuzz:jquery-js': {
            'comment': '',
            'name': 'jQuery',
            'url': 'https://jquery.org/',
            'license_url': '',
            'license_file': 'third_party/jquery-js/LICENSE',
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
        'com.graphicsfuzz:semantic-ui': {
            'comment': '',
            'name': 'Semantic UI',
            'url': 'https://github.com/semantic-org/semantic-ui/',
            'license_url': '',
            'license_file': 'third_party/semantic-ui/LICENSE',
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
        'com.graphicsfuzz:thrift-js': {
            'comment': '',
            'name': 'Apache Thrift',
            'url': 'https://thrift.apache.org/',
            'license_url': '',
            'license_file': ['third_party/thrift-js/NOTICE', 'third_party/thrift-js/LICENSE'],
            'skipped': '',
        },
        'com.graphicsfuzz:thrift-py': {
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
                '5ce931b904d1f2961078b99049c293c11e779fec/NOTICE.txt'
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
            'license_file': 'build/licenses/javacpp.txt',
            'skipped': '',
        },
        'org.bytedeco.javacpp-presets:opencv': {
            'comment': 'This will just cover javacpp-presets; must make another entry for opencv.',
            'name': 'JavaCPP Presets (OpenCV)',
            'url': 'https://github.com/bytedeco/javacpp-presets',
            'license_url': '',
            'license_file': 'build/licenses/javacpp.txt',
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
        'org.slf4j:slf4j-api': {
            'comment': '',
            'name': 'Simple Logging Facade for Java (SLF4J)',
            'url': 'https://github.com/qos-ch/slf4j',
            'license_url': 'https://raw.githubusercontent.com/qos-ch/slf4j'
                           '/5e93462dd781677fb75aefc6cc7df65822970ca0/LICENSE.txt',
            'license_file': '',
            'skipped': '',
        },
    }


def read_maven_dependencies(dependencies_dict: Dict[str, Dict], dependencies_file: str):

    with io.open(dependencies_file, 'r') as fin:
        for line in fin:
            if not line.startswith("   "):
                continue
            line = line.strip()
            line = line.split(":")
            dependency = line[0] + ":" + line[1]
            if dependency not in dependencies_dict:
                dependencies_dict[dependency] = dict()


def go():
    maven_dependencies: Dict[str, Dict] = dict()
    read_maven_dependencies(maven_dependencies, path("assembly", "target", "dependencies.txt"))
    maven_dependencies_populated = get_maven_dependencies_populated()
    extras = get_extras()

    # Check for new maven dependencies for which we have not provided license information.
    for dep in maven_dependencies:
        if dep not in maven_dependencies_populated:
            print("Missing dependency " + dep)
            sys.exit(1)





if __name__ == "__main__":
    go()

