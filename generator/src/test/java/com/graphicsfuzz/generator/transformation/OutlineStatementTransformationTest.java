package com.graphicsfuzz.generator.transformation;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.generator.semanticspreserving.OutlineStatementMutationFinder;
import java.io.File;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class OutlineStatementTransformationTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testApplyRepeatedly() throws Exception {
    // Checks that applying outlining a few times does not lead to an invalid shader.
    final int limit = 4;
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    StringBuilder program = new StringBuilder("#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {\n");
    for (int i = 0; i < limit; i++) {
      program.append("  int x" + i + ";\n");
    }
    for (int i = 0; i < limit; i++) {
      program.append("  x" + i + " = 0;\n");
    }
    program.append("}\n");
    final TranslationUnit tu = ParseHelper.parse(program.toString());

    // Check that structifying many times, with no memory in-between (other than the AST itself),
    // leads to valid shaders.
    for (int i = 0; i < limit; i++) {
      new OutlineStatementMutationFinder(tu)
          .findMutations()
          .get(0).apply();

      final File shaderJobFile = temporaryFolder.newFile("shaderjob" + i + ".json");
      fileOperations.writeShaderJobFile(new GlslShaderJob(Optional.empty(),
          new PipelineInfo("{}"),
          tu), shaderJobFile);
      assertTrue(fileOperations.areShadersValid(shaderJobFile, false));
    }
  }

}
