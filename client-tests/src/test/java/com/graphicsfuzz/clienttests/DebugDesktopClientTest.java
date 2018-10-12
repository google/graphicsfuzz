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

import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.graphicsfuzz.server.FuzzerServer;

public class DebugDesktopClientTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Test
  public void someTest() throws Exception {
    File serverWorkDir = temporaryFolder.newFolder();
    Thread server = new Thread(() -> {
      try {
        final FuzzerServer fuzzerServer = new FuzzerServer(
              serverWorkDir.getAbsolutePath(), 8080, fileOps);
        fuzzerServer.start();
      } catch (Exception exception) {
        throw new RuntimeException(exception);
      }
    });
    server.start();

    /*
    for (int i = 10; i > 0; i--) {
      System.out.println(i);
      Thread.sleep(1000);
    }

    DesktopClientTest.runComputeShader("42es.comp", temporaryFolder);
    */
  }
}
