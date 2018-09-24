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

//Key for paths, stored in sessionStorage
var PATHNAME = "gputesting_path";
var DEFAULTPATH = "http://" + getDomain(window.location) + "/webui";

//Get and update the path through the website, construct the breadcrumb links
function constructBreadcrumb() {
  if (typeof(window.Storage) === "undefined") {
    console.log("No web storage support, breadcrumbing disabled!");
    return;
  }
  var location = window.location.toString();
  if (location.charAt(location.length - 1) === "/") {
    location = location.substring(0, location.length - 1);
  }
  //If arriving from different webpage, reset path
  if (getDomain(location) !== getDomain(document.referrer) || !getValue(PATHNAME)) {
    storeValue(PATHNAME, DEFAULTPATH + ";" + location);
  } else {
    storeValue(PATHNAME, getValue(PATHNAME) + ";" + location);
  }

  //Read path, cut off path beyond first occurrence of current location
  //e.g. "webui - shaderset - result - shaderset" becomes "webui - shaderset"
  var path = getValue(PATHNAME);
  var path = path.split(";");
  var cutoff = path.length;
  for (var i = 0; i < path.length; i++) {
    //Remove trailing forward-slash
    if (path[i].charAt(path[i].length - 1) === "/") {
      path[i] = path[i].substring(0, path[i].length - 1);
    }
    if (location === path[i]) {
      cutoff = i + 1;
      break;
    }
  }

  //Construct links, update path to remove cutoff parts
  var new_path = "";
  for (var i = 0; i < cutoff; i++) {
    if (i > 0) {
      new_path += ";";
    }
    new_path += path[i];
    var breadcrumb = document.getElementById("breadcrumb");
    if (i > 0) {
      var dash = document.createElement('span');
      dash.innerHTML = " - ";
      breadcrumb.appendChild(dash);
    }
    var link = document.createElement('a');
    link.innerHTML = stripDomain(path[i]);
    link.href = path[i];
    link.classList.add("visible-link");
    breadcrumb.appendChild(link);
  }
  storeValue(PATHNAME, new_path);
}

//Get the domain from a URL
function getDomain(ref) {
  return ref.toString().split("/")[2];
}

//Strip the domain from a URL
function stripDomain(ref) {
  var refParts = ref.toString().split("/");
  ref = "";
  var start = 1;
  if (refParts[0] === "http:") {
    start = 3;
  }
  for (var i = start; i < refParts.length; i++) {
    if (i > start) {
      ref += "/";
    }
    ref += refParts[i];
  }
  return ref;
}

//Write a key/value pair to sessionStorage
function storeValue(key, value) {
  window.sessionStorage.setItem(key, value);
}

//Read a value from sessionStorage with the given key
function getValue(key) {
  return window.sessionStorage.getItem(key);
}

//Run constructBreadcrumb when page is loaded
$(document).ready(function() {
  constructBreadcrumb();
});