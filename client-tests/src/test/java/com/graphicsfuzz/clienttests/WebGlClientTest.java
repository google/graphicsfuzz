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

package com.graphicsfuzz.clienttests;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import com.graphicsfuzz.server.FuzzerServer;

public class WebGlClientTest extends CommonClientTest {

  @BeforeClass
  public static void startServerAndWorker() throws Exception {
    File serverWorkDir = temporaryFolder.newFolder();
    server = new Thread(() -> {
      try {
        final FuzzerServer fuzzerServer = new FuzzerServer(
            serverWorkDir.getAbsolutePath(), 8080, fileOps);
        fuzzerServer.start();
      } catch (Exception exception) {
        throw new RuntimeException(exception);
      }
    });
    server.start();

    Thread.sleep(5000);

    final List<String> command = Arrays.asList(
        "firefox", "-new-instance", "http://localhost:8080/static/runner.html?context=webgl2&token=" + TOKEN);
    final ProcessBuilder pb =
        new ProcessBuilder()
            .command(command)
            .directory(temporaryFolder.getRoot());

    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

    worker = pb.start();

    int exceptionCount = 0;
    final int limit = 1000;
    File workerDirectory = Paths.get(serverWorkDir.getAbsolutePath(), "processing", TOKEN)
        .toFile();
    while (true) {
      Thread.sleep(10);
      if (workerDirectory.exists()) {
        break;
      }
      if (exceptionCount >= limit) {
        throw new RuntimeException("Problem starting worker and server");
      }
      exceptionCount++;
    }
    System.out.println("Got token after " + exceptionCount + " tries");
  }

  @AfterClass
  public static void destroyWorker() {
    worker.destroy();
  }


}
