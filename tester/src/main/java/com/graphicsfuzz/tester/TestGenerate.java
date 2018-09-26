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

package com.graphicsfuzz.tester;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.controlflow.AddJumpStmts;
import com.graphicsfuzz.generator.transformation.controlflow.AddWrappingConditionalStmts;
import com.graphicsfuzz.generator.transformation.donation.DonateDeadCode;
import com.graphicsfuzz.generator.transformation.donation.DonateLiveCode;
import com.graphicsfuzz.generator.transformation.mutator.MutateExpressions;
import com.graphicsfuzz.generator.transformation.vectorizer.VectorizeStatements;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TestGenerate {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestGenerate.class);

  public static void main(String[] args)
        throws IOException, ParseTimeoutException, InterruptedException {

    File[] originalShaderFiles = new File(TestShadersDirectory.getTestShadersDirectory(),
          "references").listFiles((dir, name) -> name.endsWith(".frag"));

    if (originalShaderFiles == null) {
      throw new FileNotFoundException("Could not find test fragment shaders.");
    }

    Random random = new Random(0);
    ShadingLanguageVersion shadingLanguageVersion =
        ShadingLanguageVersion.WEBGL_SL;
    File donorsFolder = new File(TestShadersDirectory.getTestShadersDirectory(),
          "references");

    final int repeatShallow = 3;
    final int repeatDeep = 10;

    final GenerationParams generationParams = GenerationParams.normal(ShaderKind.FRAGMENT);

    List<TranslationUnit> translationUnits = new ArrayList<>();
    for (File originalShaderFile : originalShaderFiles) {
      LOGGER.info("Parsing {}", originalShaderFile);
      translationUnits.add(Generate.getRecipientTranslationUnit(originalShaderFile));
    }

    for (int tu = 0; tu < translationUnits.size(); ++tu) {
      for (int i = 0; i < repeatShallow; ++i) {
        LOGGER.info("Shallow test {} of {}", i, originalShaderFiles[tu].getName());
        shallowTests(translationUnits.get(tu), shadingLanguageVersion, donorsFolder, random,
              generationParams);
      }
    }

    for (int tu = 0; tu < translationUnits.size(); ++tu) {
      for (int i = 0; i < repeatDeep; ++i) {
        LOGGER.info("Deep test {} of {}", i, originalShaderFiles[tu].getName());
        deepTest(translationUnits.get(tu), shadingLanguageVersion, donorsFolder, random,
              generationParams);
      }
    }

  }

  private static void shallowTests(TranslationUnit reference,
      ShadingLanguageVersion shadingLanguageVersion,
      File donorsFolder, Random random, GenerationParams generationParams)
      throws IOException, InterruptedException, ParseTimeoutException {

    RandomWrapper generator = new RandomWrapper(random.nextInt());

    TransformationProbabilities probabilities = TransformationProbabilities
          .closeToDefaultProbabilities(generator);

    List<ITransformation> transformations = getTransformations(probabilities,
        shadingLanguageVersion,
          generator, donorsFolder, generationParams);

    File outputFile = new File("temp.frag");

    for (ITransformation transformation : transformations) {
      LOGGER.info("Testing {}", transformation.getName());
      TranslationUnit clonedRecipient = reference.clone();
      generator.setSeed(random.nextInt());
      transformation.apply(clonedRecipient, probabilities, shadingLanguageVersion, generator,
            generationParams);
      writeTranslationUnit(clonedRecipient, shadingLanguageVersion, outputFile);
      ExecResult res = ToolHelper.runValidatorOnShader(RedirectType.TO_LOG, outputFile);
      if (res.res != 0) {
        throw new AssertionError("Failed to validate generated shader:" + outputFile);
      }
    }
  }

  private static void deepTest(TranslationUnit reference,
      ShadingLanguageVersion shadingLanguageVersion,
      File donorsFolder, Random random,
      GenerationParams generationParams)
      throws IOException, InterruptedException, ParseTimeoutException {

    RandomWrapper generator = new RandomWrapper(random.nextInt());

    TransformationProbabilities probabilities = TransformationProbabilities
          .closeToDefaultProbabilities(generator);

    List<ITransformation> transformations = getTransformations(probabilities,
        shadingLanguageVersion,
          generator, donorsFolder, generationParams);

    Collections.shuffle(transformations, random);

    File outputFile = new File("temp.frag");

    TranslationUnit clonedRecipient = reference.clone();
    for (ITransformation transformation : transformations) {
      LOGGER.info("Applying {}", transformation.getName());
      transformation.apply(clonedRecipient, probabilities, shadingLanguageVersion, generator,
            generationParams);
      writeTranslationUnit(clonedRecipient, shadingLanguageVersion, outputFile);
      ExecResult res = ToolHelper.runValidatorOnShader(RedirectType.TO_LOG, outputFile);
      if (res.res != 0) {
        throw new AssertionError("Failed to validate generated shader:" + outputFile);
      }
    }
  }

  private static List<ITransformation> getTransformations(TransformationProbabilities probabilities,
        ShadingLanguageVersion shadingLanguageVersion, IRandom generator, File donorsFolder,
        GenerationParams generationParams) {

    List<ITransformation> transformations = new ArrayList<>();
    transformations.add(new DonateLiveCode(probabilities::donateLiveCodeAtStmt, donorsFolder,
          generationParams, false));
    transformations.add(new DonateDeadCode(probabilities::donateDeadCodeAtStmt, donorsFolder,
          generationParams));
    transformations.add(new MutateExpressions());
    transformations.add(new AddWrappingConditionalStmts());
    transformations.add(new AddJumpStmts());
    transformations.add(new VectorizeStatements());

    return transformations;
  }


  public static void writeTranslationUnit(TranslationUnit translationUnit,
      ShadingLanguageVersion shadingLanguageVersion,
      File outputFile)
      throws IOException, ParseTimeoutException {

    try (PrintStream ps = new PrintStream(new FileOutputStream(outputFile))) {
      // TODO: these tests ultimately should not be limited to fragment shaders.
      Helper.emitDefines(ps, shadingLanguageVersion, ShaderKind.FRAGMENT, true);
      PrettyPrinterVisitor ppv = new PrettyPrinterVisitor(ps);
      ppv.visit(translationUnit);
    }
  }

}
