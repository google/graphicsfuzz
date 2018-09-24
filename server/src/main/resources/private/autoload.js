/*
 * Copyright 2018 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//When going back in browser history to previous page through UI, window.name is
//set to "autoload" so that the page reloads upon arrival (as opposed to
//loading a cached version). An example is deletion of a result:
//If you start from a results page, go through to a result, and delete it,
//the browser will be redirected back to the results page. Without autoload the
//result will not appear to be deleted without refreshing the page. Autoload
//just refreshes the page for the user.

//Reloads page if window.name === "autoload"
function autoload() {
  var windowName = window.name;
  window.name = "";
  if (windowName === "autoload") {
    window.location.reload();
  }
}