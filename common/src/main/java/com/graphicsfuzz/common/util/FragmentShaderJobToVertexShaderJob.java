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

import com.graphicsfuzz.common.ast.IParentMap;
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
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.LocationLayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public final class FragmentShaderJobToVertexShaderJob {

  private static Optional<Integer> maybeGetArrayCount(VariableDeclInfo vdi) {
    if (vdi.hasArrayInfo()) {
      return Optional.of(vdi.getArrayInfo().getConstantSize());
    }
    return Optional.empty();
  }

  /**
   * Converts a shader job where the fragment shader does all of the work
   * into a shader job where the vertex shader does all of the work, discarding
   * the existing vertex shader and replacing the fragment shader with a
   * pass-through shader.
   *
   * @param sj A fragment shader based shader job
   * @return A vertex shader based shader job.
   */
  public static ShaderJob convertShaderJob(ShaderJob sj) throws Exception {
    // Get the current fragment shader and change it to vertex shader
    TranslationUnit vertexShader = sj.getFragmentShader().get();
    vertexShader.setShaderKind(ShaderKind.VERTEX);

    // Analyse the shader for used features (can't use anonymous class due to member access)
    class Analysis extends StandardVisitor {
      public boolean usesFragCoord = false;
      public boolean usesFragDepth = false;

      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRAG_COORD)) {
          usesFragCoord = true;
        }
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRAG_DEPTH)) {
          usesFragDepth = true;
        }
      }
    }
    
    final Analysis analysis = new Analysis();
    analysis.visit(vertexShader);


    /* Create a pass-through fragment shader, of the form:

        #version 430
        precision highp float;

        layout(location = 0) out vec4 _GLF_color;
        layout(location = 0) in vec4 frag_color;

        void main() {
          _GLF_color = frag_color;
        }
    */
    final String fragColor = "frag_color";

    final TranslationUnit fragmentShader = new TranslationUnit(ShaderKind.FRAGMENT,
        Optional.of(vertexShader.getShadingLanguageVersion()), Arrays.asList(
        new PrecisionDeclaration("precision highp float;"),
        new VariablesDeclaration(new QualifiedType(BasicType.VEC4,
            Arrays.asList(new LayoutQualifierSequence(
                    new LocationLayoutQualifier(0)),
                TypeQualifier.SHADER_OUTPUT)),
            new VariableDeclInfo(Constants.GLF_COLOR, null, null)),
        new VariablesDeclaration(new QualifiedType(BasicType.VEC4,
            Arrays.asList(new LayoutQualifierSequence(
                    new LocationLayoutQualifier(0)),
                TypeQualifier.SHADER_INPUT)),
            new VariableDeclInfo(fragColor, null, null)),
        new FunctionDefinition(
            new FunctionPrototype("main", VoidType.VOID, Collections.emptyList()),
            new BlockStmt(Arrays.asList(
                new ExprStmt(new BinaryExpr(
                    new VariableIdentifierExpr(Constants.GLF_COLOR),
                    new VariableIdentifierExpr(fragColor),
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

    if (analysis.usesFragDepth) {
      // Add a global _GLF_FragDepth that will replace gl_FragDepth
      vertexShader.addDeclarationBefore(
          new VariablesDeclaration(
              BasicType.FLOAT, new VariableDeclInfo(Constants.GLF_FRAGDEPTH, null, null)),
          firstNonPrecisionDeclaration);
    }

    if (analysis.usesFragCoord) {
      // Add a global _GLF_FragCoord that will replace gl_FragCoord
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
    }

    final IParentMap parentMap = IParentMap.createParentMap(vertexShader);
    new StandardVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        // Replace all instances of gl_FragCoord with _GLF_FragCoord
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRAG_COORD)) {
          variableIdentifierExpr.setName(Constants.GLF_FRAGCOORD);
        }
        // Replace all instances of gl_FragDepth with _GLF_FragDepth
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRAG_DEPTH)) {
          variableIdentifierExpr.setName(Constants.GLF_FRAGDEPTH);
        }
        // Replace all instances of _GLF_color with frag_color
        if (variableIdentifierExpr.getName().equals(Constants.GLF_COLOR)) {
          variableIdentifierExpr.setName(fragColor);
        }
      }

      // Also rename declarations of _GLF_color
      @Override
      public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
        final Type baseType = variablesDeclaration.getBaseType();
        visit(baseType);
        for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
          if (vdi.getName().equals(Constants.GLF_COLOR)) {
            vdi.setName(fragColor);
          }
          if (vdi.hasArrayInfo()) {
            visit(vdi.getArrayInfo().getSizeExpr());
          } else if (baseType instanceof ArrayType) {
            visit(((ArrayType) baseType).getArrayInfo().getSizeExpr());
          }
          if (vdi.hasInitializer()) {
            visit(vdi.getInitializer());
          }
        }
      }

      // Replace discard with a null statement
      @Override
      public void visitDiscardStmt(DiscardStmt discardStmt) {
        // Re
        parentMap.getParent(discardStmt).replaceChild(discardStmt, new NullStmt());
      }

      // Replace fragment-only functions with parenthesis expression
      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        // For most of the functions, they take only one parameter and it's the same
        // type as the output.
        if (Arrays.asList("dFdx", "dFdy", "dFdxCoarse", "dFdyCoarse", "dFdxFine", "dFdyFine",
            "fwidth", "fwidthCoarse", "fwidthFine", "interpolateAtCentroid")
            .contains(functionCallExpr.getCallee())) {
          assert functionCallExpr.getNumChildren() == 1;
          final Expr childExpr = functionCallExpr.getChild(0);
          parentMap.getParent(functionCallExpr).replaceChild(functionCallExpr,
              new ParenExpr(childExpr));
        }
        // the first parameter of interpolateAtOffset has same type as return type, so use it
        if (Arrays.asList("interpolateAtOffset").contains(functionCallExpr.getCallee())) {
          assert functionCallExpr.getNumChildren() == 2;
          final Expr childExpr = functionCallExpr.getChild(0);
          parentMap.getParent(functionCallExpr).replaceChild(functionCallExpr,
              new ParenExpr(childExpr));
        }
        for (int i = 0; i < functionCallExpr.getNumChildren(); i++) {
          visit(functionCallExpr.getChild(i));
        }
      }

    }.visit(vertexShader);

    final PipelineInfo pipelineInfo = sj.getPipelineInfo();

    pipelineInfo.addGridInfo(256, 256);

    // Return transformed shader job
    return new GlslShaderJob(Optional.empty(), pipelineInfo,
        Arrays.asList(vertexShader, fragmentShader));
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
