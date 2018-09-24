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

package com.graphicsfuzz.serverpublic;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Main {
  public static void main(String[] args) throws Exception {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("Fuzzer server")
        .defaultHelp(true);

    parser.addArgument("--port")
        .help("Port on which to listen.")
        .setDefault(8080)
        .type(Integer.class);

    try {
      Namespace ns = parser.parseArgs(args);

      new FuzzerServer(ns.get("port")).start();

    } catch (ArgumentParserException ex) {
      ex.getParser().handleError(ex);
    }
  }
}
