package com.graphicsfuzz.generator.semanticschanging;

import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.util.ToolPaths;
import java.io.File;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class InterchangeExprMutationFinderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testValidInterchanges() throws Exception {
    final IRandom generator = new RandomWrapper(0);
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    for (File shaderJobFile : Paths.get(ToolPaths.getShadersDirectory(), "samples",
        "100").toFile().listFiles((dir, name) -> name.endsWith(".json"))) {
      final ShaderJob shaderJob = fileOperations.readShaderJobFile(shaderJobFile);
      new InterchangeExprMutationFinder(shaderJob.getFragmentShader().get(), generator)
          .findMutations().forEach(Mutation::apply);
    }
  }

}
