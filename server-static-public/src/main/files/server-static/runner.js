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

//Common functions/code for runner and runner_multi pages
// On 10/04/2018, Hugues inlines things here that may break runner_multi
var transport = null;
var protocol = null;
var platformInfo = null;
var clients = [];
var workers = [];
var completedJobs = [];
var gls = [];

var msWaitDefault = 10;
var msWaitMax = 5000;
var msWait = msWaitDefault;

var reverseWorkerNameError = {};

var webgl_context;

for (var key in graphicsfuzzserver.WorkerNameError) {
  reverseWorkerNameError[graphicsfuzzserver.WorkerNameError[key]] = key;
}

//Get time since [time] in microseconds (time should be obtained with window.performance.now() to match precision)
function timeSince(time) {
  return Math.floor((window.performance.now() - time) * 1000);
}

function resetWait(i) {
  msWait = msWaitDefault;
}

function backoff(i) {
  msWait *= 2;
  if(msWait > msWaitMax) msWait = msWaitMax;
}

function doRefresh() {
  document.location.reload();
}

// Get argument from URL
function getArg(arg) {
  var query = window.location.search.substring(1);
  var vars = query.split('&');
  for (var i = 0; i < vars.length; i++) {
    var pair = vars[i].split('=');
    if (pair[0] == arg) {
      return pair[1];
    }
  }
  return undefined;
}

//Initialise gl context for client i
function initWebgl(i) {
  var canvas = document.getElementById("opengl-canvas" + i);
  try {
    if (gls.length <= i) {
      gls.push(canvas.getContext(webgl_context));
    }
  }
  catch(e) {
  }
  if (!gls[i]) {
    throw "Failed to initialize WebGL.";
  }
  checkGLOK(gls[i]);

  if (platformInfo == null) {
    platformInfo = getPlatformInfo(gls[i]);
  }
}

//Setup transport between client and server
function initTransport() {
  transport = new Thrift.Transport("/requestJSON");
  protocol = new Thrift.TJSONProtocol(transport);

  if (typeof(Storage) === "undefined") {
    alert("Sorry! No Web Storage support..");
    throw "No web storage support!";
  }
}

//Setup one client (client number i)
function initClient(i) {
  //Only allows next client to be initialised
  //(i.e. if i clients exist, then client i+1 must be initialised next)
  if (i != clients.length) {
    throw "Attempted to initialise client " + i + " when only " + clients.length + " exist.";
  }
  clients.push(new graphicsfuzzserver.FuzzerServiceClient(protocol));
  workers.push("");
  completedJobs.push(0);

  //Check URL for workers
  workers[i] = getArg("worker" + i);
  if (i == 0 && workers[i] == undefined) {
    workers[i] = getArg("worker");
  }
  if (workers[i] == undefined) {
    workers[i] = localStorage.getItem("worker" + i);
  }
  myLog("Trying worker '" + workers[i] + "'.", i);
  var getWorkerNameResult = clients[i].getWorkerName(platformInfo, workers[i]);
  if (!getWorkerNameResult.workerName) {
    throw "Worker name rejected: " + reverseWorkerNameError[getWorkerNameResult.error];
  }
  workers[i] = getWorkerNameResult.workerName;

  localStorage.setItem("worker" + i, workers[i]);
  myLog("Worker from server '" + workers[i] + "'.", i);

  document.getElementById("current_worker" + i).value = "Current worker: " + workers[i];
}

//Get job and execute shader if necessary
function getAndHandleJob(i) {

  //Get job
  var job = clients[i].getJob(workers[i]);
  myLog("Got a job.", i);

  //No job
  if (job.noJob != null) {
    myLog("No job for me.", i);
    backoff(i);
    return;
  }

  resetWait(i);

  //Skip job
  if(job.skipJob != null) {
    myLog("Got a skip job.", i);
    finishJob(i, job);
    return;
  }

  //Unknown job type
  if (job.imageJob == null) {
    throw { error: "UNEXPECTED_ERROR", log: "Unknown job type!" };
  }

  //Image job
  myLog("Job type is: image job.", i);

  var imageJob = job.imageJob;
  imageJob.result = new graphicsfuzzserver.ImageJobResult();
  imageJob.result.status = graphicsfuzzserver.JobStatus.UNKNOWN;
  imageJob.result.log = "WebGL worker default log: Nothing to declare\n";

  myLog("Rendering shader '" + imageJob.name + "'.", i);
  document.getElementById("rendered_shader" + i).value = "Rendered shader: " + imageJob.name;
  var shaderPng;
  var uniformsInfo = imageJob.uniformsInfo;
  var vertexShaderSource = imageJob.vertexSource == null ? getStandardVertexShaderSource() : imageJob.vertexSource;
  var fragmentShaderSource = imageJob.fragmentSource;

  var vertices = imageJob.points;
  if (vertices == null) {
    vertices = [
      -1.0, -1.0, 0.0,
       1.0, -1.0, 0.0,
       1.0,  1.0, 0.0,
      -1.0, -1.0, 0.0,
      -1.0,  1.0, 0.0,
       1.0,  1.0, 0.0
    ];
  }
  var indices = [];
  for (var x = 0; x < vertices.length / 3; x++) {
    indices.push(x);
  }

  var verticesBuffer = gls[i].createBuffer();
  gls[i].bindBuffer(gls[i].ARRAY_BUFFER, verticesBuffer);
  gls[i].bufferData(gls[i].ARRAY_BUFFER, new Float32Array(vertices), gls[i].STATIC_DRAW);

  var indicesBuffer = gls[i].createBuffer();
  gls[i].bindBuffer(gls[i].ELEMENT_ARRAY_BUFFER, indicesBuffer);
  gls[i].bufferData(gls[i].ELEMENT_ARRAY_BUFFER, new Uint16Array(indices), gls[i].STATIC_DRAW);
  checkGLOK(gls[i]);


  var res = renderPNG(imageJob, vertexShaderSource, fragmentShaderSource, gls[i], "opengl-canvas" + i, verticesBuffer, indicesBuffer, indices.length, JSON.parse(uniformsInfo));
  imageJob.result.timingInfo = res.timingInfo;

  //Rendering failed
  if(res.result === null) {
    var status;
    var statusString;
    if (res.error === "COMPILE_ERROR") {
       status = graphicsfuzzserver.JobStatus.COMPILE_ERROR;
       statusString = "COMPILE_ERROR";
    } else if (res.error === "LINK_ERROR") {
       status = graphicsfuzzserver.JobStatus.LINK_ERROR;
       statusString = "LINK_ERROR";
    } else if (res.error === "NONDET") {
      status = graphicsfuzzserver.JobStatus.NONDET;
      statusString = "NONDET";
    } else {
      status = graphicsfuzzserver.JobStatus.UNEXPECTED_ERROR;
      statusString = "UNEXPECTED_ERROR";
    }
    myLog("Image job status is '" + statusString + "'.", i);
    if (statusString === "UNEXPECTED_ERROR") {
      myLog(res.log, i);
    }
    imageJob.result.status = status;
    imageJob.result.log = res.error + "\n" + res.log;
    finishJob(i, job);
    return;
  }

  //Rendering successful
  shaderPng = res.result;
  myLog("Image job status is 'SUCCESS'.", i);
  imageJob.result.status = graphicsfuzzserver.JobStatus.SUCCESS;
  imageJob.result.PNG = atob(shaderPng.split(',')[1]);

  //Return result
  finishJob(i, job);
}

function checkWebGlContext(i, job) {
  var canvas = document.getElementById("opengl-canvas" + i);
  var tempGl;
  try {
    tempGl = canvas.getContext(webgl_context);
    if (!tempGl) {
      throw "Failed to initialize WebGL.";
    }
    var error = tempGl.getError();
    if(error == tempGl.CONTEXT_LOST_WEBGL) {
      throw "Lost WebGL context";
    } else if (error != tempGl.NO_ERROR) {
      throw "Error getting WebGL context";
    }
  } catch(e) {
    if (job.imageJob != null) {
      job.imageJob.result.passSanityCheck = false;
      job.imageJob.result.log = "SANITY: lost WebGL context";
    }
    return false;
  }
}

//Checks whether WebGL context is OK, and handles jobDone and logging
function finishJob(i, job) {

  var contextOK = checkWebGlContext(i, job);

  // Keeps track of and prints the number of jobs executed by this worker
  completedJobs[i] += 1;
  console.log("Job Done " + completedJobs[i] + " ");

  //Return result
  clients[i].jobDone(workers[i], job);

  if (!contextOK) {
    location.reload();
  }

}

//Log text in str to console i
function myLog(str, i) {
  document.getElementById("console" + i).value += str + "\n";
}

//Start n clients, with error handling
function startSafe(n) {
  var currI = 0;
  try {
    for (var i = 0; i < n; i++)
    {
      currI = i;
      document.getElementById("console" + i).value = "";
      start(i);
    }
    setTimeout(function() {startSafe(n);}, msWait);
  } catch(e) {
    str = "Exception occurred: " + e + "\n";
    for(x in e) {
      str += x + ":" + e[x] + "\n";
    }
    var workerRejected = (typeof e === "string" && e.startsWith("Worker name rejected"));
    if(workerRejected) {
      str = "WORKER NAME WAS REJECTED\n" + e;
    }
    myLog(str, currI);
    if(workerRejected) {
      setTimeout(doRefresh, 10000);
    } else {
      setTimeout(doRefresh, 2000);
    }
  }
}

//Start client i
function start(i) {
  myLog("Start", i);

  initWebgl(i);

  if (transport == null) {
    initTransport();
  }

  if (clients.length <= i) {
    initClient(i);
  }
  getAndHandleJob(i);
}

function trySetUniform(gl, shaderProgram, name, uniformFunc, args) {
  var loc = gl.getUniformLocation(shaderProgram, name);
  if (loc == null) {
    return;
  }

  if (uniformFunc === "glUniform1f") {
    gl.uniform1f(loc, args[0]);
  } else if (uniformFunc === "glUniform2f") {
    gl.uniform2f(loc, args[0], args[1]);
  } else if (uniformFunc === "glUniform3f") {
    gl.uniform3f(loc, args[0], args[1], args[2]);
  } else if (uniformFunc === "glUniform4f") {
    gl.uniform4f(loc, args[0], args[1], args[2], args[3]);
  } else if (uniformFunc === "glUniform1fv") {
    gl.uniform1fv(loc, args);
  } else if (uniformFunc === "glUniform1i") {
    gl.uniform1i(loc, args[0]);
  } else if (uniformFunc === "glUniform2i") {
    gl.uniform2i(loc, args[0], args[1]);
  } else if (uniformFunc === "glUniform3i") {
    gl.uniform3i(loc, args[0], args[1], args[2]);
  } else if (uniformFunc === "glUniform4i") {
    gl.uniform4i(loc, args[0], args[1], args[2], args[3]);
  } else if (uniformFunc === "glUniform1iv") {
    gl.uniform1iv(loc, args);
  } else {
    console.log("Do not know how to set uniform via function " + uniformFunc + " and args " + args)
  }
}

function renderSingleFrame(gl, shaderProgram, uniformsJSON, verticesBuffer, indicesBuffer, numIndices) {
  gl.useProgram(shaderProgram);

  var vertexPositionAttribute = gl.getAttribLocation(shaderProgram, "a_position");
  gl.enableVertexAttribArray(vertexPositionAttribute);

  for (uniformName in uniformsJSON) {
    trySetUniform(gl, shaderProgram, uniformName, uniformsJSON[uniformName].func, uniformsJSON[uniformName].args)
  }

  gl.bindBuffer(gl.ARRAY_BUFFER, verticesBuffer);
  gl.vertexAttribPointer(vertexPositionAttribute, 3, gl.FLOAT, false, 0, 0);
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indicesBuffer);

  gl.clearColor(0.0, 0.0, 0.0, 0.0);

  gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

  gl.drawElements(gl.TRIANGLES, numIndices, gl.UNSIGNED_SHORT, 0);

  checkGLOK(gl);
}

function renderPNG(imageJob, vertexShaderSource, fragmentShaderSource, gl, canvasId, verticesBuffer, indicesBuffer, numIndices, uniformsJSON) {
  var canvas = document.getElementById(canvasId);
  var timingInfo = new graphicsfuzzserver.TimingInfo();
  timingInfo.otherRendersTime = 0;
  checkGLOK(gl);

  // vertex shader
  var vertexShader = gl.createShader(gl.VERTEX_SHADER);
  gl.shaderSource(vertexShader, vertexShaderSource);
  gl.compileShader(vertexShader);
  if (!gl.getShaderParameter(vertexShader, gl.COMPILE_STATUS)) {
    return { result: null, error : "COMPILE_ERROR", log : "Failed to compile vertex shader.\n" + gl.getShaderInfoLog(vertexShader) };
  }

  // fragment shader
  time = window.performance.now();
  var fragmentShader = gl.createShader(gl.FRAGMENT_SHADER);
  gl.shaderSource(fragmentShader, fragmentShaderSource);
  gl.compileShader(fragmentShader);
  timingInfo.compilationTime = timeSince(time);
  if (!gl.getShaderParameter(fragmentShader, gl.COMPILE_STATUS)) {
    return { result: null, error : "COMPILE_ERROR", log : "Failed to compile fragment shader.\n" + gl.getShaderInfoLog(fragmentShader) };
  }

  // shader program
  time = window.performance.now();
  var shaderProgram = gl.createProgram();
  gl.attachShader(shaderProgram, vertexShader);
  gl.attachShader(shaderProgram, fragmentShader);
  gl.linkProgram(shaderProgram);
  timingInfo.linkingTime = timeSince(time);
  if (!gl.getProgramParameter(shaderProgram, gl.LINK_STATUS)) {
    return { result: null, error : "LINK_ERROR", log : "Failed to link shader program.\n" + gl.getProgramInfoLog(shaderProgram) };
  }
  time = window.performance.now();
  renderSingleFrame(gl, shaderProgram, uniformsJSON, verticesBuffer, indicesBuffer, numIndices);
  timingInfo.firstRenderTime = timeSince(time);
  time = window.performance.now();
  var res = canvas.toDataURL("image/png");
  timingInfo.captureTime = timeSince(time);
  checkGLOK(gl);

  //Check for non-determinism - render 3 extra times
  time = window.performance.now();
  for (var i = 0; i < 3; i++) {
    renderSingleFrame(gl, shaderProgram, uniformsJSON, verticesBuffer, indicesBuffer, numIndices);
    var newRes = canvas.toDataURL("image/png");
    if (newRes !== res) {
      imageJob.result.PNG = atob(newRes.split(',')[1]);
      imageJob.result.PNG2 = atob(res.split(',')[1]);
      return {result: null, error: "NONDET", log: "Shader was non-deterministic."}
    }
  }
  timingInfo.otherRendersTime = timeSince(time)

  return { result: res, error : null, log : null, timingInfo : timingInfo};
}

function checkGLOK(gl) {
  var error = gl.getError();
  if(error == gl.CONTEXT_LOST_WEBGL) {
    throw { error : "ERROR", log: "CONTEXT_LOST_WEBGL" };
  } else if (error != gl.NO_ERROR) {
    throw { error : "ERROR", log: "GL error" };
  }
}

function getPlatformInfo(gl) {
  var platformInfo = JSON.stringify(
            {
              clientplatform: "webgl",
              appCodeName:                 window.navigator.appCodeName,
              appName:                     window.navigator.appName,
              appVersion:                  window.navigator.appVersion,
              oscpu:                       window.navigator.oscpu,
              platform:                    window.navigator.platform,
              product:                     window.navigator.product,
              userAgent:                   window.navigator.userAgent,
              vendor:                      window.navigator.vendor,
              vendorSub:                   window.navigator.vendorSub,
              GL_VERSION:                  gl.getParameter(gl.VERSION),
              GL_SHADING_LANGUAGE_VERSION: gl.getParameter(gl.SHADING_LANGUAGE_VERSION),
              GL_VENDOR:                   gl.getParameter(gl.VENDOR),
              GL_RENDERER:                 gl.getParameter(gl.RENDERER),
              UNMASKED_VENDOR_WEBGL:       getUnmaskedInfo(gl).vendor,
              UNMASKED_RENDERER_WEBGL:     getUnmaskedInfo(gl).renderer,
              Antialiasing:                gl.getContextAttributes().antialias,
            });
  return platformInfo;
}

function getUnmaskedInfo(gl) {
  var unMaskedInfo = {
    vendor: '',
    renderer: ''
  };

  var dbgRenderInfo = gl.getExtension("WEBGL_debug_renderer_info");
  if (dbgRenderInfo != null) {
    unMaskedInfo.vendor   = gl.getParameter(dbgRenderInfo.UNMASKED_VENDOR_WEBGL);
    unMaskedInfo.renderer = gl.getParameter(dbgRenderInfo.UNMASKED_RENDERER_WEBGL);
  }

  return unMaskedInfo;
}

function getStandardVertexShaderSource() {
  document.getElementById("version").value = "WebGL Context: " + webgl_context;
  if (webgl_context === "webgl2") {
    return "#version 300 es\n"
         + "in vec3 a_position;\n"
         + "\n"
         + "void main(void) {\n"
         + "    gl_Position = vec4(a_position, 1.0);\n"
         + "}\n";
  } else {
    return "#version 100\n"
         + "attribute vec3 a_position;\n"
         + "\n"
         + "void main(void) {\n"
         + "    gl_Position = vec4(a_position, 1.0);\n"
         + "}\n";
  }
}

// Starting function for single runner
function onloadSingle() {
  startSafe(1);
}

// Starting function for multi runner

//Load numClients clients starting from client i (recursive)
// Hugues: not sure why Daniel made that function recursive. A loop might be clearer.
function loadClient(i, numClients) {

    var containingElem = document.createElement("tr");
    containingElem.setAttribute("id", "containing-element");
    document.getElementById("table").appendChild(containingElem);

    // Hugues: it is not trivial to get rid of jQuery for the call on "load" here.
    // So I leave it for the multi runner. Still, it's non-sense to load() from the server
    // a static string of template. It should be here as a string. And I sounds dubious
    // to use a template, juste create the elements here.

    $("#containing-element").load('runner_multi_template.html', function (data) {
    //Update html
    document.getElementById("containing-element").removeAttribute("id");
    var elem = [];
    elem.push(document.getElementById("opengl-canvas{i}"));
    elem.push(document.getElementById("current_worker{i}"));
    elem.push(document.getElementById("rendered_shader{i}"));
    elem.push(document.getElementById("console{i}"));
    for (var j = 0; j < elem.length; j++) {
      elem[j].id = elem[j].id.replace("{i}", i);
    }

    //Load next client, or start webgl instances if all clients loaded
    i++;
    if (i < numClients) {
      loadClient(i, numClients);
    } else {
      startSafe(numClients);
    }
  });
}

function onloadMulti() {
  var numClients = getArg("n");
  if (numClients == undefined) {
    alert("Please set the number of clients with the query n = [desired number of clients].\n(e.g. [url]?n=1)");
  } else {
    document.getElementById("version").value = "WebGL Context: " + webgl_context;
    loadClient(0, numClients);
  }
}

// Main entry point
function onloadStart() {

  webgl_context = getArg("context");
  if (webgl_context == undefined) webgl_context = "experimental-webgl";

  // isRunnerMulti is expected to be initialized in the HTML as a global variable
  if (isRunnerMulti) {
    onloadMulti();
  } else {
    onloadSingle();
  }
}

window.onload = onloadStart;

//# sourceURL=runner.js
