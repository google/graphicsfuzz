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

package com.graphicsfuzz.server;

import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager.Iface;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class CommandRunnable implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CommandRunnable.class);

  private final String name;
  private final List<String> command;
  private final String queueName;
  private final String logFile;
  private final FuzzerServiceManager.Iface fuzzerServiceManager;
  private final ICommandDispatcher commandDispatcher;

  public CommandRunnable(
        String name,
        List<String> command,
        String queueName,
        String logFile,
        Iface fuzzerServiceManager,
        ICommandDispatcher commandDispatcher) {
    this.name = name;
    this.command = command;
    this.queueName = queueName;
    this.logFile = logFile;
    this.fuzzerServiceManager = fuzzerServiceManager;
    this.commandDispatcher = commandDispatcher;
  }

  @Override
  public void run() {
    try {
      if (logFile != null) {
        // Due to the way the logging framework is configured (see log4j2.xml),
        // this will redirect the log to a file.
        MDC.put("logfile", logFile);
      }

      LOGGER.info(String.join(" ", command));

      commandDispatcher.dispatchCommand(command, fuzzerServiceManager);

    } catch (Throwable ex) {
      throw new RuntimeException("Command threw Throwable", ex);
    } finally {
      if (logFile != null) {
        MDC.remove("logfile");
      }
    }
  }

  public String getName() {
    return name;
  }

  public List<String> getCommand() {
    return command;
  }

  public String getLogFile() {
    return logFile;
  }
}
