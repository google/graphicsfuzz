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

package com.graphicsfuzz.reducer;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.ExecHelper.RedirectType;
import com.graphicsfuzz.common.util.ExecResult;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.ToolHelper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import org.junit.rules.TemporaryFolder;

public class TestUtils {

  public static void checkValid(TemporaryFolder testFolder, TranslationUnit tu) throws IOException, InterruptedException {
    File tempFile = testFolder.newFile("temp.frag");
    new PrettyPrinterVisitor(System.out).visit(tu);
    PrintStream ps = new PrintStream(new FileOutputStream(tempFile));
    Helper.emitDefines(ps, ShadingLanguageVersion.ESSL_100,
            ShaderKind.FRAGMENT,true);
    PrettyPrinterVisitor ppv = new PrettyPrinterVisitor(ps);
    ppv.visit(tu);
    ps.close();
    ExecResult result = ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER, tempFile);
    assertEquals(result.stderr.toString() + result.stdout.toString(), 0, result.res);
  }

}
