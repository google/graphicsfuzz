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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.PruneUniforms;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.StatsVisitor;
import com.graphicsfuzz.common.util.StripUnusedFunctions;
import com.graphicsfuzz.common.util.StripUnusedGlobals;
import com.graphicsfuzz.generator.transformation.AddDeadBarrierTransformation;
import com.graphicsfuzz.generator.transformation.AddDeadOutputWriteTransformation;
import com.graphicsfuzz.generator.transformation.AddJumpTransformation;
import com.graphicsfuzz.generator.transformation.AddLiveOutputWriteTransformation;
import com.graphicsfuzz.generator.transformation.AddSwitchTransformation;
import com.graphicsfuzz.generator.transformation.AddWrappingConditionalTransformation;
import com.graphicsfuzz.generator.transformation.DonateDeadCodeTransformation;
import com.graphicsfuzz.generator.transformation.DonateLiveCodeTransformation;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.IdentityTransformation;
import com.graphicsfuzz.generator.transformation.OutlineStatementTransformation;
import com.graphicsfuzz.generator.transformation.SplitForLoopTransformation;
import com.graphicsfuzz.generator.transformation.StructificationTransformation;
import com.graphicsfuzz.generator.transformation.VectorizeTransformation;
import com.graphicsfuzz.generator.util.FloatLiteralReplacer;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Generate {

  private static final Logger LOGGER = LoggerFactory.getLogger(Generate.class);

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("Generate")
        .defaultHelp(true)
        .description("Generate a shader.");

    // Required arguments
    parser.addArgument("reference-json")
        .help("Input reference shader json file.")
        .type(File.class);

    parser.addArgument("donors")
        .help("Path of folder of donor shaders.")
        .type(File.class);

    parser.addArgument("glsl-version")
        .help("Version of GLSL to target.")
        .type(String.class);

    parser.addArgument("output")
        .help("Output shader job file file (.json.")
        .type(File.class);

    addGeneratorCommonArguments(parser);

    return parser.parseArgs(args);

  }

  public static void addGeneratorCommonArguments(ArgumentParser parser) {
    parser.addArgument("--seed")
        .help("Seed to initialize random number generator with.")
        .type(Integer.class);

    parser.addArgument("--small")
        .help("Try to generate small shaders.")
        .action(Arguments.storeTrue());

    parser.addArgument("--allow-long-loops")
        .help("During live code injection, care is taken by default to avoid loops with "
            + "very long or infinite iteration counts.  This option disables this care so that "
            + "loops may end up being very long running.")
        .action(Arguments.storeTrue());

    parser.addArgument("--disable")
        .help("Disable a given series of transformations.")
        .type(String.class);

    parser.addArgument("--enable-only")
        .help("Disable all but the given series of transformations.")
        .type(String.class);

    parser.addArgument("--aggressively-complicate-control-flow")
        .help("Make control flow very complicated.")
        .action(Arguments.storeTrue());

    parser.addArgument("--single-pass")
        .help("Do not apply any individual transformation pass more than once.")
        .action(Arguments.storeTrue());

    parser.addArgument("--replace-float-literals")
        .help("Replace float literals with uniforms.")
        .action(Arguments.storeTrue());

    parser.addArgument("--generate-uniform-bindings")
        .help("Put all uniforms in uniform blocks and generate bindings; required for Vulkan "
            + "compatibility.")
        .action(Arguments.storeTrue());

    parser.addArgument("--max-uniforms")
        .help("Ensure that generated shaders have no more than the given number of uniforms; "
            + "required for Vulkan compatibility.")
        .setDefault(0)
        .type(Integer.class);

    parser.addArgument("--no-injection-switch")
        .help("Do not generate the injectionSwitch uniform.")
        .action(Arguments.storeTrue());

    // Hidden option; for developer debugging.
    parser.addArgument("--write-probabilities")
        .help(Arguments.SUPPRESS)
        .action(Arguments.storeTrue());
  }

  /**
   * Mutates the given shader job into a variant.
   *
   * @param shaderJob The shader job to be mutated.
   * @param args      Arguments to control generation.
   * @return Details of the transformations that were applied.
   */
  public static StringBuilder generateVariant(ShaderJob shaderJob,
                                              GeneratorArguments args,
                                              int seed) {
    final StringBuilder result = new StringBuilder();
    final IRandom random = new RandomWrapper(seed);

    if (args.getAddInjectionSwitch()) {
      for (TranslationUnit shader : shaderJob.getShaders()) {
        addInjectionSwitchIfNotPresent(shader);
      }
      setInjectionSwitch(shaderJob.getPipelineInfo());
    }

    for (TranslationUnit tu : shaderJob.getShaders()) {
      result.append(transformShader(
          tu,
          shaderJob,
          random.spawnChild(),
          args));
    }

    if (args.limitUniforms()) {
      if (!(PruneUniforms.prune(shaderJob, args.getMaxUniforms(),
          Arrays.asList(Constants.DEAD_PREFIX, Constants.LIVE_PREFIX)))) {
        throw new RuntimeException("It was not possible to prune sufficient uniforms from a "
            + "shader.");
      }
    }

    if (args.getGenerateUniformBindings()) {
      shaderJob.makeUniformBindings();
    }

    return result;

  }

  /**
   * Transforms the given shader job to produce a variant.
   *
   * @param fileOps                Handle for performing file operations.
   * @param referenceShaderJobFile The shader job to be transformed.
   * @param outputShaderJobFile    Output file for the variant.
   * @param generatorArguments     Arguments to control generation.
   * @param seed                   Seed for random number generation.
   * @param writeProbabilities     Records whether details about probabilities should be written.
   * @throws IOException           if file reading or writing goes wrong.
   * @throws ParseTimeoutException if parsing takes too long.
   * @throws InterruptedException  if something goes wrong invoking an external tool such as the
   *                               preprocessor or validator.
   * @throws GlslParserException   if a shader in the job fails to parse.
   */
  public static void generateVariant(ShaderJobFileOperations fileOps,
                                     File referenceShaderJobFile,
                                     File outputShaderJobFile,
                                     GeneratorArguments generatorArguments,
                                     int seed,
                                     boolean writeProbabilities)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    // This is mutated into the variant.
    final ShaderJob variantShaderJob = fileOps.readShaderJobFile(referenceShaderJobFile);

    final StringBuilder generationInfo = generateVariant(
        variantShaderJob,
        generatorArguments,
        seed);

    fileOps.writeShaderJobFile(
        variantShaderJob,
        outputShaderJobFile);

    if (writeProbabilities) {
      fileOps.writeAdditionalInfo(
          outputShaderJobFile,
          ".prob",
          generationInfo.toString());
    }

  }

  private static StringBuilder transformShader(TranslationUnit shaderToTransform,
                                               ShaderJob parentShaderJob,
                                               IRandom random,
                                               GeneratorArguments args) {
    final ShaderKind shaderKind = shaderToTransform.getShaderKind();
    StringBuilder result = new StringBuilder();
    result.append("======\n" + shaderKind + ":\n");

    if (args.getReplaceFloatLiterals()) {
      FloatLiteralReplacer.replace(
          shaderToTransform,
          parentShaderJob.getPipelineInfo());
    }
    parentShaderJob.getPipelineInfo().zeroUnsetUniforms(shaderToTransform);

    final GenerationParams generationParams =
        args.getSmall()
            ? GenerationParams.small(shaderKind, args.getAddInjectionSwitch())
            : GenerationParams.normal(shaderKind, args.getAddInjectionSwitch());

    final TransformationProbabilities probabilities =
        createProbabilities(args, random);

    result.append(probabilities);

    if (args.getAggressivelyComplicateControlFlow()) {
      result.append(applyControlFlowComplication(
          args,
          shaderToTransform,
          random,
          generationParams,
          probabilities));
    } else if (args.getSinglePass()) {
      result.append(applyTransformationsSinglePass(
          args,
          shaderToTransform,
          random,
          generationParams,
          probabilities));
    } else {
      result.append(applyTransformationsMultiPass(
          args,
          shaderToTransform,
          random,
          generationParams,
          probabilities));
    }

    if (args.getSmall()) {
      StripUnusedFunctions.strip(shaderToTransform);
      StripUnusedGlobals.strip(shaderToTransform);
    }

    randomiseUnsetUniforms(shaderToTransform, parentShaderJob.getPipelineInfo(),
        random.spawnChild());

    return result;

  }

  public static void main(String[] args) {
    try {
      mainHelper(args);
    } catch (Throwable exception) {
      LOGGER.error("", exception);
      System.exit(1);
    }
  }

  public static void mainHelper(String[] args)
      throws IOException, ParseTimeoutException, ArgumentParserException, InterruptedException,
      GlslParserException {
    final Namespace ns = parse(args);

    Integer seed = ns.get("seed");
    if (seed == null) {
      seed = new Random().nextInt();
    }

    generateVariant(
        new ShaderJobFileOperations(),
        ns.get("reference_json"),
        ns.get("output"),
        getGeneratorArguments(ns),
        seed,
        ns.getBoolean("write_probabilities"));
  }

  public static GeneratorArguments getGeneratorArguments(
      Namespace ns) {
    final EnabledTransformations enabledTransformations
        = getTransformationDisablingFlags(ns);
    return new GeneratorArguments(
        ns.getBoolean("small"),
        ns.getBoolean("allow_long_loops"),
        ns.getBoolean("single_pass"),
        ns.getBoolean("aggressively_complicate_control_flow"),
        ns.getBoolean("replace_float_literals"),
        ns.get("donors"),
        ns.get("generate_uniform_bindings"),
        ns.get("max_uniforms"),
        enabledTransformations,
        !ns.getBoolean("no_injection_switch"));
  }

  public static EnabledTransformations getTransformationDisablingFlags(Namespace ns) {
    final EnabledTransformations result = new EnabledTransformations();
    final List<Class<? extends ITransformation>> toDisable = new ArrayList<>();
    if (ns.get("disable") != null) {
      if (ns.get("enable_only") != null) {
        throw new RuntimeException("--disable and --enable_only are not compatible");
      }
      toDisable.addAll(EnabledTransformations.namesToList(ns.get("disable")));
    } else if (ns.get("enable_only") != null) {
      toDisable.addAll(EnabledTransformations.allTransformations());
      toDisable.removeAll(EnabledTransformations.namesToList(ns.get("enable_only")));
    }
    toDisable.forEach(result::disable);
    return result;
  }

  private static TransformationProbabilities createProbabilities(
      GeneratorArguments args,
      IRandom generator) {
    if (args.getAggressivelyComplicateControlFlow()) {
      return TransformationProbabilities.closeTo(generator,
          TransformationProbabilities.AGGRESSIVE_CONTROL_FLOW);
    }
    if (args.getSinglePass()) {
      return TransformationProbabilities.randomProbabilitiesSinglePass(generator);
    }
    return TransformationProbabilities.randomProbabilitiesMultiPass(generator);
  }

  private static String applyTransformationsMultiPass(GeneratorArguments args,
                                                      TranslationUnit reference,
                                                      IRandom generator,
                                                      GenerationParams generationParams,
                                                      TransformationProbabilities probabilities) {
    List<ITransformation> transformations = populateTransformations(args,
        generationParams, probabilities, reference.getShadingLanguageVersion(),
        reference.getShaderKind());
    {
      // Keep roughly half of them
      final List<ITransformation> toKeep = new ArrayList<>();
      while (!transformations.isEmpty()) {
        int index = generator.nextInt(transformations.size());
        if (transformations.size() == 1 && toKeep.isEmpty()) {
          toKeep.add(transformations.remove(index));
        } else {
          ITransformation candidate = transformations.remove(index);
          if (candidate instanceof AddSwitchTransformation) {
            // Compilers are so variable in whether they accept switch statements that
            // we generate, so we make this an unusual transformation to apply to avoid
            // tons of compile fail results.
            if (generator.nextInt(8) == 0) {
              toKeep.add(candidate);
            }
          } else if (generator.nextBoolean()) {
            toKeep.add(candidate);
          }
        }
      }
      transformations = toKeep;
    }

    List<ITransformation> nextRoundTransformations = new ArrayList<>();
    String result = "";
    // Keep applying transformations until all transformations cease to be effective, or
    // we get a large enough shader.
    while (!transformations.isEmpty() && !shaderLargeEnough(reference, generator)) {
      ITransformation transformation = transformations.remove(generator.nextInt(
          transformations.size()));
      result += transformation.getName() + "\n";
      if (transformation.apply(reference, probabilities,
          generator.spawnChild(),
          generationParams)) {
        // Keep the size down by stripping unused stuff.
        StripUnusedFunctions.strip(reference);
        StripUnusedGlobals.strip(reference);
        assert canTypeCheckWithoutFailure(reference, reference.getShadingLanguageVersion());

        // Only if the transformation applied successfully (i.e., made a change), do we add it
        // to the list of transformations to be applied next round.
        nextRoundTransformations.add(transformation);
      }
      if (transformations.isEmpty()) {
        transformations = nextRoundTransformations;
        nextRoundTransformations = new ArrayList<>();
      }
    }
    return result;
  }

  private static boolean canTypeCheckWithoutFailure(TranslationUnit reference,
                                                    ShadingLanguageVersion shadingLanguageVersion) {
    // Debugging aid: fail early if we end up messing up the translation unit so that type checking
    // does not work.
    new Typer(reference, shadingLanguageVersion);
    return true;
  }

  private static boolean shaderLargeEnough(TranslationUnit tu, IRandom generator) {
    final StatsVisitor statsVisitor = new StatsVisitor(tu);

    // WebGL:
    //final int minNodes = 3000;
    //final int maxNodes = 20000;

    final int minNodes = 5000;
    final int maxNodes = 22000;
    final int nodeLimit = generator.nextInt(maxNodes - minNodes) + minNodes;

    return statsVisitor.getNumNodes() > nodeLimit;

  }

  private static String applyTransformationsSinglePass(
      GeneratorArguments args,
      TranslationUnit reference,
      IRandom generator,
      GenerationParams generationParams,
      TransformationProbabilities probabilities) {
    String result = "";

    final List<ITransformation> transformations = populateTransformations(args,
        generationParams, probabilities, reference.getShadingLanguageVersion(),
        reference.getShaderKind());

    int numTransformationsApplied = 0;
    while (!transformations.isEmpty()) {
      int index = generator.nextInt(transformations.size());
      ITransformation transformation = transformations.remove(index);

      // We randomly choose whether to apply a transformation, unless this is the last
      // opportunity and we have not applied any transformation yet.
      if ((transformations.isEmpty() && numTransformationsApplied == 0)
          || decideToApplyTransformation(generator, numTransformationsApplied)) {
        result += transformation.getName() + "\n";
        transformation.apply(reference, probabilities,
            generator.spawnChild(),
            generationParams);
        numTransformationsApplied++;
      }
    }
    return result;
  }

  private static List<ITransformation> populateTransformations(
      GeneratorArguments args,
      GenerationParams generationParams,
      TransformationProbabilities probabilities,
      ShadingLanguageVersion shadingLanguageVersion,
      ShaderKind shaderKind) {
    List<ITransformation> result = new ArrayList<>();
    final EnabledTransformations flags = args.getEnabledTransformations();
    if (flags.isEnabledDead()) {
      result.add(new DonateDeadCodeTransformation(probabilities::donateDeadCodeAtStmt,
          args.getDonorsFolder(),
          generationParams));
    }
    if (flags.isEnabledJump()) {
      result.add(new AddJumpTransformation());
    }
    if (flags.isEnabledLive()) {
      result.add(new DonateLiveCodeTransformation(probabilities::donateLiveCodeAtStmt,
          args.getDonorsFolder(),
          generationParams, args.getAllowLongLoops()));
    }
    if (flags.isEnabledMutate()) {
      result.add(new IdentityTransformation());
    }
    if (flags.isEnabledOutline()) {
      result.add(new OutlineStatementTransformation());
    }
    if (flags.isEnabledSplit()) {
      result.add(new SplitForLoopTransformation());
    }
    if (flags.isEnabledStruct()) {
      result.add(new StructificationTransformation());
    }
    if (flags.isEnabledSwitch()
        && shadingLanguageVersion.supportedSwitchStmt()) {
      result.add(new AddSwitchTransformation());
    }
    if (flags.isEnabledVec()) {
      result.add(new VectorizeTransformation());
    }
    if (flags.isEnabledWrap()) {
      result.add(new AddWrappingConditionalTransformation());
    }
    if (generationParams.getShaderKind() == ShaderKind.FRAGMENT
        && flags.isEnabledDeadFragColorWrites()) {
      result.add(new AddDeadOutputWriteTransformation());
    }
    if (flags.isEnabledLiveFragColorWrites()) {
      result.add(new AddLiveOutputWriteTransformation());
    }
    if (shaderKind == ShaderKind.COMPUTE && flags.isEnabledDeadBarriers()) {
      result.add(new AddDeadBarrierTransformation());
    }
    if (result.isEmpty()) {
      throw new RuntimeException("At least one transformation must be enabled");
    }
    return result;
  }

  private static boolean decideToApplyTransformation(IRandom generator,
                                                     int numTransformationsAppliedSoFar) {
    return generator.nextFloat() < 0.5 * Math.pow(0.9, numTransformationsAppliedSoFar);
  }

  private static String applyControlFlowComplication(GeneratorArguments args,
                                                     TranslationUnit reference, IRandom generator,
                                                     GenerationParams generationParams,
                                                     TransformationProbabilities probabilities) {
    String result = "";
    List<ITransformation> transformations = new ArrayList<>();
    transformations.add(new AddJumpTransformation());
    transformations.add(new OutlineStatementTransformation());
    transformations.add(new AddWrappingConditionalTransformation());
    transformations.add(new AddSwitchTransformation());
    transformations.add(new AddDeadOutputWriteTransformation());
    transformations.add(new AddLiveOutputWriteTransformation());

    final int minIterations = 3;
    final int numIterations = minIterations + generator.nextInt(5);
    for (int i = 0; i < numIterations; i++) {
      ITransformation transformation = transformations
          .get(generator.nextInt(transformations.size()));
      transformation
          .apply(reference, probabilities,
              generator, generationParams);
      result += transformation.getName() + "\n";
    }
    return result;
  }

  public static void randomiseUnsetUniforms(TranslationUnit tu, PipelineInfo pipelineInfo,
        IRandom generator) {
    final Supplier<Float> floatSupplier = () -> generator.nextFloat();
    final Supplier<Integer> intSupplier = () -> generator.nextInt(1 << 15);
    final Supplier<Integer> uintSupplier = () -> generator.nextInt(1 << 15);
    final Supplier<Integer> boolSupplier = () -> generator.nextInt(2);
    pipelineInfo.setUniforms(tu, floatSupplier, intSupplier, uintSupplier, boolSupplier);
  }

  public static void addInjectionSwitchIfNotPresent(TranslationUnit tu) {
    if (alreadyDeclaresInjectionSwitch(tu)) {
      return;
    }
    tu.addDeclaration(new VariablesDeclaration(new QualifiedType(BasicType.VEC2,
        Arrays.asList(TypeQualifier.UNIFORM)),
        new VariableDeclInfo(Constants.INJECTION_SWITCH, null, null)));
  }

  private static boolean alreadyDeclaresInjectionSwitch(TranslationUnit tu) {
    return tu.getGlobalVarDeclInfos()
        .stream()
        .map(item -> item.getName())
        .collect(Collectors.toList())
        .contains(Constants.INJECTION_SWITCH);
  }

  public static void setInjectionSwitch(PipelineInfo pipelineInfo) {
    pipelineInfo.addUniform(Constants.INJECTION_SWITCH, BasicType.VEC2, Optional.empty(),
          Arrays.asList(new Float(0.0), new Float(1.0)));
  }

}
