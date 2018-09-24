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
import com.graphicsfuzz.common.util.ExecHelper.RedirectType;
import com.graphicsfuzz.common.util.ExecResult;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ToolHelper;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunities;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunityContext;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestReduce {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestReduce.class);

  private static class ReductionFailedToValidateException extends Exception {

    public final TranslationUnit previousState;
    public final TranslationUnit state;
    public final IReductionOpportunity opportunity;
    public final String output;

    public ReductionFailedToValidateException(TranslationUnit previousState, TranslationUnit state,
          IReductionOpportunity opportunity, String output) {
      super(output);
      this.previousState = previousState;
      this.state = state;
      this.opportunity = opportunity;
      this.output = output;
    }
  }

  private static final int NUM_SEEDS = 2;
  private static final int SHALLOW_STEPS = 15;
  private static final int VALIDATION_PERIOD = 20;

  private static File[] getVariants(String subdir) {
    return Paths.get(TestShadersDirectory.getTestShadersDirectory(), "variants", subdir).toFile()
          .listFiles((dir, name) -> name.endsWith(".frag"));
  }

  private static void testVariants(String id, File[] variants, int[] seeds, int maxSteps,
        int validationPeriod) {
    LOGGER.info("TESTS: {}", id);
    for (File variant : variants) {
      for (int seed : seeds) {
        for (int reduceEverywhere = 0; reduceEverywhere < 2; ++reduceEverywhere) {
          try {
            reduceLoop(id, variant, seed, reduceEverywhere != 0, maxSteps, validationPeriod);
          } catch (Exception ex) {
            LOGGER.info("", ex);
          }
        }
      }
    }
  }

  public static void main(String[] args)
        throws IOException, ParseTimeoutException,
        ReductionFailedToValidateException, InterruptedException {

    Random rand = new Random(0);
    File[] largeVariants = getVariants("large");

    if (largeVariants == null || largeVariants.length == 0) {
      throw new FileNotFoundException("Could not find variants!");
    }
    int[] seeds = new int[NUM_SEEDS];
    for (int i = 0; i < NUM_SEEDS; ++i) {
      seeds[i] = rand.nextInt();
    }
    File[] smallVariants = getVariants("small");

    testVariants("large-shallow", largeVariants, seeds, SHALLOW_STEPS, 1);
    testVariants("large-coarse", largeVariants, seeds, 0, VALIDATION_PERIOD);
    testVariants("small-shallow", smallVariants, seeds, SHALLOW_STEPS, 1);
    testVariants("small-coarse", smallVariants, seeds, 0, VALIDATION_PERIOD);

  }

  private static void reduceLoop(String id, File variant, int seed, boolean reduceEverywhere,
        int maxSteps,
        int validationPeriod)
        throws IOException, ParseTimeoutException,
        InterruptedException, ReductionFailedToValidateException {

    ShadingLanguageVersion shadingLanguageVersion =
        ShadingLanguageVersion.getGlslVersionFromShader(variant);
    try {
      LOGGER.info("ReduceLoop: {}, {}, {}, {}.", variant, seed, reduceEverywhere, maxSteps);

      final IRandom generator = new RandomWrapper(seed);

      final IdGenerator idGenerator = new IdGenerator();

      TranslationUnit state = ParseHelper.parse(variant, true);

      int stepCount = 0;
      int validationStepCount = 0;
      while (true) {

        if (maxSteps > 0 && stepCount >= maxSteps) {
          break;
        }
        ++stepCount;

        List<IReductionOpportunity> reductionOpportunities =
              ReductionOpportunities.getReductionOpportunities(
                    state, new ReductionOpportunityContext(reduceEverywhere, shadingLanguageVersion,
                                                           generator, idGenerator));

        if (reductionOpportunities.isEmpty()) {
          break;
        }

        TranslationUnit previousState = state.clone();

        int index = generator.nextInt(reductionOpportunities.size());
        IReductionOpportunity op = reductionOpportunities.get(index);
        op.applyReduction();

        // Only validate every `validationPeriod` steps.
        if (validationPeriod > 1) {
          ++validationStepCount;
          if (validationStepCount >= validationPeriod) {
            validationStepCount = 0;
          } else {
            continue;
          }
        }

        File outputFile = new File("temp.frag");
        TestGenerate.writeTranslationUnit(state, shadingLanguageVersion, outputFile);

        ExecResult result = ToolHelper
              .runValidatorOnShader(RedirectType.TO_BUFFER, outputFile);
        if (result.res != 0) {
          result.stdout.append(result.stderr);
          throw new ReductionFailedToValidateException(state, previousState, op,
                result.stdout.toString());
        }
        // TODO: shader translator

      }
    } catch (ReductionFailedToValidateException ex) {

      String prefix = FilenameUtils.removeExtension(
            variant.getName()) + "-failure-" + id + seed + (reduceEverywhere ? "everywhere" : "");

      LOGGER.info("Writing validation error to files starting with: {}", prefix);
      TestGenerate
            .writeTranslationUnit(ex.previousState,
                shadingLanguageVersion, new File(prefix + "-before.frag"));
      TestGenerate.writeTranslationUnit(ex.state,
          shadingLanguageVersion, new File(prefix + "-after.frag"));
      FileUtils
            .writeStringToFile(new File(prefix + "-output.txt"), ex.output,
                  Charset.defaultCharset());
      throw ex;
    }


  }
}
