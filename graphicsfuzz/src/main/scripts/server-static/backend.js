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

/* global variables ------------------------------------------------------*/
var gl = null;

var triangleLeftVertexPositionBuffer = null;
var triangleRightVertexPositionBuffer = null;

var transport = null;
var protocol = null;
var client = null;
var worker = null;

var msWait = 10;

var msWaitMax = 5000;

var isRunning = true;

/* HTML listeners --------------------------------------------------------*/
function doSetURL(url) {
	if (gl)
		gl.deleteProgram(gl.getParameter(gl.CURRENT_PROGRAM));
	webGLStart(getShaderFromURL(url));
}

function doSet(url) {
	if (gl)
		gl.deleteProgram(gl.getParameter(gl.CURRENT_PROGRAM));
	webGLStart(url);
}

function doCycle() {
	var shaders;
	var jqxhr = $.ajax({
			url: document.getElementById("location").value,
			async: false
	})
			.done(function(data) {
				$("<div>").html(data).find(".shader_array").text().split(":");
			});
	for (var i = 0; i < shaders.length; i++) {
		do_set("shaders/" + shaders[i]);
	}
}

function runnerOnLoad() {
    var canvas = document.getElementById("opengl-canvas");

    setTimeout(doRun, msWait);
}

function backoff() {
    msWait *= 2;
    if(msWait > msWaitMax) msWait = msWaitMax;
}

function resetWait() {
    msWait = 10;
}

function doRefresh() {
    document.location.reload();
}

function doRun() {
    if(!isRunning) return;
    try {
        if(transport == null) {
            transport = new Thrift.Transport("/requestJSON");
            protocol = new Thrift.TJSONProtocol(transport);
            client = new oglfuzzerserver.FuzzerServiceClient(protocol);

            worker = new oglfuzzerserver.WorkerName();
            var platformInfo = getPlatformInfo();

            if (typeof(Storage) === "undefined") {
                alert("Sorry! No Web Storage support..");
                return;
            }

            var queryParams = $.jurlp(window.location.href).query();
            worker.value = queryParams.worker != undefined ? queryParams.worker : localStorage.getItem("worker");
            console.log("Trying worker name'" + worker.value + "'.");
            worker = client.getWorkerName(platformInfo, worker);
            localStorage.setItem("worker", worker.value);
            console.log("Worker name from server '" + worker.value + "'.");

            document.getElementById("current_worker").value = "Current worker: " + worker.value;

            isRunning = true;
        }

        var job = client.getJob(worker);
        console.log("Got a job.");

        if (job.noJob != null) {
            console.log("No job for me.");
            backoff();
            return;
        }

        resetWait();

        if(job.skipJob != null) {
            console.log("Got a skip job.");
            client.jobDone(worker, job);
            return;
        }

        if (job.imageJob != null) {
            console.log("Job type is: image job.");

            var imageJob = job.imageJob;
            imageJob.result = new oglfuzzerserver.ImageJobResult();

            var result;

            console.log("Rendering variant shader '" + imageJob.shader.name + "'.");
            document.getElementById("rendered_shader").value = "Rendered shader: " + imageJob.shader.name;

            result = webGLStart(imageJob.shader.contents);
            checkGLOK();
            if (result.succeeded) {
                var image = getImage();
                checkGLOK();
                console.log("Image job status is 'SUCCESS'.");
                imageJob.result.status = oglfuzzerserver.JobStatus.SUCCESS;
                imageJob.result.imageContents = image.split(',')[1];
            } else {
                console.log("Image job status is 'ERROR'.");
                console.log(result.log);
                imageJob.result.status = oglfuzzerserver.JobStatus.ERROR;
                imageJob.result.errorMessage = result.error + "\n" + result.log;
            }

            client.jobDone(worker, job);
        }
	} catch (exception) {
		console.log("Exception occurred: " + exception);
		backoff();
		transport = null;
		if(exception === "CONTEXT_LOST_WEBGL" || exception === "Could not initialise WebGL") {
		    setTimeout(doRefresh, 5000);
		}
	} finally {
	    if(isRunning) {
	        setTimeout(doRun, msWait);
	    }
	}
}

function stopRun() {
	console.log("Disconnected from server.");
	isRunning = false;
}

function resetWorker() {
	localStorage.removeItem("worker");
}

/* OpenGL functions ------------------------------------------------------*/

function checkGLOK() {
    var error = gl.getError();
    if(error == gl.CONTEXT_LOST_WEBGL) {
        throw "CONTEXT_LOST_WEBGL";
    } else if (error != gl.NO_ERROR) {
        throw "GL error";
    }

}

function getShaderFromURL(url) {
	return $.ajax({
		url: url,
		async: false,
		dataType: "text",
        mimeType: "textPlain"
	}).responseText;
}

function initGL(canvas) {
	try {
	    gl = canvas.getContext("webgl") || canvas.getContext("experimental-webgl");
		//gl = canvas.getContext("experimental-webgl",
		//	{ preserveDrawingBuffer: true });
		gl.viewportWidth = canvas.width;
		gl.viewportHeight = canvas.height;
	} catch (e) {
	}
	if (!gl) {
		throw "Could not initialise WebGL";
	}
	checkGLOK();
}

function makeShader(gl, src, shaderType) {
	var shader;
	if (shaderType == "fragment") {
		shader = gl.createShader(gl.FRAGMENT_SHADER);
	} else if (shaderType == "vertex") {
		shader = gl.createShader(gl.VERTEX_SHADER);
	} else {
		return {
			shader: null,
			log: "Shader type '" + shaderType + "' is not fragment or vertex."
		};
	}

	gl.shaderSource(shader, src);
	gl.compileShader(shader);

	if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
		var error = gl.getShaderInfoLog(shader);
		console.log(error);
		return {
			shader: null,
			log: error
		};
	}

	return {
		shader: shader,
		log: ""
	};
}

function trySetUniform1f(shaderProgram, name, x) {
	var loc = gl.getUniformLocation(shaderProgram, name);
	if (loc != null) {
		gl.uniform1f(loc, x);
	}
}

function trySetUniform2f(shaderProgram, name, x, y) {
	var loc = gl.getUniformLocation(shaderProgram, name);
	if (loc != null) {
		gl.uniform2f(loc, x, y);
	}
}

function initShaders(fragmentShader, vertexShader) {
	var shaderProgram = gl.createProgram();
	gl.attachShader(shaderProgram, vertexShader);
	gl.attachShader(shaderProgram, fragmentShader);
	gl.linkProgram(shaderProgram);
	gl.detachShader(shaderProgram, vertexShader);
	gl.detachShader(shaderProgram, fragmentShader);

	if (!gl.getProgramParameter(shaderProgram, gl.LINK_STATUS)) {
		console.log("Error linking shaders.");
		return {
			succeeded: false,
			log: "Error linking shaders."
		};
	}

	gl.useProgram(shaderProgram);

	shaderProgram.vertexPositionAttribute = gl.getAttribLocation(shaderProgram, "a_position");
	gl.enableVertexAttribArray(shaderProgram.vertexPositionAttribute);

	trySetUniform2f(shaderProgram, "injectionSwitch", 0.0, 1.0);
	trySetUniform1f(shaderProgram, "time", 0.0);
	trySetUniform2f(shaderProgram, "mouse", 0.0, 0.0);
	trySetUniform2f(shaderProgram, "resolution", gl.viewportWidth, gl.viewportHeight);

	return {
		succeeded: true,
		log: "",
		shaderProgram: shaderProgram
	};
}

function initBuffers() {

	triangleLeftVertexPositionBuffer = gl.createBuffer();
	gl.bindBuffer(gl.ARRAY_BUFFER, triangleLeftVertexPositionBuffer);
	var vertices = [
		 1.0,  1.0,  0.0,
		-1.0,  1.0,  0.0,
		 1.0, -1.0,  0.0
	];
	gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(vertices), gl.STATIC_DRAW);
	triangleLeftVertexPositionBuffer.itemSize = 3;
	triangleLeftVertexPositionBuffer.numItems = 3;

	triangleRightVertexPositionBuffer = gl.createBuffer();
	gl.bindBuffer(gl.ARRAY_BUFFER, triangleRightVertexPositionBuffer);
	var vertices = [
		-1.0,  1.0,  0.0,
		-1.0, -1.0,  0.0,
		 1.0, -1.0,  0.0
	];
	gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(vertices), gl.STATIC_DRAW);
	triangleRightVertexPositionBuffer.itemSize = 3;
	triangleRightVertexPositionBuffer.numItems = 3;
}

function drawScene(shaderProgram) {
	gl.viewport(0, 0, gl.viewportWidth, gl.viewportHeight);
	gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

	gl.bindBuffer(gl.ARRAY_BUFFER, triangleLeftVertexPositionBuffer);
	gl.vertexAttribPointer(shaderProgram.vertexPositionAttribute, triangleLeftVertexPositionBuffer.itemSize, gl.FLOAT, false, 0, 0);
	gl.drawArrays(gl.TRIANGLES, 0, triangleLeftVertexPositionBuffer.numItems);

	gl.bindBuffer(gl.ARRAY_BUFFER, triangleRightVertexPositionBuffer);
	gl.vertexAttribPointer(shaderProgram.vertexPositionAttribute, triangleRightVertexPositionBuffer.itemSize, gl.FLOAT, false, 0, 0);
	gl.drawArrays(gl.TRIANGLES, 0, triangleRightVertexPositionBuffer.numItems);
}

// Returns true if and only if compilation and execution was successful
function webGLStart(fragShader) {
	var canvas = document.getElementById("opengl-canvas");
	initGL(canvas);

	var fragmentShaderResult = makeShader(gl, fragShader, "fragment");
	if (fragmentShaderResult.shader == null) {
		var error = "Failed making fragment shader.";
		console.log(error);
		return {
			succeeded: false,
			error: "COMPILE_ERROR",
			log: error + "\n" + fragmentShaderResult.log
		};
	}

	var vertexShaderResult = makeShader(gl, getShaderFromURL("shaders/shader.vert"), "vertex");
	if (vertexShaderResult.shader == null) {
		var error = "Failed making vertex shader.";
	    console.log(error);
		return {
			succeeded: false,
			error: "COMPILE_ERROR",
			log: error + "\n" + vertexShaderResult.log
		};
	}

	var initResult = initShaders(fragmentShaderResult.shader, vertexShaderResult.shader);
	if (!initResult.succeeded) {
		return {
			succeeded: false,
			error: "LINK_ERROR",
			log: initResult.log
		};
	}

	initBuffers();

	gl.clearColor(0.0, 0.0, 0.0, 1.0);
	drawScene(initResult.shaderProgram);
	gl.finish();

	return {
		succeeded: true,
		log: ""
	};
}

function getPlatformInfo() {
	var canvas = document.getElementById("opengl-canvas");
	initGL(canvas);


	var platformInfo = new oglfuzzerserver.PlatformInfo(
	{
        contents: JSON.stringify(
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
        })
    });

	return platformInfo;
}

/* Helper functions ------------------------------------------------------*/
function makePPM(data) {
	var ppm_str = "P3\n";
	ppm_str += gl.viewportWidth + " " + gl.viewportHeight + "\n";
	ppm_str += "255\n";
	for (var i = 0; i < data.length; i++)
		if (i % 4 == 3)
			ppm_str += "\n";
		else
			ppm_str += data[i] + " ";
	return ppm_str;
}

// Taken from http://stackoverflow.com/questions/4998908/convert-data-uri-to-file-then-append-to-formdata
// TODO: understand why this works
function dataURItoBlob(dataURI) {
    // separate out the mime component
	var mimeString = dataURI.split(',')[0].split(':')[1].split(';')[0];
	return new Blob(dataURItoBinary(dataURI), { type: mimeString });
}

function dataURItoBinary(dataURI) {
	// convert base64/URLEncoded data component to raw binary data held in a string
    var byteString = dataURItoString(dataURI);

	// write the bytes of the string to a typed array
	var ia = new Uint8Array(byteString.length);
	for (var i = 0; i < byteString.length; i++) {
		ia[i] = byteString.charCodeAt(i);
	}

	return ia;
}

function dataURItoString(dataURI) {
    var byteString;
    if (dataURI.split(',')[0].indexOf('base64') >= 0)
        byteString = atob(dataURI.split(',')[1]);
    else
        byteString = unescape(dataURI.split(',')[1]);
    return byteString;
}

function getImage() {
	var canvas = document.getElementById("opengl-canvas");
	return canvas.toDataURL("image/png");
}

function downloadFile(worker, url) {
	var file;
	$.ajax({
		url: url + "?worker=" + worker.value,
		async: false,
		dataType: "text",
        mimeType: "textPlain"
		})
		.done(function(response) {
			file = response;
		})
		.fail(function() {
			alert("Failed downloading from URL: " + url);
		});
	return file;
}

function uploadFile(worker, url, shaderSet, file, destinationFilename) {
	var packet = new FormData();
	packet.append("worker", worker.value);
	packet.append("id", shaderSet.shaderSetId);
	packet.append("file", dataURItoBlob(file), destinationFilename);
	$.ajax({
		url: url,
		contentType: false,
		processData: false,
		type: "POST",
		data: packet
	})
	.fail(function() {
		alert("Failed uploading file " + destinationFilename + ".");
	});
}

function filenameWithoutExtension(name) {
	return name.replace(/\.[^/.]+$/, "");
}

// Taken from http://webglreport.com
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
