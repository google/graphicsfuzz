/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.DefaultLayout;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.BindingLayoutQualifier;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.LocalSizeLayoutQualifier;
import com.graphicsfuzz.common.ast.type.Std430LayoutQualifier;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.SsboFieldData;
import com.graphicsfuzz.generator.util.RemoveDiscardStatements;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Turns a fragment shader into a compute shader.  Intended as a best-effort bootstrapping
 * mechanism to get a source of compute shaders.
 */
public class Fragment2Compute {

  private static final Logger LOGGER = LoggerFactory.getLogger(Fragment2Compute.class);

  private static final String COMPUTE_SHADER_DATA = "_compute_data";

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("Fragment2Compute")
        .defaultHelp(true)
        .description("Turn a fragment shader into a compute shader.");

    // Required arguments
    parser.addArgument("fragment-json")
        .help("Input fragment shader JSON file.")
        .type(File.class);

    parser.addArgument("compute-json")
        .help("Output compute shader JSON file.")
        .type(File.class);

    parser.addArgument("--seed")
        .help("Seed for random number generator.")
        .type(Integer.class);

    parser.addArgument("--generate-uniform-bindings")
        .help("Put all uniforms in uniform blocks and generate bindings; required for Vulkan "
            + "compatibility.")
        .action(Arguments.storeTrue());

    return parser.parseArgs(args);

  }

  private static ShaderJob transform(ShaderJob fragmentShaderJob, IRandom generator) {
    assert fragmentShaderJob.getFragmentShader().isPresent();
    assert !fragmentShaderJob.getVertexShader().isPresent();
    assert !fragmentShaderJob.getComputeShader().isPresent();

    int localSizeX;
    int localSizeY;
    int localSizeZ;
    int numGroupsX;
    int numGroupsY;
    int numGroupsZ;
    int totalSize;

    do {
      localSizeX = generator.nextInt(40) + 1;
      localSizeY = generator.nextInt(40) + 1;
      localSizeZ = generator.nextInt(40) + 1;
      numGroupsX = generator.nextInt(7) + 1;
      numGroupsY = generator.nextInt(7) + 1;
      numGroupsZ = generator.nextInt(7) + 1;
      totalSize = localSizeX * localSizeY * localSizeZ * numGroupsX * numGroupsY * numGroupsZ;
    } while (totalSize >= 20000);

    final TranslationUnit computeTu = fragmentShaderJob.getFragmentShader().get().clone();
    final PipelineInfo computePipelineInfo = fragmentShaderJob.getPipelineInfo().clone();

    computeTu.setShaderKind(ShaderKind.COMPUTE);
    final String outputVariableName = demoteOutputVariable(computeTu);
    replaceFragCoordWithIdLookup(computeTu);
    addWritesToOutputBuffer(computeTu, outputVariableName);
    new RemoveDiscardStatements(computeTu); // Remove all discard statements.
    addComputeShaderStructures(computeTu, localSizeX, localSizeY, localSizeZ);
    final List<Float> zeros = new LinkedList<>();
    for (int i = 0; i < totalSize * 4; i++) {
      zeros.add(0.0f);
    }
    computePipelineInfo.addComputeInfo(numGroupsX, numGroupsY, numGroupsZ,
        0, Collections.singletonList(new SsboFieldData(BasicType.VEC4, zeros)));
    return new GlslShaderJob(fragmentShaderJob.getLicense(), computePipelineInfo, computeTu);
  }

  /**
   * <p>
   * Find the single output variable for the fragment shader, demote it to a regular global
   * variable, and return its name.
   * </p>
   * <p>
   * Complain if there are multiple or no output variables
   * </p>
   */
  private static String demoteOutputVariable(TranslationUnit tu) {
    List<VariablesDeclaration> outputVariables =
        tu.getTopLevelDeclarations().stream()
          .filter(item -> item instanceof VariablesDeclaration)
          .map(item -> (VariablesDeclaration) item)
          .filter(item -> item.getBaseType().hasQualifier(TypeQualifier.SHADER_OUTPUT))
          .collect(Collectors.toList());
    assert 1 == outputVariables.size();
    assert 1 == outputVariables.get(0).getNumDecls();
    assert BasicType.VEC4 == outputVariables.get(0).getBaseType().getWithoutQualifiers();
    outputVariables.get(0).setBaseType(outputVariables.get(0).getBaseType().getWithoutQualifiers());
    return outputVariables.get(0).getDeclInfo(0).getName();
  }

  /**
   * Replace all references to gl_FragCoord with gl_GlobalInvocationID.
   */
  private static void replaceFragCoordWithIdLookup(TranslationUnit computeTu) {
    new ScopeTreeBuilder() {

      private IParentMap parentMap = IParentMap.createParentMap(computeTu);

      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRAG_COORD)) {
          // It has the right name.
          if (currentScope.lookupScopeEntry(OpenGlConstants.GL_FRAG_COORD) == null) {
            // It has no scope; i.e., it is built-in - so it is the real gl_FragCoord.
            // Replace it with something made from gl_GlobalInvocationID.
            parentMap.getParent(variableIdentifierExpr).replaceChild(variableIdentifierExpr,
                new TypeConstructorExpr(
                    BasicType.VEC4.toString(),
                    getGlobalInvocationIdCastToFloat("x"),
                    getGlobalInvocationIdCastToFloat("y"),
                    getGlobalInvocationIdCastToFloat("z"),
                    new FloatConstantExpr("0.0")));
          }
        }
      }
    }.visit(computeTu);
  }

  public static TypeConstructorExpr getGlobalInvocationIdCastToFloat(String dimension) {
    return new TypeConstructorExpr(BasicType.FLOAT.toString(),
        getGlobalInvocationId(dimension));
  }

  public static MemberLookupExpr getGlobalInvocationId(String dimension) {
    return new MemberLookupExpr(new VariableIdentifierExpr(OpenGlConstants.GL_GLOBAL_INVOCATION_ID),
        dimension);
  }

  /**
   * Instrument main so that before each return, the output variable is written to the output
   * buffer.
   */
  private static void addWritesToOutputBuffer(TranslationUnit computeTu,
                                              String outputVariableName) {
    // Get the main function, which is assumed to exist.
    final FunctionDefinition mainFunction = computeTu.getMainFunction();

    // Make sure there is an explicit return at the end of main.
    if (mainFunction.getBody().getNumStmts() == 0
        || !(mainFunction.getBody().getStmt(mainFunction.getBody().getNumStmts() - 1)
          instanceof ReturnStmt)) {
      mainFunction.getBody().addStmt(new ReturnStmt());
    }

    new StandardVisitor() {

      private IParentMap parentMap = IParentMap.createParentMap(computeTu);

      @Override
      public void visitReturnStmt(ReturnStmt returnStmt) {
        final Expr indexExpr =
            new BinaryExpr(
              new BinaryExpr(
                new BinaryExpr(
                    getGlobalInvocationId("z"),
                    new BinaryExpr(getWorkGroupSizeTimesNumWorkGroups("y"),
                                   getWorkGroupSizeTimesNumWorkGroups("x"),
                                   BinOp.MUL),
                    BinOp.MUL),
                new BinaryExpr(
                    getGlobalInvocationId("y"),
                    getWorkGroupSizeTimesNumWorkGroups("x"),
                    BinOp.MUL),
                BinOp.ADD),
              getGlobalInvocationId("x"),
              BinOp.ADD);

        final Stmt assignment = new ExprStmt(
            new BinaryExpr(
                new ArrayIndexExpr(
                    new VariableIdentifierExpr(COMPUTE_SHADER_DATA),
                        indexExpr),
                new VariableIdentifierExpr(outputVariableName),
                BinOp.ASSIGN));
        parentMap.getParent(returnStmt).replaceChild(returnStmt,
            new BlockStmt(Arrays.asList(assignment, returnStmt), true));
      }

    }.visit(mainFunction);

  }

  public static BinaryExpr getWorkGroupSizeTimesNumWorkGroups(String dimension) {
    return new BinaryExpr(new MemberLookupExpr(new
        VariableIdentifierExpr(OpenGlConstants.GL_WORK_GROUP_SIZE),
dimension),
new MemberLookupExpr(new VariableIdentifierExpr(OpenGlConstants.GL_NUM_WORK_GROUPS), dimension),
        BinOp.MUL);
  }

  /**
   * Add SSBO, etc.
   */
  private static void addComputeShaderStructures(TranslationUnit computeTu,
                                                 int localSizeX,
                                                 int localSizeY,
                                                 int localSizeZ) {

    computeTu.addDeclaration(
        new DefaultLayout(new LayoutQualifierSequence(
            new LocalSizeLayoutQualifier("x", localSizeX),
            new LocalSizeLayoutQualifier("y", localSizeY),
            new LocalSizeLayoutQualifier("z", localSizeZ)),
            TypeQualifier.SHADER_INPUT)
    );

    computeTu.addDeclaration(new InterfaceBlock(
        Optional.of(new LayoutQualifierSequence(new Std430LayoutQualifier(),
            new BindingLayoutQualifier(0))),
        TypeQualifier.BUFFER,
        "doesNotMatter",
        Arrays.asList(COMPUTE_SHADER_DATA),
        Arrays.asList(new ArrayType(BasicType.VEC4, new ArrayInfo())),
        Optional.empty()));

  }

  public static void mainHelper(String... args) throws ArgumentParserException,
      InterruptedException, GlslParserException, ParseTimeoutException, IOException {
    final Namespace ns = parse(args);
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final IRandom generator = new RandomWrapper(ns.getInt("seed") == null
        ? new Random().nextInt() : ns.getInt("seed"));

    final ShaderJob transformedShaderJob = transform(
        fileOps.readShaderJobFile(ns.get("fragment_json")), generator);
    if (ns.getBoolean("generate_uniform_bindings")) {
      transformedShaderJob.makeUniformBindings();
    }
    fileOps.writeShaderJobFile(transformedShaderJob,
        ns.get("compute_json"));
  }

  public static void main(String[] args) {
    try {
      mainHelper(args);
    } catch (Throwable exception) {
      LOGGER.error("", exception);
      System.exit(1);
    }
  }

}
