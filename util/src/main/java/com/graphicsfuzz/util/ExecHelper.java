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

package com.graphicsfuzz.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecHelper {

  public enum RedirectType {
    TO_STDOUT,
    TO_LOG,
    TO_BUFFER,
    TO_FILE
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(ExecHelper.class);

  public static final boolean IS_WINDOWS =
      System.getProperty("os.name").toLowerCase().startsWith("windows");

  private static final String pathVar;

  static {
    String pathVarTemp = "PATH";
    for (String var : System.getenv().keySet()) {
      if (var.equalsIgnoreCase(pathVarTemp)) {
        pathVarTemp = var;
        break;
      }
    }

    pathVar = pathVarTemp;
  }

  private final String additionalPathDirectories;

  public ExecHelper(String additionalPathDirectories) {
    this.additionalPathDirectories = additionalPathDirectories;
  }

  public ExecHelper() {
    this.additionalPathDirectories = null;
  }

  public ExecResult exec(
      RedirectType redirectType,
      File directory,
      boolean shell,
      InputStream inputStream,
      String... command) throws IOException, InterruptedException {

    LOGGER.info(String.join(" ", command));

    List<String> commandList = new ArrayList<>(Arrays.asList(command));

    if (shell) {
      if (IS_WINDOWS) {
        commandList.addAll(0, Arrays.asList("cmd.exe", "/c"));
      } else {
        commandList.addAll(0, Arrays.asList("env", "--"));
      }
    }

    final ProcessBuilder pb =
        new ProcessBuilder()
            .command(commandList)
            .directory(directory);

    Map<String, String> env = pb.environment();
    if (additionalPathDirectories != null) {
      addToPath(env, additionalPathDirectories);
    }


    StringBuffer stdout = null;
    StringBuffer stderr = null;
    File stdoutFile = null;
    File stderrFile = null;
    StreamGobbler outputGobbler = null;
    StreamGobbler errorGobbler = null;

    switch (redirectType) {
      case TO_FILE:
        stdoutFile = File.createTempFile("stdout", "");
        stderrFile = File.createTempFile("stderr", "");
        pb.redirectOutput(stdoutFile);
        pb.redirectError(stderrFile);
        break;
      case TO_STDOUT:
        pb.redirectOutput(Redirect.INHERIT);
        pb.redirectError(Redirect.INHERIT);
        break;
      case TO_LOG:
      case TO_BUFFER:
        break;
      default:
        assert false;
    }

    Process process = pb.start();

    switch (redirectType) {
      case TO_LOG:
        outputGobbler = new StreamGobblerLogger(process.getInputStream(), "stdout.");
        outputGobbler.start();
        errorGobbler = new StreamGobblerLogger(process.getErrorStream(), "stderr.");
        errorGobbler.start();
        break;
      case TO_BUFFER:
        outputGobbler = new StreamGobblerBuffer(process.getInputStream());
        outputGobbler.start();
        errorGobbler = new StreamGobblerBuffer(process.getErrorStream());
        errorGobbler.start();
        break;
      case TO_STDOUT:
      case TO_FILE:
        break;
      default:
        assert false;
    }

    // If an input stream has been provided, copy it to the process's standard input.
    if (inputStream != null) {
      IOUtils.copy(inputStream, process.getOutputStream());
    }
    process.getOutputStream().close();

    int res = process.waitFor();

    if (outputGobbler != null) {
      outputGobbler.join();
      stdout = outputGobbler.getResult();
    }
    if (errorGobbler != null) {
      errorGobbler.join();
      stderr = errorGobbler.getResult();
    }

    LOGGER.info("Result: {}", res);

    return new ExecResult(res, stdout, stderr, stdoutFile, stderrFile);
  }

  /**
   * An overload that does not take an input stream.
   */
  public ExecResult exec(
      RedirectType redirectType,
      File directory,
      boolean shell,
      String... command) throws IOException, InterruptedException {
    return exec(redirectType, directory, shell, null, command);
  }

  public static void addToPath(Map<String, String> envVars, String pathToAdd) {
    if (!envVars.containsKey(pathVar)) {
      envVars.put(pathVar, pathToAdd);
    } else {
      String currentPath = envVars.get(pathVar);
      envVars.put(pathVar, pathToAdd + File.pathSeparator + currentPath);
    }
  }

}
