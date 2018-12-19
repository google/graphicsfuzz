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

# Documentation: https://docs.python.org/2/library/webbrowser.html
import webbrowser

### Choose one of the following browsers types:

# BROWSER = 'windows-default'
# BROWSER = 'chrome'
# BROWSER = 'firefox'
# BROWSER = 'safari'

### Or choose chrome in Windows, Linux and macOS
# BROWSER = 'C:\Program Files (x86)\Google\Chrome\Application\chrome.exe %s'
# BROWSER = '/usr/bin/google-chrome %s'
BROWSER = 'open -a /Applications/Google\ Chrome.app %s'

### Or, set your own browser:
# BROWSER = 'PATH %s'
# BROWSER = 'PATH %s'
# BROWSER = 'open -a /PATH/ %s'

### Set the WebGL client url:
WEBGL_CLIENT_URL = "http://localhost:8080/static/runner.html"

browser = webbrowser.get(BROWSER)
browser.open_new(WEBGL_CLIENT_URL)
