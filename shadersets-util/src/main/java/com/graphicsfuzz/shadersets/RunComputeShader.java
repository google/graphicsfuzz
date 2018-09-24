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

package com.graphicsfuzz.shadersets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.graphicsfuzz.common.util.JsonHelper;
import com.graphicsfuzz.server.thrift.ComputeJobResult;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunComputeShader {

  private static final Logger LOGGER = LoggerFactory.getLogger(RunComputeShader.class);

  public static void main(String[] args) throws InterruptedException, IOException {
    try {
      mainHelper(args, null);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

  public static void mainHelper(
      String[] args,
      FuzzerServiceManager.Iface managerOverride)
      throws ShaderDispatchException, InterruptedException, IOException, ArgumentParserException {

    ArgumentParser parser = ArgumentParsers.newArgumentParser("RunComputeShader")
        .defaultHelp(true)
        .description("Runs a compute shader.");

    parser.addArgument("--verbose")
        .action(Arguments.storeTrue())
        .help("Verbose output.");

    parser.addArgument("--server")
        .help(
            "URL of server to use for sending compute requests.")
        .type(String.class);

    parser.addArgument("--token")
        .help("The token of the client used for compute requests. Used with --server.")
        .type(String.class);

    parser.addArgument("--output")
        .help("Output directory.")
        .setDefault(new File("."))
        .type(File.class);

    parser.addArgument("compute_shader")
        .help("Compute shader")
        .type(File.class);


    Namespace ns = parser.parseArgs(args);

    final boolean verbose = ns.get("verbose");
    final File computeShaderFile = ns.get("compute_shader");
    final String server = ns.get("server");
    final String token = ns.get("token");
    final File outputDir = ns.get("output");

    if (server == null || token == null) {
      throw new ArgumentParserException("Must supply server and token -- "
          + "local execution of compute shaders is not yet supported.", parser);
    }

    IShaderDispatcher shaderDispatcher = new RemoteShaderDispatcher(
                server + "/manageAPI",
                token,
                managerOverride,
                new AtomicLong());

    FileUtils.forceMkdir(outputDir);

    if (!computeShaderFile.isFile() || !computeShaderFile.getName().endsWith(".comp")) {
      throw new ArgumentParserException(
          "Compute shader must be a single .comp shader.", parser);
    }
    runComputeShader(computeShaderFile, outputDir, shaderDispatcher);
  }

  private static ComputeJobResult runComputeShader(File computeShaderFile, File outputDir,
      IShaderDispatcher shaderDispatcher)
      throws ShaderDispatchException, InterruptedException, IOException {
    final ComputeJobResult res = shaderDispatcher.dispatchCompute(computeShaderFile, false);

    // Dump job info in JSON
    File outputJson = new File(outputDir,
        FilenameUtils.removeExtension(computeShaderFile.getName()) + ".info.json");
    JsonObject infoJson = makeInfoJson(res);
    FileUtils.writeStringToFile(outputJson,
        JsonHelper.jsonToString(infoJson), Charset.defaultCharset());
    return res;
  }

  private static JsonObject makeInfoJson(ComputeJobResult res) {
    JsonObject infoJson = new JsonObject();
    if (res.isSetStatus()) {
      infoJson.addProperty("Status", res.getStatus().toString());
    }
    if (res.isSetLog()) {
      infoJson.addProperty("Log", res.getLog());
    }
    if (res.isSetOutputs()) {
      infoJson.add("Outputs", new Gson().fromJson(res.getOutputs(), JsonObject.class));
    }
    return infoJson;
  }

}
