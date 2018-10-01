package com.graphicsfuzz.reducer.util;

import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("WeakerAccess")
public class ShaderJudgeUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShaderJudgeUtil.class);

  @SuppressWarnings("RedundantIfStatement")
  public static boolean shadersAreValid(File workDir,
                                        String shaderFilesPrefix,
                                        boolean throwExceptionOnInvalid)
      throws IOException, InterruptedException {
    final File fragmentShaderFile = new File(workDir,shaderFilesPrefix + ".frag");
    final File vertexShaderFile = new File(workDir,shaderFilesPrefix + ".vert");
    assert fragmentShaderFile.isFile() || vertexShaderFile.isFile();
    if (fragmentShaderFile.isFile() && !shaderIsValid(fragmentShaderFile,
        throwExceptionOnInvalid)) {
      return false;
    }
    if (vertexShaderFile.isFile() && !shaderIsValid(vertexShaderFile, throwExceptionOnInvalid)) {
      return false;
    }
    return true;
  }

  public static boolean shaderIsValid(File shaderFile, boolean throwExceptionOnValidationError)
      throws IOException, InterruptedException {
    ExecResult res = ToolHelper.runValidatorOnShader(ExecHelper.RedirectType.TO_LOG, shaderFile);
    if (res.res != 0) {
      LOGGER.warn("Shader {} failed to validate.", shaderFile.getName());
      if (throwExceptionOnValidationError) {
        throw new RuntimeException("Validation failed during reduction.");
      }
      return false;
    }
    return true;
  }
}
