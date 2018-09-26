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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StreamGobbler extends Thread {

  private final InputStream inputStream;

  private static final Logger LOGGER = LoggerFactory.getLogger(StreamGobbler.class);

  public StreamGobbler(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  public void run() {
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
      String line;
      while ((line = br.readLine()) != null) {
        handleLine(line);
      }
    } catch (IOException exception) {
      LOGGER.error("Exception while gobbling stream", exception);
    }
  }

  protected abstract void handleLine(String line);

  public abstract StringBuffer getResult();

}