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
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.PruneUniforms;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.StatsVisitor;
import com.graphicsfuzz.common.util.StripUnusedFunctions;
import com.graphicsfuzz.common.util.StripUnusedGlobals;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.FloatLiteralReplacer;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.controlflow.AddDeadOutputVariableWrites;
import com.graphicsfuzz.generator.transformation.controlflow.AddJumpStmts;
import com.graphicsfuzz.generator.transformation.controlflow.AddLiveOutputVariableWrites;
import com.graphicsfuzz.generator.transformation.controlflow.AddSwitchStmts;
import com.graphicsfuzz.generator.transformation.controlflow.AddWrappingConditionalStmts;
import com.graphicsfuzz.generator.transformation.controlflow.SplitForLoops;
import com.graphicsfuzz.generator.transformation.donation.DonateDeadCode;
import com.graphicsfuzz.generator.transformation.donation.DonateLiveCode;
import com.graphicsfuzz.generator.transformation.mutator.MutateExpressions;
import com.graphicsfuzz.generator.transformation.outliner.OutlineStatements;
import com.graphicsfuzz.generator.transformation.structifier.Structification;
import com.graphicsfuzz.generator.transformation.vectorizer.VectorizeStatements;
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
    parser.addArgument("reference_json")
        .help("Input reference shader json file.")
        .type(File.class);

    parser.addArgument("donors")
          .help("Path of folder of donor shaders.")
          .type(File.class);

    parser.addArgument("glsl_version")
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
          .setDefault(new Random().nextInt())
          .type(Integer.class);

    parser.addArgument("--webgl")
          .help("Restrict to WebGL-compatible features.")
          .action(Arguments.storeTrue());

    parser.addArgument("--small")
          .help("Try to generate small shaders.")
          .action(Arguments.storeTrue());

    parser.addArgument("--avoid_long_loops")
          .help("During live code injection, reduce the chances of injecting lengthy loops by "
                + "ensuring that loops do not appear *directly* in the code being injected; they "
                + "may still appear in functions called by the injected code (avoidance of this "
                + "could be added if needed).")
          .action(Arguments.storeTrue());

    parser.addArgument("--disable")
          .help("Disable a given series of transformations.")
          .type(String.class);

    parser.addArgument("--enable_only")
        .help("Disable all but the given series of transformations.")
        .type(String.class);

    parser.addArgument("--aggressively_complicate_control_flow")
          .help("Make control flow very complicated.")
          .action(Arguments.storeTrue());

    parser.addArgument("--multi_pass")
          .help("Apply multiple transformation passes, each with low probablity.")
          .action(Arguments.storeTrue());

    parser.addArgument("--replace_float_literals")
          .help("Replace float literals with uniforms.")
          .action(Arguments.storeTrue());

    parser.addArgument("--generate_uniform_bindings")
        .help("Put all uniforms in uniform blocks and generate bindings; required for Vulkan "
            + "compatibility.")
        .action(Arguments.storeTrue());

    parser.addArgument("--max_uniforms")
        .help("Ensure that generated shaders have no more than the given number of uniforms; "
            + "required for Vulkan compatibility.")
        .setDefault(0)
        .type(Integer.class);

  }

  /**
   * Mutates the given shader job into a variant.
   * @param shaderJob The shader job to be mutated.
   * @param args Arguments to control generation.
   * @return Details of the transformations that were applied.
   */
  public static StringBuilder generateVariant(ShaderJob shaderJob,
                                              GeneratorArguments args) {
    final StringBuilder result = new StringBuilder();
    final IRandom random = new RandomWrapper(args.getSeed());

    for (TranslationUnit shader : shaderJob.getShaders()) {
      addInjectionSwitchIfNotPresent(shader);
    }
    setInjectionSwitch(shaderJob.getUniformsInfo());

    for (TranslationUnit tu : shaderJob.getShaders()) {
      result.append(transformShader(
          tu,
          shaderJob,
          random.spawnChild(),
          args));
      tu.setShadingLanguageVersion(args.getShadingLanguageVersion());
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
          parentShaderJob.getUniformsInfo(),
          args.getShadingLanguageVersion());
    }
    parentShaderJob.getUniformsInfo().zeroUnsetUniforms(shaderToTransform);

    final GenerationParams generationParams =
            args.getSmall()
                    ? GenerationParams.small(shaderKind)
                    : GenerationParams.normal(shaderKind);

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
    } else if (args.getMultiPass()) {
      result.append(applyTransformationsMultiPass(
          args,
          shaderToTransform,
          random,
          generationParams,
          probabilities));
    } else {
      result.append(applyTransformationsRandomly(
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

    randomiseUnsetUniforms(shaderToTransform, parentShaderJob.getUniformsInfo(),
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
      throws IOException, ParseTimeoutException, ArgumentParserException {

    final Namespace ns = parse(args);

    final EnabledTransformations enabledTransformations
          = getTransformationDisablingFlags(ns);
    final ShadingLanguageVersion shadingLanguageVersion = ns.get("webgl")
        ? ShadingLanguageVersion.webGlFromVersionString(ns.get("glsl_version"))
        : ShadingLanguageVersion.fromVersionString(ns.get("glsl_version"));

    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final File referenceFile = ns.get("reference_json");

    // This is mutated into the variant.
    final ShaderJob variantShaderJob = fileOps.readShaderJobFile(referenceFile);
    final StringBuilder generationInfo = generateVariant(
        variantShaderJob,
        new GeneratorArguments(shadingLanguageVersion,
              ns.get("seed"),
              ns.getBoolean("small"),
              ns.getBoolean("avoid_long_loops"),
              ns.getBoolean("multi_pass"),
              ns.getBoolean("aggressively_complicate_control_flow"),
              ns.getBoolean("replace_float_literals"),
              ns.get("donors"),
              ns.get("generate_uniform_bindings"),
              ns.get("max_uniforms"),
              enabledTransformations));

    final File outputShaderJobFile = ns.get("output");

    fileOps.writeShaderJobFile(
        variantShaderJob,
        outputShaderJobFile);

    fileOps.writeAdditionalInfo(
        outputShaderJobFile,
        ".prob",
        generationInfo.toString());

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
    if (args.getMultiPass()) {
      return TransformationProbabilities.randomProbabilitiesMultiPass(generator);
    }
    return TransformationProbabilities.randomProbabilitiesSinglePass(generator);
  }

  private static String applyTransformationsMultiPass(GeneratorArguments args,
        TranslationUnit reference, IRandom generator,
        GenerationParams generationParams, TransformationProbabilities probabilities) {
    List<ITransformation> transformations = populateTransformations(args,
          generationParams, probabilities);
    {
      // Keep roughly half of them
      final List<ITransformation> toKeep = new ArrayList<>();
      while (!transformations.isEmpty()) {
        int index = generator.nextInt(transformations.size());
        if (transformations.size() == 1 && toKeep.isEmpty()) {
          toKeep.add(transformations.remove(index));
        } else {
          ITransformation candidate = transformations.remove(index);
          if (candidate instanceof AddSwitchStmts) {
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

    List<ITransformation> done = new ArrayList<>();
    String result = "";
    while (!shaderLargeEnough(reference, generator)) {
      ITransformation transformation = transformations.remove(generator.nextInt(
            transformations.size()));
      result += transformation.getName() + "\n";
      transformation.apply(reference, probabilities, args.getShadingLanguageVersion(),
            generator.spawnChild(),
            generationParams);
      // Keep the size down by stripping unused stuff.
      StripUnusedFunctions.strip(reference);
      StripUnusedGlobals.strip(reference);
      assert canTypeCheckWithoutFailure(reference, args.getShadingLanguageVersion());
      done.add(transformation);
      if (transformations.isEmpty()) {
        transformations = done;
        done = new ArrayList<>();
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

  private static String applyTransformationsRandomly(
        GeneratorArguments args,
        TranslationUnit reference,
        IRandom generator,
        GenerationParams generationParams,
        TransformationProbabilities probabilities) {
    String result = "";

    final List<ITransformation> transformations = populateTransformations(args,
          generationParams, probabilities);

    int numTransformationsApplied = 0;
    while (!transformations.isEmpty()) {
      int index = generator.nextInt(transformations.size());
      ITransformation transformation = transformations.remove(index);

      // We randomly choose whether to apply a transformation, unless this is the last
      // opportunity and we have not applied any transformation yet.
      if ((transformations.isEmpty() && numTransformationsApplied == 0)
            || decideToApplyTransformation(generator, numTransformationsApplied)) {
        result += transformation.getName() + "\n";
        transformation.apply(reference, probabilities, args.getShadingLanguageVersion(),
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
          TransformationProbabilities probabilities) {
    List<ITransformation> result = new ArrayList<>();
    final EnabledTransformations flags = args.getEnabledTransformations();
    if (flags.isEnabledDead()) {
      result.add(new DonateDeadCode(probabilities::donateDeadCodeAtStmt, args.getDonorsFolder(),
              generationParams));
    }
    if (flags.isEnabledJump()) {
      result.add(new AddJumpStmts());
    }
    if (flags.isEnabledLive()) {
      result.add(new DonateLiveCode(probabilities::donateLiveCodeAtStmt, args.getDonorsFolder(),
              generationParams, args.getAvoidLongLoops()));
    }
    if (flags.isEnabledMutate()) {
      result.add(new MutateExpressions());
    }
    if (flags.isEnabledOutline()) {
      result.add(new OutlineStatements(new IdGenerator()));
    }
    if (flags.isEnabledSplit()) {
      result.add(new SplitForLoops());
    }
    if (flags.isEnabledStruct()) {
      result.add(new Structification());
    }
    if (flags.isEnabledSwitch()
            && args.getShadingLanguageVersion().supportedSwitchStmt()) {
      result.add(new AddSwitchStmts());
    }
    if (flags.isEnabledVec()) {
      result.add(new VectorizeStatements());
    }
    if (flags.isEnabledWrap()) {
      result.add(new AddWrappingConditionalStmts());
    }
    if (generationParams.getShaderKind() == ShaderKind.FRAGMENT
            && flags.isEnabledDeadFragColorWrites()) {
      result.add(new AddDeadOutputVariableWrites());
    }
    if (flags.isEnabledLiveFragColorWrites()) {
      result.add(new AddLiveOutputVariableWrites());
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
        TranslationUnit reference, IRandom generator, GenerationParams generationParams,
        TransformationProbabilities probabilities) {
    String result = "";
    List<ITransformation> transformations = new ArrayList<>();
    transformations.add(new AddJumpStmts());
    transformations.add(new OutlineStatements(new IdGenerator()));
    transformations.add(new AddWrappingConditionalStmts());
    transformations.add(new AddSwitchStmts());
    transformations.add(new AddDeadOutputVariableWrites());
    transformations.add(new AddLiveOutputVariableWrites());

    final int minIterations = 3;
    final int numIterations = minIterations + generator.nextInt(5);
    for (int i = 0; i < numIterations; i++) {
      ITransformation transformation = transformations
            .get(generator.nextInt(transformations.size()));
      transformation
            .apply(reference, probabilities, args.getShadingLanguageVersion(),
                generator, generationParams);
      result += transformation.getName() + "\n";
    }
    return result;
  }

  public static void randomiseUnsetUniforms(TranslationUnit tu, UniformsInfo uniformsInfo,
        IRandom generator) {
    final Supplier<Float> floatSupplier = () -> generator.nextFloat();
    final Supplier<Integer> intSupplier = () -> generator.nextInt(1 << 15);
    final Supplier<Integer> uintSupplier = () -> generator.nextInt(1 << 15);
    final Supplier<Integer> boolSupplier = () -> generator.nextInt(2);
    uniformsInfo.setUniforms(tu, floatSupplier, intSupplier, uintSupplier, boolSupplier);
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
  
  public static void setInjectionSwitch(UniformsInfo uniformsInfo) {
    uniformsInfo.addUniform(Constants.INJECTION_SWITCH, BasicType.VEC2, Optional.empty(),
          Arrays.asList(new Float(0.0), new Float(1.0)));
  }

}
