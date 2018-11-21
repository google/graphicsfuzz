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

import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.reducer.tool.GlslReduce;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager.Iface;
import com.graphicsfuzz.shadersets.RunShaderFamily;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import java.io.IOException;
import java.util.List;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class GraphicsFuzzServerCommandDispatcher implements ICommandDispatcher {

  @Override
  public void dispatchCommand(List<String> command, Iface fuzzerServiceManager)
        throws ShaderDispatchException, ArgumentParserException, InterruptedException,
        IOException, ParseTimeoutException {
    switch (command.get(0)) {
      case "run_shader_family":
        RunShaderFamily.mainHelper(
              command.subList(1, command.size()).toArray(new String[0]),
              fuzzerServiceManager
        );
        break;
      case "glsl-reduce":
        GlslReduce.mainHelper(
              command.subList(1, command.size()).toArray(new String[0]),
              fuzzerServiceManager
        );
        break;
      default:
        throw new RuntimeException("Unknown command: " + command.get(0));
    }
  }
}
