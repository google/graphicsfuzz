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

try:
    from setuptools import setup, Extension
except Exception:
    from distutils.core import setup, Extension


setup(
    name="gfauto",
    version=0.9,
    description="GraphicsFuzz auto.",
    keywords="GraphicsFuzz fuzzing GLSL SPIRV",
    author="The GraphicsFuzz Project Authors",
    author_email="android-graphics-tools-team@google.com",
    url="https://github.com/google/graphicsfuzz",
    license="Apache License 2.0",
    packages=["gfauto"],
    python_requires=">=3.6",
    install_requires=["protobuf"],
    package_data={"gfauto": ["*.proto", "*.pyi"]},
    classifiers=[
        "Environment :: Console",
        "Intended Audience :: Developers",
        "License :: OSI Approved::Apache Software License",
        "Operating System :: OS Independent",
        "Programming Language :: Python",
        "Programming Language :: Python :: 3.6",
        "Programming Language :: Python :: 3.7",
        "Programming Language :: Python :: 3 :: Only",
    ],
    entry_points={"console_scripts": [
        "gfauto_fuzz = gfauto.fuzz:main",
        "gfauto_interestingness_test = gfauto.gfauto_interestingness_test:main",
        "gfauto_write_device_file = gfauto.devices_util:write_device_file",
        "add_amber_tests_to_cts = gfauto.add_amber_tests_to_cts:main",
    ]},
)
