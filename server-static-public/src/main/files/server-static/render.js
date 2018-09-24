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

// Sets up a non-global webgl2 pipeline.
// All of the inputs are specified in JSON format.
// The order of the top level objects will be preserved, ensuring the gl state
// machine transitions in an expected way.
//
// JSON format:
//
//   { "glstate": [ { transition(s) } ] }
//
// transition format:
//
//   "width" : num
//   "height" : num
//   "program : { "vs": string, "fs": string }
//   "uniform" : { "name": { "func": string, "args": [ val ] } * }
//   "buffer" : { "data": [ val ] }
//   "vertex" : { "enabled": "false", "func": string, "args": [val] }
//          OR  { "enabled": "true", "size": num, "stride": num, "offset": num }
//   "texture" : { "width": num, "height": num, "data": base64string }
//
// Any parameter not shown is fixed.


//
// Throws if the parameter is undefined.
//
function checkParameter(functionName, parameter, parameterName) {
  if (parameter === undefined) {
    throw parameterName + " undefined in function " + functionName;
  }
}

//
// Create a shader program from a parsed program JSON.
//
function parseProgram(gl, programJSON) {
  checkParameter("parseProgram", gl, "gl");
  checkParameter("parseProgram", programJSON, "programJSON");
  var vs = programJSON["vs"];
  checkParameter("parseProgram", vs, "programJSON[vs]");
  var fs = programJSON["fs"];
  checkParameter("parseProgram", fs, "programJSON[fs]");
  // Vertex shader
  var vertexShader = gl.createShader(gl.VERTEX_SHADER);
  gl.shaderSource(vertexShader, vs);
  gl.compileShader(vertexShader);
  if (!gl.getShaderParameter(vertexShader, gl.COMPILE_STATUS)) {
    throw "Failed to compile vertex shader.\n" + gl.getShaderInfoLog(vertexShader);
  }
  // Fragment shader
  var fragmentShader = gl.createShader(gl.FRAGMENT_SHADER);
  gl.shaderSource(fragmentShader, fs);
  gl.compileShader(fragmentShader);
  if (!gl.getShaderParameter(fragmentShader, gl.COMPILE_STATUS)) {
    throw "Failed to compile fragment shader.\n" + gl.getShaderInfoLog(fragmentShader);
  }
  // Shader program
  var shaderProgram = gl.createProgram();
  gl.attachShader(shaderProgram, vertexShader);
  gl.attachShader(shaderProgram, fragmentShader);
  gl.linkProgram(shaderProgram);
  if (!gl.getProgramParameter(shaderProgram, gl.LINK_STATUS)) {
    throw "Failed to link shader program.\n" + gl.getProgramInfoLog(shaderProgram);
  }
  gl.useProgram(shaderProgram);
}

//
// Parse and apply uniforms.
// Requires active shader program.
//
function parseUniforms(gl, uniformsJSON) {
  checkParameter("parseUniforms", gl, "gl");
  checkParameter("parseUniforms", uniformsJSON, "uniformsJSON");
  var shaderProgram = gl.getParameter(gl.CURRENT_PROGRAM);
  if (shaderProgram === null) {
    throw "Applying vertex attributes with no shader program";
  }

  for (uniform in uniformsJSON) {
    var location = gl.getUniformLocation(shaderProgram, uniform);
    if (location == null) {
      continue;
    }
    var uniformInfo = uniformsJSON[uniform];
    var func = uniformInfo["func"];
    checkParameter("parseUniforms", func, "uniformsJSON[" + uniform + "][func]");
    var args = uniformInfo["args"];
    checkParameter("parseUniforms", args, "uniformsJSON[" + uniform + "][args]");
    // Find function name and reflect.
    if (func === "glUniform1f") {
      gl.uniform1f(location, args[0]);
    } else if (func === "glUniform2f") {
      gl.uniform2f(location, args[0], args[1]);
    } else if (func === "glUniform3f") {
      gl.uniform3f(location, args[0], args[1], args[2]);
    } else if (func === "glUniform4f") {
      gl.uniform4f(location, args[0], args[1], args[2], args[3]);
    } else if (func === "glUniform1fv") {
      gl.uniform1fv(locationloc, args);
    } else if (func === "glUniform2fv") {
      gl.uniform2fv(location, args);
    } else if (func === "glUniform3fv") {
      gl.uniform3fv(location, args);
    } else if (func === "glUniform4fv") {
      gl.uniform4fv(location, args);
    } else if (func === "glUniform1i") {
      gl.uniform1i(location, args[0]);
    } else if (func === "glUniform2i") {
      gl.uniform2i(location, args[0], args[1]);
    } else if (func === "glUniform3i") {
      gl.uniform3i(location, args[0], args[1], args[2]);
    } else if (func === "glUniform4i") {
      gl.uniform4i(location, args[0], args[1], args[2], args[3]);
    } else if (func === "glUniform1iv") {
      gl.uniform1iv(location, args);
    } else if (func === "glUniformMatrix4fv") {
      gl.uniformMatrix4fv(location, false, args);
    } else {
      console.log("Do not know how to set uniform via function " + func + " and args " + args);
    }
  }
}

//
// Create buffer out of JSON.
// Only supports ARRAY_BUFFER of type FLOAT with STATIC_DRAW.
//
function parseBuffer(gl, bufferJSON) {
  checkParameter("parseBuffer", gl, "gl");
  checkParameter("parseBuffer", bufferJSON, "bufferJSON");
  var data = bufferJSON["data"];
  checkParameter("parseBuffer", data, "bufferJSON[data]");
  var verticesBuffer = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, verticesBuffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(data), gl.STATIC_DRAW);
}

//
// Parse and set up vertex attributes.
// Attributes come in two types:
//   - Enabled: Data comes from the currently bound buffer (only supports FLOAT).
//   - Disabled: Generic value.
//
function parseVertex(gl, vertexJSON) {
  checkParameter("parseVertex", gl, "gl");
  checkParameter("parseVertex", vertexJSON, "vertexJSON");
  var shaderProgram = gl.getParameter(gl.CURRENT_PROGRAM);
  if (shaderProgram === null) {
    throw "Applying vertex attributes with no shader program";
  }
  var maxAttribute = 0;

  for (attribute in vertexJSON) {
    var attributeIndex = maxAttribute++;
    gl.bindAttribLocation(shaderProgram, attributeIndex, attribute);
    var attributeInfo = vertexJSON[attribute];
    var enabled = attributeInfo["enabled"];
    checkParameter("parseVertex", enabled, "vertexJSON[" + attribute + "][enabled]");
    if (enabled == "false") {
      // For disabled, it is the same as uniforms.
      var func = attributeInfo["func"];
      checkParameter("parseVertex", func, "vertexJSON[" + attribute + "][func]");
      var args = attributeInfo["args"];
      checkParameter("parseVertex", args, "vertexJSON[" + attribute + "][args]");
      // Find function name and reflect.
      if (func === "glVertexAttrib1f") {
        gl.vertexAttrib1f(attributeIndex, args[0]);
      } else if (func === "glVertexAttrib2f") {
        gl.vertexAttrib2f(attributeIndex, args[0], args[1]);
      } else if (func === "glVertexAttrib3f") {
        gl.vertexAttrib3f(attributeIndex, args[0], args[1], args[2]);
      } else if (func === "glVertexAttrib4f") {
        gl.vertexAttrib4f(attributeIndex, args[0], args[1], args[2], args[3]);
      } else if (func === "glVertexAttrib1fv") {
        gl.vertexAttrib1fv(attributeIndex, args);
      } else if (func === "glVertexAttrib2fv") {
        gl.vertexAttrib2fv(attributeIndex, args);
      } else if (func === "glVertexAttrib3fv") {
        gl.vertexAttrib3fv(attributeIndex, args);
      } else if (func === "glVertexAttrib4fv") {
        gl.vertexAttrib4fv(attributeIndex, args);
      } else {
        console.log("Do not know how to set attribute via function " + func + " and args " + args);
      }
    } else {
      // Otherwise the input comes form the bound buffer.
      var size = attributeInfo["size"];
      checkParameter("parseVertex", size, "vertexJSON[" + attribute + "][size]");
      var stride = attributeInfo["stride"];
      checkParameter("parseVertex", stride, "vertexJSON[" + attribute + "][stride]");
      var offset = attributeInfo["offset"];
      checkParameter("parseVertex", offset, "vertexJSON[" + attribute + "][offset]");
      gl.vertexAttribPointer(attributeIndex, size, gl.FLOAT, false, stride, offset);
      gl.enableVertexAttribArray(attributeIndex);
    }
  }
}

//
// Parse and decode texture input.
// Textures are in base64 format.
// For now, only supports one texture, as they are all bound to TEXTURE0.
// Many of the other params are also fixed for now.
//
function parseTexture(gl, textureJSON) {
  checkParameter("parseTexture", gl, "gl");
  checkParameter("parseTexture", textureJSON, "textureJSON");
  var width = textureJSON["width"];
  checkParameter("parseTexture", width, "textureJSON[width]");
  var height = textureJSON["height"];
  checkParameter("parseTexture", height, "textureJSON[height]");
  var data = textureJSON["data"];
  checkParameter("parseTexture", data, "textureJSON[data]");
  var texture = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
  gl.activeTexture(gl.TEXTURE0);
  texture.image = new Image();
  texture.image.src = data;
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, width, height, 0, gl.RGBA, gl.UNSIGNED_BYTE, texture.image);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_R, gl.REPEAT);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.REPEAT);
}

//
// Parse a transition and apply the transition to the gl state.
// There is no order to the elements in each transition object, for ordering,
// create separate transitions and order them at the top level glstate array.
//
function parseTransition(gl, transitionJSON) {
  checkParameter("parseTransition", gl, "gl");
  checkParameter("parseTransition", transitionJSON, "transitionJSON");
  for (item in transitionJSON) {
    var value = transitionJSON[item];
    if (item === "width") {
      gl.canvas.width = value;
      gl.viewport(0, 0, value, gl.canvas.height);
    } else if (item === "height") {
      gl.canvas.height = value;
      gl.viewport(0, 0, gl.canvas.width, value);
    } else if (item === "program") {
      parseProgram(gl, value);
    } else if (item === "uniform") {
      parseUniforms(gl, value);
    } else if (item === "buffer") {
      parseBuffer(gl, value);
    } else if (item === "vertex") {
      parseVertex(gl, value);
    } else if (item === "texture") {
      parseTexture(gl, value);
    }
  }
}

//
// Render webgl pipeline specification onto the provided DOM canvas.
//
function render(canvas, specification) {
  var gl = canvas.getContext("webgl2", { alpha: false });

  var specificationJSON = JSON.parse(specification);
  var glstateJSON = specificationJSON["glstate"];
  checkParameter("render", glstateJSON, "specificationJSON[glstate]");
  for (transition in glstateJSON) {
    parseTransition(gl, glstateJSON[transition]);
  }

  var drawFunction = function(now) {
    gl.clearColor(0.0, 0.0, 0.0, 1.0);
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.drawArrays(gl.TRIANGLE_FAN, 0, 4);
    requestAnimationFrame(drawFunction);
  };
  requestAnimationFrame(drawFunction);
}
