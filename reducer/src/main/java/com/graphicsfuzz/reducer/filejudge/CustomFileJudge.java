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

package com.graphicsfuzz.reducer.filejudge;

import com.graphicsfuzz.reducer.FileJudgeException;
import com.graphicsfuzz.reducer.IFileJudge;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ExecResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomFileJudge implements IFileJudge {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomFileJudge.class);

  private final List<String> judgeScript;

  public CustomFileJudge(List<String> judgeScript) {
    this.judgeScript = judgeScript;
  }

  @Override
  public boolean isInteresting(File shaderJobFile, File shaderResultFileOutput)
      throws FileJudgeException {
    List<String> scriptPlusShaderArg = new ArrayList<>(judgeScript);
    scriptPlusShaderArg.add(shaderJobFile.toString());
    try {
      final ExecResult execResult = new ExecHelper().exec(
          ExecHelper.RedirectType.TO_LOG,
          null,
          true,
          scriptPlusShaderArg.toArray(new String[0])
      );
      LOGGER.info("Custom file judge result: " + execResult.res);
      return execResult.res == 0;
    } catch (IOException | InterruptedException exception) {
      throw new FileJudgeException(exception);
    }
  }
}
