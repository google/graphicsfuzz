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

package com.graphicsfuzz.common.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.StatsVisitor;
import java.io.File;
import java.io.IOException;

public class ReportShaderSize {


  public static void main(String[] args) throws IOException, ParseTimeoutException,
      InterruptedException, GlslParserException {
    if (args.length != 1) {
      System.err.println("Usage: ReportShaderSize <file>");
      System.exit(1);
    }
    File inputFile = new File(args[0]);
    TranslationUnit tu = ParseHelper.parse(inputFile);

    StatsVisitor sv = new StatsVisitor(tu);

    System.out.printf(
        "{ \"num_bytes\": %d, \"num_nodes\": %d, \"num_statements\": %d }",
        inputFile.length(),
        sv.getNumNodes(),
        sv.getNumStatements());

  }

}

