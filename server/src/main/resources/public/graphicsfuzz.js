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

// general ====================================================================

function toggleDiv(link) {
  var div = document.getElementsByClassName(link.getAttribute("data-hide"));
  var size = div.length;
  for (var i = 0; i < size; i++) {
    var e = div[i];
    if (e.classList.contains("invisible")) {
      e.classList.remove("invisible");
    } else {
      e.classList.add("invisible");
    }
  }
}

function promptForInfo(msg, url, current, urlEmpty, newTab) {
  var value = prompt(msg, current);
  var finUrl;
  if (value == null) {
    return;
  } else if (value === "") {
    finUrl = urlEmpty;
  } else {
    finUrl = url + value;
  }
  if (newTab) {
    window.open(finUrl);
  } else {
    window.location.assign(finUrl);
  }
}

function checkAndSubmit(msg, form) {
  var c = confirm(msg);
  if (c === true) {
    form.submit();
  }
}

function redirect(msg, url) {
  if (msg != null) {
    alert(msg);
  }
  window.location.assign(url);
}

function promptAndSubmit(msg, input, form) {
  var value = prompt(msg, input.value);
  if (value == null) {
    return;
  } else if (value === "") {
    return;
  } else {
    input.value = value
    form.submit()
  }
}

// Starting experiments =======================================================

function setClass(elem, c, on) {
  if (elem.classList.contains(c) !== on) {
    elem.classList.toggle(c);
  }
}

function updateReductionElements(elem) {
  var reduction_kind_val =
      document.getElementsByName("reduction-kind")[0].selectedOptions[0].value;
  var metric_val =
      document.getElementsByName("metric")[0].selectedOptions[0].value;

  var error_string_invisible = (reduction_kind_val !== "NO_IMAGE");
  var metric_invisible =
      (reduction_kind_val !== "BELOW_THRESHOLD" && reduction_kind_val !== "ABOVE_THRESHOLD");
  var threshold_invisible = (metric_invisible || metric_val === "FUZZY_DIFF");

  setClass(error_string_tr, "invisible", error_string_invisible);
  setClass(metric_tr, "invisible", metric_invisible);
  setClass(metric_hints, "invisible", metric_invisible);
  setClass(threshold_tr, "invisible", threshold_invisible);

}

var lastChecked = -1;

//Apply checkbox - called when clicking on a link or checkbox from checkCol,
//allows selection of checkboxes by checkbox, link, and shift-click functionality
function applyCheckbox(event) {
  //If shift clicked, undo highlighting on page
  if (event.shiftKey) {
    document.getSelection().removeAllRanges();
  }

  //Get element clicked/changed
  var elem = event.target;
  var num = parseInt(elem.getAttribute("data-num"));
  var checkboxes;

  //Setup based on element type
  if (elem.nodeName === "INPUT") {
    if (!event.shiftKey) {
      lastChecked = num;
      return;
    }

    elem.checked = !elem.checked;
    checkboxes = Array.prototype.slice.call(document.getElementsByName(elem.name));
  } else {
    checkboxes = Array.prototype.slice.call(document.getElementsByName(elem.getAttribute("data-check")));
  }

  //Filter checkboxes to fit included range
  checkboxes = checkboxes.filter(function(checkbox) {
    var n = parseInt(checkbox.getAttribute("data-num"));
    if (lastChecked === -1 || !event.shiftKey) {
      return num === n;
    } else if (lastChecked < num) {
      return ((lastChecked <= n) && (n <= num));
    } else {
      return  ((num <= n) && (n <= lastChecked));
    }
  });

  //If holding shift, flip majority of checkboxes (e.g. if 5 selected and 3 checked, uncheck all)
  //Otherwise flip selected checkbox
  if (event.shiftKey) {
    var nChecked = 0;

    for (var i = 0; i < checkboxes.length; i++) {
      if (checkboxes[i].checked) {
        nChecked++;
      }
    }

    var check = nChecked <= checkboxes.length / 2;
    for (var i = 0; i < checkboxes.length; i++) {
      checkboxes[i].checked = check;
    }

  } else {
    checkboxes[0].checked = !checkboxes[0].checked;
  }

  //Update lastChecked clicked
  lastChecked = num;
}

function applyAllCheckboxes(name, checked) {
  if (name.length > 1) {
    for (var i = 0; i < name.length; i++) {
      var checkbox = name[i];
      checkbox.checked = checked;
    }
  } else {
    name.checked = checked;
  }
}

// Main =======================================================================

$(document).ready(function() {

  // semantic-ui inits
  $('.ui.checkbox').checkbox();

  // scroll down homepage server log
  var ServerLog = document.getElementById("ServerLog");
  if (ServerLog != null) {
    ServerLog.onload = function() {
      ServerLog.scrollTop = ServerLog.scrollHeight;
    };
  }

});
