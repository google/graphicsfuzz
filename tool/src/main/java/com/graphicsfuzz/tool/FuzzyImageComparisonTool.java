/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.tool;

import com.graphicsfuzz.common.util.FuzzyImageComparison;
import java.io.IOException;
import java.io.PrintWriter;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class FuzzyImageComparisonTool {

  public static void main(String[] args) throws IOException {
    try {
      FuzzyImageComparison.MainResult result = FuzzyImageComparison.mainHelper(args);
      System.out.println(result.outputsString());
      System.exit(result.exitStatus);

    } catch (ArgumentParserException exception) {
      System.err.println(exception.getMessage());
      exception.getParser().printHelp(new PrintWriter(System.err, true));
      System.exit(3);
    } catch (Throwable throwable) {
      throwable.printStackTrace();
      System.exit(2);
    }
  }

}
