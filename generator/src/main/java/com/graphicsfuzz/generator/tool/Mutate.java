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

package com.graphicsfuzz.generator.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.generator.mutateapi.MutationFinder;
import com.graphicsfuzz.generator.semanticschanging.AddArrayMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.Compound2BodyMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.Expr2ArgMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.Expr2ArrayAccessMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.Expr2BinaryMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.Expr2LiteralMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.If2DiscardMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.RemoveInitializerMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.RemoveStmtMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.ReplaceBlockStmtsWithSwitchMutationFinder;
import com.graphicsfuzz.generator.semanticschanging.SwapVariableIdentifiersMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.AddDeadBarrierMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.AddDeadOutputWriteMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.AddJumpMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.AddLiveOutputWriteMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.AddSwitchMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.AddWrappingConditionalMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.IdentityMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.OutlineStatementMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.SplitForLoopMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.StructificationMutationFinder;
import com.graphicsfuzz.generator.semanticspreserving.VectorizeMutationFinder;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.util.ArgsUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mutate {

  private static final Logger LOGGER = LoggerFactory.getLogger(Mutate.class);

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("Mutate")
        .defaultHelp(true)
        .description("Takes a shader, gives back a mutated shader.");

    // Required arguments
    parser.addArgument("input")
        .help("A .frag or .vert shader")
        .type(File.class);

    parser.addArgument("output")
        .help("Path of mutated shader.")
        .type(File.class);

    parser.addArgument("--seed")
        .help("Seed to initialize random number generator with.")
        .type(Integer.class);

    return parser.parseArgs(args);
  }

  /**
   * Entry point to Mutate, guarded so that it cannot throw exceptions.
   * @param args Command-line arguments for the tool.
   */
  public static void main(String[] args) {
    try {
      mainHelper(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (IOException | ParseTimeoutException | InterruptedException
        | GlslParserException exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Entry point to Mutate, except that it can throw exceptions.
   * @param args Command-line arguments.
   * @throws ArgumentParserException if there is a problem with the command-line arguments.
   * @throws IOException if something I/O-related goes wrong.
   * @throws ParseTimeoutException if a shader takes too long to parse.
   * @throws InterruptedException if something goes wrong with an external tool.
   * @throws GlslParserException if a shader fails to parse.
   */
  public static void mainHelper(String[] args) throws ArgumentParserException, IOException,
      ParseTimeoutException, InterruptedException, GlslParserException {

    Namespace ns = parse(args);

    final File input = ns.get("input");
    final File output = ns.get("output");
    final int seed = ArgsUtil.getSeedArgument(ns);

    final TranslationUnit tu = ParseHelper.parse(input);

    LOGGER.info("Mutating from " + input + " to " + output + " with seed " + seed);

    mutate(tu, new RandomWrapper(seed));

    try (PrintStream stream = new PrintStream(new FileOutputStream(output))) {
      PrettyPrinterVisitor.emitShader(
          tu,
          Optional.empty(),
          stream,
          PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
          PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER,
          false
      );
    }

  }

  private static void mutate(TranslationUnit tu, IRandom random) {
    final int maxTries = 10;

    List<? extends Mutation> mutations;
    int tries = 0;
    final GenerationParams generationParams = GenerationParams.normal(tu.getShaderKind(),
        true);
    final List<Supplier<MutationFinder<?>>> mutationFinders = Arrays.asList(

        // Semantics-changing mutations, in alphabetical order:
        () -> new AddArrayMutationFinder(tu, random),
        () -> new Compound2BodyMutationFinder(tu),
        () -> new Expr2ArgMutationFinder(tu),
        () -> new Expr2ArrayAccessMutationFinder(tu, random),
        () -> new Expr2BinaryMutationFinder(tu, random),
        () -> new Expr2LiteralMutationFinder(tu, random),
        () -> new If2DiscardMutationFinder(tu),
        () -> new RemoveInitializerMutationFinder(tu),
        () -> new RemoveStmtMutationFinder(tu),
        () -> new ReplaceBlockStmtsWithSwitchMutationFinder(tu, random),
        () -> new SwapVariableIdentifiersMutationFinder(tu, random),

        // Semantics-preserving mutations, in alphabetical order
        () -> new AddDeadBarrierMutationFinder(tu, random, generationParams),
        () -> new AddDeadOutputWriteMutationFinder(tu, random, generationParams),
        () -> new AddJumpMutationFinder(tu, random, generationParams),
        () -> new AddLiveOutputWriteMutationFinder(tu, random, generationParams),
        () -> new AddSwitchMutationFinder(tu, random, generationParams),
        () -> new AddWrappingConditionalMutationFinder(tu, random, generationParams),
        () -> new IdentityMutationFinder(tu, random, generationParams),
        () -> new OutlineStatementMutationFinder(tu),
        () -> new SplitForLoopMutationFinder(tu, random),
        () -> new StructificationMutationFinder(tu, random, generationParams),
        () -> new VectorizeMutationFinder(tu, random)
    );

    do {
      mutations = mutationFinders.get(
          random.nextInt(mutationFinders.size())).get().findMutations();
      tries++;
    } while (mutations.isEmpty() && tries < maxTries);

    if (mutations.isEmpty()) {
      LOGGER.warn("Did not manage to apply a mutation.");
    } else {
      final Mutation mutation = mutations.get(random.nextInt(mutations.size()));
      LOGGER.info("Applying mutation of type " + mutation.getClass());
      mutation.apply();
    }

  }


}
