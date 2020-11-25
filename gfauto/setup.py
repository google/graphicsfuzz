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
    install_requires=[
        "protobuf",
        "requests",
        "python-dateutil",
        'dataclasses;python_version<"3.7"',
    ],
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
        "gfauto_test_update_binaries = gfauto.test_update_binaries:main",
        "gfauto_test_create_readme = gfauto.test_create_readme:main",
        "gfauto_download_cts_gf_tests = gfauto.download_cts_gf_tests:main",
        "gfauto_run_cts_gf_tests = gfauto.run_cts_gf_tests:main",
        "gfauto_run_bin = gfauto.run_bin:main",
        "gfauto_cov_merge = gfauto.cov_merge:main",
        "gfauto_cov_new = gfauto.cov_new:main",
        "gfauto_cov_to_source = gfauto.cov_to_source:main",
        "gfauto_cov_from_gcov = gfauto.cov_from_gcov:main",
        "gfauto_settings_update = gfauto.settings_update:main",
        "gfauto_reduce_source_dir = gfauto.reduce_source_dir:main",
        "gfauto_bucket_via_transformations = gfauto.bucket_via_transformations:main",
        "gfauto_run_amber_android = gfauto.run_amber_android:main",
        "gfauto_generate_amber = gfauto.generate_amber:main",
    ]},
)
