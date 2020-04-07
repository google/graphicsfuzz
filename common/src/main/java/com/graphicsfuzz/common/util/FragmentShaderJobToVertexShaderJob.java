/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.LocationLayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.util.ArgsUtil;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public final class FragmentShaderJobToVertexShaderJob {

  private FragmentShaderJobToVertexShaderJob() {
    // Utility class
  }

  private static Optional<Integer> maybeGetArrayCount(VariableDeclInfo vdi) {
    if (vdi.hasArrayInfo()) {
      return Optional.of(vdi.getArrayInfo().getConstantSize());
    }
    return Optional.empty();
  }

  /**
   * Creates a shader job with the given translation unit as its fragment shader, a pipeline state
   * that provides a random value for every uniform declared in the shader, and if needed a vertex
   * shader that provides outputs for the fragment shader's inputs.
   *
   * @param tu A fragment shader.
   * @return A shader job that includes the fragment shader.
   */


  public static ShaderJob convertShaderJob(ShaderJob sj) throws Exception {
    final PipelineInfo pipelineInfo = sj.getPipelineInfo();
    final List<TranslationUnit> shaders = new ArrayList<>();
    
    // Get the current fragment shader and change it to vertex shader
    TranslationUnit vertexShader = sj.getFragmentShader().get();
    vertexShader.setShaderKind(ShaderKind.VERTEX);

    // Create pass-through fragment shader
    final TranslationUnit fragmentShader = new TranslationUnit(ShaderKind.FRAGMENT,
        Optional.of(ShadingLanguageVersion.GLSL_430), Arrays.asList(
        new PrecisionDeclaration("precision highp float;"),
        new VariablesDeclaration(new QualifiedType(BasicType.VEC4,
            Arrays.asList(new LayoutQualifierSequence(
                    new LocationLayoutQualifier(0)),
                TypeQualifier.SHADER_INPUT)),
            new VariableDeclInfo(Constants.GLF_COLOR, null, null)),
        new VariablesDeclaration(new QualifiedType(BasicType.VEC4,
            Arrays.asList(new LayoutQualifierSequence(
                    new LocationLayoutQualifier(0)),
                TypeQualifier.SHADER_OUTPUT)),
            new VariableDeclInfo("frag_color", null, null)),
        new FunctionDefinition(
            new FunctionPrototype("main", VoidType.VOID, Collections.emptyList()),
            new BlockStmt(Arrays.asList(
                new ExprStmt(new BinaryExpr(
                    new VariableIdentifierExpr("frag_color"),
                    new VariableIdentifierExpr(Constants.GLF_COLOR),
                    BinOp.ASSIGN))), false))));

    // Find the first declaration that is not a precision declaration.
    Declaration firstNonPrecisionDeclaration = null;
    for (Declaration decl : vertexShader.getTopLevelDeclarations()) {
      if (decl instanceof PrecisionDeclaration) {
        continue;
      }
      firstNonPrecisionDeclaration = decl;
      break;
    }

    assert firstNonPrecisionDeclaration != null;

    // Add a global _GLF_FragCoord that will replace gl_Fragcoord
    vertexShader.addDeclarationBefore(
        new VariablesDeclaration(
            BasicType.VEC4, new VariableDeclInfo(Constants.GLF_FRAGCOORD, null, null)),
        firstNonPrecisionDeclaration);

    // Add vertex position input
    vertexShader.addDeclarationBefore(
        new VariablesDeclaration(new QualifiedType(BasicType.VEC4,
        Arrays.asList(new LayoutQualifierSequence(
                new LocationLayoutQualifier(0)),
            TypeQualifier.SHADER_INPUT)),
        new VariableDeclInfo(Constants.GLF_POS, null, null)),
        firstNonPrecisionDeclaration);

    // At start of main, populate _GLF_FragCoord with coordinates calculated from vertex input
    vertexShader.getMainFunction().getBody().insertStmt(0,
        new ExprStmt(
            new BinaryExpr(
                new VariableIdentifierExpr(Constants.GLF_FRAGCOORD),
                    new BinaryExpr(
                    new ParenExpr(
                    new BinaryExpr(
                        new VariableIdentifierExpr(Constants.GLF_POS),
                        new TypeConstructorExpr(BasicType.VEC4.toString(),
                            new FloatConstantExpr("1.0"),
                            new FloatConstantExpr("1.0"),
                            new FloatConstantExpr("0.0"),
                            new FloatConstantExpr("0.0")),
                        BinOp.ADD)),
                        new TypeConstructorExpr(BasicType.VEC4.toString(),
                            new FloatConstantExpr("128.0"),
                            new FloatConstantExpr("128.0"),
                            new FloatConstantExpr("1.0"),
                            new FloatConstantExpr("1.0")),
                        BinOp.MUL),
            BinOp.ASSIGN)));

    // Replace all instances of gl_FragCoord with _GLF_FragCoord
    new StandardVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRAG_COORD)) {
          variableIdentifierExpr.setName(Constants.GLF_FRAGCOORD);
        }
      }
    }.visit(vertexShader);

    // Add both shaders to list
    shaders.add(fragmentShader);
    shaders.add(vertexShader);

    // Return transformed shader job
    return new GlslShaderJob(Optional.empty(), pipelineInfo, shaders);
  }

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("FragmentShaderToShaderJob")
        .defaultHelp(true)
        .description("Turns a fragment shader into a shader job with randomized uniforms and "
            + "(if needed) a suitable vertex shader.");

    // Required arguments
    parser.addArgument("shader")
        .help("Path of .frag shader to be turned into a shader job.")
        .type(File.class);

    parser.addArgument("output")
        .help("Target shader job .json file.")
        .type(String.class);

    parser.addArgument("--seed")
        .help("Seed (unsigned 64 bit long integer) for the random number generator.")
        .type(String.class);

    return parser.parseArgs(args);
  }

  public static void main(String[] args) throws IOException, InterruptedException {

    try {
      Namespace ns = parse(args);
      long startTime = System.currentTimeMillis();
      final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
      final ShaderJob input = fileOperations.readShaderJobFile(new File(ns.getString("shader")));
      long endTime = System.currentTimeMillis();
      System.err.println("Time for parsing: " + (endTime - startTime));

      startTime = System.currentTimeMillis();
      final ShaderJob result = convertShaderJob(input);
      endTime = System.currentTimeMillis();
      System.err.println("Time for converting shader job: " + (endTime - startTime));

      fileOperations.writeShaderJobFile(result, new File(ns.getString("output")));
    } catch (Throwable exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }
}
